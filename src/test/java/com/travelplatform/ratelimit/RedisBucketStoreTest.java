package com.travelplatform.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With rate-limit buckets in each server's memory, two backend instances meant twice the limit:
 * a login brute-forcer behind a round-robin balancer got 20 guesses a minute, not 10. Buckets in
 * Redis are one counter shared by every instance.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisBucketStoreTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static RedisClient client;

    @BeforeAll
    static void connect() {
        client = RedisClient.create(RedisURI.create(REDIS.getHost(), REDIS.getMappedPort(6379)));
    }

    @AfterAll
    static void close() {
        client.shutdown();
    }

    private static String key() {
        return "auth:ip:" + UUID.randomUUID();
    }

    @Test
    @DisplayName("two backend instances share one budget instead of each granting the full limit")
    void instancesShareOneBucket() {
        BucketStore serverA = new RedisBucketStore(client, new LocalBucketStore());
        BucketStore serverB = new RedisBucketStore(client, new LocalBucketStore());
        String key = key();

        for (int i = 0; i < 3; i++) {
            assertTrue(serverA.tryConsume(key, 5).isConsumed());
        }
        assertTrue(serverB.tryConsume(key, 5).isConsumed());
        assertTrue(serverB.tryConsume(key, 5).isConsumed());

        assertFalse(serverA.tryConsume(key, 5).isConsumed(), "6th request across both servers must be refused");
        assertFalse(serverB.tryConsume(key, 5).isConsumed());
    }

    @Test
    @DisplayName("Redis down: limits still apply, per instance, and requests are never failed by it")
    void fallsBackToLocalWhenRedisIsDown() {
        RedisClient dead = RedisClient.create(RedisURI.builder()
                .withHost("127.0.0.1").withPort(1).withTimeout(Duration.ofMillis(200)).build());
        try {
            BucketStore store = new RedisBucketStore(dead, new LocalBucketStore());
            String key = key();

            assertTrue(store.tryConsume(key, 2).isConsumed());
            assertTrue(store.tryConsume(key, 2).isConsumed());
            assertFalse(store.tryConsume(key, 2).isConsumed(), "the local fallback still enforces the limit");
        } finally {
            dead.shutdown();
        }
    }

    @Test
    @DisplayName("different keys and different limits never share a bucket")
    void keysAreIndependent() {
        BucketStore store = new RedisBucketStore(client, new LocalBucketStore());
        String a = key();
        String b = key();

        assertTrue(store.tryConsume(a, 1).isConsumed());
        assertFalse(store.tryConsume(a, 1).isConsumed());
        assertTrue(store.tryConsume(b, 1).isConsumed());
    }
}
