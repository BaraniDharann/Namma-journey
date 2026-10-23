package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where every online driver is right now, so a Ride Now booking can find who is near its pickup.
 *
 * <p>Positions live in a Redis GEO set, which answers "who is within 3 km of here, nearest first"
 * in one command for every backend instance. GEO members never expire on their own, so each
 * driver also has a "last seen" key with a TTL: a driver whose phone stopped reporting (app
 * closed, signal lost) drops out of searches once it lapses, and is pruned from the set then.
 *
 * <p>Redis is optional, as it is for live tracking: when it is off or unreachable an in-memory
 * map and a haversine scan take over. That only sees drivers reporting to this instance, which
 * is exactly right for a single-instance deployment and degraded, not broken, for more.
 */
@Service
public class DriverPresenceService {

    private static final Logger log = LoggerFactory.getLogger(DriverPresenceService.class);

    static final String GEO_KEY = "travelplatform::drivers:geo";
    static final String SEEN_PREFIX = "travelplatform::drivers:seen:";

    private static final long REDIS_COOLDOWN_MS = 60_000L;

    /** A driver found near a pickup, and how far away. */
    public record Nearby(long driverId, double distanceKm) {
    }

    private record Fix(double lat, double lon, long seenAtMillis) {
    }

    private final ConcurrentHashMap<Long, Fix> localFixes = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final boolean redisConfigured;
    private final DispatchProperties properties;
    private final AtomicLong redisMutedUntil = new AtomicLong(0L);

    /** Not final so tests can move time. */
    private Clock clock = Clock.systemUTC();

    public DriverPresenceService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                 @Value("${app.cache.type:redis}") String cacheType,
                                 DispatchProperties properties) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.redisConfigured = "redis".equalsIgnoreCase(cacheType) && this.redisTemplate != null;
        this.properties = properties;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Records that this driver is online at this position. */
    public void report(long driverId, double lat, double lon) {
        if (!validCoordinate(lat, lon)) {
            return;
        }
        if (redisUsable()) {
            try {
                redisTemplate.opsForGeo().add(GEO_KEY, new Point(lon, lat), Long.toString(driverId));
                redisTemplate.opsForValue().set(SEEN_PREFIX + driverId, "1", ttl());
                return;
            } catch (Exception e) {
                muteRedis("write", e);
            }
        }
        localFixes.put(driverId, new Fix(lat, lon, clock.millis()));
    }

    /** The driver went offline: no more offers until they report again. */
    public void remove(long driverId) {
        localFixes.remove(driverId);
        if (redisUsable()) {
            try {
                redisTemplate.opsForGeo().remove(GEO_KEY, Long.toString(driverId));
                redisTemplate.delete(SEEN_PREFIX + driverId);
            } catch (Exception e) {
                muteRedis("delete", e);
            }
        }
    }

    /** Drivers seen recently within {@code radiusKm} of the point, nearest first. */
    public List<Nearby> findWithin(double lat, double lon, double radiusKm) {
        if (!validCoordinate(lat, lon) || radiusKm <= 0) {
            return List.of();
        }
        if (redisUsable()) {
            try {
                return searchRedis(lat, lon, radiusKm);
            } catch (Exception e) {
                muteRedis("search", e);
            }
        }
        return searchLocal(lat, lon, radiusKm);
    }

    private List<Nearby> searchRedis(double lat, double lon, double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(lon, lat),
                new Distance(radiusKm, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending());
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }
        List<String> members = new ArrayList<>();
        List<String> seenKeys = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
            members.add(r.getContent().getName());
            seenKeys.add(SEEN_PREFIX + r.getContent().getName());
        }
        List<String> seen = redisTemplate.opsForValue().multiGet(seenKeys);
        List<Nearby> nearby = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            String member = members.get(i);
            if (seen == null || seen.get(i) == null) {
                stale.add(member);
                continue;
            }
            try {
                nearby.add(new Nearby(Long.parseLong(member),
                        results.getContent().get(i).getDistance().getValue()));
            } catch (NumberFormatException ignored) {
                stale.add(member);
            }
        }
        if (!stale.isEmpty()) {
            redisTemplate.opsForGeo().remove(GEO_KEY, stale.toArray(new String[0]));
        }
        return nearby;
    }

    private List<Nearby> searchLocal(double lat, double lon, double radiusKm) {
        long freshAfter = clock.millis() - ttl().toMillis();
        List<Nearby> nearby = new ArrayList<>();
        localFixes.forEach((driverId, fix) -> {
            if (fix.seenAtMillis() < freshAfter) {
                localFixes.remove(driverId, fix);
                return;
            }
            double km = TripRules.distanceKm(lat, lon, fix.lat(), fix.lon());
            if (km <= radiusKm) {
                nearby.add(new Nearby(driverId, km));
            }
        });
        nearby.sort(Comparator.comparingDouble(Nearby::distanceKm));
        return nearby;
    }

    private Duration ttl() {
        return Duration.ofSeconds(Math.max(1, properties.getPresenceTtlSeconds()));
    }

    // Redis GEO refuses latitudes beyond ±85.05; a phone reporting NaN or (0,0) has no real fix.
    private static boolean validCoordinate(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && Math.abs(lat) <= 85.05 && Math.abs(lon) <= 180
                && !(lat == 0 && lon == 0);
    }

    private boolean redisUsable() {
        return redisConfigured && System.currentTimeMillis() >= redisMutedUntil.get();
    }

    private void muteRedis(String operation, Exception e) {
        long until = System.currentTimeMillis() + REDIS_COOLDOWN_MS;
        if (redisMutedUntil.getAndSet(until) <= System.currentTimeMillis() - REDIS_COOLDOWN_MS) {
            log.warn("Redis driver-presence {} failed; using in-memory presence for {}s: {}",
                    operation, REDIS_COOLDOWN_MS / 1000, e.toString());
        }
    }
}
