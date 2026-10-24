package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The same rules as the local twin, against a real Redis: the Lua scripts are where the
 * atomicity lives, so they are worth running for real.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisDriverStateStoreTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    private final Map<Long, Driver> table = new ConcurrentHashMap<>();
    private final Set<Long> busyInDatabase = ConcurrentHashMap.newKeySet();
    private DriverStateStore store;
    private long base;

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
        DriverRepository drivers = mock(DriverRepository.class);
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        when(drivers.findAllById(any())).thenAnswer(i -> {
            List<Driver> found = new ArrayList<>();
            for (Long id : i.<Iterable<Long>>getArgument(0)) {
                if (table.containsKey(id)) {
                    found.add(table.get(id));
                }
            }
            return found;
        });
        when(bookings.findBusyDriverIds(anyCollection(), any(LocalDate.class)))
                .thenAnswer(i -> i.<Collection<Long>>getArgument(0).stream().filter(busyInDatabase::contains).toList());
        when(bookings.findOfferedDriverIds(anyCollection())).thenReturn(List.of());
        store = new RedisDriverStateStore(template, new DriverCardLoader(drivers, bookings));
        base = 1000L;
        table.put(base, driver(base, VehicleType.CAR, 4));
    }

    private static Driver driver(long id, VehicleType vehicle, int seats) {
        Driver d = new Driver();
        d.setId(id);
        d.setVehicleType(vehicle);
        d.setVehicleSeats(seats);
        d.setServesCity(true);
        d.setStatus(Driver.Status.ACTIVE);
        d.setVerificationStatus(Driver.Verification.APPROVED);
        return d;
    }

    @Test
    @DisplayName("a missing card is loaded from the database and cached in Redis")
    void missingCardLoadedFromDatabase() {
        assertEquals(new DriverCard(base, DriverState.IDLE, VehicleType.CAR, 4, true, true),
                store.cards(List.of(base, 99L)).get(base));
        assertTrue(template.hasKey(RedisDriverStateStore.key(base)));
        assertFalse(template.hasKey(RedisDriverStateStore.key(99L)));
    }

    @Test
    @DisplayName("of many instances racing IDLE -> OFFERED on one driver, exactly one wins")
    void compareAndSetOnlyFromExpected() throws Exception {
        store.cards(List.of(base));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger wins = new AtomicInteger();
        List<Future<?>> all = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            all.add(pool.submit(() -> {
                if (store.compareAndSetState(base, DriverState.IDLE, DriverState.OFFERED)) {
                    wins.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : all) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, wins.get());
        assertEquals(DriverState.OFFERED, store.cards(List.of(base)).get(base).state());
    }

    @Test
    @DisplayName("a profile change keeps the state; setState never creates a half card")
    void putProfileKeepsState() {
        store.cards(List.of(base));
        store.setState(base, DriverState.BUSY);
        store.putProfile(driver(base, VehicleType.AUTO, 3));
        store.setState(77L, DriverState.BUSY);

        DriverCard card = store.cards(List.of(base)).get(base);
        assertEquals(DriverState.BUSY, card.state());
        assertEquals(VehicleType.AUTO, card.vehicle());
        assertFalse(template.hasKey(RedisDriverStateStore.key(77L)));
    }

    @Test
    @DisplayName("reconcile pulls a drifted state back to the database's")
    void reconcileFixesDrift() {
        store.cards(List.of(base));
        busyInDatabase.add(base);

        store.reconcile();

        assertEquals(DriverState.BUSY, store.cards(List.of(base)).get(base).state());
    }
}
