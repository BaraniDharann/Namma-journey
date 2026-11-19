package com.travelplatform.controller;

import com.travelplatform.storage.FileStore;
import com.travelplatform.storage.ServedFile;
import com.travelplatform.storage.SignedUploadUrls;
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
    private final SignedUploadUrls signedUrls;

    public UploadsController(FileStore fileStore, SignedUploadUrls signedUrls) {
        this.fileStore = fileStore;
        this.signedUrls = signedUrls;
    }

    @GetMapping({"/uploads/**", "/driverphoto/**"})
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String key = request.getRequestURI().substring(request.getContextPath().length() + 1);
        MediaType type = imageType(key);
        if (type == null) {
            return ResponseEntity.notFound().build();
        }
        // Identity documents need a valid, unexpired server signature (SignedUploadUrls). 404, not
        // 403, so the response does not even confirm the file exists.
        boolean sensitive = SignedUploadUrls.isSensitive(key);
        if (sensitive && !signedUrls.isValid(key, request.getParameter("exp"), request.getParameter("sig"))) {
            return ResponseEntity.notFound().build();
        }

        return fileStore.serve(key)
                .map(served -> respond(served, type, sensitive))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Resource> respond(ServedFile served, MediaType type, boolean sensitive) {
        if (served instanceof ServedFile.Redirect redirect) {
            // The redirect itself must not be cached: the signed URL behind it expires.
            ResponseEntity.BodyBuilder found = ResponseEntity.status(HttpStatus.FOUND)
                    .location(redirect.location())
                    .cacheControl(CacheControl.noStore());
            return privacyHeaders(found, sensitive).build();
        }
        return privacyHeaders(ResponseEntity.ok(), sensitive)
                .contentType(type)
                .header("X-Content-Type-Options", "nosniff")
                // Identity documents are never cached (not even by the browser); other photos privately.
                .cacheControl(sensitive ? CacheControl.noStore() : CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(((ServedFile.Stream) served).resource());
    }

    /**
     * For identity documents: the signed link must not travel on in a Referer header (a page that
     * opens it cannot pass it to another site), must never be indexed or archived by a search
     * engine, and a saved file gets a neutral name instead of the stored key.
     */
    private static ResponseEntity.BodyBuilder privacyHeaders(ResponseEntity.BodyBuilder b, boolean sensitive) {
        if (!sensitive) {
            return b;
        }
        return b.header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive, nosnippet")
                .header("Content-Disposition", "inline; filename=\"document\"");
    }

    private static MediaType imageType(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0 || dot == key.length() - 1) {
            return null;
        }
        return IMAGE_TYPES.get(key.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
