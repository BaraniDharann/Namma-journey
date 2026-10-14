package com.travelplatform.controller;

import com.travelplatform.storage.FileStore;
import com.travelplatform.storage.ServedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Serves uploaded images from the active {@link FileStore}, at the URLs already saved in the
 * database ({@code /uploads/drivers/...}, {@code /uploads/trips/...}, legacy {@code /driverphoto/...}).
 *
 * <p>Replaces the static resource handlers, which could only read this server's disk. With S3/R2
 * the answer is a 302 to a signed URL that expires; with local disk the bytes are streamed.
 *
 * <p>Only image extensions are ever served, with {@code nosniff}, so a file that slipped past
 * upload validation still cannot be rendered by the browser as HTML or script.
 */
@RestController
public class UploadsController {

    private static final Map<String, MediaType> IMAGE_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"),
            "gif", MediaType.IMAGE_GIF);

    private final FileStore fileStore;

    public UploadsController(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @GetMapping({"/uploads/**", "/driverphoto/**"})
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String key = request.getRequestURI().substring(request.getContextPath().length() + 1);
        MediaType type = imageType(key);
        if (type == null) {
            return ResponseEntity.notFound().build();
        }

        return fileStore.serve(key)
                .map(served -> respond(served, type))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Resource> respond(ServedFile served, MediaType type) {
        if (served instanceof ServedFile.Redirect redirect) {
            // The redirect itself must not be cached: the signed URL behind it expires.
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(redirect.location())
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                // private: these are personal documents; never let a shared cache keep them.
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(((ServedFile.Stream) served).resource());
    }

    private static MediaType imageType(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return null;
        }
        return IMAGE_TYPES.get(key.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
