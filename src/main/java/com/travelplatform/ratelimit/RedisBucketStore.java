package com.travelplatform.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate-limit buckets in Redis, so every backend instance draws on the same budget. With buckets
 * in memory, N instances behind a load balancer granted N times every limit - including the
 * 10-a-minute login limit that is the only brake on password and OTP guessing.
 *
 * <p>Bucket4j keeps each bucket's state in one Redis key and updates it with compare-and-swap,
 * so concurrent requests on different instances cannot both take the last token. Keys expire
 * once the bucket would have refilled anyway, so idle clients leave nothing behind.
 *
 * <p>Redis is optional, as everywhere else in this app: if it is unreachable the per-instance
 * {@link LocalBucketStore} takes over for 30 seconds before Redis is tried again. Limits keep
 * applying throughout - per instance instead of globally - and a request is never failed
 * because the limiter's own storage is down.
 */
public class RedisBucketStore implements BucketStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBucketStore.class);

    private static final String KEY_PREFIX = "travelplatform::ratelimit:";
    private static final long COOLDOWN_MS = 30_000L;

    private final RedisClient client;
    private final LocalBucketStore fallback;
    private final AtomicLong mutedUntil = new AtomicLong();

    private volatile StatefulRedisConnection<byte[], byte[]> connection;
    private volatile LettuceBasedProxyManager<byte[]> proxyManager;

    public RedisBucketStore(RedisClient client, LocalBucketStore fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    @Override
    public ConsumptionProbe tryConsume(String key, int limitPerMinute) {
        if (System.currentTimeMillis() >= mutedUntil.get()) {
            try {
                byte[] redisKey = (KEY_PREFIX + key + "#" + limitPerMinute).getBytes(StandardCharsets.UTF_8);
                return proxyManager().builder()
                        .build(redisKey, () -> configuration(limitPerMinute))
                        .tryConsumeAndReturnRemaining(1);
            } catch (RuntimeException e) {
                mute(e);
            }
        }
        return fallback.tryConsume(key, limitPerMinute);
    }

    public LocalBucketStore fallback() {
        return fallback;
    }

    private static BucketConfiguration configuration(int limitPerMinute) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(limitPerMinute, Refill.greedy(limitPerMinute, LocalBucketStore.REFILL)))
                .build();
    }

    /** Connects on first use, not at startup: a Redis that is down at boot must not stop the app. */
    private LettuceBasedProxyManager<byte[]> proxyManager() {
        LettuceBasedProxyManager<byte[]> current = proxyManager;
        if (current == null) {
            synchronized (this) {
                if (proxyManager == null) {
                    connection = client.connect(ByteArrayCodec.INSTANCE);
                    proxyManager = LettuceBasedProxyManager.builderFor(connection)
                            .withExpirationStrategy(ExpirationAfterWriteStrategy
                                    .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                            .build();
                }
                current = proxyManager;
            }
        }
        return current;
    }

    private void mute(RuntimeException e) {
        long now = System.currentTimeMillis();
        if (mutedUntil.getAndSet(now + COOLDOWN_MS) <= now - COOLDOWN_MS) {
            log.warn("Redis rate limiting unavailable; enforcing limits per instance for {}s: {}",
                    COOLDOWN_MS / 1000, e.toString());
        }
        // Drop a broken connection so the next attempt reconnects from scratch.
        synchronized (this) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (RuntimeException ignored) {
                    // already broken
                }
            }
            connection = null;
            proxyManager = null;
        }
    }
}
