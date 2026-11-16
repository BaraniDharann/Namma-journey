package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real Aadhaar and PAN cards in {@code sample document/} (encrypted with
 * {@code scripts/sample-docs.mjs}) through the checks, so the thresholds are known to pass genuine
 * cards. Decrypted in memory only; prints colour shares and statuses, never names or numbers.
 *
 * <p>Skipped unless {@code SAMPLE_DOCS_KEY} is set. Folder: {@code SAMPLE_DOCS_DIR}, default
 * {@code sample document}. File names say what each is: containing "aadhaar"/"aadhar" or "pan".
 * With {@code AADHAAR_QR_CERTIFICATES} set, every Aadhaar must verify as VALID.
 */
class SampleDocumentsTest {

    @Test
    @DisplayName("Real sample cards pass the colour check, and every Aadhaar QR reads (VALID with the UIDAI certificate)")
    void realCardsPass() throws Exception {
        String key = System.getenv("SAMPLE_DOCS_KEY");
        assumeTrue(key != null && !key.isBlank(), "SAMPLE_DOCS_KEY not set");
        Path dir = Path.of(System.getenv().getOrDefault("SAMPLE_DOCS_DIR", "sample document"));
        assumeTrue(Files.isDirectory(dir), "no sample folder at " + dir.toAbsolutePath());

        String certs = System.getenv().getOrDefault("AADHAAR_QR_CERTIFICATES", "");
        AadhaarQrVerifier verifier = new AadhaarQrVerifier(certs);
        List<Path> encrypted;
        try (Stream<Path> s = Files.list(dir)) {
            encrypted = s.filter(p -> p.toString().endsWith(".enc")).sorted().toList();
        }
        assumeTrue(!encrypted.isEmpty(), "no .enc files in " + dir.toAbsolutePath());

        int checked = 0;
        for (Path p : encrypted) {
            String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
            byte[] plain = decrypt(Files.readAllBytes(p), key);
            DocumentImages.Decoded decoded = DocumentImages.decode(plain);
            if (decoded.passwordProtected()) {
                // UIDAI's e-Aadhaar is locked; its password goes in SAMPLE_PDF_PASSWORD (in .env), never in code.
                String pdfPassword = System.getenv("SAMPLE_PDF_PASSWORD");
                assumeTrue(pdfPassword != null && !pdfPassword.isBlank(),
                        p.getFileName() + " is password-protected: set SAMPLE_PDF_PASSWORD");
                decoded = DocumentImages.decode(plain, pdfPassword);
                assertTrue(decoded.image() != null, p.getFileName() + ": SAMPLE_PDF_PASSWORD did not open it");
            }
            BufferedImage img = decoded.image();
            if (img == null) {
                System.out.println(p.getFileName() + ": could not be read as an image or PDF, skipped");
                continue;
            }
            CardLook.Shares s = CardLook.shares(img);
            System.out.printf("%s: saffron %.4f, green %.4f, pale blue %.4f%n",
                    p.getFileName(), s.saffron(), s.green(), s.paleBlue());

            if (name.contains("aadhaar") || name.contains("aadhar") || name.contains("adhar")) {
                assertTrue(CardLook.looksLikeAadhaar(img), p.getFileName() + " should look like an Aadhaar");
                AadhaarSecureQr.Result qr = new IdentityDocumentChecker(verifier).readQr(decoded);
                System.out.println(p.getFileName() + ": QR " + qr.status());
                if (qr.status() == AadhaarSecureQr.Status.UNREADABLE) {
                    // The side without the QR (e.g. a PVC card's front) - fine for the colour check only.
                    continue;
                }
                assertNotEquals(AadhaarSecureQr.Status.INVALID_SIGNATURE, qr.status(), "a genuine card must not fail");
                assertNotNull(qr.aadhaarLast4());
                if (!certs.isBlank()) {
                    assertEquals(AadhaarSecureQr.Status.VALID, qr.status());
                }
                // The whole check, as at signup: SAMPLE_AADHAAR_NAME / _NUMBER in .env, typed as a person would.
                String typedName = System.getenv("SAMPLE_AADHAAR_NAME");
                String typedNumber = System.getenv("SAMPLE_AADHAAR_NUMBER");
                if (typedName != null && !typedName.isBlank() && typedNumber != null && !typedNumber.isBlank()) {
                    IdentityDocumentChecker.Result full = new IdentityDocumentChecker(verifier)
                            .aadhaar(plain, typedNumber, typedName);
                    System.out.println(p.getFileName() + ": full check " + full.outcome());
                    assertEquals(certs.isBlank() ? IdentityDocumentChecker.Outcome.REVIEW : IdentityDocumentChecker.Outcome.PASS,
                            full.outcome(), String.valueOf(full.reason()));
                }
                checked++;
            } else if (name.contains("pan")) {
                assertTrue(CardLook.looksLikePan(img), p.getFileName() + " should look like a PAN card");
                checked++;
            }
        }
        assertTrue(checked > 0, "no file named like an Aadhaar or PAN card was checked");
    }

    /** Reverses scripts/sample-docs.mjs: "NJDOC1" | salt 16 | iv 12 | ciphertext | tag 16. */
    static byte[] decrypt(byte[] blob, String password) throws Exception {
        if (blob.length < 6 + 16 + 12 + 16 || !new String(blob, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("NJDOC1")) {
            throw new IOException("not a sample-docs file");
        }
        byte[] salt = Arrays.copyOfRange(blob, 6, 22);
        byte[] iv = Arrays.copyOfRange(blob, 22, 34);
        byte[] body = Arrays.copyOfRange(blob, 34, blob.length); // ciphertext followed by the tag
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 600_000, 256)).getEncoded();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(body);
    }
}
