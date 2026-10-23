package com.travelplatform.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aadhaar and licence scans were served to anyone holding the URL, forever - and the URLs were
 * written to the logs. They now need a signature that only the server can make and that expires.
 */
class SignedUploadUrlsTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final SignedUploadUrls urls = new SignedUploadUrls("test-signing-secret-0123456789abcdef",
            Duration.ofMinutes(30), Clock.fixed(NOW, ZoneOffset.UTC));

    private static String[] parts(String signed) {
        Matcher m = Pattern.compile("^(/[^?]+)\\?exp=(\\d+)&sig=([A-Za-z0-9_-]+)$").matcher(signed);
        assertTrue(m.matches(), signed);
        return new String[] {m.group(1), m.group(2), m.group(3)};
    }

    @Test
    @DisplayName("an identity document link is signed and verifies until it expires")
    void signedLinkVerifies() {
        String[] p = parts(urls.signIfSensitive("/uploads/drivers/aadhaar_ab12.jpg"));
        assertEquals("/uploads/drivers/aadhaar_ab12.jpg", p[0]);
        assertTrue(urls.isValid("uploads/drivers/aadhaar_ab12.jpg", p[1], p[2]));
    }

    @Test
    @DisplayName("a signature for one file does not open another, and cannot be forged")
    void signatureIsBoundToTheFile() {
        String[] p = parts(urls.signIfSensitive("/uploads/drivers/aadhaar_ab12.jpg"));
        assertFalse(urls.isValid("uploads/drivers/aadhaar_zz99.jpg", p[1], p[2]));
        assertFalse(urls.isValid("uploads/drivers/aadhaar_ab12.jpg", String.valueOf(Long.parseLong(p[1]) + 3600), p[2]));
        assertFalse(urls.isValid("uploads/drivers/aadhaar_ab12.jpg", p[1], "forged"));
        assertFalse(urls.isValid("uploads/drivers/aadhaar_ab12.jpg", null, null));
    }

    @Test
    @DisplayName("an expired link no longer opens the document")
    void expiredLinkIsRefused() {
        String[] p = parts(urls.signIfSensitive("/uploads/drivers/license_ab12.png"));
        SignedUploadUrls later = new SignedUploadUrls("test-signing-secret-0123456789abcdef",
                Duration.ofMinutes(30), Clock.fixed(NOW.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        assertFalse(later.isValid("uploads/drivers/license_ab12.png", p[1], p[2]));
    }

    @Test
    @DisplayName("only identity documents are protected; profile and trip photos stay plain links")
    void onlySensitiveFilesAreSigned() {
        assertTrue(SignedUploadUrls.isSensitive("uploads/drivers/aadhaar_x.jpg"));
        assertTrue(SignedUploadUrls.isSensitive("uploads/drivers/license_x.jpg"));
        assertTrue(SignedUploadUrls.isSensitive("uploads/drivers/rc_x.jpg"));
        assertFalse(SignedUploadUrls.isSensitive("uploads/drivers/photo_x.jpg"));
        assertFalse(SignedUploadUrls.isSensitive("uploads/trips/trip_1/end.jpg"));
        assertEquals("/uploads/drivers/photo_x.jpg", urls.signIfSensitive("/uploads/drivers/photo_x.jpg"));
        assertEquals(null, urls.signIfSensitive(null));
    }
}
