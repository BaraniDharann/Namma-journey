package com.travelplatform.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Negative and positive cases for every official format the forms accept. */
class IndianFormatsTest {

    // A number with a correct Verhoeff check digit (used across the test suite).
    private static final String AADHAAR = "234567890124";

    @Test
    @DisplayName("Aadhaar: 12 digits with a valid check digit; spaces and hyphens allowed")
    void aadhaarAccepted() {
        assertEquals(AADHAAR, IndianFormats.requireAadhaar("2345 6789 0124"));
        assertEquals(AADHAAR, IndianFormats.requireAadhaar("2345-6789-0124"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345678901", "2345678901234", "23456789012a", "034567890124", "134567890124",
            "234567890123", "2345678901245678901", "abcdefghijkl", "        ", "2345 6789 01245"})
    @DisplayName("Aadhaar: short, long, letters, starts with 0/1, wrong check digit - all refused")
    void aadhaarRefused(String bad) {
        assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireAadhaar(bad));
    }

    @Test
    @DisplayName("PAN: upper-cased and spaces removed; the 4th letter is the holder type")
    void panAccepted() {
        assertEquals("ABCPE1234F", IndianFormats.requirePan(" abcpe 1234f "));
        assertEquals("AAACT1234K", IndianFormats.requirePan("AAACT1234K")); // company
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ABCD1234F", "ABCDE12345F", "ABCDE1234F", "ABCP11234F", "1BCPE1234F", "ABCPE1234",
            "ABCPE12345", "ABCPE1234FF", "ABC@E1234F"})
    @DisplayName("PAN: wrong length, digits in letter positions, bad holder type - all refused")
    void panRefused(String bad) {
        assertThrows(IllegalArgumentException.class, () -> IndianFormats.requirePan(bad));
    }

    @Test
    @DisplayName("Licence: 15 characters, separators removed")
    void licenceAccepted() {
        assertEquals("TN0120190001234", IndianFormats.requireLicence("tn01 2019 0001234"));
        assertEquals("KA0520150012345", IndianFormats.requireLicence("KA05-20150012345"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"DL1", "TN012019000123", "TN01201900012345", "TN0118990001234", "T10120190001234",
            "TNAB20190001234", "TN0120190001234567890", "LICENCE-NUMBER-THAT-IS-WAY-TOO-LONG"})
    @DisplayName("Licence: short, long, bad year, bad state or RTO - all refused")
    void licenceRefused(String bad) {
        assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireLicence(bad));
    }

    @Test
    @DisplayName("Mobile: 10 digits starting 6-9; +91 and spaces tolerated")
    void mobile() {
        assertEquals("9597965911", IndianFormats.requireMobile("+91 95979 65911"));
        for (String bad : new String[]{null, "", "5597965911", "959796591", "95979659111", "95979a5911", "0000000000"}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireMobile(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Vehicle plates: state series and Bharat series")
    void plates() {
        for (String ok : new String[]{"TN 01 AB 1234", "KA01A1234", "DL1CAB1234", "MH12 1234", "22 BH 1234 AA", "tn-01-ab-1234"}) {
            assertTrue(IndianFormats.validPlate(ok), ok);
        }
        for (String bad : new String[]{null, "", "1234", "TN01AB123", "TN01ABCD1234", "TN0A1234", "TN01AB0000", "XXXXXXXXXXXXXXXXXXXX", "22BH12345A"}) {
            assertFalse(IndianFormats.validPlate(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Names: letters (Tamil too), 2 to 60; no digits, symbols or over-long names")
    void names() {
        assertEquals("Barani Dharan", IndianFormats.requireName("  Barani   Dharan ", "Name"));
        assertEquals("பரணி", IndianFormats.requireName("பரணி", "Name"));
        assertEquals("K. O'Neil-Raj", IndianFormats.requireName("K. O'Neil-Raj", "Name"));
        for (String bad : new String[]{null, "", "A", "Ravi123", "<script>", "Ravi@", "x".repeat(61)}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireName(bad, "Name"), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Email: normal addresses only, at most 254 characters")
    void email() {
        assertEquals("tlokeshthiru123@gmail.com", IndianFormats.requireEmail(" TLokeshThiru123@Gmail.com "));
        for (String bad : new String[]{null, "", "a", "a@b", "a b@c.com", "@x.com", "a@.com", "a@x.c",
                "x".repeat(250) + "@x.com"}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireEmail(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("New passwords: 8 to 64, a letter and a digit, no edge spaces")
    void passwords() {
        IndianFormats.requireNewPassword("Lokesh123@");
        IndianFormats.requireNewPassword("Barani234#");
        for (String bad : new String[]{null, "", "short1", "allletters", "12345678", " Lokesh123", "Lokesh123 ",
                "a1" + "x".repeat(63)}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireNewPassword(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Bank: IFSC, 9-18 digit account, UPI ID")
    void bank() {
        assertEquals("SBIN0001234", IndianFormats.requireIfsc("sbin0001234"));
        assertEquals("123456789012", IndianFormats.requireAccount("1234 5678 9012"));
        assertEquals("ravi@okaxis", IndianFormats.requireUpi("ravi@okaxis"));
        for (String bad : new String[]{"SBIN1001234", "SBI0001234", "SBIN00012345", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireIfsc(bad), String.valueOf(bad));
        }
        for (String bad : new String[]{"12345678", "1234567890123456789", "12ab5678901", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireAccount(bad), String.valueOf(bad));
        }
        for (String bad : new String[]{"ravi", "ravi@", "@okaxis", "ra vi@okaxis", "ravi@ok axis", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireUpi(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Business names: letters, digits and & , . ( ) / - up to 80")
    void businessNames() {
        assertEquals("Sri Murugan Travels & Tours", IndianFormats.requireBusinessName("Sri Murugan Travels & Tours", "Travels name"));
        for (String bad : new String[]{null, "", "A", "<b>Travels</b>", "x".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> IndianFormats.requireBusinessName(bad, "Travels name"));
        }
    }
}
