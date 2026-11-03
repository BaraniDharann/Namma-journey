package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AadhaarPhotoConverterTest {

    @Test
    @DisplayName("bytes that are not an image give null, not an exception")
    void garbageGivesNull() {
        assertNull(AadhaarPhotoConverter.toJpeg(new byte[]{1, 2, 3}));
        assertNull(AadhaarPhotoConverter.toJpeg(null));
        assertNull(AadhaarPhotoConverter.toJpeg(new byte[0]));
    }

    @Test
    @DisplayName("a JPEG 2000 photo, as UIDAI puts in the QR, comes out as a JPEG a browser can show")
    void jpeg2000RoundTrip() throws Exception {
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        img.setRGB(5, 5, Color.RED.getRGB());
        ByteArrayOutputStream jp2 = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, "jpeg2000", jp2), "JPEG 2000 plugin must be on the classpath");

        byte[] jpeg = AadhaarPhotoConverter.toJpeg(jp2.toByteArray());

        assertNotNull(jpeg);
        assertEquals((byte) 0xFF, jpeg[0]);
        assertEquals((byte) 0xD8, jpeg[1]);
    }
}
