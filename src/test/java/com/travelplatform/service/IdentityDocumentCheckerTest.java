package com.travelplatform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The document check that decides whether a login is created: a fake, edited, unreadable or
 * mismatched Aadhaar is refused with a reason the person can act on; only a UIDAI-signed QR
 * matching the form passes.
 */
class IdentityDocumentCheckerTest {

    /** Valid Verhoeff number ending 0019. */
    private static final String NUMBER = validAadhaarEnding("0019");

    private AadhaarQrVerifier verifier;
    private IdentityDocumentChecker checker;

    @BeforeEach
    void setUp() {
        verifier = mock(AadhaarQrVerifier.class);
        checker = new IdentityDocumentChecker(verifier);
    }

    // ---- Aadhaar --------------------------------------------------------------------------

    @Test
    @DisplayName("Signed QR, last 4 digits and name match: PASS")
    void validCardPasses() throws IOException {
        qr(AadhaarSecureQr.Status.VALID, "0019", "Rajesh Kumar S");

        IdentityDocumentChecker.Result r = checker.aadhaar(aadhaarCard(), NUMBER, "S. Rajesh Kumar");

        assertEquals(IdentityDocumentChecker.Outcome.PASS, r.outcome());
        assertNull(r.reason());
    }

    @Test
    @DisplayName("Edited card (signature fails): REJECT, called out as edited or fake")
    void forgedSignatureRejected() throws IOException {
        qr(AadhaarSecureQr.Status.INVALID_SIGNATURE, "0019", "Rajesh Kumar");

        IdentityDocumentChecker.Result r = checker.aadhaar(aadhaarCard(), NUMBER, "Rajesh Kumar");

        assertTrue(r.rejected());
        assertTrue(r.reason().contains("edited or fake"), r.reason());
    }

    @Test
    @DisplayName("Someone else's card: typed number's last 4 differ from the QR -> REJECT")
    void numberMismatchRejected() throws IOException {
        qr(AadhaarSecureQr.Status.VALID, "7777", "Rajesh Kumar");

        IdentityDocumentChecker.Result r = checker.aadhaar(aadhaarCard(), NUMBER, "Rajesh Kumar");

        assertTrue(r.rejected());
        assertTrue(r.reason().contains("doesn't match the card"), r.reason());
    }

    @Test
    @DisplayName("Someone else's card: name differs from the QR -> REJECT")
    void nameMismatchRejected() throws IOException {
        qr(AadhaarSecureQr.Status.VALID, "0019", "Priya Lakshmi");

        IdentityDocumentChecker.Result r = checker.aadhaar(aadhaarCard(), NUMBER, "Rajesh Kumar");

        assertTrue(r.rejected());
        assertTrue(r.reason().contains("name"), r.reason());
    }

    @Test
    @DisplayName("No QR found: REJECT asking for a clearer photo; not an Aadhaar at all says so")
    void unreadableQrRejected() throws IOException {
        qr(AadhaarSecureQr.Status.UNREADABLE, null, null);

        IdentityDocumentChecker.Result onCard = checker.aadhaar(aadhaarCard(), NUMBER, "Rajesh Kumar");
        IdentityDocumentChecker.Result onSelfie = checker.aadhaar(plain(new Color(120, 90, 80)), NUMBER, "Rajesh Kumar");

        assertTrue(onCard.reason().startsWith("We couldn't read the QR code"), onCard.reason());
        assertTrue(onSelfie.reason().startsWith("This doesn't look like an Aadhaar card"), onSelfie.reason());
    }

    @Test
    @DisplayName("Old unsigned QR: REJECT pointing to a fresh e-Aadhaar")
    void oldQrRejected() throws IOException {
        qr(AadhaarSecureQr.Status.NOT_SECURE_QR, null, null);

        IdentityDocumentChecker.Result r = checker.aadhaar(aadhaarCard(), NUMBER, "Rajesh Kumar");

        assertTrue(r.rejected());
        assertTrue(r.reason().contains("myaadhaar.uidai.gov.in"), r.reason());
    }

    @Test
    @DisplayName("No UIDAI certificate configured: matching card is REVIEW, never PASS")
    void notCheckedIsReview() throws IOException {
        qr(AadhaarSecureQr.Status.NOT_CHECKED, "0019", "Rajesh Kumar");

        IdentityDocumentChecker.Result r = checker.aadhaar(aadhaarCard(), NUMBER, "Rajesh Kumar");

        assertEquals(IdentityDocumentChecker.Outcome.REVIEW, r.outcome());
    }

