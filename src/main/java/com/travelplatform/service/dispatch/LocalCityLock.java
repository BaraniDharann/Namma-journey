package com.travelplatform.service.dispatch;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The single-instance twin of {@link RedisCityLock}. With one server it always wins every city,
 * but it runs the same expiry rules over a {@link Registry} that tests share between two
 * "servers", so the take-over logic is exercised without Redis.
 */
public class LocalCityLock implements CityLock {

    static final long HEARTBEAT_WINDOW_MS = 10_000L;

    /** The shared state Redis would hold: lock holders with their expiry, and heartbeats. */
    public static final class Registry {
        private record Holder(String owner, long expiresAt) {
        }

        private final Map<Long, Holder> locks = new ConcurrentHashMap<>();
        private final Map<String, Long> beats = new ConcurrentHashMap<>();
    }

    private final Registry registry;
    private final String instanceId;
    private final LongSupplier nowMs;
    private final long ttlMs;

    public LocalCityLock() {
        this(new Registry(), UUID.randomUUID().toString(), System::currentTimeMillis, RedisCityLock.LOCK_TTL_MS);
    }

    public LocalCityLock(Registry registry, String instanceId, LongSupplier nowMs, long ttlMs) {
        this.registry = registry;
        this.instanceId = instanceId;
        this.nowMs = nowMs;
        this.ttlMs = ttlMs;
    }

    @Override
    public boolean tryAcquire(long cityId) {
        long now = nowMs.getAsLong();
        Registry.Holder mine = new Registry.Holder(instanceId, now + ttlMs);
        Registry.Holder after = registry.locks.compute(cityId, (k, old) ->
                old == null || old.expiresAt() <= now ? mine : old);
        return after == mine;
    }

    @Override
    public boolean renew(long cityId) {
        long now = nowMs.getAsLong();
        boolean[] renewed = {false};
        registry.locks.computeIfPresent(cityId, (k, old) -> {
            if (old.owner().equals(instanceId) && old.expiresAt() > now) {
                renewed[0] = true;
                return new Registry.Holder(instanceId, now + ttlMs);
            }
            return old;
        });
        return renewed[0];
    }

    @Override
    public void release(long cityId) {
        registry.locks.computeIfPresent(cityId, (k, old) -> old.owner().equals(instanceId) ? null : old);
    }

    @Override
    public void heartbeat() {
        registry.beats.put(instanceId, nowMs.getAsLong());
    }

    @Override
    public int liveServers() {
        long cutoff = nowMs.getAsLong() - HEARTBEAT_WINDOW_MS;
        registry.beats.values().removeIf(at -> at < cutoff);
        return Math.max(1, registry.beats.size());
    }
}
