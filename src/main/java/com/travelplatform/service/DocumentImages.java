package com.travelplatform.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * An uploaded identity document as an image: a phone photo (JPG/PNG) as it is, or the first page
 * of a PDF - the e-Aadhaar and e-PAN people download - rendered at print quality, so the Aadhaar's
 * dense Secure QR can still be read.
 */
public final class DocumentImages {

    /** Enough for the e-Aadhaar's Secure QR (about 3 cm wide, ~140 modules) to decode. */
    static final float PDF_DPI = 300f;

    private DocumentImages() {
    }

    /**
     * @param image             null when the upload could not be turned into an image
     * @param passwordProtected the PDF is locked (UIDAI's e-Aadhaar is, unless saved unlocked)
     */
    public record Decoded(BufferedImage image, boolean pdf, boolean passwordProtected,
                          List<BufferedImage> qrCandidates) {

        Decoded(BufferedImage image, boolean pdf, boolean passwordProtected) {
            this(image, pdf, passwordProtected, List.of());
        }
    }

    /**
     * Square-ish pictures embedded in a PDF page big enough to hold a Secure QR. UIDAI's e-Aadhaar
     * carries its QR as a crisp 1000x1000 image: read there, it decodes even where the rendered page
     * (300 DPI) is too soft for its ~3000-digit code.
     */
    static final int MIN_QR_SIDE = 300;
    static final int MAX_QR_CANDIDATES = 6;

    public static boolean isPdf(byte[] bytes) {
        return bytes != null && bytes.length > 5
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
    }

    public static Decoded decode(byte[] bytes) {
        return decode(bytes, null);
    }

    /** A locked e-Aadhaar opened with the password worked out from the holder's name, and that password. */
    public record Unlocked(Decoded decoded, String password) {
    }

    /**
     * Opens UIDAI's locked e-Aadhaar without asking for its password. UIDAI's rule: the first four
     * letters of the name as printed, in capitals, then the year of birth (SURESH KUMAR, 1990 ->
     * SURE1990; "P. KUMAR" -> P.KU1990). The name is the one the person typed; the year is found by
     * trying each one - a wrong try costs about 2 ms, so all ~130 years for a few spellings of the
     * name take well under a second.
     *
     * @return null when no candidate opens it
     */
    public static Unlocked unlockEAadhaar(byte[] pdf, String typedName) {
        List<String> prefixes = passwordPrefixes(typedName);
        if (prefixes.isEmpty() || !isPdf(pdf)) {
            return null;
        }
        int thisYear = java.time.Year.now().getValue();
        for (int year = thisYear; year >= 1900; year--) {
            for (String prefix : prefixes) {
                String password = prefix + year;
                try (PDDocument doc = Loader.loadPDF(pdf, password)) {
                    // Opened: render with the same code path as any other PDF.
                    return new Unlocked(decode(pdf, password), password);
                } catch (InvalidPasswordException wrong) {
                    // next guess
                } catch (IOException | RuntimeException broken) {
                    return null; // not a password problem: the file itself is bad
                }
            }
        }
        return null;
    }

    /** The first-four-letters part of the e-Aadhaar password, for each likely way the name is printed. */
    static List<String> passwordPrefixes(String typedName) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (typedName == null || typedName.isBlank()) {
            return List.of();
        }
        String[] words = typedName.trim().toUpperCase(java.util.Locale.ROOT).split("\\s+");
        // The order the person typed it, then each rotation ("RAJESH KUMAR S" is printed "S RAJESH KUMAR").
        for (int start = 0; start < Math.min(words.length, 4); start++) {
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                joined.append(words[(start + i) % words.length]);
            }
            String asPrinted = joined.toString();                         // dots kept: P.KUMAR -> P.KU
            String lettersOnly = asPrinted.replaceAll("[^A-Z]", "");      // dots dropped: PKUMAR -> PKUM
            for (String s : new String[] {asPrinted, lettersOnly}) {
                if (!s.isEmpty()) {
                    out.add(s.substring(0, Math.min(4, s.length())));
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** @param pdfPassword opens a locked PDF (UIDAI's e-Aadhaar); null for none */
    public static Decoded decode(byte[] bytes, String pdfPassword) {
        if (!isPdf(bytes)) {
            return new Decoded(bytes == null ? null : AadhaarQrVerifier.decode(bytes), false, false);
        }
        try (PDDocument doc = pdfPassword == null ? Loader.loadPDF(bytes) : Loader.loadPDF(bytes, pdfPassword)) {
            if (doc.getNumberOfPages() < 1) {
                return new Decoded(null, true, false);
            }
            PDRectangle box = doc.getPage(0).getCropBox();
            // Same bound as photos: a PDF declaring a huge page must not render gigapixels.
            float dpi = PDF_DPI;
            double pixels = (box.getWidth() / 72.0 * dpi) * (box.getHeight() / 72.0 * dpi);
            if (pixels > AadhaarQrVerifier.MAX_PIXELS) {
                dpi = (float) (dpi * Math.sqrt(AadhaarQrVerifier.MAX_PIXELS / pixels));
            }
            BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, dpi, ImageType.RGB);
            return new Decoded(img, true, false, qrCandidates(doc.getPage(0).getResources()));
        } catch (InvalidPasswordException e) {
            return new Decoded(null, true, true);
        } catch (IOException | RuntimeException e) {
            return new Decoded(null, true, false);
        }
    }

    private static List<BufferedImage> qrCandidates(PDResources resources) {
        List<BufferedImage> out = new ArrayList<>();
        if (resources == null) {
            return out;
        }
        for (COSName name : resources.getXObjectNames()) {
            if (out.size() >= MAX_QR_CANDIDATES) {
                break;
            }
            try {
                if (resources.getXObject(name) instanceof PDImageXObject im) {
                    long w = im.getWidth(), h = im.getHeight();
                    boolean squareish = Math.min(w, h) * 10 >= Math.max(w, h) * 8;
                    if (squareish && Math.min(w, h) >= MIN_QR_SIDE && w * h <= AadhaarQrVerifier.MAX_PIXELS) {
                        out.add(im.getImage());
                    }
                }
            } catch (IOException | RuntimeException e) {
                // an unreadable embedded picture is simply not a candidate
            }
        }
        return out;
    }

    /**
     * What to store for an upload: photos as sent, a PDF as a JPEG of its first page (storage takes
     * images only, and the reviewer needs to see the card, not download a file).
     */
    public static MultipartFile storable(MultipartFile upload) {
        return storable(upload, null);
    }

    /** @param pdfPassword the password the document check found for a locked e-Aadhaar; null for none */
    public static MultipartFile storable(MultipartFile upload, String pdfPassword) {
        if (upload == null || upload.isEmpty()) {
            return upload;
        }
        byte[] bytes = DriverSignupService.bytes(upload);
        if (!isPdf(bytes)) {
            return upload;
        }
        Decoded d = decode(bytes, pdfPassword);
        if (d.image() == null) {
            throw new IllegalArgumentException(d.passwordProtected()
                    ? "This PDF is password-protected. Upload the unlocked PDF, a screenshot or a photo of the card."
                    : "This PDF could not be opened. Upload a photo of the card instead.");
        }
        return new JpegUpload(upload.getOriginalFilename(), jpeg(d.image()));
    }

    static byte[] jpeg(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A rendered PDF page standing in for the uploaded file. */
    private record JpegUpload(String originalName, byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            String base = originalName == null ? "document" : originalName.replaceAll("(?i)\\.pdf$", "");
            return base + ".jpg";
        }

        @Override
        public String getContentType() {
            return "image/jpeg";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), bytes);
        }
    }
}
