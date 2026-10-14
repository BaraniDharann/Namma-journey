package com.travelplatform.service;

import com.travelplatform.storage.LocalFileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every uploaded image - driver licence, Aadhaar scan, end-of-trip photo - goes through one door
 * into the configured FileStore, and comes out at a URL the app can serve.
 */
class FileStorageServiceTest {

    @TempDir
    Path tmp;

    private LocalFileStore store;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        store = new LocalFileStore(Map.of(
                "uploads/drivers/", tmp.resolve("drivers"),
                "uploads/trips/", tmp.resolve("trips")));
        service = new FileStorageService(store);
    }

    private static MockMultipartFile image(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, new byte[] {1, 2, 3});
    }

    private static String key(String publicPath) {
        return publicPath.substring(1);
    }

    @Test
    @DisplayName("driver documents keep their /uploads/drivers/ URL and can be served back")
    void driverDocumentRoundTrip() {
        String path = service.storeFile(image("licence.jpg", "image/jpeg"), "license");

        assertTrue(path.matches("/uploads/drivers/license_[0-9a-f-]{36}\\.jpg"), path);
        assertTrue(store.serve(key(path)).isPresent());
    }

    @Test
    @DisplayName("trip photos land in the shared store under /uploads/trips/, not a folder outside the volume")
    void tripPhotoGoesToTheStore() {
        String path = service.storeImage(image("p.png", "image/png"), "trips/trip_b-1", "driver_7");

        assertTrue(path.matches("/uploads/trips/trip_b-1/driver_7_[0-9a-f-]{36}\\.png"), path);
        assertTrue(store.serve(key(path)).isPresent());
    }

    @Test
    @DisplayName("the extension comes from the verified image type, never from the uploader's filename")
    void extensionFollowsContentType() {
        String path = service.storeFile(image("evil.html", "image/png"), "photo");

        assertTrue(path.endsWith(".png"), path);
    }

    @Test
    @DisplayName("anything that is not a jpeg, png, webp or gif is refused")
    void nonImagesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(image("a.svg", "image/svg+xml"), "photo"));
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(image("a.pdf", "application/pdf"), "photo"));
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(image("a", null), "photo"));
    }

    @Test
    @DisplayName("an optional document left empty stores nothing")
    void emptyUploadIsNull() {
        assertNull(service.storeFile(new MockMultipartFile("file", new byte[0]), "photo"));
        assertNull(service.storeFile(null, "photo"));
    }
}
