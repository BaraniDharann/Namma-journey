package com.travelplatform.controller;

import com.travelplatform.storage.FileStore;
import com.travelplatform.storage.ServedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploaded images are served from whichever FileStore is active, at the same URLs as before, so
 * every path already saved in the database keeps working after a switch to S3/R2.
 */
class UploadsControllerTest {

    private MockMvc mvc;

    /** Disk-like store: some keys stream, one key redirects like S3 would. */
    private final FileStore store = new FileStore() {
        private final Map<String, ServedFile> files = Map.of(
                "uploads/drivers/license_a.jpg", new ServedFile.Stream(new ByteArrayResource(new byte[] {7, 7})),
                "driverphoto/trip_1/p.png", new ServedFile.Stream(new ByteArrayResource(new byte[] {8})),
                "uploads/trips/trip_2/p.jpg", new ServedFile.Redirect(URI.create("https://r2.example/p.jpg?X-Amz-Expires=600")),
                "uploads/drivers/page.html", new ServedFile.Stream(new ByteArrayResource(new byte[] {1})));

        @Override
        public void put(String key, InputStream content, long size, String contentType) {
        }

        @Override
        public Optional<ServedFile> serve(String key) {
            return Optional.ofNullable(files.get(key));
        }
    };

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UploadsController(store)).build();
    }

    @Test
    @DisplayName("a file on disk is streamed with its image type, a cache header and nosniff")
    void streamsLocalFiles() throws Exception {
        mvc.perform(get("/uploads/drivers/license_a.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[] {7, 7}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "max-age=604800, private"));
    }

    @Test
    @DisplayName("photos saved under the old /driverphoto/ path still load")
    void legacyDriverPhotoPath() throws Exception {
        mvc.perform(get("/driverphoto/trip_1/p.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    @DisplayName("an object-storage file is a redirect to its short-lived signed link")
    void redirectsToSignedUrl() throws Exception {
        mvc.perform(get("/uploads/trips/trip_2/p.jpg"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://r2.example/p.jpg?X-Amz-Expires=600"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("missing files and non-image extensions are 404 - even if the store has them")
    void notFoundCases() throws Exception {
        mvc.perform(get("/uploads/drivers/nope.jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/uploads/drivers/page.html")).andExpect(status().isNotFound());
    }
}
