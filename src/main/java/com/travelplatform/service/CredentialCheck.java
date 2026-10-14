package com.travelplatform.service;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password checks that take the same time whether or not the account exists.
 *
 * <p>Skipping BCrypt for an unknown account answers in about a millisecond instead of ~100ms,
 * which reveals that the account does not exist as clearly as a "not found" message would. So an
 * unknown account (or one with no password, e.g. Google-only) still pays for one BCrypt check,
 * against a hash nobody can match.
 */
final class CredentialCheck {

    /** BCrypt of a random throwaway string, made by the app's own encoder so its cost matches. */
    private static volatile String dummyHash;

    private CredentialCheck() {
    }

    static boolean matches(PasswordEncoder encoder, String raw, String storedHash) {
        if (storedHash == null || storedHash.isBlank() || raw == null) {
            if (dummyHash == null) {
                dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
            }
            encoder.matches(raw == null ? "" : raw, dummyHash);
            return false;
        }
        return encoder.matches(raw, storedHash);
    }
}
