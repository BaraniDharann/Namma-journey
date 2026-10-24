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

    @Test
    @DisplayName("a big phone-sized photo comes back as a JPEG, 2000 px long side, about 1 MB at most")
    void shrinksLargePhoto() throws Exception {
        byte[] original = encode(noisy(4000, 3000), "png");

        byte[] out = ImageCompressor.toCompressedJpeg(original);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(out));

        assertEquals(2000, back.getWidth());
        assertEquals(1500, back.getHeight());
        assertTrue(out.length <= ImageCompressor.TARGET_BYTES * 1.5, "stored " + out.length + " bytes");
        assertTrue(out.length < original.length / 5);
        assertEquals((byte) 0xFF, out[0]);
        assertEquals((byte) 0xD8, out[1]);
    }

    @Test
    @DisplayName("small images are not enlarged; transparency becomes white, as on paper")
    void smallAndTransparent() throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0, 0, 0, 0));
        g.fillRect(0, 0, 40, 20);
        g.dispose();

        BufferedImage back = ImageIO.read(new ByteArrayInputStream(ImageCompressor.toCompressedJpeg(encode(img, "png"))));

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
        assertThrows(ImageCompressor.UnreadableImageException.class, () -> ImageCompressor.toCompressedJpeg(html));
    }

    @Test
    @DisplayName("a decompression bomb is refused from its header, before any pixel is decoded")
    void bombRefused() {
        assertThrows(ImageCompressor.UnreadableImageException.class,
                () -> ImageCompressor.toCompressedJpeg(pngHeaderClaiming(50_000, 50_000)));
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
