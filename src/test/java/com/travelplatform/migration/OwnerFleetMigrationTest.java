package com.travelplatform.migration;

import org.flywaydb.core.Flyway;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V27 gives every travels owner their own fleet: the existing fleet, Trips, packages and owner
 * notifications become NJO-0001's, each fleet driver's car becomes a vehicle record, and owner-fleet
 * drivers stop taking City rides.
 *
 * <p>Needs Docker. Skipped (not failed) on a machine without it.
 */
@Testcontainers(disabledWithoutDocker = true)
class OwnerFleetMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

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

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private static String driverSql(String name, String mobile, String fleet, String plate, int seats) {
        return "INSERT INTO drivers (name, mobile, password, license_number, aadhaar_number, role, status, "
                + "first_login, email_verified, token_version, created_at, vehicle_type, serves_trip, serves_city, "
                + "fleet, vehicle_number, vehicle_seats) VALUES ('" + name + "', '" + mobile + "', 'x', 'L-" + mobile
                + "', '1111" + mobile.substring(2) + "', 'ROLE_DRIVER', 'ACTIVE', true, true, 0, now(), 'CAR', "
                + ("OWNER_FLEET".equals(fleet) ? "true" : "false") + ", true, '" + fleet + "', "
                + (plate == null ? "NULL" : "'" + plate + "'") + ", " + seats + ") RETURNING id";
    }

    private static String bookingSql(UUID id, String mode, Long driverId) {
        return "INSERT INTO travel_bookings (id, booking_date, distance_km, estimated_time_minutes, from_place, "
                + "status, to_place, travel_days, user_id, user_name, user_phone, ac_type, from_date, to_date, "
                + "travel_members, driver_id, total_amount, service_mode) VALUES ('" + id + "', now(), 100, 120, 'A', "
                + "'CONFIRMED', 'B', 1, '" + UUID.randomUUID() + "', 'T', '9876543210', 'AC', current_date, current_date, 2, "
                + (driverId == null ? "NULL" : driverId) + ", 1000, '" + mode + "')";
    }

    @Test
    @DisplayName("the existing fleet, Trips, packages and owner notifications become NJO-0001's")
    void existingFleetMovesToNJO0001() throws SQLException {
        flyway("26").migrate();
        long owner;
        long suvDriver;
        long noPlateDriver;
        long independent;
        UUID trip = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            owner = id(s, "INSERT INTO owners (email, password, role, created_at, owner_code, status) "
                    + "VALUES ('boss@x.com', 'h', 'ROLE_OWNER', now(), 'NJO-0001', 'APPROVED') RETURNING id");
            suvDriver = id(s, driverSql("Suv", "9000000001", "OWNER_FLEET", "KA01AB1234", 7));
            noPlateDriver = id(s, driverSql("Plain", "9000000002", "OWNER_FLEET", null, 4));
            independent = id(s, driverSql("Indie", "9000000003", "INDEPENDENT", null, 4));
            s.executeUpdate(bookingSql(trip, "TRIP", suvDriver));
            s.executeUpdate(bookingSql(city, "CITY", independent));
            s.executeUpdate("INSERT INTO travel_packages (name, category, state, places_included, duration_days, "
                    + "duration_nights, price_per_person, max_group_size, active, accommodation_included, food_included, "
                    + "guide_included, sightseeing_included, transport_included, toll_free, created_at) "
                    + "VALUES ('Ooty', 'HILL_STATION', 'TN', 'Ooty', 2, 1, 5000, 10, true, false, false, false, false, false, false, now())");
            s.executeUpdate("INSERT INTO notifications (id, created_at, message, read, recipient_id, recipient_role, title, type) "
                    + "VALUES ('" + UUID.randomUUID() + "', now(), 'm', false, 'owner', 'ROLE_OWNER', 't', 'BOOKING_CREATED')");
            s.executeUpdate("INSERT INTO pricing_config (price_per_km, price_per_hour, updated_at, updated_by) "
                    + "VALUES (12.50, 200.00, now(), 1)");
        }

        flyway(null).migrate();

        try (Connection c = connect(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT id, owner_id, serves_city, default_vehicle_id FROM drivers ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(owner, rs.getLong(2));
                assertFalse(rs.getBoolean(3));
                long suvVehicle = rs.getLong(4);
                assertTrue(rs.next());
                assertEquals(owner, rs.getLong(2));
                assertFalse(rs.getBoolean(3));
                long plainVehicle = rs.getLong(4);
                assertTrue(rs.next());
                rs.getLong(2);
                assertTrue(rs.wasNull(), "independent drivers belong to no owner");
                assertTrue(rs.getBoolean(3), "independent drivers keep City rides");

                try (Statement s2 = c.createStatement();
                     ResultSet v = s2.executeQuery("SELECT id, owner_id, registration_number, category, seats, active "
                             + "FROM vehicles ORDER BY id")) {
                    assertTrue(v.next());
                    assertEquals(suvVehicle, v.getLong(1));
                    assertEquals(owner, v.getLong(2));
                    assertEquals("KA01AB1234", v.getString(3));
                    assertEquals("SUV", v.getString(4));
                    assertEquals(7, v.getInt(5));
                    assertTrue(v.getBoolean(6));
                    assertTrue(v.next());
                    assertEquals(plainVehicle, v.getLong(1));
                    assertEquals("TEMP-" + noPlateDriver, v.getString(3));
                    assertEquals("SEDAN", v.getString(4));
                    assertFalse(v.next());
                }
            }
            try (ResultSet rs = s.executeQuery("SELECT owner_id, car_category, vehicle_id FROM travel_bookings WHERE id = '" + trip + "'")) {
                assertTrue(rs.next());
                assertEquals(owner, rs.getLong(1));
                assertEquals("SUV", rs.getString(2));
                assertTrue(rs.getLong(3) > 0);
            }
            try (ResultSet rs = s.executeQuery("SELECT owner_id, car_category FROM travel_bookings WHERE id = '" + city + "'")) {
                assertTrue(rs.next());
                rs.getLong(1);
                assertTrue(rs.wasNull(), "City rides belong to the platform, not an owner");
                assertNull(rs.getString(2));
            }
            assertEquals(owner, id(s, "SELECT owner_id FROM travel_packages"));
            try (ResultSet rs = s.executeQuery("SELECT recipient_id FROM notifications")) {
                assertTrue(rs.next());
                assertEquals(String.valueOf(owner), rs.getString(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT category, per_km, per_hour FROM owner_trip_rates WHERE owner_id = "
                    + owner + " ORDER BY category")) {
                int n = 0;
                while (rs.next()) {
                    n++;
                    assertEquals(0, new java.math.BigDecimal("12.50").compareTo(rs.getBigDecimal(2)));
                    assertEquals(0, new java.math.BigDecimal("200.00").compareTo(rs.getBigDecimal(3)));
                }
                assertEquals(4, n);
            }
            try (ResultSet rs = s.executeQuery("SELECT assignment_mode FROM owners")) {
                assertTrue(rs.next());
                assertEquals("AUTO", rs.getString(1));
            }
        }
    }

    @Test
    @DisplayName("a fresh database migrates, and new owners start on manual assignment")
    void freshDatabaseMigrates() throws SQLException {
        flyway(null).migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            assertEquals(0, id(s, "SELECT count(*) FROM vehicles"));
            s.executeUpdate("INSERT INTO owners (email, role, created_at) VALUES ('new@x.com', 'ROLE_OWNER', now())");
            try (ResultSet rs = s.executeQuery("SELECT assignment_mode FROM owners")) {
                assertTrue(rs.next());
                assertEquals("MANUAL", rs.getString(1));
            }
        }
    }

    @Test
    @DisplayName("the database refuses City rides for an owner-fleet driver")
    void ownerFleetCannotServeCity() throws SQLException {
        flyway(null).migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            long owner = id(s, "INSERT INTO owners (email, role, created_at) VALUES ('o@x.com', 'ROLE_OWNER', now()) RETURNING id");
            SQLException e = assertThrows(SQLException.class, () -> s.executeUpdate(
                    driverSql("Fleet", "9000000011", "OWNER_FLEET", null, 4).replace(" RETURNING id", "")));
            assertEquals("23514", e.getSQLState());
            assertTrue(owner > 0);
        }
    }

    @Test
    @DisplayName("one vehicle cannot be on two active trips on overlapping dates")
    void vehicleCannotDoubleBook() throws SQLException {
        flyway(null).migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            long owner = id(s, "INSERT INTO owners (email, role, created_at) VALUES ('o@x.com', 'ROLE_OWNER', now()) RETURNING id");
            long vehicle = id(s, "INSERT INTO vehicles (owner_id, category, registration_number, seats, ac) "
                    + "VALUES (" + owner + ", 'SEDAN', 'KA01X1', 4, true) RETURNING id");
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            s.executeUpdate(bookingSql(a, "TRIP", null));
            s.executeUpdate(bookingSql(b, "TRIP", null));
            s.executeUpdate("UPDATE travel_bookings SET vehicle_id = " + vehicle + " WHERE id = '" + a + "'");
            SQLException e = assertThrows(SQLException.class, () ->
                    s.executeUpdate("UPDATE travel_bookings SET vehicle_id = " + vehicle + " WHERE id = '" + b + "'"));
            assertEquals("23P01", e.getSQLState());
        }
    }

    @Test
    @DisplayName("a payment takes the owner of its booking")
    void paymentInheritsBookingOwner() throws SQLException {
        flyway(null).migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            long owner = id(s, "INSERT INTO owners (email, role, created_at) VALUES ('o@x.com', 'ROLE_OWNER', now()) RETURNING id");
            UUID trip = UUID.randomUUID();
            s.executeUpdate(bookingSql(trip, "TRIP", null));
            s.executeUpdate("UPDATE travel_bookings SET owner_id = " + owner + " WHERE id = '" + trip + "'");
            s.executeUpdate("INSERT INTO payments (id, amount, booking_id, created_at, payment_method, status, user_id, version, purpose) "
                    + "VALUES ('" + UUID.randomUUID() + "', 1000, '" + trip + "', now(), 'UPI', 'PENDING', '"
                    + UUID.randomUUID() + "', 0, 'FARE')");
            assertEquals(owner, id(s, "SELECT owner_id FROM payments"));
        }
    }
}
