package com.travelplatform.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Disk storage keeps working exactly as before - same URLs, same folders - so existing uploads
 * still load. Keys map onto directories by prefix, and nothing can escape those directories.
 */
class LocalFileStoreTest {

    @TempDir
    Path tmp;

    private LocalFileStore store() {
        return new LocalFileStore(Map.of(
                "uploads/drivers/", tmp.resolve("drivers"),
                "uploads/trips/", tmp.resolve("trips"),
                "driverphoto/", tmp.resolve("legacy-driverphoto")));
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a stored file is served back from the directory its prefix maps to")
    void putThenServe() throws Exception {
        LocalFileStore store = store();
        store.put("uploads/trips/b-1/end.jpg", bytes("jpeg-bytes"), 10, "image/jpeg");

        assertTrue(Files.exists(tmp.resolve("trips/b-1/end.jpg")));
        ServedFile served = store.serve("uploads/trips/b-1/end.jpg").orElseThrow();
        ServedFile.Stream stream = assertInstanceOf(ServedFile.Stream.class, served);
        assertArrayEquals("jpeg-bytes".getBytes(StandardCharsets.UTF_8), stream.resource().getContentAsByteArray());
    }

    @Test
    @DisplayName("files written before this change are still found at their old paths")
    void legacyFilesStillServe() throws Exception {
        Files.createDirectories(tmp.resolve("drivers"));
        Files.writeString(tmp.resolve("drivers/license_abc.png"), "old");
        Files.createDirectories(tmp.resolve("legacy-driverphoto/trip-9"));
        Files.writeString(tmp.resolve("legacy-driverphoto/trip-9/p.jpg"), "older");

        assertTrue(store().serve("uploads/drivers/license_abc.png").isPresent());
        assertTrue(store().serve("driverphoto/trip-9/p.jpg").isPresent());
    }

    @Test
    @DisplayName("a missing file is empty, not an error")
    void missingIsEmpty() {
        assertFalse(store().serve("uploads/drivers/nope.jpg").isPresent());
    }

    @Test
    @DisplayName("path traversal and unknown prefixes are refused")
    void traversalIsRefused() {
        LocalFileStore store = store();
        assertFalse(store.serve("uploads/drivers/../../etc/passwd").isPresent());
        assertFalse(store.serve("uploads/drivers/..%2F..%2Fsecret").isPresent());
        assertFalse(store.serve("etc/passwd").isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> store.put("uploads/trips/../../evil.jpg", bytes("x"), 1, "image/jpeg"));
        assertThrows(IllegalArgumentException.class,
                () -> store.put("somewhere/else.jpg", bytes("x"), 1, "image/jpeg"));
    }
}
