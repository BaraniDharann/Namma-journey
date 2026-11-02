package com.travelplatform.storage;

import com.travelplatform.security.FieldEncryption;
import com.travelplatform.security.FieldEncryptionTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Moving from db to s3: every stored file lands in the bucket under its old key, decrypted, once. */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseToS3ImportTest {

    private static final String BUCKET = "namma-uploads";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    private static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:3.11.0")
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    private static JdbcTemplate jdbc;
    private static S3Client client;
    private static S3FileStore s3;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        S3StorageProperties props = new S3StorageProperties(
                BUCKET, "us-east-1",
                "http://" + S3.getHost() + ":" + S3.getMappedPort(9090),
                "test-access", "test-secret-key", true, Duration.ofMinutes(10));
        client = S3FileStore.client(props);
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        s3 = new S3FileStore(client, S3FileStore.presigner(props), props);
    }

    @AfterEach
    void noKey() {
        FieldEncryption.configure(null);
    }

    private byte[] inBucket(String key) {
        ResponseBytes<GetObjectResponse> bytes = client.getObjectAsBytes(b -> b.bucket(BUCKET).key(key));
        return bytes.asByteArray();
    }

    @Test
    @DisplayName("copies every stored file as plaintext under the same key, and skips them on the next run")
    void copiesThenSkips() {
        FieldEncryption.configure(FieldEncryptionTest.KEY);
        DatabaseFileStore db = new DatabaseFileStore(jdbc);
        byte[] scan = "pretend aadhaar scan".getBytes(StandardCharsets.UTF_8);
        byte[] photo = "pretend trip photo".getBytes(StandardCharsets.UTF_8);
        db.put("uploads/drivers/aadhaar_1.jpg", new ByteArrayInputStream(scan), scan.length, "image/jpeg");
        db.put("uploads/trips/trip_7/driver_2_x.jpg", new ByteArrayInputStream(photo), photo.length, "image/jpeg");

        DatabaseToS3Import importer = new DatabaseToS3Import(jdbc, s3);

        assertEquals(new DatabaseToS3Import.Result(2, 0, 0), importer.run());
        assertArrayEquals(scan, inBucket("uploads/drivers/aadhaar_1.jpg"));
        assertArrayEquals(photo, inBucket("uploads/trips/trip_7/driver_2_x.jpg"));

        assertEquals(new DatabaseToS3Import.Result(0, 2, 0), importer.run());
    }

    @Test
    @DisplayName("without the encryption key nothing is copied")
    void needsKey() {
        assertEquals(new DatabaseToS3Import.Result(0, 0, 0), new DatabaseToS3Import(jdbc, s3).run());
    }
}
