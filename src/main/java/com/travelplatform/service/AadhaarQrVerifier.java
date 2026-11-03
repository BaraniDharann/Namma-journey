package com.travelplatform.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Checks an uploaded Aadhaar image: finds the Secure QR on it and verifies UIDAI's signature
 * ({@link AadhaarSecureQr}). Free and offline - the only input besides the image is UIDAI's
 * public certificate, downloaded once from uidai.gov.in (Developer section, "UIDAI Certificate
 * Details", Secure QR code) and pointed to by {@code app.aadhaar.qr-certificates}.
 *
 * <p>UIDAI rotates the key; list the old and new certificates together so cards signed with
 * either still verify. With none configured QR details are still read, marked NOT_CHECKED.
 *
 * <p>Only call this for a holder who has consented to offline verification (Aadhaar Act 8A).
 */
@Component
public class AadhaarQrVerifier {

    private static final Logger log = LoggerFactory.getLogger(AadhaarQrVerifier.class);

    private static final Map<DecodeHintType, Object> HINTS = Map.of(
            DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER, Boolean.TRUE);

    /** 12 MP phone photos pass; a header claiming gigapixels does not. */
    static final long MAX_PIXELS = 50_000_000L;
    /** Long side decoded at most; a card QR stays readable well below this. */
    static final long MAX_DECODE_SIDE = 4000;

    private final List<PublicKey> uidaiKeys;

    @Autowired
    public AadhaarQrVerifier(@Value("${app.aadhaar.qr-certificates:}") String certificatePaths) {
        this(loadKeys(certificatePaths));
        if (uidaiKeys.isEmpty()) {
            log.warn("No UIDAI Secure QR certificate configured (AADHAAR_QR_CERTIFICATES): "
                    + "Aadhaar QR details will be read but their signature not checked");
        }
    }

    AadhaarQrVerifier(List<PublicKey> uidaiKeys) {
        this.uidaiKeys = List.copyOf(uidaiKeys);
    }

    public boolean isConfigured() {
        return !uidaiKeys.isEmpty();
    }

    /** @param image JPEG or PNG of the card (either side that carries the QR) */
    public AadhaarSecureQr.Result verifyImage(byte[] image) {
        String qrText = readQr(image);
        return qrText == null
                ? AadhaarSecureQr.Result.of(AadhaarSecureQr.Status.UNREADABLE)
                : AadhaarSecureQr.verify(qrText, uidaiKeys);
    }

    private static String readQr(byte[] image) {
        if (image == null || image.length == 0) {
            return null;
        }
        BufferedImage img = decode(image);
        if (img == null) {
            return null; // not an image ImageIO can read (a PDF, HEIC, ...), or absurdly large
        }
        LuminanceSource source = new BufferedImageLuminanceSource(img);
        // Hybrid copes with uneven lighting in phone photos; global with flat scans.
        for (BinaryBitmap bitmap : List.of(new BinaryBitmap(new HybridBinarizer(source)),
                new BinaryBitmap(new GlobalHistogramBinarizer(source)))) {
            try {
                return new MultiFormatReader().decode(bitmap, HINTS).getText();
            } catch (NotFoundException e) {
                // try the next binarizer
            }
        }
        return null;
    }

    /**
     * Decodes from the header first: this runs on a public upload before any other check, and a
     * few-KB PNG can declare a raster of gigabytes. Big photos are subsampled to about
     * {@link #MAX_DECODE_SIDE} px on the long side - still far more than a card's QR needs.
     */
    private static BufferedImage decode(byte[] image) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_PIXELS) {
                    return null;
                }
                int step = (int) Math.max(1, (Math.max(w, h) + MAX_DECODE_SIDE - 1) / MAX_DECODE_SIDE);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<PublicKey> loadKeys(String paths) {
        List<PublicKey> keys = new ArrayList<>();
        if (paths == null || paths.isBlank()) {
            return keys;
        }
        for (String p : Arrays.stream(paths.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
            try (InputStream in = Files.newInputStream(Path.of(p))) {
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
                // Expiry is not enforced: a card signed while the certificate was current stays valid.
                log.info("UIDAI Secure QR certificate loaded: {} (valid {} to {})",
                        cert.getSubjectX500Principal(), cert.getNotBefore(), cert.getNotAfter());
                keys.add(cert.getPublicKey());
            } catch (IOException | CertificateException | RuntimeException e) {
                throw new IllegalStateException("Cannot read UIDAI certificate " + p + ": " + e.getMessage(), e);
            }
        }
        return keys;
    }
}
