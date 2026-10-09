package com.travelplatform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real Flyway migrations against a real Postgres and proves V5's no_driver_overlap
 * constraint does what the Java retry in UserService relies on: a second active booking for the
 * same driver on overlapping dates is refused with SQLSTATE 23P01, even when both arrive at once.
 *
 * <p>Needs Docker. Skipped (not failed) on a machine without it; CI runners always have it.
 */
@Testcontainers(disabledWithoutDocker = true)
class DriverOverlapConstraintTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final long DRIVER = 1L;

    @BeforeEach
    void resetSchema() {
        flyway(null).clean();
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static UUID insert(Connection c, Long driverId, String status, LocalDate from, LocalDate to,
                               LocalDateTime bookedAt) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO travel_bookings (id, booking_date, distance_km, estimated_time_minutes, from_place,
                    status, to_place, travel_days, user_id, user_name, user_phone, ac_type, from_date, to_date,
                    travel_members, driver_id)
                VALUES (?, ?, 100, 120, 'Chennai', ?, 'Madurai', 1, ?, 'Test', '9876543210', 'AC', ?, ?, 2, ?)
                """)) {
            ps.setObject(1, id);
            ps.setTimestamp(2, Timestamp.valueOf(bookedAt));
            ps.setString(3, status);
            ps.setObject(4, UUID.randomUUID());
            ps.setDate(5, Date.valueOf(from));
            ps.setDate(6, Date.valueOf(to));
            ps.setObject(7, driverId);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insert(Connection c, Long driverId, String status, String from, String to) throws SQLException {
        return insert(c, driverId, status, LocalDate.parse(from), LocalDate.parse(to), LocalDateTime.now());
    }

    @Test
    @DisplayName("a second active trip for the same driver on overlapping dates is refused with 23P01")
    void refusesOverlap() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect()) {
            insert(c, DRIVER, "CONFIRMED", "2026-11-01", "2026-11-03");

            SQLException ex = assertThrows(SQLException.class,
                    () -> insert(c, DRIVER, "PENDING", "2026-11-03", "2026-11-05"));
            assertEquals("23P01", ex.getSQLState(), "the last day of one trip is still a busy day");
        }
    }

    @Test
    @DisplayName("back-to-back trips, other drivers, unassigned and finished bookings never clash")
    void allowsWhatIsNotAClash() throws Exception {
        flyway(null).migrate();
        try (Connection c = connect()) {
            insert(c, DRIVER, "CONFIRMED", "2026-11-01", "2026-11-03");
            insert(c, DRIVER, "PENDING", "2026-11-04", "2026-11-05");   // starts the day after
            insert(c, 2L, "PENDING", "2026-11-01", "2026-11-03");       // a different driver
            insert(c, null, "PENDING", "2026-11-01", "2026-11-03");     // nobody assigned yet
            insert(c, DRIVER, "CANCELLED", "2026-11-02", "2026-11-02"); // cancelled frees the slot
            insert(c, DRIVER, "COMPLETED", "2026-11-02", "2026-11-02"); // so does finishing
        }
    }

    @Test
    @DisplayName("two bookings racing for the same driver: exactly one wins")
    void concurrentInsertsLetExactlyOneThrough() throws Exception {
        flyway(null).migrate();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            try (Connection c = connect()) {
                start.await();
                insert(c, DRIVER, "PENDING", "2026-12-10", "2026-12-12");
                return true;
            } catch (SQLException ex) {
                // Racing inserters waiting on each other's uncommitted rows can also be broken up
                // by Postgres' deadlock detector (40P01) instead of the constraint error itself.
                // Either way the booking is refused - and UserService retries on both.
                assertTrue(Set.of("23P01", "40P01").contains(ex.getSQLState()),
                        "unexpected SQLSTATE " + ex.getSQLState());
                return false;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(attempt));
            }
            start.countDown();
            int winners = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("V5 untangles clashes already in the data instead of refusing to start")
    void migrationResolvesExistingClashes() throws Exception {
        flyway("4").migrate();
        UUID confirmed;
        UUID earlierPending;
        UUID started;
        try (Connection c = connect()) {
            LocalDate d1 = LocalDate.parse("2026-11-01");
            LocalDate d3 = LocalDate.parse("2026-11-03");
            // Booked LATER but already confirmed: confirmed outranks the earlier pending one.
            earlierPending = insert(c, DRIVER, "PENDING", d1, d3, LocalDateTime.of(2026, 10, 1, 9, 0));
            confirmed = insert(c, DRIVER, "CONFIRMED", d1, d3, LocalDateTime.of(2026, 10, 2, 9, 0));
            // A trip in progress always keeps its driver.
            started = insert(c, 2L, "STARTED", d1, d3, LocalDateTime.of(2026, 10, 5, 9, 0));
            insert(c, 2L, "CONFIRMED", d3, d3, LocalDateTime.of(2026, 10, 1, 9, 0));
        }

        flyway(null).migrate();

        try (Connection c = connect()) {
            assertEquals(DRIVER, driverOf(c, confirmed));
            assertNull(driverOf(c, earlierPending));
            assertEquals("PENDING", statusOf(c, earlierPending));
            assertEquals(2L, driverOf(c, started));
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT count(*) FROM travel_bookings WHERE driver_id = 2")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "the confirmed trip clashing with a started one loses its driver");
            }
        }
    }

    private static Long driverOf(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT driver_id FROM travel_bookings WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long v = rs.getLong(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    private static String statusOf(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT status FROM travel_bookings WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
