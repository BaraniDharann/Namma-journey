package com.travelplatform.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * One-way storage for government ID numbers (Aadhaar, PAN). The full number is never kept:
 *
 * <ul>
 *   <li>a <b>bcrypt</b> hash (salted, slow), so a stolen database row cannot be turned back into
 *       the number, and a number someone types later can still be confirmed with {@link #matches};</li>
 *   <li>a keyed HMAC ({@link FieldEncryption#keyedHash}) next to it, only for "is this number already
 *       registered?". bcrypt's random salt makes two hashes of one number differ, so it cannot be
 *       searched; the HMAC can, and without the server key it is as unreadable as the bcrypt;</li>
 *   <li>the last few characters in clear, to show a masked number and to compare with the last 4
 *       digits printed in an Aadhaar QR.</li>
 * </ul>
 */
public final class IdNumberHash {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private IdNumberHash() {
    }

    /** bcrypt of an already-normalised number (digits only for Aadhaar, upper case for PAN). */
    public static String bcrypt(String normalised) {
        return normalised == null || normalised.isBlank() ? null : BCRYPT.encode(normalised);
    }

    /** True when {@code normalised} is the number behind {@code hash}. */
    public static boolean matches(String normalised, String hash) {
        return normalised != null && hash != null && BCRYPT.matches(normalised, hash);
    }

    /** "2345 6789-0124" -> "234567890124". */
    public static String aadhaarDigits(String raw) {
        return raw == null ? null : raw.replaceAll("\\D", "");
    }

    /** "XXXX XXXX 0124" from the last 4 digits; null when unknown. */
    public static String maskAadhaar(String last4) {
        return last4 == null || last4.isBlank() ? null : "XXXX XXXX " + last4;
    }
}
