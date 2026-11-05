package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OwnerApplicationChecksTest {

    @org.junit.jupiter.api.BeforeEach
    void key() {
        com.travelplatform.security.FieldEncryption.configure("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Test
    @DisplayName("PAN: five letters, four digits, a letter - case and outer spaces ignored")
    void pan() {
        assertTrue(OwnerApplicationChecks.validPan("abcde1234f"));
        assertTrue(OwnerApplicationChecks.validPan(" ABCDE1234F "));
        assertFalse(OwnerApplicationChecks.validPan("ABCD1234F"));
        assertFalse(OwnerApplicationChecks.validPan("ABCDE12345"));
        assertFalse(OwnerApplicationChecks.validPan(null));
    }

    @Test
    @DisplayName("IFSC: four letters, a zero, six letters or digits")
    void ifsc() {
        assertTrue(OwnerApplicationChecks.validIfsc("SBIN0001234"));
        assertTrue(OwnerApplicationChecks.validIfsc("hdfc0abc123"));
        assertFalse(OwnerApplicationChecks.validIfsc("SBIN1001234"));
        assertFalse(OwnerApplicationChecks.validIfsc(null));
    }

    @Test
    @DisplayName("the same PAN typed differently hashes the same, so duplicates are caught")
    void panHashIgnoresFormatting() {
        assertEquals(OwnerApplicationChecks.panHash("ABCDE1234F"), OwnerApplicationChecks.panHash(" abcde 1234f"));
        assertNotEquals(OwnerApplicationChecks.panHash("ABCDE1234F"), OwnerApplicationChecks.panHash("ABCDE1234G"));
        assertEquals(64, OwnerApplicationChecks.panHash("ABCDE1234F").length());
    }

    @Test
    @DisplayName("the PAN hash is keyed: with only ~10^10 PANs, a plain SHA-256 is reversible by brute force")
    void panHashIsKeyed() throws Exception {
        com.travelplatform.security.FieldEncryption.configure("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        String plain = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("ABCDE1234F".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String keyed = OwnerApplicationChecks.panHash("ABCDE1234F");
        assertNotEquals(plain, keyed);

        com.travelplatform.security.FieldEncryption.configure("ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=");
        assertNotEquals(keyed, OwnerApplicationChecks.panHash("ABCDE1234F"), "another key, another hash");
    }

    @Test
    @DisplayName("masks show only the tail")
    void masks() {
        assertEquals("XXXXX1234F", OwnerApplicationChecks.maskPan("ABCDE1234F"));
        assertEquals("XXXXXX6789", OwnerApplicationChecks.maskAccount("12345678906789"));
        assertNull(OwnerApplicationChecks.maskPan(null));
        assertNull(OwnerApplicationChecks.maskAccount(null));
    }

    @Test
    @DisplayName("only the last four Aadhaar digits are kept")
    void aadhaarLast4() {
        assertEquals("0124", OwnerApplicationChecks.aadhaarLast4("2345 6789 0124"));
    }
}
