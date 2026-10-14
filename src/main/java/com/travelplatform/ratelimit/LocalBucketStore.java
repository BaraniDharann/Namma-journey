package com.travelplatform.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buckets in this JVM's memory: correct for a single instance, and the fallback whenever Redis is
 * unavailable. With N instances each one grants the full limit, which is why
 * {@link RedisBucketStore} exists.
 */
public class LocalBucketStore implements BucketStore {

    static final Duration REFILL = Duration.ofMinutes(1);
    private static final long STALE_AFTER_MS = Duration.ofMinutes(10).toMillis();

    private record Entry(Bucket bucket, long[] lastAccess) {
    }

    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    @Override
    public ConsumptionProbe tryConsume(String key, int limitPerMinute) {
        // The limit is part of the key: changing a configured limit starts a fresh bucket rather
        // than carrying on with one built for the old number.
        Entry entry = buckets.computeIfAbsent(key + "#" + limitPerMinute, ignored -> new Entry(
                Bucket.builder().addLimit(Bandwidth.classic(limitPerMinute, Refill.greedy(limitPerMinute, REFILL))).build(),
                new long[1]));
        entry.lastAccess()[0] = System.currentTimeMillis();
        return entry.bucket().tryConsumeAndReturnRemaining(1);
    }

    /** Drops buckets nobody has touched for 10 minutes; called periodically by the filter. */
    public int removeStale() {
        long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
        int before = buckets.size();
        buckets.values().removeIf(e -> e.lastAccess()[0] < cutoff);
        return before - buckets.size();
    }
}
