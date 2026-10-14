package com.travelplatform.ratelimit;

import io.github.bucket4j.ConsumptionProbe;

/**
 * Token buckets for RateLimitingFilter: {@code limitPerMinute} tokens per key, refilled greedily
 * over a minute. {@link RedisBucketStore} shares buckets across every instance;
 * {@link LocalBucketStore} keeps them in this JVM.
 */
public interface BucketStore {

    ConsumptionProbe tryConsume(String key, int limitPerMinute);
}
