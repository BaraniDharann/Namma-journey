package com.travelplatform.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Expiring, server-signed links for drivers' identity documents (Aadhaar and licence scans).
 *
 * <p>These were served to anyone holding the URL, with no expiry - and the URL was written to the
 * logs. An &lt;img&gt; tag cannot send the JWT, so instead the API hands the owner (or the driver
 * themselves) a link carrying {@code exp} and an HMAC {@code sig} over path + expiry; the uploads
 * endpoint serves an identity document only with a valid, unexpired signature. Profile and trip
 * photos are not sensitive and stay plain links.
 *
 * <p>The key is derived from the JWT secret unless {@code app.uploads.signing-key} is set, so no
 * new secret is required to deploy this.
 */
@Component
public class SignedUploadUrls {

    private static final String[] SENSITIVE_PREFIXES = {"uploads/drivers/aadhaar_", "uploads/drivers/license_"};

    private final byte[] key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public SignedUploadUrls(@Value("${app.uploads.signing-key:}") String signingKey,
                            @Value("${jwt.secret}") String jwtSecret,
                            @Value("${app.uploads.signed-url-ttl:30m}") Duration ttl) {
        this(signingKey == null || signingKey.isBlank() ? "uploads:" + jwtSecret : signingKey, ttl, Clock.systemUTC());
    }

    SignedUploadUrls(String secret, Duration ttl, Clock clock) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    /** Keys (no leading slash) that need a signature to be served. */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        for (String prefix : SENSITIVE_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** "/uploads/drivers/aadhaar_x.jpg" -> same path + "?exp=...&sig=..."; anything else unchanged. */
    public String signIfSensitive(String publicPath) {
        if (publicPath == null || !publicPath.startsWith("/") || !isSensitive(publicPath.substring(1))) {
            return publicPath;
        }
        long exp = clock.instant().plus(ttl).getEpochSecond();
        return publicPath + "?exp=" + exp + "&sig=" + signature(publicPath.substring(1), exp);
    }

    public boolean isValid(String key, String exp, String sig) {
        if (key == null || exp == null || sig == null) {
            return false;
        }
        long expiry;
        try {
            expiry = Long.parseLong(exp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (clock.instant().getEpochSecond() > expiry) {
            return false;
        }
        return MessageDigest.isEqual(signature(key, expiry).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    private String signature(String key, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.key, "HmacSHA256"));
            byte[] raw = mac.doFinal((key + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is mandatory on every JVM", e);
        }
    }
}
