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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V26 turns the single owner into owner NJO-0001, already approved, and makes every owner added
 * afterwards start as a pending application with no password.
 *
 * <p>Needs Docker. Skipped (not failed) on a machine without it; CI runners always have it.
 */
@Testcontainers(disabledWithoutDocker = true)
class MultiOwnerMigrationTest {

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

    @Test
    @DisplayName("the existing owner becomes NJO-0001, approved, and keeps its password")
    void existingOwnerBecomesNJO0001Approved() throws SQLException {
        flyway("25").migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO owners (email, password, role, created_at) "
                    + "VALUES ('boss@x.com', 'h', 'ROLE_OWNER', now())");
        }

        flyway(null).migrate();

        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT owner_code, status, must_change_password, password FROM owners")) {
            assertTrue(rs.next());
            assertEquals("NJO-0001", rs.getString(1));
            assertEquals("APPROVED", rs.getString(2));
            assertFalse(rs.getBoolean(3));
            assertEquals("h", rs.getString(4));
        }
    }

    @Test
    @DisplayName("an owner added after V26 is a pending application, and Owner IDs continue from 2")
    void newOwnerDefaultsToPendingAndCodesStartAt2() throws SQLException {
        flyway(null).migrate();

        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO owners (email, role, created_at) VALUES ('new@x.com', 'ROLE_OWNER', now())");
            try (ResultSet rs = s.executeQuery("SELECT status, password FROM owners WHERE email = 'new@x.com'")) {
                assertTrue(rs.next());
                assertEquals("PENDING_REVIEW", rs.getString(1));
                assertNull(rs.getString(2));
            }
            try (ResultSet rs = s.executeQuery("SELECT nextval('owner_code_seq')")) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }
        }
    }

    @Test
    @DisplayName("a fresh database has no owners and an admins table")
    void freshDatabaseHasNoOwners() throws SQLException {
        flyway(null).migrate();

        try (Connection c = connect(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM owners")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM admins")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
