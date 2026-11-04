package com.travelplatform.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V30 adds the bus module. The database itself never sells one seat twice for the same stretch of a
 * trip, adjacent stretches may share a seat, expired and cancelled allocations free the seat, and
 * every owner table is behind row-level security.
 *
 * <p>Needs Docker. Skipped (not failed) on a machine without it.
 */
@Testcontainers(disabledWithoutDocker = true)
class BusBookingMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private long ownerA;
    private long ownerB;
    private long trip;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    @BeforeEach
    void fixtures() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE bus_seat_allocations, bus_trips, bus_route_fares, bus_route_points, bus_route_stops, "
                    + "bus_routes, bus_seats, buses RESTART IDENTITY CASCADE");
            // Not TRUNCATE: that would cascade into the seeded places.
            s.execute("DELETE FROM owners");
            ownerA = id(s, "INSERT INTO owners (email, role, created_at, owner_code, status) "
                    + "VALUES ('a@x.com', 'ROLE_OWNER', now(), 'NJO-0002', 'APPROVED') RETURNING id");
            ownerB = id(s, "INSERT INTO owners (email, role, created_at, owner_code, status) "
                    + "VALUES ('b@x.com', 'ROLE_OWNER', now(), 'NJO-0003', 'APPROVED') RETURNING id");
            long busA = bus(s, ownerA, "TN01AB1234");
            bus(s, ownerB, "TN02CD5678");
            long route = id(s, "INSERT INTO bus_routes (owner_id, bus_id, name, run_days, active_from, cancellation_policy) "
                    + "VALUES (" + ownerA + ", " + busA + ", 'Chennai - Madurai', 127, current_date, "
                    + "'[{\"minHours\":0,\"percent\":100}]') RETURNING id");
            trip = id(s, "INSERT INTO bus_trips (route_id, bus_id, owner_id, travel_date, departure_at, arrival_at) "
                    + "VALUES (" + route + ", " + busA + ", " + ownerA + ", current_date, now(), now() + interval '8 hours') RETURNING id");
        }
    }

    private static long bus(Statement s, long owner, String plate) throws SQLException {
        return id(s, "INSERT INTO buses (owner_id, registration_number, name, permit_type) VALUES ("
                + owner + ", '" + plate + "', 'Bus " + plate + "', 'STAGE_CARRIAGE') RETURNING id");
    }

    private String allocation(String seat, int from, int to, String status) {
        return "INSERT INTO bus_seat_allocations (trip_id, owner_id, seat_label, from_seq, to_seq, status) VALUES ("
                + trip + ", " + ownerA + ", '" + seat + "', " + from + ", " + to + ", '" + status + "')";
    }

    @Test
    @DisplayName("places are seeded, and owners default to cars with an 8% bus commission")
    void seedsAndOwnerDefaults() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertTrue(id(s, "SELECT count(*) FROM bus_places") >= 60);
            assertEquals(1, id(s, "SELECT count(*) FROM bus_places WHERE lower(name) = 'madurai' AND lat IS NOT NULL"));
            try (ResultSet rs = s.executeQuery("SELECT fleet_type, bus_commission_percent FROM owners WHERE id = " + ownerA)) {
                assertTrue(rs.next());
                assertEquals("CARS", rs.getString(1));
                assertEquals(0, new java.math.BigDecimal("8").compareTo(rs.getBigDecimal(2)));
            }
            SQLException dup = assertThrows(SQLException.class,
                    () -> s.executeUpdate("INSERT INTO bus_places (name) VALUES ('MADURAI')"));
            assertEquals("23505", dup.getSQLState());
        }
    }

    @Test
    @DisplayName("one seat cannot be sold twice for overlapping stretches of a trip")
    void overlappingLegsRefused() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate(allocation("U3", 0, 2, "CONFIRMED"));
            SQLException e = assertThrows(SQLException.class, () -> s.executeUpdate(allocation("U3", 1, 3, "HELD")));
            assertEquals("23P01", e.getSQLState());
            SQLException blocked = assertThrows(SQLException.class, () -> s.executeUpdate(allocation("U3", 0, 3, "BLOCKED")));
            assertEquals("23P01", blocked.getSQLState());
            // Another seat on the same stretch is fine.
            assertDoesNotThrow(() -> s.executeUpdate(allocation("U4", 0, 2, "HELD")));
        }
    }

    @Test
    @DisplayName("adjacent stretches [0,1) and [1,2) can share a seat")
    void adjacentLegsAllowed() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate(allocation("L1", 0, 1, "CONFIRMED"));
            assertDoesNotThrow(() -> s.executeUpdate(allocation("L1", 1, 2, "HELD")));
            assertEquals("[0,1)", stringOf(s, "SELECT legs::text FROM bus_seat_allocations ORDER BY id LIMIT 1"));
        }
    }

    @Test
    @DisplayName("expired and cancelled allocations do not block the seat")
    void deadRowsDoNotBlock() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate(allocation("U1", 0, 2, "EXPIRED"));
            s.executeUpdate(allocation("U1", 0, 2, "CANCELLED"));
            assertDoesNotThrow(() -> s.executeUpdate(allocation("U1", 0, 2, "HELD")));
            // ...and flipping a stale hold to EXPIRED frees it for the next customer.
            s.executeUpdate("UPDATE bus_seat_allocations SET status = 'EXPIRED' WHERE status = 'HELD'");
            assertDoesNotThrow(() -> s.executeUpdate(allocation("U1", 1, 2, "CONFIRMED")));
        }
    }

    @Test
    @DisplayName("an owner on the scoped role sees only their own buses")
    void ownerSeesOnlyOwnBuses() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("SET ROLE nj_owner_scope");
            s.execute("SELECT set_config('nj.owner_id', '" + ownerB + "', false)");
            assertEquals(1, id(s, "SELECT count(*) FROM buses"));
            assertEquals(ownerB, id(s, "SELECT owner_id FROM buses"));
            assertEquals(0, id(s, "SELECT count(*) FROM bus_trips"));
            // Shared towns stay visible to everyone.
            assertTrue(id(s, "SELECT count(*) FROM bus_places") >= 60);
            SQLException e = assertThrows(SQLException.class, () -> bus(s, ownerA, "TN09ZZ0001"));
            assertEquals("42501", e.getSQLState());
            s.execute("RESET ROLE");
            s.execute("SELECT set_config('nj.owner_id', '', false)");
            assertEquals(2, id(s, "SELECT count(*) FROM buses"));
        }
    }

    @Test
    @DisplayName("every bus table with an owner is behind forced row-level security with both policies")
    void everyOwnerTableHasRowLevelSecurity() throws SQLException {
        List<String> missing = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT t.relname, t.relrowsecurity, t.relforcerowsecurity, "
                     + "(SELECT count(*) FROM pg_policies p WHERE p.tablename = t.relname "
                     + " AND p.policyname IN ('app_unscoped', 'owner_scoped')) "
                     + "FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace "
                     + "WHERE n.nspname = 'public' AND t.relkind = 'r' AND t.relname LIKE 'bus%' "
                     + "AND EXISTS (SELECT 1 FROM information_schema.columns col WHERE col.table_name = t.relname "
                     + " AND col.column_name = 'owner_id')")) {
            int tables = 0;
            while (rs.next()) {
                tables++;
                if (!rs.getBoolean(2) || !rs.getBoolean(3) || rs.getInt(4) != 2) {
                    missing.add(rs.getString(1));
                }
            }
            assertEquals(16, tables);
        }
        assertEquals(List.of(), missing);
    }

    private static String stringOf(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }
}
