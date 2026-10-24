package com.travelplatform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts individual database fields - today a driver's payout UPI ID - with AES-256-GCM.
 *
 * <p>Format: {@code v1:} + base64(12-byte IV ‖ ciphertext ‖ 16-byte tag). A fresh random IV per
 * value means two drivers with the same UPI ID store different ciphertext, and GCM's tag means a
 * tampered value fails to decrypt instead of decrypting to something else. The version prefix
 * leaves room for key rotation.
 *
 * <p>The key ({@code APP_ENCRYPTION_KEY}, base64 of 32 random bytes) never has a default: an
 * empty key leaves the app running, but {@link #encrypt} refuses, so nothing sensitive is ever
 * written in a form a leaked default key could read.
 *
 * <p>Held statically because JPA instantiates {@link EncryptedStringConverter} itself.
 */
@Component
public class FieldEncryption {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryption.class);

    static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec key;

    public FieldEncryption(@Value("${app.crypto.key:}") String base64Key) {
        configure(base64Key);
    }

    /** Also used by tests to install a key without a Spring context. */
    public static void configure(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            key = null;
            log.warn("APP_ENCRYPTION_KEY is not set: encrypted fields (driver payout UPI IDs) cannot be saved");
            return;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be base64 (openssl rand -base64 32)");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must decode to 32 bytes (openssl rand -base64 32)");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    public static boolean isConfigured() {
        return key != null;
    }

    public static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        SecretKeySpec k = requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unrecognised encrypted value");
        }
        SecretKeySpec k = requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Encrypted value could not be read (wrong key or tampered)", e);
        }
    }

    private static SecretKeySpec requireKey() {
        SecretKeySpec k = key;
        if (k == null) {
            throw new IllegalStateException("Payouts are not set up yet (APP_ENCRYPTION_KEY missing)");
        }
        return k;
    }
}
