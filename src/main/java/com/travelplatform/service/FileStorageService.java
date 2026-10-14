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
        // The Content-Type is the client's claim. The first bytes of the file are not: an HTML or
        // script file labelled image/png is refused here rather than stored.
        if (!startsLike(file, contentType)) {
            throw new IllegalArgumentException("The file is not a valid " + extension.substring(1).toUpperCase(Locale.ROOT) + " image");
        }

        String key = "uploads/" + folder + "/" + namePrefix + "_" + UUID.randomUUID() + extension;
        try (InputStream in = file.getInputStream()) {
            fileStore.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }
        // Folder and kind only: the full key is the file's address, and logs are not access control.
        log.info("Stored upload in uploads/{} ({})", folder, namePrefix.replaceAll("_[0-9]+$", ""));
        return "/" + key;
    }

    /**
     * Deletes a previously stored upload by its public path. Best-effort: failing to clean up an
     * old file must not fail the request that replaced it.
     */
    public void delete(String publicPath) {
        if (publicPath == null || !publicPath.startsWith("/")) {
            return;
        }
        try {
            fileStore.delete(publicPath.substring(1));
        } catch (RuntimeException e) {
            log.warn("Could not delete replaced upload: {}", e.toString());
        }
    }

    /** Checks the file's signature ("magic bytes") against its declared image type. */
    private static boolean startsLike(MultipartFile file, String contentType) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> read >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "image/png" -> read >= 8
                    && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                    && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A;
            case "image/gif" -> read >= 6
                    && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8'
                    && (head[4] == '7' || head[4] == '9') && head[5] == 'a';
            case "image/webp" -> read >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default -> false;
        };
    }
}
