package com.travelplatform.config;

import com.travelplatform.storage.FileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageConfigTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("the local store accepts travels owner documents under uploads/owners/")
    void localStoreTakesOwnerDocuments() {
        FileStore store = new StorageConfig().localFileStore(
                tmp.resolve("drivers").toString(), tmp.resolve("trips").toString(), tmp.resolve("owners").toString());

        store.put("uploads/owners/pan_x.jpg", new ByteArrayInputStream(new byte[]{1, 2}), 2, "image/jpeg");

        assertTrue(Files.exists(tmp.resolve("owners/pan_x.jpg")));
        assertTrue(store.serve("uploads/owners/pan_x.jpg").isPresent());
    }
}