    @Test
    @DisplayName("Tiny image, non-image and missing file: REJECT before the QR is even tried")
    void unusableUploadsRejected() throws IOException {
        BufferedImage small = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);

        assertTrue(checker.aadhaar(png(small), NUMBER, "R").reason().contains("too small"));
        assertTrue(checker.aadhaar("%PDF-1.7 ...".getBytes(), NUMBER, "R").reason().contains("JPG or PNG"));
        assertTrue(checker.aadhaar(null, NUMBER, "R").reason().contains("Upload your Aadhaar"));
        verify(verifier, never()).verifyDecoded(any());
    }

    @Test
    @DisplayName("Signed QR on a black-and-white photocopy: PASS (the QR proves it), noted for the record")
    void signedQrOnPhotocopyPasses() throws IOException {
        qr(AadhaarSecureQr.Status.VALID, "0019", "Rajesh Kumar");

        IdentityDocumentChecker.Result r = checker.aadhaar(plain(Color.LIGHT_GRAY), NUMBER, "Rajesh Kumar");

        assertEquals(IdentityDocumentChecker.Outcome.PASS, r.outcome());
        assertTrue(r.summary().contains("black-and-white"), r.summary());
    }

    // ---- PAN ------------------------------------------------------------------------------

    @Test
    @DisplayName("PAN: card colours and right format -> REVIEW (cannot be proven offline)")
    void panLooksRight() throws IOException {
        IdentityDocumentChecker.Result r = checker.pan(plain(new Color(205, 225, 240)), "ABCDE1234F");

        assertEquals(IdentityDocumentChecker.Outcome.REVIEW, r.outcome());
    }

    @Test
    @DisplayName("PAN: not a PAN-looking image, or a bad number -> REJECT")
    void panRejected() throws IOException {
        assertTrue(checker.pan(plain(new Color(120, 90, 80)), "ABCDE1234F").rejected());
        assertTrue(checker.pan(plain(new Color(205, 225, 240)), "ABCD1234F").rejected());
    }

    // ---- name matching --------------------------------------------------------------------

    @Test
    @DisplayName("Names: initials and word order ignored, one-letter spelling slip allowed, different people refused")
    void names() {
        assertTrue(NameMatch.sameName("S. Rajesh Kumar", "Rajesh Kumar S"));
        assertTrue(NameMatch.sameName("Rajesh", "Rajesh Kumar"));
        assertTrue(NameMatch.sameName("Barani Dharan", "Barani Dharan T"));
        assertTrue(NameMatch.sameName("Lakshmi Narayanan", "Lakshmi Narayanan"));
        assertTrue(NameMatch.sameName("Lakshmi Narayana", "Lakshmi Narayanan"));
        assertFalse(NameMatch.sameName("Rajesh Kumar", "Priya Lakshmi"));
        assertFalse(NameMatch.sameName("Raj", "Ram"));
        assertFalse(NameMatch.sameName("", "Rajesh"));
        assertFalse(NameMatch.sameName("Rajesh", null));
    }

    // ---- helpers --------------------------------------------------------------------------

    private void qr(AadhaarSecureQr.Status status, String last4, String name) {
        when(verifier.verifyDecoded(any())).thenReturn(new AadhaarSecureQr.Result(status, "2", last4, name,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    /** White card with a saffron band on top and a green band below, plus noise so the PNG is a realistic size. */
    static byte[] aadhaarCard() throws IOException {
        BufferedImage img = noisy(Color.WHITE);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 153, 51));
        g.fillRect(0, 0, img.getWidth(), 40);
        g.setColor(new Color(19, 136, 8));
        g.fillRect(0, img.getHeight() - 40, img.getWidth(), 40);
        g.dispose();
        return png(img);
    }

    static byte[] plain(Color c) throws IOException {
        return png(noisy(c));
    }

    private static BufferedImage noisy(Color base) {
        BufferedImage img = new BufferedImage(860, 540, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(7);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int n = rnd.nextInt(7) - 3;
                img.setRGB(x, y, new Color(clamp(base.getRed() + n), clamp(base.getGreen() + n),
                        clamp(base.getBlue() + n)).getRGB());
            }
        }
        return img;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static String validAadhaarEnding(String last4) {
        for (int d = 0; d <= 9; d++) {
            // 11 digits then the check digit; vary the 8th digit so the number ends in last4.
            String candidate = "2341234" + d + last4;
            if (DriverApplicationChecks.validAadhaar(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("no valid number ends in " + last4);
    }
}
