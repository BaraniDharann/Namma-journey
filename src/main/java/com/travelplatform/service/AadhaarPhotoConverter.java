package com.travelplatform.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * The photo inside an Aadhaar Secure QR is JPEG 2000, which browsers (bar Safari) cannot show.
 * The admin compares it with the person on the card, so it is turned into a JPEG once, at signup.
 */
public final class AadhaarPhotoConverter {

    static final long MAX_PIXELS = 1_000_000L;

    private AadhaarPhotoConverter() {
    }

    /** JPEG bytes, or null if these are not an image ImageIO can read. */
    public static byte[] toJpeg(byte[] jpeg2000) {
        if (jpeg2000 == null || jpeg2000.length == 0) {
            return null;
        }
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg2000))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            BufferedImage img;
            try {
                reader.setInput(in, true, true);
                // A QR photo is about 100x120. The QR itself may be unsigned (forged), so its
                // header is not trusted to declare a sane size.
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    return null;
                }
                img = reader.read(0);
            } finally {
                reader.dispose();
            }
            if (img == null) {
                return null;
            }
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(img, 0, 0, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return ImageIO.write(rgb, "jpeg", out) ? out.toByteArray() : null;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }
}
