package com.travelplatform.service.dispatch;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Per-city locks in Redis: {@code travelplatform::lock:match:city:{id}}, taken with
 * {@code SET NX PX}, holding this server's id. Renew and release compare that id first, in Lua,
 * so a server that stalled past its expiry can never extend or delete the lock its successor
 * now holds.
 *
 * <p>Live servers are a sorted set, {@code travelplatform::match:servers}, of server ids scored by
 * their last heartbeat. Counting expiring per-server keys would need a SCAN over the whole
 * keyspace, every location key included, on every tick.
 */
public class RedisCityLock implements CityLock {

    static final long LOCK_TTL_MS = 5_000L;
    static final long HEARTBEAT_WINDOW_MS = 10_000L;
    static final String LOCK_PREFIX = "travelplatform::lock:match:city:";
    static final String SERVERS_KEY = "travelplatform::match:servers";

    private static final RedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('PEXPIRE', KEYS[1], ARGV[2]) end return 0", Long.class);

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
            Long.class);

    private final StringRedisTemplate redis;
    private final String instanceId;
    private final long ttlMs;

    public RedisCityLock(StringRedisTemplate redis) {
        this(redis, UUID.randomUUID().toString(), LOCK_TTL_MS);
    }

    public RedisCityLock(StringRedisTemplate redis, String instanceId, long ttlMs) {
        this.redis = redis;
        this.instanceId = instanceId;
        this.ttlMs = ttlMs;
    }

    @Override
    public boolean tryAcquire(long cityId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(cityId), instanceId, Duration.ofMillis(ttlMs)));
    }

    @Override
    public boolean renew(long cityId) {
        Long ok = redis.execute(RENEW, List.of(key(cityId)), instanceId, Long.toString(ttlMs));
        return ok != null && ok == 1L;
    }

    @Override
    public void release(long cityId) {
        redis.execute(RELEASE, List.of(key(cityId)), instanceId);
    }

    @Override
    public void heartbeat() {
        long now = System.currentTimeMillis();
        redis.opsForZSet().add(SERVERS_KEY, instanceId, now);
        redis.opsForZSet().removeRangeByScore(SERVERS_KEY, Double.NEGATIVE_INFINITY, now - HEARTBEAT_WINDOW_MS);
    }

    @Override
    public int liveServers() {
        Long n = redis.opsForZSet().count(SERVERS_KEY, System.currentTimeMillis() - HEARTBEAT_WINDOW_MS,
                Double.POSITIVE_INFINITY);
        return n == null ? 1 : (int) Math.max(1, n);
    }

    private static String key(long cityId) {
        return LOCK_PREFIX + cityId;
    }
}
