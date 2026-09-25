package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression guard for a full authentication bypass.
 *
 * <p>Sign-in with Google used to be "verified" by splitting the ID token on '.', base64-decoding
 * the middle segment and trusting whatever <code>email</code> it found. Nothing was checked — not
 * the signature, not the issuer, not the audience, not the expiry. A request carrying a
 * hand-written <code>{"alg":"none"}</code> token with any address in the payload came back 200 with
 * a real session for that account, so anyone could sign in as anyone, including users who had never
 * touched Google sign-in.
 *
 * <p>These cases pin the token shapes that made that work. The end-to-end proof that the running
 * server refuses them lives in playwright-tests/tests/11-login-e2e.spec.ts; what is checked here is
 * that verification is attempted at all and that it fails closed.
 */
class GoogleTokenVerifierTest {

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** The exact forgery that used to be accepted. */
    private static String forgedToken(String email) {
        return b64("{\"alg\":\"none\",\"typ\":\"JWT\"}")
                + "." + b64("{\"email\":\"" + email + "\",\"email_verified\":true,\"name\":\"Impersonator\"}")
                + ".not-a-real-signature";
    }

    private static GoogleTokenVerifier configured() {
        GoogleTokenVerifier v = new GoogleTokenVerifier();
        ReflectionTestUtils.setField(v, "clientId", "1234567890-test.apps.googleusercontent.com");
        v.init();
        return v;
    }

    @Test
    @DisplayName("an unsigned token naming any address is rejected, not decoded and believed")
    void forgedTokenIsRejected() {
        GoogleTokenVerifier verifier = configured();
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(forgedToken("victim@example.com")));
    }

    @Test
    @DisplayName("filling in a plausible issuer and audience does not help — only the signature counts")
    void forgedTokenWithPlausibleClaimsIsRejected() {
        GoogleTokenVerifier verifier = configured();
        String token = b64("{\"alg\":\"RS256\",\"kid\":\"made-up\"}")
                + "." + b64("{\"email\":\"victim@example.com\",\"email_verified\":true,"
                          + "\"iss\":\"https://accounts.google.com\","
                          + "\"aud\":\"1234567890-test.apps.googleusercontent.com\",\"exp\":9999999999}")
                + ".still-not-a-real-signature";
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(token));
    }

    @Test
    @DisplayName("missing and malformed tokens are rejected")
    void emptyAndMalformedTokensAreRejected() {
        GoogleTokenVerifier verifier = configured();
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(null));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(""));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify("   "));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify("not-a-jwt"));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify("a.b"));
    }

    @Test
    @DisplayName("with no client id configured the verifier fails closed rather than skipping the audience check")
    void unconfiguredVerifierRefusesEverything() {
        GoogleTokenVerifier verifier = new GoogleTokenVerifier();
        ReflectionTestUtils.setField(verifier, "clientId", "");
        verifier.init();
        // Accepting anything here would mean honouring a token minted for somebody else's site.
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(forgedToken("victim@example.com")));
    }
}
