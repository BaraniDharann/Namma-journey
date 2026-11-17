package com.travelplatform.security;

import com.travelplatform.entity.Driver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdNumberHashTest {

    @AfterEach
    void forgetKey() {
        FieldEncryption.configure(null);
    }

    @Test
    void bcryptIsOneWaySaltedAndCheckable() {
        String a = IdNumberHash.bcrypt("234567890124");
        String b = IdNumberHash.bcrypt("234567890124");
        assertTrue(a.startsWith("$2"));
        assertFalse(a.contains("234567890124"));
        assertNotEquals(a, b, "a fresh salt each time");
        assertTrue(IdNumberHash.matches("234567890124", a));
        assertFalse(IdNumberHash.matches("234567890125", a));
        assertNull(IdNumberHash.bcrypt(" "));
    }

    @Test
    void driverKeepsNoReadableAadhaar() {
        FieldEncryption.configure(FieldEncryptionTest.KEY);
        Driver d = new Driver();
        d.setAadhaar("2345 6789 0124");
        assertEquals("0124", d.getAadhaarLast4());
        assertEquals("XXXX XXXX 0124", d.maskedAadhaar());
        assertEquals(64, d.getAadhaarHash().length());
        assertFalse(d.getAadhaarHash().contains("234567890124"));
        assertTrue(IdNumberHash.matches("234567890124", d.getAadhaarBcrypt()));

        Driver same = new Driver();
        same.setAadhaar("234567890124");
        assertEquals(d.getAadhaarHash(), same.getAadhaarHash(), "duplicates are still found");
    }

    @Test
    void driverAadhaarNeedsTheServerKey() {
        FieldEncryption.configure(null);
        assertThrows(IllegalStateException.class, () -> new Driver().setAadhaar("234567890124"));
    }
}
