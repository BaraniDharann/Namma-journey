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

    // Real file signatures, so the content check sees genuine images.
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 1};
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 0, 1, 0, 0, 0}; // "GIF89a"
    static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}; // "RIFF" ... "WEBP" at 8-11

    /** A real, decodable picture: uploads are now re-drawn as JPEG (ImageCompressor). */
    private static byte[] real(String format) {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(30, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockMultipartFile image(String filename, String contentType) {
        byte[] body = contentType == null ? new byte[] {1, 2, 3} : switch (contentType) {
            case "image/jpeg", "image/jpg" -> real("jpg");
            case "image/png" -> real("png");
            case "image/gif" -> GIF;
            case "image/webp" -> WEBP;
            default -> new byte[] {1, 2, 3};
        };
        return new MockMultipartFile("file", filename, contentType, body);
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

        assertTrue(path.matches("/uploads/trips/trip_b-1/driver_7_[0-9a-f-]{36}\\.jpg"), path);
        assertTrue(store.serve(key(path)).isPresent());
    }

    @Test
    @DisplayName("every upload is stored as a re-drawn JPEG, whatever the uploader named or labelled it")
    void storedAsCompressedJpeg() {
        String path = service.storeFile(image("evil.html", "image/png"), "photo");

        assertTrue(path.endsWith(".jpg"), path);
    }

    @Test
    @DisplayName("anything that is not a jpeg, png, webp or gif is refused")
    void nonImagesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(image("a.svg", "image/svg+xml"), "photo"));
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(image("a.pdf", "application/pdf"), "photo"));
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(image("a", null), "photo"));
    }

    @Test
    @DisplayName("a file labelled as an image but not actually one is refused (the label is the client's word)")
    void contentMustMatchTheLabel() {
        MockMultipartFile disguised = new MockMultipartFile("file", "x.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(disguised, "photo"));

        MockMultipartFile pngLabelledJpeg = new MockMultipartFile("file", "x.jpg", "image/jpeg", PNG);
        assertThrows(IllegalArgumentException.class, () -> service.storeFile(pngLabelledJpeg, "photo"));
    }

    @Test
    @DisplayName("an optional document left empty stores nothing")
    void emptyUploadIsNull() {
        assertNull(service.storeFile(new MockMultipartFile("file", new byte[0]), "photo"));
        assertNull(service.storeFile(null, "photo"));
    }
}
