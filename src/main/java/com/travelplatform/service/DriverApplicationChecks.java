package com.travelplatform.service;

import com.travelplatform.entity.Driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What can be checked about a driver application without a person looking at it, before it is
 * approved automatically.
 *
 * <p>These catch typos, made-up numbers and missing uploads - not a forged document. That is
 * what the owner's Reject / Hold window (and, later, a KYC service) is for. Anything that fails
 * here is not approved automatically; it waits for the owner.
 */
public final class DriverApplicationChecks {

    /**
     * Indian driving licence as issued since 2019 (and as most older ones were re-issued):
     * state code, 2-digit RTO, optional separator, year of issue, 7-digit serial.
     * e.g. TN01 20190001234, KA0520150012345.
     */
    private static final Pattern LICENCE = Pattern.compile("^[A-Z]{2}[0-9]{2}[ -]?(19|20)[0-9]{2}[0-9]{7}$");

    private DriverApplicationChecks() {
    }

    /** Empty when the application may be approved automatically; otherwise why not, in plain words. */
    public static List<String> problems(Driver d) {
        List<String> problems = new ArrayList<>();
        if (blank(d.getPhoto())) problems.add("profile photo missing");
        if (blank(d.getLicensePhoto())) problems.add("licence photo missing");
        if (blank(d.getAadhaarPhoto())) problems.add("Aadhaar photo missing");
        if (blank(d.getRcPhoto())) problems.add("vehicle RC photo missing");
        if (blank(d.getVehicleNumber())) problems.add("vehicle number missing");
        if (!validLicence(d.getLicenseNumber())) problems.add("licence number is not in the standard format");
        if (!validAadhaar(d.getAadhaarNumber())) problems.add("Aadhaar number fails its check digit");
        if (!d.isEmailVerified()) problems.add("email not confirmed");
        return problems;
    }

    static boolean validLicence(String raw) {
        return raw != null && LICENCE.matcher(raw.trim().toUpperCase(Locale.ROOT)).matches();
    }

    /**
     * 12 digits, not starting with 0 or 1 (UIDAI never issues those), last digit the Verhoeff
     * check digit of the other 11. A random 12-digit number passes 1 time in 10.
     */
    static boolean validAadhaar(String raw) {
        if (raw == null) {
            return false;
        }
        String digits = raw.replaceAll("\\s+", "");
        if (!digits.matches("^[2-9]\\d{11}$")) {
            return false;
        }
        int c = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - 1 - i) - '0';
            c = VERHOEFF_D[c][VERHOEFF_P[i % 8][digit]];
        }
        return c == 0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static final int[][] VERHOEFF_D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};

    private static final int[][] VERHOEFF_P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};
}
