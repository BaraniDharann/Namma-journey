package com.travelplatform.migration;

import com.travelplatform.security.FieldEncryption;
import com.travelplatform.security.FieldEncryptionTest;
import com.travelplatform.security.IdNumberHash;
import com.travelplatform.service.IdNumberBackfill;
import com.travelplatform.service.OwnerApplicationChecks;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V37 + IdNumberBackfill against real Postgres: readable Aadhaar and PAN numbers saved before the
 * change end up as bcrypt + keyed hash + masked tail, with the readable copies erased.
 *
 * <p>Needs Docker. Skipped (not failed) on a machine without it.
 */
@Testcontainers(disabledWithoutDocker = true)
class IdNumberHashMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        flyway(null).clean();
        FieldEncryption.configure(FieldEncryptionTest.KEY);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @AfterEach
    void forgetKey() {
        FieldEncryption.configure(null);
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

    private long driver(String mobile, String aadhaar) {
        return jdbc.queryForObject("INSERT INTO drivers (name, mobile, password, license_number, aadhaar_number, role, "
                + "status, first_login, email_verified, token_version, created_at, fleet, serves_trip) VALUES ('D', ?, 'x', ?, ?, "
                + "'ROLE_DRIVER', 'ACTIVE', true, true, 0, now(), 'INDEPENDENT', false) RETURNING id", Long.class, mobile, "L-" + mobile, aadhaar);
    }

    @Test
    @DisplayName("Old readable Aadhaar and PAN numbers are hashed and erased; running again changes nothing")
    void convertsOldRows() {
        flyway("36").migrate();
        long d1 = driver("9000000001", "2345 6789 0124");
        long d2 = driver("9000000002", "987654321098");
        long owner = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, status, pan_number) "
                + "VALUES ('o@x.com', 'ROLE_OWNER', now(), 'APPROVED', ?) RETURNING id", Long.class,
                FieldEncryption.encrypt("ABCDE1234F"));

        flyway(null).migrate();
        assertEquals("0124", jdbc.queryForObject("SELECT aadhaar_last4 FROM drivers WHERE id = ?", String.class, d1),
                "the SQL fills the last 4 digits on its own");

        new IdNumberBackfill(jdbc).run(null);

        Map<String, Object> r1 = jdbc.queryForMap("SELECT * FROM drivers WHERE id = ?", d1);
        assertNull(r1.get("aadhaar_number"));
        assertEquals(OwnerApplicationChecks.aadhaarHash("234567890124"), r1.get("aadhaar_hash"));
        assertTrue(IdNumberHash.matches("234567890124", (String) r1.get("aadhaar_bcrypt")));
        assertEquals("1098", jdbc.queryForObject("SELECT aadhaar_last4 FROM drivers WHERE id = ?", String.class, d2));

        Map<String, Object> o = jdbc.queryForMap("SELECT * FROM owners WHERE id = ?", owner);
        assertNull(o.get("pan_number"));
        assertEquals("XXXXX1234F", o.get("pan_masked"));
        assertTrue(IdNumberHash.matches("ABCDE1234F", (String) o.get("pan_bcrypt")));
        assertEquals(OwnerApplicationChecks.panHash("ABCDE1234F"), o.get("pan_hash"));

        String bcryptBefore = (String) r1.get("aadhaar_bcrypt");
        new IdNumberBackfill(jdbc).run(null);
        assertEquals(bcryptBefore, jdbc.queryForObject("SELECT aadhaar_bcrypt FROM drivers WHERE id = ?", String.class, d1));
    }

    @Test
    @DisplayName("One Aadhaar still cannot be registered to two drivers")
    void aadhaarHashIsUnique() {
        flyway(null).migrate();
        String hash = OwnerApplicationChecks.aadhaarHash("234567890124");
        String insert = "INSERT INTO drivers (name, mobile, password, license_number, aadhaar_hash, role, status, "
                + "first_login, email_verified, token_version, created_at, fleet, serves_trip) VALUES ('D', ?, 'x', ?, ?, 'ROLE_DRIVER', "
                + "'ACTIVE', true, true, 0, now(), 'INDEPENDENT', false)";
        jdbc.update(insert, "9000000001", "L-1", hash);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update(insert, "9000000002", "L-2", hash));
    }

    @Test
    @DisplayName("Without the server key nothing is converted and the app still starts")
    void waitsForTheKey() {
        flyway("36").migrate();
        long d = driver("9000000003", "234567890124");
        flyway(null).migrate();
        FieldEncryption.configure(null);
        new IdNumberBackfill(jdbc).run(null);
        assertEquals("234567890124", jdbc.queryForObject("SELECT aadhaar_number FROM drivers WHERE id = ?", String.class, d));
    }
}
