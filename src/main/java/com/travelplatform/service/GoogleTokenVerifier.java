package com.travelplatform.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Verifies the ID token that Sign-in with Google hands the browser.
 *
 * <p>The previous implementation split the token on '.', base64-decoded the middle segment and
 * trusted whatever <code>email</code> it found. Nothing was checked — not the signature, not the
 * issuer, not the audience, not the expiry — so anyone could POST a hand-written token with
 * <code>{"alg":"none"}</code> and any email in the payload and receive a real session for that
 * account. An ID token is only evidence of identity once its signature has been checked against
 * Google's published keys, which is what {@link GoogleIdTokenVerifier} does here, along with:
 *
 * <ul>
 *   <li><b>audience</b> — the token was minted for <em>this</em> application, not some other site
 *       the same person signed into (without this, any site's token would be accepted);</li>
 *   <li><b>issuer</b> — it really came from Google;</li>
 *   <li><b>expiry</b> — it is still current.</li>
 * </ul>
 *
 * <p>The verifier caches Google's key set and honours its cache headers, so a login does not make
 * an outbound call per request.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private static final List<String> ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String clientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        if (clientId == null || clientId.isBlank()) {
            log.warn("Google client id is not configured — Sign-in with Google will be refused.");
            return;
        }
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .setIssuers(ISSUERS)
                .build();
    }

    /** The verified identity behind a Google ID token. */
    public record GoogleIdentity(String email, String name, String subject) {}

    /**
     * @throws IllegalArgumentException if the token is missing, unverifiable, for another audience,
     *         expired, or carries an address Google itself has not confirmed.
     */
    public GoogleIdentity verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("Google token is required");
        }
        if (verifier == null) {
            // Failing closed matters more than a helpful message: with no client id configured we
            // cannot check the audience, and an unchecked token is worth nothing.
            throw new IllegalArgumentException("Google sign-in is not available");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            // Covers a malformed token as well as a transport failure fetching Google's keys.
            log.warn("Google ID token verification failed: {}", e.toString());
            throw new IllegalArgumentException("Invalid Google token");
        }
        if (idToken == null) {
            log.warn("Google ID token rejected: signature, audience, issuer or expiry check failed");
            throw new IllegalArgumentException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google token carries no email address");
        }
        // An unverified address is one the account holder typed, not one Google confirmed. Matching
        // an existing account on it would let someone claim another person's address.
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new IllegalArgumentException("Google account email is not verified");
        }

        Object name = payload.get("name");
        if (name == null) name = payload.get("given_name");
        return new GoogleIdentity(email, name != null ? String.valueOf(name) : null, payload.getSubject());
    }
}
