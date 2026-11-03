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

    private AadhaarPhotoConverter() {
    }

    /** JPEG bytes, or null if these are not an image ImageIO can read. */
    public static byte[] toJpeg(byte[] jpeg2000) {
        if (jpeg2000 == null || jpeg2000.length == 0) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg2000));
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
