package com.travelplatform.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCompressorTest {

    private static byte[] encode(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** Noise compresses badly - the worst case for size. */
    private static BufferedImage noisy(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, r.nextInt(0xFFFFFF));
            }
        }
        return img;
    }

    /** Looks like a scanned document or a photo: smooth areas, edges and some text-like detail. */
    private static BufferedImage photoLike(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(230, 225, 210), w, h, new Color(120, 140, 170)));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, Math.max(12, h / 40)));
        for (int y = h / 20; y < h; y += h / 20) {
            g.drawString("DL No. TN-0920190012345  Valid till 12/03/2034  Class LMV MCWG", w / 20, y);
        }
        g.dispose();
        return img;
    }

    @Test
    @DisplayName("a licence photo is stored at 1600 px long side and about 300 KB at most")
    void documentProfile() throws Exception {
        byte[] original = encode(photoLike(4032, 3024), "png");

        byte[] out = ImageCompressor.toCompressedJpeg(original, ImageProfile.DOCUMENT);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(out));

        assertEquals(1600, back.getWidth());
        assertEquals(1200, back.getHeight());
        assertTrue(out.length <= ImageProfile.DOCUMENT.targetBytes(), "stored " + out.length + " bytes");
        assertEquals((byte) 0xFF, out[0]);
        assertEquals((byte) 0xD8, out[1]);
    }

    @Test
    @DisplayName("a profile photo is stored at 512 px long side and 50 KB at most")
    void profilePhoto() throws Exception {
        byte[] out = ImageCompressor.toCompressedJpeg(encode(photoLike(3024, 4032), "png"), ImageProfile.PROFILE);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(out));

        assertEquals(512, back.getHeight());
        assertEquals(384, back.getWidth());
        assertTrue(out.length <= 50 * 1024, "stored " + out.length + " bytes");
    }

    @Test
    @DisplayName("when quality alone cannot reach the target, resolution drops - but never below the readable floor")
    void shrinksResolutionWithinFloor() throws Exception {
        // Pure noise is the worst case: it cannot get near 50 KB at 512 px.
        byte[] out = ImageCompressor.toCompressedJpeg(encode(noisy(2000, 2000), "png"), ImageProfile.PROFILE);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(out));

        assertTrue(back.getWidth() < 512, "shrunk: " + back.getWidth());
        assertTrue(back.getWidth() >= ImageProfile.PROFILE.minSide, "floor kept: " + back.getWidth());
    }

    @Test
    @DisplayName("small images are not enlarged; transparency becomes white, as on paper")
    void smallAndTransparent() throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, 40, 20);
        g.dispose();

        BufferedImage back = ImageIO.read(new ByteArrayInputStream(ImageCompressor.toCompressedJpeg(encode(img, "png"), ImageProfile.DOCUMENT)));

        assertEquals(40, back.getWidth());
        Color c = new Color(back.getRGB(20, 10));
        assertTrue(c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240, "white, not black: " + c);
    }

    @Test
    @DisplayName("EXIF orientation 6 (phone held upright) turns a landscape bitmap upright")
    void rotatesByOrientation() {
        BufferedImage wide = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        wide.setRGB(0, 0, 0xFF0000); // top-left red
        BufferedImage upright = ImageCompressor.orient(wide, 6);

        assertEquals(2, upright.getWidth());
        assertEquals(4, upright.getHeight());
        assertEquals(0xFF0000, upright.getRGB(1, 0) & 0xFFFFFF, "top-left moves to top-right on a clockwise turn");
    }

    @Test
    @DisplayName("not an image at all: refused with a message a person can act on")
    void junkRefused() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(ImageCompressor.UnreadableImageException.class, () -> ImageCompressor.toCompressedJpeg(html, ImageProfile.DOCUMENT));
    }

    @Test
    @DisplayName("a decompression bomb is refused from its header, before any pixel is decoded")
    void bombRefused() {
        assertThrows(ImageCompressor.UnreadableImageException.class,
                () -> ImageCompressor.toCompressedJpeg(pngHeaderClaiming(50_000, 50_000), ImageProfile.DOCUMENT));
    }

    /** A PNG signature and a valid IHDR chunk claiming the given size, with no image data. */
    private static byte[] pngHeaderClaiming(int w, int h) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(w).putInt(h)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        return ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(13).put(ihdr.array()).putInt((int) crc.getValue())
                .array();
    }
}
