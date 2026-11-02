package com.travelplatform.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads the Secure QR code printed on Aadhaar cards (and on e-Aadhaar / mAadhaar since 2019) and
 * checks UIDAI's digital signature on it - UIDAI's free offline verification: no API call, no
 * fee, nothing sent anywhere.
 *
 * <p>The QR text is one big decimal number. As bytes it is a gzip of: ISO-8859-1 text fields
 * separated by byte 255, then the holder's photo (JPEG 2000), then 0, 1 or 2 SHA-256 hashes of
 * their email / mobile, then a 256-byte SHA256withRSA signature over everything before it.
 * A card edited in Photoshop still has the original QR, or a QR whose signature fails - which is
 * why this, and not the printed text, is what the admin compares the owner against.
 *
 * <p>Only the last 4 digits of the Aadhaar number are in the QR (as the start of the reference
 * id), so nothing here ever holds the full number.
 */
public final class AadhaarSecureQr {

    public enum Status {
        /** UIDAI's signature is good: the details are exactly what UIDAI issued. */
        VALID,
        /** Read, but the signature does not match any configured UIDAI key - edited or forged. */
        INVALID_SIGNATURE,
        /** Read, but no UIDAI certificate is configured, so nothing was proven. */
        NOT_CHECKED,
        /** Not an Aadhaar Secure QR: an old pre-2019 XML QR, some other QR, or garbage. */
        NOT_SECURE_QR,
        /** No QR code could be found in the image. */
        UNREADABLE
    }

    /** What the QR says. Every field but status is null unless the QR could be read. */
    public record Result(Status status, String version, String aadhaarLast4, String name, String dob,
                         String gender, String careOf, String house, String street, String landmark,
                         String location, String vtc, String subDistrict, String district,
                         String state, String pincode, String postOffice, String mobileLast4,
                         byte[] photoJpeg2000) {

        static Result of(Status status) {
            return new Result(status, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    private static final int SIGNATURE_BYTES = 256;
    private static final int HASH_BYTES = 32;
    private static final int DELIMITER = 255;
    /** A real payload is 1-5 KB; anything that inflates past this is not one. */
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    /** QR codes hold at most 7089 digits. */
    private static final int MAX_QR_DIGITS = 8000;

    /** Text fields of the layout without a version field (the first Secure QR, 2019). */
    private static final int LEGACY_TEXT_FIELDS = 16;
    /** V2 adds the version first and the last 4 digits of the mobile last. */
    private static final int V2_TEXT_FIELDS = 18;

    private AadhaarSecureQr() {
    }

    /** @param uidaiKeys UIDAI's published signing keys; a QR verifies if any of them signed it. */
    public static Result verify(String qrText, List<PublicKey> uidaiKeys) {
        byte[] data = decode(qrText);
        if (data == null || data.length <= SIGNATURE_BYTES) {
            return Result.of(Status.NOT_SECURE_QR);
        }
        boolean versioned = data[0] == 'V';
        int textFields = versioned ? V2_TEXT_FIELDS : LEGACY_TEXT_FIELDS;

        // Only the first textFields delimiters separate text; the photo is binary and may contain 255.
        int[] ends = new int[textFields];
        int found = 0;
        for (int i = 0; i < data.length - SIGNATURE_BYTES && found < textFields; i++) {
            if ((data[i] & 0xFF) == DELIMITER) {
                ends[found++] = i;
            }
        }
        if (found < textFields) {
            return Result.of(Status.NOT_SECURE_QR);
        }
        String[] f = new String[textFields];
        int start = 0;
        for (int i = 0; i < textFields; i++) {
            f[i] = new String(data, start, ends[i] - start, StandardCharsets.ISO_8859_1);
            start = ends[i] + 1;
        }

        int o = versioned ? 1 : 0;
        String indicator = f[o];
        if (indicator.length() != 1 || indicator.charAt(0) < '0' || indicator.charAt(0) > '3'
                || f[o + 1].length() < 4) {
            return Result.of(Status.NOT_SECURE_QR);
        }
        // 1 = email hash present, 2 = mobile hash, 3 = both.
        int hashes = indicator.charAt(0) == '3' ? 2 : indicator.charAt(0) == '0' ? 0 : 1;
        int signatureStart = data.length - SIGNATURE_BYTES;
        int photoEnd = signatureStart - hashes * HASH_BYTES;
        if (photoEnd < start) {
            return Result.of(Status.NOT_SECURE_QR);
        }

        Status status = uidaiKeys.isEmpty() ? Status.NOT_CHECKED
                : signedByAny(data, signatureStart, uidaiKeys) ? Status.VALID : Status.INVALID_SIGNATURE;

        return new Result(status,
                versioned ? f[0] : null,
                f[o + 1].substring(0, 4),
                f[o + 2], f[o + 3], f[o + 4], f[o + 5], f[o + 8], f[o + 13], f[o + 7], f[o + 9],
                f[o + 15], f[o + 14], f[o + 6], f[o + 12], f[o + 10], f[o + 11],
                versioned ? blankToNull(f[o + 16]) : null,
                Arrays.copyOfRange(data, start, photoEnd));
    }

    private static boolean signedByAny(byte[] data, int signatureStart, List<PublicKey> keys) {
        byte[] signature = Arrays.copyOfRange(data, signatureStart, data.length);
        for (PublicKey key : keys) {
            try {
                Signature s = Signature.getInstance("SHA256withRSA");
                s.initVerify(key);
                s.update(data, 0, signatureStart);
                if (s.verify(signature)) {
                    return true;
                }
            } catch (GeneralSecurityException e) {
                // A key of the wrong type or a malformed signature - not signed by this key.
            }
        }
        return false;
    }

    /** The decimal number back to bytes and un-gzipped; null if it is not that. */
    private static byte[] decode(String qrText) {
        if (qrText == null) {
            return null;
        }
        String digits = qrText.trim();
        if (digits.isEmpty() || digits.length() > MAX_QR_DIGITS || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        byte[] gz = new BigInteger(digits).toByteArray();
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_PAYLOAD_BYTES) {
                    return null;
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
