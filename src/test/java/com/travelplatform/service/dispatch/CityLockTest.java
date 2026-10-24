package com.travelplatform.service.dispatch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One server per city: two servers starting at the same moment must not both run a city's
 * rounds, or a ride could get two offers. The local twin is checked always; the Redis lock
 * (what production runs) when Docker is available.
 */
class CityLockTest {

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void startRedis() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redisContainer.start();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (factory != null) {
            factory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @Test
    @DisplayName("local: a second holder cannot take a held lock until it is released")
    void secondHolderCannotTakeLock() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalCityLock.Registry shared = new LocalCityLock.Registry();
        CityLock a = new LocalCityLock(shared, "a", now::get, 5_000);
        CityLock b = new LocalCityLock(shared, "b", now::get, 5_000);

        assertTrue(a.tryAcquire(1L));
        assertFalse(b.tryAcquire(1L));
        assertFalse(b.renew(1L), "renew only extends a lock you hold");
        assertTrue(b.tryAcquire(2L), "other cities are unaffected");
        a.release(1L);
        assertTrue(b.tryAcquire(1L));
        assertFalse(a.renew(1L));
    }

    @Test
    @DisplayName("local: a lock nobody renews expires, and another server takes the city")
    void lockExpiresWithoutRenewal() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalCityLock.Registry shared = new LocalCityLock.Registry();
        CityLock a = new LocalCityLock(shared, "a", now::get, 5_000);
        CityLock b = new LocalCityLock(shared, "b", now::get, 5_000);
        assertTrue(a.tryAcquire(1L));

        now.addAndGet(4_000);
        assertTrue(a.renew(1L));
        now.addAndGet(4_000);
        assertFalse(b.tryAcquire(1L), "renewed 4 s ago, still held");
        now.addAndGet(1_001);
        assertTrue(b.tryAcquire(1L), "5 s without a renewal: the holder is presumed dead");
        assertFalse(a.renew(1L), "the old holder finds out at its next renewal");
    }

    @Test
    @DisplayName("local: live servers are those that beat in the last 10 s")
    void liveServersCountsRecentHeartbeats() {
        AtomicLong now = new AtomicLong(1_000_000L);
        LocalCityLock.Registry shared = new LocalCityLock.Registry();
        CityLock a = new LocalCityLock(shared, "a", now::get, 5_000);
        CityLock b = new LocalCityLock(shared, "b", now::get, 5_000);
        a.heartbeat();
        b.heartbeat();
        assertEquals(2, a.liveServers());

        now.addAndGet(10_001);
        a.heartbeat();
        assertEquals(1, b.liveServers());
    }

    @Test
    @DisplayName("redis: a second holder cannot take a held lock; release frees it")
    void redisSecondHolderCannotTakeLock() {
        Assumptions.assumeTrue(template != null, "Docker not available");
        CityLock a = new RedisCityLock(template, "a", 5_000);
        CityLock b = new RedisCityLock(template, "b", 5_000);

        assertTrue(a.tryAcquire(11L));
        assertFalse(b.tryAcquire(11L));
        assertFalse(b.renew(11L));
        b.release(11L); // must not free a lock b does not hold
        assertFalse(b.tryAcquire(11L));
        a.release(11L);
        assertTrue(b.tryAcquire(11L));
        b.release(11L);
    }

    @Test
    @DisplayName("redis: a lock nobody renews expires")
    void redisLockExpiresWithoutRenewal() throws InterruptedException {
        Assumptions.assumeTrue(template != null, "Docker not available");
        CityLock a = new RedisCityLock(template, "a", 300);
        CityLock b = new RedisCityLock(template, "b", 300);
        assertTrue(a.tryAcquire(12L));
        assertTrue(a.renew(12L));

        Thread.sleep(450);

        assertTrue(b.tryAcquire(12L));
        assertFalse(a.renew(12L));
        b.release(12L);
    }

    @Test
    @DisplayName("redis: heartbeats count live servers")
    void redisLiveServers() {
        Assumptions.assumeTrue(template != null, "Docker not available");
        template.delete(RedisCityLock.SERVERS_KEY);
        CityLock a = new RedisCityLock(template, "a", 5_000);
        CityLock b = new RedisCityLock(template, "b", 5_000);
        a.heartbeat();
        b.heartbeat();
        a.heartbeat();

        assertEquals(2, a.liveServers());
    }
}
