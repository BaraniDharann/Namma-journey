package com.travelplatform.service.dispatch;

import com.travelplatform.entity.VehicleType;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Waiting rides and open offers in Redis, shared by every instance:
 * <ul>
 *   <li>{@code travelplatform::city:{id}:waiting}: sorted set of ride ids, scored by search start</li>
 *   <li>{@code travelplatform::ride:{bookingId}}: hash with the ticket (and the offered driver)</li>
 *   <li>{@code travelplatform::ride:{bookingId}:declined}: set of driver ids</li>
 *   <li>{@code travelplatform::city:{id}:offers}: sorted set of {@code bookingId:driverId}, scored by deadline</li>
 *   <li>{@code travelplatform::driver:{id}:offer}: the ride a driver holds, expiring at the deadline</li>
 * </ul>
 * Only the city's lock holder writes waiting and offers in a round; accept, decline and cancel
 * write from request threads. The multi-key moves that must not interleave with those are Lua.
 */
public class RedisRideQueue implements RideQueue {

    static final String PREFIX = "travelplatform::";
    /** Ride data and declined sets outlive any search (2 min, restarted at most by a back-out) by far. */
    static final Duration RIDE_TTL = Duration.ofHours(2);

    /**
     * Drops a ride's open offer: its member in the offers set, and the driver's offer key if it
     * still names this ride. If the ride hash is gone the member is found by prefix, so an orphan
     * offer can never be "expired" round after round.
     * KEYS: ride hash, offers set. ARGV: booking id, driver key prefix.
     */
    private static final RedisScript<String> CLEAR_OFFER = new DefaultRedisScript<>(
            "local d = redis.call('HGET', KEYS[1], 'driver') "
                    + "if d then "
                    + "  redis.call('ZREM', KEYS[2], ARGV[1] .. ':' .. d) "
                    + "  local k = ARGV[2] .. d .. ':offer' "
                    + "  if redis.call('GET', k) == ARGV[1] then redis.call('DEL', k) end "
                    + "  redis.call('HDEL', KEYS[1], 'driver', 'deadline') "
                    + "  return d "
                    + "end "
                    + "local p = ARGV[1] .. ':' "
                    + "for _, m in ipairs(redis.call('ZRANGE', KEYS[2], 0, -1)) do "
                    + "  if string.sub(m, 1, #p) == p then "
                    + "    redis.call('ZREM', KEYS[2], m) "
                    + "    local k = ARGV[2] .. string.sub(m, #p + 1) .. ':offer' "
                    + "    if redis.call('GET', k) == ARGV[1] then redis.call('DEL', k) end "
                    + "  end "
                    + "end "
                    + "return false", String.class);

    private final StringRedisTemplate redis;

