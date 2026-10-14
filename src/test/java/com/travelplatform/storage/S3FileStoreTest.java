package com.travelplatform.storage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Uploads in shared object storage, so a photo uploaded through one backend instance loads
 * through any other. Adobe S3Mock speaks the same S3 API as AWS S3 and Cloudflare R2.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FileStoreTest {

    private static final String BUCKET = "namma-uploads";

    @Container
    private static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:3.11.0")
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));

    private static S3FileStore store;

    @BeforeAll
    static void createBucket() {
        S3StorageProperties props = new S3StorageProperties(
                BUCKET, "us-east-1",
                "http://" + S3.getHost() + ":" + S3.getMappedPort(9090),
                "test-access", "test-secret-key", true, Duration.ofMinutes(10));
        S3Client client = S3FileStore.client(props);
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        store = new S3FileStore(client, S3FileStore.presigner(props), props);
    }

    @Test
    @DisplayName("an uploaded image is served as a short-lived signed link that returns its bytes")
    void putThenServeViaSignedUrl() throws Exception {
        byte[] jpeg = "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        store.put("uploads/trips/b-1/end.jpg", new ByteArrayInputStream(jpeg), jpeg.length, "image/jpeg");

        ServedFile served = store.serve("uploads/trips/b-1/end.jpg").orElseThrow();
        ServedFile.Redirect redirect = assertInstanceOf(ServedFile.Redirect.class, served);

        HttpURLConnection conn = (HttpURLConnection) redirect.location().toURL().openConnection();
        assertEquals(200, conn.getResponseCode());
        assertEquals("image/jpeg", conn.getContentType());
        try (InputStream in = conn.getInputStream()) {
            assertArrayEquals(jpeg, in.readAllBytes());
        }
    }

    @Test
    @DisplayName("the signed link expires instead of being a permanent public URL")
    void signedUrlCarriesAnExpiry() {
        store.put("uploads/drivers/license_x.jpg", new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg");

        String url = ((ServedFile.Redirect) store.serve("uploads/drivers/license_x.jpg").orElseThrow())
                .location().toString();

        assertEquals(true, url.contains("X-Amz-Expires=600"), url);
    }

    @Test
    @DisplayName("keys outside the upload folders, or with traversal, are refused")
    void badKeysAreRefused() {
        assertFalse(store.serve("../etc/passwd").isPresent());
        assertFalse(store.serve("config/secret.json").isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> store.put("uploads/../x.jpg", new ByteArrayInputStream(new byte[] {1}), 1, "image/jpeg"));
    }
}
