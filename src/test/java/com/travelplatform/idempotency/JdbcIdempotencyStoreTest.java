package com.travelplatform.idempotency;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The store behind Idempotency-Key, against real Postgres: the guarantee that matters - two
 * identical requests at the same instant, exactly one runs - only exists in the database.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcIdempotencyStoreTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static JdbcTemplate jdbc;
    private JdbcIdempotencyStore store;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM idempotency_keys");
        store = new JdbcIdempotencyStore(jdbc);
    }

    @Test
    @DisplayName("first sighting acquires; a repeat while running is in progress; a different body is a mismatch")
    void claimStates() {
        assertEquals(IdempotencyStore.Claim.Kind.ACQUIRED, store.claim("u-1|POST|/b", "k", "h1").kind());
        assertEquals(IdempotencyStore.Claim.Kind.IN_PROGRESS, store.claim("u-1|POST|/b", "k", "h1").kind());
        assertEquals(IdempotencyStore.Claim.Kind.MISMATCH, store.claim("u-1|POST|/b", "k", "h2").kind());
    }

    @Test
    @DisplayName("a completed key replays the exact stored answer")
    void completedKeyReplays() {
        store.claim("s", "k", "h");
        byte[] body = "{\"bookingId\":\"b-1\"}".getBytes(StandardCharsets.UTF_8);
        store.complete("s", "k", new IdempotencyStore.StoredResponse(200, "application/json", body));

        IdempotencyStore.Claim again = store.claim("s", "k", "h");

        assertEquals(IdempotencyStore.Claim.Kind.REPLAY, again.kind());
        assertEquals(200, again.response().status());
        assertEquals("application/json", again.response().contentType());
        assertArrayEquals(body, again.response().body());
    }

    @Test
    @DisplayName("a released key can be claimed again")
    void releasedKeyIsFree() {
        store.claim("s", "k", "h");
        store.release("s", "k");

        assertEquals(IdempotencyStore.Claim.Kind.ACQUIRED, store.claim("s", "k", "h").kind());
    }

    @Test
    @DisplayName("ten identical requests at once: exactly one runs")
    void concurrentClaimsLetExactlyOneThrough() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<IdempotencyStore.Claim.Kind>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return store.claim("s", "race", "h").kind();
                }));
            }
            start.countDown();
            int acquired = 0;
            for (Future<IdempotencyStore.Claim.Kind> f : results) {
                if (f.get() == IdempotencyStore.Claim.Kind.ACQUIRED) {
                    acquired++;
                }
            }
            assertEquals(1, acquired);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a claim abandoned by a crashed request is taken over once it goes stale")
    void staleInProgressIsTakenOver() {
        store.claim("s", "k", "h");
        jdbc.update("UPDATE idempotency_keys SET created_at = now() - interval '5 minutes'");

        assertEquals(IdempotencyStore.Claim.Kind.ACQUIRED, store.claim("s", "k", "h").kind());
    }

    @Test
    @DisplayName("keys older than 24 hours are purged")
    void purgeRemovesExpiredKeys() {
        store.claim("s", "old", "h");
        store.claim("s", "new", "h");
        jdbc.update("UPDATE idempotency_keys SET created_at = now() - interval '25 hours' WHERE idem_key = 'old'");

        int removed = store.purgeExpired();

        assertEquals(1, removed);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Integer.class));
    }
}
