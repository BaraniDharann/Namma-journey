package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.VehicleType;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Driver cards in Redis, shared by every instance: one hash per driver,
 * {@code travelplatform::driver:{id}} with {@code state}, {@code vehicle}, {@code seats},
 * {@code servesCity} and {@code active}. A round reads all its candidates in one pipelined
 * request instead of one query per ride.
 *
 * <p>Every write is a small Lua script, so it is atomic against the other instances: the
 * compare-and-set is one script, and "update only if present" means a write can never leave a
 * half-card behind (a state without a vehicle) for a round to misread. Cards expire after
 * {@value #CARD_TTL_MS} ms without a write, and a sorted set of when each driver was last
 * touched drives {@link #reconcile()}.
 */
public class RedisDriverStateStore implements DriverStateStore {

    static final String CARD_PREFIX = "travelplatform::driver:";
    static final String SEEN_KEY = "travelplatform::driver:seen";
    static final long CARD_TTL_MS = 15 * 60_000L;
    static final long SEEN_WINDOW_MS = 10 * 60_000L;

    private static final RedisScript<Long> SET_STATE = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then "
                    + "redis.call('HSET', KEYS[1], 'state', ARGV[1]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[2]) return 1 end return 0", Long.class);

    /** 1 moved, 0 the state was something else, -1 no card. */
    private static final RedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
            "local s = redis.call('HGET', KEYS[1], 'state') "
                    + "if not s then return -1 end "
                    + "if s == ARGV[1] then redis.call('HSET', KEYS[1], 'state', ARGV[2]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[3]) return 1 end return 0", Long.class);

    private static final RedisScript<Long> PUT_PROFILE = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then "
                    + "redis.call('HSET', KEYS[1], 'vehicle', ARGV[1], 'seats', ARGV[2], 'servesCity', ARGV[3], 'active', ARGV[4]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[5]) return 1 end return 0", Long.class);

    /** Writes a whole card; with ARGV[7] = '1' only when there is none yet (a load racing a write loses). */
    private static final RedisScript<Long> PUT_CARD = new DefaultRedisScript<>(
            "if ARGV[7] == '1' and redis.call('EXISTS', KEYS[1]) == 1 then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'state', ARGV[1], 'vehicle', ARGV[2], 'seats', ARGV[3], "
                    + "'servesCity', ARGV[4], 'active', ARGV[5]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[6]) return 1", Long.class);

    private final StringRedisTemplate redis;
    private final DriverCardLoader loader;

    public RedisDriverStateStore(StringRedisTemplate redis, DriverCardLoader loader) {
        this.redis = redis;
        this.loader = loader;
    }

    @Override
    public Map<Long, DriverCard> cards(Collection<Long> ids) {
        Map<Long, DriverCard> found = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return found;
        }
        List<Long> order = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Object> hashes = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection c = (StringRedisConnection) connection;
            for (Long id : order) {
                c.hGetAll(key(id));
            }
            return null;
        });
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            DriverCard card = parse(order.get(i), hashes.get(i));
            if (card == null) {
                missing.add(order.get(i));
            } else {
                found.put(order.get(i), card);
            }
        }
        if (!missing.isEmpty()) {
            for (DriverCard card : loader.load(missing).values()) {
                if (write(card, true)) {
                    found.put(card.driverId(), card);
                } else {
                    // Someone wrote it while we loaded; theirs is newer than our snapshot.
                    DriverCard current = parse(card.driverId(), redis.opsForHash().entries(key(card.driverId())));
                    found.put(card.driverId(), current != null ? current : card);
                }
            }
        }
        touch(order);
        return found;
    }

    @Override
    public void putProfile(Driver driver) {
        if (driver == null || driver.getId() == null) {
            return;
        }
        DriverCard p = DriverCardLoader.profile(driver, DriverState.IDLE);
        redis.execute(PUT_PROFILE, List.of(key(p.driverId())), p.vehicle().name(), Integer.toString(p.seats()),
                flag(p.servesCity()), flag(p.active()), Long.toString(CARD_TTL_MS));
    }

    @Override
    public void setState(long driverId, DriverState state) {
        redis.execute(SET_STATE, List.of(key(driverId)), state.name(), Long.toString(CARD_TTL_MS));
        touch(List.of(driverId));
    }

    @Override
    public boolean compareAndSetState(long driverId, DriverState expected, DriverState next) {
        Long moved = cas(driverId, expected, next);
        if (moved != null && moved == -1L) {
            cards(List.of(driverId)); // no card: load it, then try once more
            moved = cas(driverId, expected, next);
        }
        touch(List.of(driverId));
        return moved != null && moved == 1L;
    }

    private Long cas(long driverId, DriverState expected, DriverState next) {
        return redis.execute(COMPARE_AND_SET, List.of(key(driverId)), expected.name(), next.name(),
                Long.toString(CARD_TTL_MS));
    }

    @Override
    @Scheduled(fixedDelayString = "${app.dispatch.reconcile-interval-ms:60000}",
               initialDelayString = "${app.dispatch.reconcile-interval-ms:60000}")
    public void reconcile() {
        long cutoff = System.currentTimeMillis() - SEEN_WINDOW_MS;
        redis.opsForZSet().removeRangeByScore(SEEN_KEY, Double.NEGATIVE_INFINITY, cutoff);
        Set<String> members = redis.opsForZSet().rangeByScore(SEEN_KEY, cutoff, Double.POSITIVE_INFINITY);
        if (members == null || members.isEmpty()) {
            return;
        }
        Set<Long> recent = new HashSet<>();
        for (String m : members) {
            try {
                recent.add(Long.parseLong(m));
            } catch (NumberFormatException ignored) {
                redis.opsForZSet().remove(SEEN_KEY, m);
            }
        }
        Map<Long, DriverCard> truth = loader.load(recent);
        for (Long id : recent) {
            DriverCard card = truth.get(id);
            if (card == null) {
                redis.delete(key(id)); // the driver was deleted
                redis.opsForZSet().remove(SEEN_KEY, id.toString());
            } else {
                write(card, false);
            }
        }
    }

    private boolean write(DriverCard card, boolean onlyIfAbsent) {
        Long written = redis.execute(PUT_CARD, List.of(key(card.driverId())), card.state().name(),
                card.vehicle().name(), Integer.toString(card.seats()), flag(card.servesCity()), flag(card.active()),
                Long.toString(CARD_TTL_MS), onlyIfAbsent ? "1" : "0");
        return written != null && written == 1L;
    }

    /** Records that these drivers were just read or written, for reconcile. One ZADD for all of them. */
    private void touch(Collection<Long> ids) {
        try {
            double now = System.currentTimeMillis();
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (Long id : ids) {
                tuples.add(org.springframework.data.redis.core.ZSetOperations.TypedTuple.of(id.toString(), now));
            }
            redis.opsForZSet().add(SEEN_KEY, tuples);
        } catch (DataAccessException e) {
            // Only reconcile's reach suffers; the card itself is already written.
        }
    }

    private static DriverCard parse(long driverId, Object raw) {
        if (!(raw instanceof Map<?, ?> hash) || hash.isEmpty()) {
            return null;
        }
        try {
            return new DriverCard(driverId,
                    DriverState.valueOf(String.valueOf(hash.get("state"))),
                    VehicleType.valueOf(String.valueOf(hash.get("vehicle"))),
                    Integer.parseInt(String.valueOf(hash.get("seats"))),
                    "1".equals(hash.get("servesCity")),
                    "1".equals(hash.get("active")));
        } catch (RuntimeException e) {
            return null; // a damaged card is reloaded from the database
        }
    }

    private static String flag(boolean b) {
        return b ? "1" : "0";
    }

    static String key(long driverId) {
        return CARD_PREFIX + driverId;
    }
}
