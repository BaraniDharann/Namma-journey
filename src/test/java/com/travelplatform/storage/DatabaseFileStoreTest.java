package com.travelplatform.storage;

import com.travelplatform.security.FieldEncryption;
import com.travelplatform.security.FieldEncryptionTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V22 against real Postgres: files survive in the table, and only as ciphertext. */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseFileStoreTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static JdbcTemplate jdbc;
    private DatabaseFileStore store;
    private final byte[] scan = "ÿØ pretend this is an Aadhaar scan 1234 5678 9012".getBytes(StandardCharsets.ISO_8859_1);

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @BeforeEach
    void setUp() {
        FieldEncryption.configure(FieldEncryptionTest.KEY);
        jdbc.update("DELETE FROM stored_files");
        store = new DatabaseFileStore(jdbc);
    }

    @AfterEach
    void noKey() {
        FieldEncryption.configure(null);
    }

    private byte[] served(String key) throws Exception {
        ServedFile.Stream s = (ServedFile.Stream) store.serve(key).orElseThrow();
        return s.resource().getInputStream().readAllBytes();
    }

    @Test
    @DisplayName("stored, served back byte for byte, and never readable in the table")
    void roundTripEncryptedAtRest() throws Exception {
        String key = "uploads/drivers/aadhaar_1.jpg";
        store.put(key, new ByteArrayInputStream(scan), scan.length, "image/jpeg");

        byte[] raw = jdbc.queryForObject("SELECT data FROM stored_files WHERE file_key = ?", byte[].class, key);
        assertFalse(new String(raw, StandardCharsets.ISO_8859_1).contains("Aadhaar"), "ciphertext only");
        assertArrayEquals(scan, served(key));
    }

    @Test
    @DisplayName("replacing a key overwrites it; delete removes it")
    void replaceAndDelete() throws Exception {
        String key = "uploads/trips/trip_1/driver_9_x.jpg";
        store.put(key, new ByteArrayInputStream(scan), scan.length, "image/jpeg");
        byte[] newer = "second".getBytes(StandardCharsets.UTF_8);
        store.put(key, new ByteArrayInputStream(newer), newer.length, "image/jpeg");
        assertArrayEquals(newer, served(key));

        store.delete(key);
        assertTrue(store.serve(key).isEmpty());
    }

    @Test
    @DisplayName("keys outside our upload folders, or with traversal, are neither stored nor served")
    void keysValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> store.put("uploads/../etc/passwd", new ByteArrayInputStream(scan), scan.length, "image/jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> store.put("secrets/x.jpg", new ByteArrayInputStream(scan), scan.length, "image/jpeg"));
        assertTrue(store.serve("uploads/%2e%2e/x").isEmpty());
    }

    @Test
    @DisplayName("without APP_ENCRYPTION_KEY an identity scan is refused, not stored in the clear")
    void failsClosedWithoutKey() {
        FieldEncryption.configure("");
        assertThrows(IllegalStateException.class,
                () -> store.put("uploads/drivers/license_1.jpg", new ByteArrayInputStream(scan), scan.length, "image/jpeg"));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM stored_files", Integer.class) == 0);
    }
}
