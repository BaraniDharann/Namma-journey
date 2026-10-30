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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V25 swaps V14's placeholder City fares for real ones on a fresh database, and leaves alone any
 * fare the owner already changed.
 *
 * <p>Needs Docker. Skipped (not failed) on a machine without it; CI runners always have it.
 */
@Testcontainers(disabledWithoutDocker = true)
class CityFaresMigrationTest {

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

    /** vehicle type -> "base/perKm/perMinute/minimum". */
    private static Map<String, String> fares() throws SQLException {
        Map<String, String> fares = new LinkedHashMap<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT vehicle_type, base_fare, per_km, per_minute, minimum_fare "
                     + "FROM city_fares ORDER BY vehicle_type")) {
            while (rs.next()) {
                fares.put(rs.getString(1), rs.getBigDecimal(2) + "/" + rs.getBigDecimal(3) + "/"
                        + rs.getBigDecimal(4) + "/" + rs.getBigDecimal(5));
            }
        }
        return fares;
    }

    @Test
    @DisplayName("a fresh deploy starts on the real fares, auto on the Bengaluru meter")
    void freshDeploy() throws SQLException {
        flyway(null).migrate();

        assertEquals(Map.of(
                "AUTO", "0.00/18.00/0.00/36.00",
                "BIKE", "10.00/6.00/0.25/25.00",
                "CAR", "40.00/14.00/1.00/80.00"), fares());
    }

    @Test
    @DisplayName("a fare the owner already set is kept")
    void keepsOwnerFare() throws SQLException {
        flyway("24").migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE city_fares SET per_km = 20.00 WHERE vehicle_type = 'CAR'");
        }

        flyway(null).migrate();

        Map<String, String> fares = fares();
        assertEquals("50.00/20.00/2.00/80.00", fares.get("CAR"));
        assertEquals("0.00/18.00/0.00/36.00", fares.get("AUTO"));
    }
}
