package com.travelplatform.outbox;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbox table and V17/V18 against real Postgres: leasing, retry, dead-lettering, and the
 * one guarantee that only the database can give - two workers never claim the same job.
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxStoreTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;
    private OutboxStore store;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM payments");
        store = new OutboxStore(jdbc);
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("a claimed job is leased: claimed again only after the lease, with the attempt counted")
    void claimLeasesJob() {
        store.append("email", "{}");

        List<OutboxJob> first = store.claimDue(10);
        assertEquals(1, first.size());
        assertEquals(1, first.get(0).attempts());
        assertTrue(store.claimDue(10).isEmpty(), "leased, so not due again yet");

        jdbc.update("UPDATE outbox_events SET available_at = now() - interval '1 second'");
        assertEquals(2, store.claimDue(10).get(0).attempts());
    }

    @Test
    @DisplayName("done, retry-later and dead each leave the row where the relay expects it")
    void outcomes() {
        store.append("a", "{}");
        store.append("b", "{}");
        store.append("c", "{}");
        List<OutboxJob> jobs = store.claimDue(10);

        store.markDone(jobs.get(0).id());
        store.retryLater(jobs.get(1).id(), Duration.ofSeconds(60), "smtp down");
        store.markDead(jobs.get(2).id(), "x".repeat(5000));

        assertEquals("DONE", status(jobs.get(0).id()));
        assertEquals("PENDING", status(jobs.get(1).id()));
        assertEquals("DEAD", status(jobs.get(2).id()));
        assertEquals(1, store.countDead());
        assertTrue(store.claimDue(10).isEmpty(), "the retry waits out its delay");
    }

    @Test
    @DisplayName("two relays polling at once never claim the same job")
    void concurrentClaimsAreDisjoint() throws Exception {
        for (int i = 0; i < 50; i++) {
            store.append("email", "{}");
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<List<OutboxJob>>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    // Each claim in its own transaction, as the @Transactional proxy would run it.
                    return new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                            .execute(s -> new OutboxStore(jdbc).claimDue(20));
                }));
            }
            go.countDown();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            int total = 0;
            for (Future<List<OutboxJob>> f : results) {
                for (OutboxJob job : f.get()) {
                    total++;
                    assertTrue(seen.add(job.id()), "job " + job.id() + " claimed twice");
                }
            }
            assertEquals(50, total);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("purge drops old finished jobs and keeps pending and dead ones")
    void purge() {
        store.append("a", "{}");
        store.append("b", "{}");
        List<OutboxJob> jobs = store.claimDue(10);
        store.markDone(jobs.get(0).id());
        store.markDead(jobs.get(1).id(), "gave up");
        jdbc.update("UPDATE outbox_events SET processed_at = now() - interval '8 days'");

        assertEquals(1, store.purgeDoneOlderThanDays(7));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class));
    }

    private void insertPayment(String method, String status, String razorpayPaymentId) {
        jdbc.update("""
                INSERT INTO payments (id, amount, booking_id, created_at, payment_method, status, user_id, razorpay_payment_id)
                VALUES (?, 100, ?, now(), ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), method, status, UUID.randomUUID(), razorpayPaymentId);
    }

    @Test
    @DisplayName("V17/V18: RAZORPAY and the refund statuses are accepted; anything else is not")
    void paymentConstraints() {
        insertPayment("RAZORPAY", "REFUND_PENDING", "pay_1");
        insertPayment("RAZORPAY", "REFUNDED", "pay_2");
        Exception bad = assertThrows(Exception.class, () -> insertPayment("BITCOIN", "PENDING", null));
        assertEquals("23514", sqlState(bad), "check_violation");
    }

    @Test
    @DisplayName("V17: one Razorpay payment can never settle two fares")
    void razorpayPaymentIdUnique() {
        insertPayment("RAZORPAY", "VERIFIED", "pay_same");
        Exception dup = assertThrows(Exception.class, () -> insertPayment("RAZORPAY", "VERIFIED", "pay_same"));
        assertEquals("23505", sqlState(dup), "unique_violation");
    }

    @Test
    @DisplayName("V20: a booking takes one advance and one fare payment, never two of either")
    void onePaymentPerPurpose() {
        UUID booking = UUID.randomUUID();
        String sql = """
                INSERT INTO payments (id, amount, booking_id, created_at, payment_method, status, user_id, purpose)
                VALUES (?, 100, ?, now(), 'RAZORPAY', 'PENDING', ?, ?)
                """;
        jdbc.update(sql, UUID.randomUUID(), booking, UUID.randomUUID(), "ADVANCE");
        jdbc.update(sql, UUID.randomUUID(), booking, UUID.randomUUID(), "FARE");
        Exception dup = assertThrows(Exception.class,
                () -> jdbc.update(sql, UUID.randomUUID(), booking, UUID.randomUUID(), "FARE"));
        assertEquals("23505", sqlState(dup), "unique_violation");
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
