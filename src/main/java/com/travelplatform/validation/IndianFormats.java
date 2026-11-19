package com.travelplatform.validation;

import com.travelplatform.service.DriverApplicationChecks;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one place every form's Indian identity and contact fields are checked, so driver signup,
 * owner signup, an owner adding a driver and profile edits all accept exactly the same things.
 * Each {@code require*} returns the normalised value (trimmed, spaces removed, upper-cased where
 * the format is upper case) or throws {@link IllegalArgumentException} with a message for the person.
 *
 * <p>Formats, as issued:
 * <ul>
 *   <li>Aadhaar: 12 digits, never starting 0 or 1, Verhoeff check digit last (UIDAI).</li>
 *   <li>PAN: AAAAA9999A; the 4th letter is the holder type (P person, C company, H HUF, F firm, E LLP,
 *       A AOP, T trust, B BOI, L local authority, J artificial person, G government).</li>
 *   <li>Driving licence: SS RR YYYY NNNNNNN, 15 characters - state, RTO, year of issue, serial
 *       (Sarathi format, used for every licence issued or renewed since 2019).</li>
 *   <li>Mobile: 10 digits starting 6-9 (TRAI numbering plan).</li>
 *   <li>Vehicle registration: SS RR X(XX) NNNN, or the Bharat series YY BH NNNN X(X).</li>
 *   <li>IFSC: 4 letters, 0, 6 letters or digits (RBI). Account: 9-18 digits.</li>
 *   <li>UPI ID: handle@psp (NPCI).</li>
 * </ul>
 */
public final class IndianFormats {

    public static final int NAME_MAX = 60;
    public static final int EMAIL_MAX = 254;
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    public static final int BUSINESS_NAME_MAX = 80;

    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{3}[PCHFATBLJGE][A-Z][0-9]{4}[A-Z]$");
    private static final Pattern LICENCE = Pattern.compile("^[A-Z]{2}[0-9]{2}(19|20)[0-9]{2}[0-9]{7}$");
    private static final Pattern PLATE = Pattern.compile("^[A-Z]{2}(0[1-9]|[1-9][0-9]?)[A-Z]{0,3}(?!0000)[0-9]{4}$");
    private static final Pattern BH_PLATE = Pattern.compile("^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final Pattern ACCOUNT = Pattern.compile("^[0-9]{9,18}$");
    private static final Pattern UPI = Pattern.compile("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z][a-zA-Z0-9]{1,63}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    /** A person's name: letters in any script (Tamil included), spaces, dot, apostrophe, hyphen. */
    private static final Pattern PERSON = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*$");
    /** A business or bank name: also digits and & , ( ) /. */
    private static final Pattern BUSINESS = Pattern.compile("^[\\p{L}\\p{M}0-9][\\p{L}\\p{M}0-9 .,'&()/-]*$");

    private IndianFormats() {
    }

    public static String requireName(String raw, String what) {
        String v = squash(raw);
        if (v.length() < 2 || v.length() > NAME_MAX || !PERSON.matcher(v).matches()) {
            throw new IllegalArgumentException(what + " must be 2 to " + NAME_MAX + " letters (spaces, . ' - allowed)");
        }
        return v;
    }

    public static String requireBusinessName(String raw, String what) {
        String v = squash(raw);
        if (v.length() < 2 || v.length() > BUSINESS_NAME_MAX || !BUSINESS.matcher(v).matches()) {
            throw new IllegalArgumentException(what + " must be 2 to " + BUSINESS_NAME_MAX + " letters or digits");
        }
        return v;
    }

    public static String requireMobile(String raw) {
        String v = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (v.startsWith("+91")) {
            v = v.substring(3);
        }
        if (!MOBILE.matcher(v).matches()) {
            throw new IllegalArgumentException("Enter a 10-digit Indian mobile number starting with 6, 7, 8 or 9");
        }
        return v;
    }

    public static String requireEmail(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (v.length() > EMAIL_MAX || !EMAIL.matcher(v).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return v;
    }

    /** For a new password only; existing passwords are never re-checked at login. */
    public static String requireNewPassword(String raw) {
        if (raw == null || raw.length() < PASSWORD_MIN || raw.length() > PASSWORD_MAX) {
            throw new IllegalArgumentException("Password must be " + PASSWORD_MIN + " to " + PASSWORD_MAX + " characters");
        }
        if (!raw.chars().anyMatch(Character::isLetter) || !raw.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password must have at least one letter and one number");
        }
        if (raw.isBlank() || !raw.equals(raw.strip())) {
            throw new IllegalArgumentException("Password cannot start or end with a space");
        }
        return raw;
    }

    public static String requireAadhaar(String raw) {
        String v = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (!v.matches("^\\d{12}$")) {
            throw new IllegalArgumentException("Aadhaar number must be exactly 12 digits");
        }
        if (!DriverApplicationChecks.validAadhaar(v)) {
            throw new IllegalArgumentException("Aadhaar number is not valid - check the 12 digits");
        }
        return v;
    }

    public static String requirePan(String raw) {
        String v = raw == null ? "" : raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!PAN.matcher(v).matches()) {
            throw new IllegalArgumentException("PAN must be 10 characters like ABCPE1234F (5 letters, 4 digits, 1 letter)");
        }
        return v;
    }

    /** Stored as typed but compact: "TN01 20190001234" and "TN-0120190001234" both become TN0120190001234. */
    public static String requireLicence(String raw) {
        String v = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!LICENCE.matcher(v).matches()) {
            throw new IllegalArgumentException(
                    "Driving licence number must be 15 characters like TN0120190001234 (state, RTO, year, 7-digit number)");
        }
        return v;
    }

    /** True for a valid Indian registration, spaces and hyphens ignored. */
    public static boolean validPlate(String raw) {
        String v = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return PLATE.matcher(v).matches() || BH_PLATE.matcher(v).matches();
    }

    public static String requireIfsc(String raw) {
        String v = raw == null ? "" : raw.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        if (!IFSC.matcher(v).matches()) {
            throw new IllegalArgumentException("IFSC must be 11 characters like SBIN0001234");
        }
        return v;
    }

    public static String requireAccount(String raw) {
        String v = raw == null ? "" : raw.replaceAll("\\s", "");
        if (!ACCOUNT.matcher(v).matches()) {
            throw new IllegalArgumentException("Bank account number must be 9 to 18 digits");
        }
        return v;
    }

    public static String requireUpi(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (!UPI.matcher(v).matches()) {
            throw new IllegalArgumentException("UPI ID must look like name@bank");
        }
        return v;
    }

    private static String squash(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
