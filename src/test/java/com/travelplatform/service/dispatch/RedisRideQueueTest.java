package com.travelplatform.service.dispatch;

import com.travelplatform.entity.VehicleType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Redis ride queue against a real Redis: the same behaviour the local twin is held to. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRideQueueTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final long CITY = 1L;

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;
    private RideQueue queue;
    private long t0;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @BeforeEach
    void setUp() {
        template.getConnectionFactory().getConnection().serverCommands().flushAll();
        queue = new RedisRideQueue(template);
        t0 = System.currentTimeMillis();
    }

    private RideTicket ticket(long startedMs) {
        return new RideTicket(UUID.randomUUID(), CITY, new GeoPoint(12.9716, 77.5946), VehicleType.AUTO, 2, startedMs);
    }

    @Test
    @DisplayName("waiting rides round-trip through the ride hash, oldest first")
    void waitingReturnsOldestFirst() {
        RideTicket newer = ticket(t0 - 1_000);
        RideTicket older = ticket(t0 - 9_000);
        queue.enqueue(newer);
        queue.enqueue(older);

        assertEquals(List.of(older, newer), queue.waiting(CITY, 10));
        assertEquals(List.of(older), queue.waiting(CITY, 1));
    }

    @Test
    @DisplayName("offer, expiry, current offer and clearing work together")
    void offerLifecycle() {
        RideTicket t = ticket(t0);
        queue.enqueue(t);
        queue.markOffered(CITY, t.bookingId(), 42L, t0 + 15_000);

        assertTrue(queue.waiting(CITY, 10).isEmpty());
        assertEquals(Optional.of(new OfferRef(t.bookingId(), 42L)), queue.currentOffer(42L));
        assertTrue(queue.expiredOffers(CITY, t0 + 15_000).isEmpty());
        assertEquals(List.of(new OfferRef(t.bookingId(), 42L)), queue.expiredOffers(CITY, t0 + 15_001));

        queue.clearOffer(CITY, t.bookingId());

        assertTrue(queue.expiredOffers(CITY, t0 + 60_000).isEmpty());
        assertEquals(Optional.empty(), queue.currentOffer(42L));
        assertTrue(queue.isEmpty(CITY), "cleared and not yet re-queued: nothing waiting, nothing offered");

        queue.enqueue(t); // back to waiting after a decline
        assertEquals(List.of(t), queue.waiting(CITY, 10));
        queue.remove(CITY, t.bookingId());
        assertTrue(queue.isEmpty(CITY));
    }

    @Test
    @DisplayName("declined drivers accumulate")
    void declinedAccumulates() {
        UUID ride = UUID.randomUUID();
        queue.decline(ride, 3L);
        queue.decline(ride, 4L);

        assertEquals(Set.of(3L, 4L), queue.declined(ride));
    }

    @Test
    @DisplayName("an offer whose ride hash vanished can still be cleared, so it never expires forever")
    void clearOfferWithoutRideHash() {
        RideTicket t = ticket(t0);
        queue.enqueue(t);
        queue.markOffered(CITY, t.bookingId(), 9L, t0 + 1_000);
        template.delete(RedisRideQueue.rideKey(t.bookingId()));

        queue.clearOffer(CITY, t.bookingId());

        assertTrue(queue.expiredOffers(CITY, t0 + 60_000).isEmpty());
    }
}
