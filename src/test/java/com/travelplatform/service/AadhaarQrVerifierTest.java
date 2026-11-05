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
        // A fixed key, so the QR is the same every run. With a fresh random key about 1 QR in 35
        // happened to carry a data pattern that hid ZXing's finder patterns, even in a lossless
        // PNG, and this test failed at random (once on CI).
        java.security.SecureRandom seeded = java.security.SecureRandom.getInstance("SHA1PRNG");
        seeded.setSeed(20261105L);
        java.security.KeyPairGenerator gen = java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, seeded);
        java.security.KeyPair key = gen.generateKeyPair();
        String qr = AadhaarSecureQrTest.qr(AadhaarSecureQrTest.V2_FIELDS, 64, key.getPrivate());
        AadhaarQrVerifier verifier = new AadhaarQrVerifier(List.of(key.getPublic()));

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
    @DisplayName("a tiny PNG claiming 46000x46000 pixels is refused from its header, not decoded into gigabytes")
    void decompressionBombImageIsRefused() throws Exception {
        AadhaarQrVerifier verifier = new AadhaarQrVerifier(List.of());
        byte[] bomb = pngHeaderOnly(30_000, 30_000); // would be a 900 MB raster
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();

        AadhaarSecureQr.Result r = verifier.verifyImage(bomb);

        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertEquals(AadhaarSecureQr.Status.UNREADABLE, r.status());
        org.junit.jupiter.api.Assertions.assertTrue(allocated < 50L * 1024 * 1024,
                "decoding allocated " + (allocated >> 20) + " MB");
    }

    /** A valid PNG signature and IHDR for a huge greyscale image, followed by one tiny IDAT. */
    static byte[] pngHeaderOnly(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.allocate(13);
        ihdr.putInt(width).putInt(height).put((byte) 8).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", ihdr.array());
        java.io.ByteArrayOutputStream z = new java.io.ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(z)) {
            d.write(new byte[1024]);
        }
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        java.io.DataOutputStream d = new java.io.DataOutputStream(out);
        d.writeInt(data.length);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        d.write(t);
        d.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(t);
        crc.update(data);
        d.writeInt((int) crc.getValue());
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
