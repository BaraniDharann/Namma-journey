package com.travelplatform.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Format checks and masking for a travels owner's application. Like {@link DriverApplicationChecks}
 * these catch typos and made-up numbers, not forgeries - the admin's checks on the official
 * portals (and the UIDAI QR signature) are what catch those.
 */
public final class OwnerApplicationChecks {

    /** Five letters, four digits, a check letter: ABCDE1234F. */
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    /** Bank code, a reserved zero, branch code: SBIN0001234. */
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private OwnerApplicationChecks() {
    }

    public static boolean validPan(String raw) {
        return raw != null && PAN.matcher(normalisePan(raw)).matches();
    }

    public static boolean validIfsc(String raw) {
        return raw != null && IFSC.matcher(raw.trim().toUpperCase(Locale.ROOT)).matches();
    }

    public static String normalisePan(String raw) {
        return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * What duplicates are found by: the PAN itself is stored encrypted with a random IV, so two
     * encryptions of one PAN never compare equal.
     */
    public static String panHash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalisePan(raw).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String maskPan(String pan) {
        return pan == null || pan.length() < 5 ? null : "XXXXX" + pan.substring(pan.length() - 5);
    }

    public static String maskAccount(String account) {
        return account == null || account.length() < 4 ? null : "XXXXXX" + account.substring(account.length() - 4);
    }

    public static String aadhaarLast4(String raw) {
        String digits = raw.replaceAll("\\D", "");
        return digits.substring(digits.length() - 4);
    }
}
