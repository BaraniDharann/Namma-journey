package com.travelplatform.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded photo into a small, upright JPEG before it is stored.
 *
 * <p>Phone photos arrive at 3-12 MB; a licence or Aadhaar scan stays perfectly legible at 2000 px
 * on the long side, which as a JPEG is a few hundred KB. Since uploads now live in the database
 * (DatabaseFileStore), that is the difference between a few hundred and a few thousand drivers
 * fitting in a small Postgres plan.
 *
 * <p>Re-encoding also helps privacy and safety: EXIF metadata (including the GPS position where
 * the photo was taken) is dropped, and whatever the file really contained, what is stored is a
 * freshly drawn bitmap - never the uploaded bytes.
 *
 * <p>Large images are decoded with subsampling, so a 48-megapixel photo never needs 190 MB of
 * heap; an image claiming absurd dimensions is refused before any pixel is decoded.
 */
public final class ImageCompressor {

    /** Long side of the stored image. Legible for documents, small for faces and odometers. */
    static final int MAX_SIDE = 2000;
    /** Anything bigger is a decompression bomb, not a photo. */
    static final long MAX_PIXELS = 100_000_000L;
    /** Try lower JPEG qualities until the result fits this. */
    static final int TARGET_BYTES = 1024 * 1024;
    private static final float[] QUALITIES = {0.82f, 0.72f, 0.6f, 0.5f};

    private ImageCompressor() {
    }

    public static final class UnreadableImageException extends RuntimeException {
        public UnreadableImageException(String message) {
            super(message);
        }
    }

    /** @return JPEG bytes, longest side at most {@link #MAX_SIDE}, upright, without metadata */
    public static byte[] toCompressedJpeg(byte[] original) {
        BufferedImage decoded = decode(original);
        BufferedImage upright = orient(decoded, exifOrientation(original));
        BufferedImage scaled = scaleOnto(upright, MAX_SIDE);
        byte[] out = null;
        for (float q : QUALITIES) {
            out = writeJpeg(scaled, q);
            if (out.length <= TARGET_BYTES) {
                break;
            }
        }
        return out;
    }

    private static BufferedImage decode(byte[] original) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new UnreadableImageException("Please upload a JPG or PNG photo");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_PIXELS) {
                    throw new UnreadableImageException("This image is too large to process");
                }
                // Decode at roughly twice the target size at most; the final scale does the rest
                // with proper interpolation.
                int step = (int) Math.max(1, Math.max(w, h) / (MAX_SIDE * 2L));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw new UnreadableImageException("Please upload a JPG or PNG photo");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof UnreadableImageException u) {
                throw u;
            }
            throw new UnreadableImageException("This photo could not be read. Please upload a JPG or PNG photo");
        }
    }

    /** EXIF orientation 1-8; 1 (as stored) when absent or unreadable. */
    static int exifOrientation(byte[] original) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // No or broken metadata: store the pixels as they are.
        }
        return 1;
    }

    static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        // Where each source pixel (x, y) lands, per EXIF orientation; arguments are
        // (m00, m10, m01, m11, m02, m12) for x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12.
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirrored
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // upside down
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirrored vertically
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transposed
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // needs 90° clockwise (phone held upright)
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            default -> new AffineTransform(0, -1, 1, 0, 0, w);  // 8: needs 90° anticlockwise
        };
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Scales down (never up) onto an opaque RGB canvas; transparency becomes white, as on paper. */
    static BufferedImage scaleOnto(BufferedImage src, int maxSide) {
        double ratio = Math.min(1.0, (double) maxSide / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