    public RedisRideQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void enqueue(RideTicket t) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("cityId", Long.toString(t.cityId()));
        hash.put("lat", Double.toString(t.pickup().lat()));
        hash.put("lon", Double.toString(t.pickup().lon()));
        hash.put("vehicle", t.vehicle().name());
        hash.put("passengers", Integer.toString(t.passengers()));
        hash.put("started", Long.toString(t.searchStartedEpochMs()));
        String id = t.bookingId().toString();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            c.hMSet(rideKey(t.bookingId()), hash);
            c.pExpire(rideKey(t.bookingId()), RIDE_TTL.toMillis());
            c.zAdd(waitingKey(t.cityId()), t.searchStartedEpochMs(), id);
            return null;
        });
    }

    @Override
    public List<RideTicket> waiting(long cityId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Set<String> ids = redis.opsForZSet().range(waitingKey(cityId), 0, limit - 1L);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> order = new ArrayList<>(ids);
        List<Object> hashes = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            for (String id : order) {
                c.hGetAll(PREFIX + "ride:" + id);
            }
            return null;
        });
        List<RideTicket> out = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            RideTicket t = parse(order.get(i), hashes.get(i));
            if (t == null) {
                stale.add(order.get(i));
            } else {
                out.add(t);
            }
        }
        if (!stale.isEmpty()) {
            // Expired ride data: nothing left to match it with. Recovery re-adds it if it still matters.
            redis.opsForZSet().remove(waitingKey(cityId), stale.toArray());
        }
        return out;
    }

    @Override
    public void remove(long cityId, UUID bookingId) {
        clearOffer(cityId, bookingId);
        redis.opsForZSet().remove(waitingKey(cityId), bookingId.toString());
        redis.delete(List.of(rideKey(bookingId), declinedKey(bookingId)));
    }

    @Override
    public void markOffered(long cityId, UUID bookingId, long driverId, long deadlineEpochMs) {
        clearOffer(cityId, bookingId);
        String id = bookingId.toString();
        long ttl = Math.max(1, deadlineEpochMs - System.currentTimeMillis());
        redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            c.zRem(waitingKey(cityId), id);
            c.zAdd(offersKey(cityId), deadlineEpochMs, id + ":" + driverId);
            c.hMSet(rideKey(bookingId), Map.of("driver", Long.toString(driverId), "deadline", Long.toString(deadlineEpochMs)));
            c.pSetEx(driverOfferKey(driverId), ttl, id);
            return null;
        });
    }

    @Override
    public List<OfferRef> expiredOffers(long cityId, long nowEpochMs) {
        // Scores are whole milliseconds, so "before now" is "at most now - 1".
        Set<String> members = redis.opsForZSet().rangeByScore(offersKey(cityId), Double.NEGATIVE_INFINITY, nowEpochMs - 1);
        List<OfferRef> out = new ArrayList<>();
        if (members == null) {
            return out;
        }
        for (String m : members) {
            int colon = m.lastIndexOf(':');
            try {
                out.add(new OfferRef(UUID.fromString(m.substring(0, colon)), Long.parseLong(m.substring(colon + 1))));
            } catch (RuntimeException e) {
                redis.opsForZSet().remove(offersKey(cityId), m); // not ours
            }
        }
        return out;
    }

    @Override
    public void clearOffer(long cityId, UUID bookingId) {
        redis.execute(CLEAR_OFFER, List.of(rideKey(bookingId), offersKey(cityId)),
                bookingId.toString(), PREFIX + "driver:");
    }

    @Override
    public void decline(UUID bookingId, long driverId) {
        redis.opsForSet().add(declinedKey(bookingId), Long.toString(driverId));
        redis.expire(declinedKey(bookingId), RIDE_TTL);
    }

    @Override
    public Set<Long> declined(UUID bookingId) {
        Set<String> members = redis.opsForSet().members(declinedKey(bookingId));
        Set<Long> out = new HashSet<>();
        if (members != null) {
            for (String m : members) {
                try {
                    out.add(Long.parseLong(m));
                } catch (NumberFormatException ignored) {
                    // not a driver id
                }
            }
        }
        return out;
    }

    @Override
    public Optional<OfferRef> currentOffer(long driverId) {
        String id = redis.opsForValue().get(driverOfferKey(driverId));
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new OfferRef(UUID.fromString(id), driverId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isEmpty(long cityId) {
        Long waiting = redis.opsForZSet().zCard(waitingKey(cityId));
        Long offers = redis.opsForZSet().zCard(offersKey(cityId));
        return (waiting == null || waiting == 0) && (offers == null || offers == 0);
    }

    private static RideTicket parse(String id, Object raw) {
        if (!(raw instanceof Map<?, ?> h) || h.isEmpty()) {
            return null;
        }
        try {
            return new RideTicket(UUID.fromString(id),
                    Long.parseLong(String.valueOf(h.get("cityId"))),
                    new GeoPoint(Double.parseDouble(String.valueOf(h.get("lat"))), Double.parseDouble(String.valueOf(h.get("lon")))),
                    VehicleType.valueOf(String.valueOf(h.get("vehicle"))),
                    Integer.parseInt(String.valueOf(h.get("passengers"))),
                    Long.parseLong(String.valueOf(h.get("started"))));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String waitingKey(long cityId) {
        return PREFIX + "city:" + cityId + ":waiting";
    }

    static String offersKey(long cityId) {
        return PREFIX + "city:" + cityId + ":offers";
    }

    static String rideKey(UUID bookingId) {
        return PREFIX + "ride:" + bookingId;
    }

    static String declinedKey(UUID bookingId) {
        return PREFIX + "ride:" + bookingId + ":declined";
    }

    static String driverOfferKey(long driverId) {
        return PREFIX + "driver:" + driverId + ":offer";
    }
}
