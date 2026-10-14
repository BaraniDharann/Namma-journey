package com.travelplatform.service;

import com.travelplatform.storage.FileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The single way an uploaded image enters storage: driver photos, licences and Aadhaar scans,
 * and end-of-trip photos alike. Where the bytes end up - local disk or S3/R2 - is the
 * {@link FileStore}'s business; callers get back a public path ("/uploads/...") to save.
 *
 * <p>Only JPEG, PNG, WebP and GIF are accepted, and the file extension is derived from that
 * verified type. It used to be copied from the uploader's filename, so "evil.html" sent as
 * image/png was stored as .html.
 */
@Service
@Slf4j
public class FileStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    private final FileStore fileStore;

    public FileStorageService(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    /** A driver document. Returns "/uploads/drivers/{fileType}_{uuid}.{ext}", or null if none was sent. */
    public String storeFile(MultipartFile file, String fileType) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return storeImage(file, "drivers", fileType);
    }

    /**
     * Stores an image under "uploads/{folder}/" and returns its public path.
     *
     * @param folder     e.g. "drivers" or "trips/trip_{bookingId}"
     * @param namePrefix e.g. "license" or "driver_{id}"; a random UUID keeps every name unique
     */
    public String storeImage(MultipartFile file, String folder, String namePrefix) {
        String contentType = file.getContentType() == null ? null : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = contentType == null ? null : EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Only JPEG, PNG, WebP or GIF images are allowed");
        }

        String key = "uploads/" + folder + "/" + namePrefix + "_" + UUID.randomUUID() + extension;
        try (InputStream in = file.getInputStream()) {
            fileStore.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }
        log.info("Stored upload {}", key);
        return "/" + key;
    }
}
