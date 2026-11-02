package com.travelplatform.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AadhaarQrVerifierTest {

    @Test
    @DisplayName("finds the Secure QR in a photo of the card and verifies it")
    void readsQrFromImage() throws Exception {
        AadhaarSecureQrTest.keys();
        String qr = AadhaarSecureQrTest.qr(AadhaarSecureQrTest.V2_FIELDS, 64, AadhaarSecureQrTest.uidai.getPrivate());
        AadhaarQrVerifier verifier = new AadhaarQrVerifier(List.of(AadhaarSecureQrTest.uidai.getPublic()));

        AadhaarSecureQr.Result r = verifier.verifyImage(cardWithQr(qr, "jpg"));

        assertEquals(AadhaarSecureQr.Status.VALID, r.status());
        assertEquals("Ravi Kumar", r.name());
        assertEquals("1234", r.aadhaarLast4());
    }

    @Test
    @DisplayName("an image without a QR, a PDF or empty bytes is UNREADABLE")
    void unreadable() throws Exception {
        AadhaarQrVerifier verifier = new AadhaarQrVerifier(List.of());
        BufferedImage blank = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(blank, "png", png);

        assertEquals(AadhaarSecureQr.Status.UNREADABLE, verifier.verifyImage(png.toByteArray()).status());
        assertEquals(AadhaarSecureQr.Status.UNREADABLE,
                verifier.verifyImage("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)).status());
        assertEquals(AadhaarSecureQr.Status.UNREADABLE, verifier.verifyImage(new byte[0]).status());
        assertEquals(AadhaarSecureQr.Status.UNREADABLE, verifier.verifyImage(null).status());
    }

    @Test
    @DisplayName("no certificate configured: runs, but says so")
    void unconfigured() {
        assertFalse(new AadhaarQrVerifier("").isConfigured());
    }

    @Test
    @DisplayName("a configured certificate that cannot be read stops startup")
    void missingCertificateFails() {
        assertThrows(IllegalStateException.class, () -> new AadhaarQrVerifier("does/not/exist.cer"));
    }

    /** The QR drawn onto a larger card-like image, as a phone photo of the back of the card. */
    static byte[] cardWithQr(String qrText, String format) throws Exception {
        BitMatrix m = new QRCodeWriter().encode(qrText, BarcodeFormat.QR_CODE, 900, 900,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
        BufferedImage qr = MatrixToImageWriter.toBufferedImage(m);
        BufferedImage card = new BufferedImage(1600, 1100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = card.createGraphics();
        g.setColor(new Color(235, 240, 245));
        g.fillRect(0, 0, card.getWidth(), card.getHeight());
        g.drawImage(qr, 620, 100, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(card, format, out);
        return out.toByteArray();
    }
}
