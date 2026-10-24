package com.travelplatform.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldEncryptionTest {

    public static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @AfterEach
    void reset() {
        FieldEncryption.configure(null);
    }

    @Test
    @DisplayName("round-trips, and the same UPI ID never encrypts the same way twice")
    void roundTripWithFreshIv() {
        FieldEncryption.configure(KEY);
        String a = FieldEncryption.encrypt("ravi@okaxis");
        String b = FieldEncryption.encrypt("ravi@okaxis");
        assertTrue(a.startsWith("v1:"));
        assertFalse(a.contains("ravi"));
        assertNotEquals(a, b);
        assertEquals("ravi@okaxis", FieldEncryption.decrypt(a));
    }

    @Test
    @DisplayName("a tampered or wrong-key value fails instead of decrypting to something else")
    void tamperDetected() {
        FieldEncryption.configure(KEY);
        String stored = FieldEncryption.encrypt("ravi@okaxis");
        char[] chars = stored.toCharArray();
        chars[10] = chars[10] == 'A' ? 'B' : 'A';
        assertThrows(IllegalStateException.class, () -> FieldEncryption.decrypt(new String(chars)));

        byte[] other = new byte[32];
        other[0] = 1;
        FieldEncryption.configure(Base64.getEncoder().encodeToString(other));
        assertThrows(IllegalStateException.class, () -> FieldEncryption.decrypt(stored));
    }

    @Test
    @DisplayName("no key: nothing is written, but reading an entity still works (field reads as absent)")
    void noKeyFailsClosedOnWriteOnly() {
        FieldEncryption.configure(KEY);
        String stored = FieldEncryption.encrypt("ravi@okaxis");
        FieldEncryption.configure("");

        assertThrows(IllegalStateException.class, () -> FieldEncryption.encrypt("ravi@okaxis"));
        assertNull(new EncryptedStringConverter().convertToEntityAttribute(stored));
    }

    @Test
    @DisplayName("a key that is not 32 bytes is refused at startup")
    void wrongKeyLength() {
        assertThrows(IllegalStateException.class,
                () -> FieldEncryption.configure(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalStateException.class, () -> FieldEncryption.configure("not base64!!"));
    }
}
