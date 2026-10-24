package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.City;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where every online driver is right now, so a City ride can find who is near its pickup.
 *
 * <p>Drivers are kept in one location set per city, not one for the whole country: a search
 * only ever looks inside the pickup's city, and a set of a few thousand drivers answers "the
 * nearest 30 within 3 km" far faster than one of a hundred thousand. Each driver also has a
 * "which city am I in" key with a TTL. It does two jobs: a driver whose phone stopped reporting
 * drops out of searches once it lapses, and a driver who drove into the next city is skipped
 * (and pruned) in the old city's set even if the move's clean-up was lost.
 *
 * <p>Phones report every few seconds, and both the location endpoint and the presence endpoint
 * report. A report less than {@value #MIN_REPORT_GAP_MS} ms after the last accepted one is
 * dropped: a driver does not move far enough in that time to change who is nearest, and at scale
 * the writes saved are most of them.
 *
 * <p>Redis is optional, as it is for live tracking: when it is off or unreachable in-memory maps
 * and a haversine scan take over. That only sees drivers reporting to this instance, which is
 * exactly right for a single-instance deployment and degraded, not broken, for more.
 */
@Service
public class DriverPresenceService {

    private static final Logger log = LoggerFactory.getLogger(DriverPresenceService.class);

    static final String CITY_GEO_PREFIX = "travelplatform::geo:city:";
    static final String DRIVER_PREFIX = "travelplatform::driver:";

    static final long MIN_REPORT_GAP_MS = 5_000L;
    private static final long REDIS_COOLDOWN_MS = 60_000L;

    /** A driver found near a pickup, and how far away. */
    public record Nearby(long driverId, double distanceKm) {
    }

    private record Fix(double lat, double lon, long seenAtMillis) {
    }

    /** cityId -> (driverId -> last fix). The fallback's per-city location sets. */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Fix>> localByCity = new ConcurrentHashMap<>();
    /** driverId -> the city whose set holds them, so a move can take them out of the old one. */
    private final ConcurrentHashMap<Long, Long> localCityOf = new ConcurrentHashMap<>();
    /** driverId -> when their last report was accepted, for the fallback's throttle. */
    private final ConcurrentHashMap<Long, AtomicLong> lastAccepted = new ConcurrentHashMap<>();

    private final StringRedisTemplate redisTemplate;
    private final boolean redisConfigured;
    private final DispatchProperties properties;
    private final AtomicLong redisMutedUntil = new AtomicLong(0L);

    /** Which city a fix is in. Absent only in hand-built tests that never report; then nobody is indexed. */
    @Autowired(required = false)
    private CityService cityService;

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

    /**
     * Records that this driver is online at this position, in the city that contains it.
     *
     * @return the city the driver was placed in; empty when the fix is junk, outside every city
     *         (the driver is then taken out of any city they were in), or too soon after the last
     */
    public Optional<Long> report(long driverId, double lat, double lon) {
        if (!validCoordinate(lat, lon)) {
            return Optional.empty();
        }
        if (redisUsable()) {
            try {
                return reportRedis(driverId, lat, lon);
            } catch (Exception e) {
                muteRedis("write", e);
            }
        }
        return reportLocal(driverId, lat, lon);
    }

    /** The driver went offline: no more offers until they report again, and that report counts at once. */
    public void remove(long driverId) {
        Long city = localCityOf.remove(driverId);
        if (city != null) {
            Map<Long, Fix> set = localByCity.get(city);
            if (set != null) {
                set.remove(driverId);
            }
        }
        lastAccepted.remove(driverId);
        if (redisUsable()) {
            try {
                String old = redisTemplate.opsForValue().get(cityKey(driverId));
                if (old != null) {
                    redisTemplate.opsForGeo().remove(CITY_GEO_PREFIX + old, Long.toString(driverId));
                }
                redisTemplate.delete(List.of(cityKey(driverId), throttleKey(driverId)));
            } catch (Exception e) {
                muteRedis("delete", e);
            }
        }
    }

    /** The city whose set holds this driver right now; empty once they are offline or stale. */
    public Optional<Long> cityOf(long driverId) {
        if (redisUsable()) {
            try {
                String city = redisTemplate.opsForValue().get(cityKey(driverId));
                return city == null ? Optional.empty() : Optional.of(Long.parseLong(city));
            } catch (Exception e) {
                muteRedis("read", e);
            }
        }
        Long city = localCityOf.get(driverId);
        if (city == null) {
            return Optional.empty();
        }
        Map<Long, Fix> set = localByCity.get(city);
        Fix fix = set == null ? null : set.get(driverId);
        return fix != null && fresh(fix) ? Optional.of(city) : Optional.empty();
    }

    /** Up to {@code limit} drivers in this city seen recently within {@code radiusKm} of the point, nearest first. */
    public List<Nearby> findWithin(long cityId, double lat, double lon, double radiusKm, int limit) {
        if (!validCoordinate(lat, lon) || radiusKm <= 0 || limit <= 0) {
            return List.of();
        }
        if (redisUsable()) {
            try {
                return searchRedis(cityId, lat, lon, radiusKm, limit);
            } catch (Exception e) {
                muteRedis("search", e);
            }
        }
        return searchLocal(cityId, lat, lon, radiusKm, limit);
    }

    private Optional<Long> reportRedis(long driverId, double lat, double lon) {
        // SET NX PX: one accepted report per driver per gap, across every instance.
        Boolean accepted = redisTemplate.opsForValue()
                .setIfAbsent(throttleKey(driverId), "1", Duration.ofMillis(MIN_REPORT_GAP_MS));
        if (!Boolean.TRUE.equals(accepted)) {
            return Optional.empty();
        }
        String member = Long.toString(driverId);
        Optional<Long> city = cityAt(lat, lon);
        String old = redisTemplate.opsForValue().get(cityKey(driverId));
        if (old != null && !(city.isPresent() && old.equals(city.get().toString()))) {
            redisTemplate.opsForGeo().remove(CITY_GEO_PREFIX + old, member);
        }
        if (city.isEmpty()) {
            redisTemplate.delete(cityKey(driverId));
            return Optional.empty();
        }
        redisTemplate.opsForGeo().add(CITY_GEO_PREFIX + city.get(), new Point(lon, lat), member);
        redisTemplate.opsForValue().set(cityKey(driverId), city.get().toString(), ttl());
        return city;
    }

    private Optional<Long> reportLocal(long driverId, double lat, double lon) {
        if (!acceptLocally(driverId)) {
            return Optional.empty();
        }
        Optional<Long> city = cityAt(lat, lon);
        Long old = city.isPresent() ? localCityOf.put(driverId, city.get()) : localCityOf.remove(driverId);
        if (old != null && !(city.isPresent() && old.equals(city.get()))) {
            Map<Long, Fix> oldSet = localByCity.get(old);
            if (oldSet != null) {
                oldSet.remove(driverId);
            }
        }
        city.ifPresent(c -> localByCity.computeIfAbsent(c, k -> new ConcurrentHashMap<>())
                .put(driverId, new Fix(lat, lon, clock.millis())));
        return city;
    }

    private boolean acceptLocally(long driverId) {
        long now = clock.millis();
        AtomicLong last = lastAccepted.computeIfAbsent(driverId, k -> new AtomicLong(Long.MIN_VALUE));
        long previous = last.get();
        if (previous != Long.MIN_VALUE && now - previous < MIN_REPORT_GAP_MS) {
            return false;
        }
        // Two reports racing on one driver: only one wins the slot.
        return last.compareAndSet(previous, now);
    }

    private Optional<Long> cityAt(double lat, double lon) {
        return cityService == null ? Optional.empty() : cityService.cityFor(lat, lon).map(City::getId);
    }

    private List<Nearby> searchRedis(long cityId, double lat, double lon, double radiusKm, int limit) {
        String geoKey = CITY_GEO_PREFIX + cityId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                geoKey,
                GeoReference.fromCoordinate(lon, lat),
                new Distance(radiusKm, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance().sortAscending().limit(limit));
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }
        List<String> members = new ArrayList<>();
        List<String> cityKeys = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
            members.add(r.getContent().getName());
            cityKeys.add(DRIVER_PREFIX + r.getContent().getName() + ":city");
        }
        // A member is live only while its city key exists and still names this city: a lapsed
        // key means the phone went quiet, another city means the move's ZREM was lost.
        List<String> cities = redisTemplate.opsForValue().multiGet(cityKeys);
        String wanted = Long.toString(cityId);
        List<Nearby> nearby = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            String member = members.get(i);
            if (cities == null || !wanted.equals(cities.get(i))) {
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
            redisTemplate.opsForGeo().remove(geoKey, stale.toArray(new String[0]));
        }
        return nearby;
    }

    private List<Nearby> searchLocal(long cityId, double lat, double lon, double radiusKm, int limit) {
        Map<Long, Fix> set = localByCity.get(cityId);
        if (set == null) {
            return List.of();
        }
        List<Nearby> nearby = new ArrayList<>();
        set.forEach((driverId, fix) -> {
            if (!fresh(fix)) {
                set.remove(driverId, fix);
                localCityOf.remove(driverId, cityId);
                return;
            }
            double km = TripRules.distanceKm(lat, lon, fix.lat(), fix.lon());
            if (km <= radiusKm) {
                nearby.add(new Nearby(driverId, km));
            }
        });
        nearby.sort(Comparator.comparingDouble(Nearby::distanceKm));
        return nearby.size() > limit ? List.copyOf(nearby.subList(0, limit)) : nearby;
    }

    private boolean fresh(Fix fix) {
        return fix.seenAtMillis() >= clock.millis() - ttl().toMillis();
    }

    private static String cityKey(long driverId) {
        return DRIVER_PREFIX + driverId + ":city";
    }

    private static String throttleKey(long driverId) {
        return DRIVER_PREFIX + driverId + ":throttle";
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
