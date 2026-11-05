package com.travelplatform.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UIDAI does not publish test QR codes, so these build payloads in the Secure QR layout
 * (255-delimited ISO-8859-1 fields, JPEG 2000 photo, optional email/mobile hashes, 256-byte
 * SHA256withRSA signature over everything before it, gzipped, as one big decimal number) and
 * sign them with a throwaway key standing in for UIDAI's.
 */
class AadhaarSecureQrTest {

    static KeyPair uidai;
    static KeyPair someoneElse;

    static final byte[] PHOTO = {0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, (byte) 0xFF, 0x20, (byte) 0xFF, 0x01};

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        uidai = gen.generateKeyPair();
        someoneElse = gen.generateKeyPair();
    }

    static final String[] V2_FIELDS = {
            "V2", "3", "123420191231103000123", "Ravi Kumar", "15-08-1985", "M", "S/O Kumar",
            "Bengaluru Urban", "Near Temple", "12", "Jayanagar", "560041", "Jayanagar", "Karnataka",
            "4th Main", "Bengaluru South", "Bengaluru", "6789"};

    static final String[] LEGACY_FIELDS = {
            "0", "987620190101093000456", "Lakshmi", "01-01-1990", "F", "W/O Raman",
            "Mysuru", "", "7", "Kuvempunagar", "570023", "Kuvempunagar", "Karnataka",
            "", "Mysuru", "Mysuru"};

    @Test
    @DisplayName("a V2 QR signed by UIDAI's key is VALID and its details are read")
    void validV2() throws Exception {
        String qr = qr(V2_FIELDS, 64, uidai.getPrivate());

        AadhaarSecureQr.Result r = AadhaarSecureQr.verify(qr, List.of(uidai.getPublic()));

        assertEquals(AadhaarSecureQr.Status.VALID, r.status());
        assertEquals("V2", r.version());
        assertEquals("1234", r.aadhaarLast4());
        assertEquals("Ravi Kumar", r.name());
        assertEquals("15-08-1985", r.dob());
        assertEquals("M", r.gender());
        assertEquals("Karnataka", r.state());
        assertEquals("560041", r.pincode());
        assertEquals("6789", r.mobileLast4());
        assertArrayEquals(PHOTO, r.photoJpeg2000());
    }

    @Test
    @DisplayName("the old layout without a version field is read too")
    void validLegacy() throws Exception {
        String qr = qr(LEGACY_FIELDS, 0, uidai.getPrivate());

        AadhaarSecureQr.Result r = AadhaarSecureQr.verify(qr, List.of(uidai.getPublic()));

        assertEquals(AadhaarSecureQr.Status.VALID, r.status());
        assertNull(r.version());
        assertEquals("9876", r.aadhaarLast4());
        assertEquals("Lakshmi", r.name());
        assertNull(r.mobileLast4());
        assertArrayEquals(PHOTO, r.photoJpeg2000());
    }

    @Test
    @DisplayName("any of several configured keys may have signed it (UIDAI rotates its key)")
    void secondKeyVerifies() throws Exception {
        String qr = qr(V2_FIELDS, 64, uidai.getPrivate());

        AadhaarSecureQr.Result r = AadhaarSecureQr.verify(qr, List.of(someoneElse.getPublic(), uidai.getPublic()));

        assertEquals(AadhaarSecureQr.Status.VALID, r.status());
    }

    @Test
    @DisplayName("an edited name no longer matches the signature")
    void tamperedIsInvalid() throws Exception {
        byte[] signed = payload(V2_FIELDS, 64, uidai.getPrivate());
        byte[] edited = new String(signed, StandardCharsets.ISO_8859_1)
                .replace("Ravi Kumar", "Ravi Kumaz").getBytes(StandardCharsets.ISO_8859_1);

        AadhaarSecureQr.Result r = AadhaarSecureQr.verify(toQrText(edited), List.of(uidai.getPublic()));

        assertEquals(AadhaarSecureQr.Status.INVALID_SIGNATURE, r.status());
        assertEquals("Ravi Kumaz", r.name(), "details are still shown, so the admin can see what was claimed");
    }

    @Test
    @DisplayName("a QR signed by any other key is INVALID")
    void otherKeyIsInvalid() throws Exception {
        String qr = qr(V2_FIELDS, 64, someoneElse.getPrivate());

        assertEquals(AadhaarSecureQr.Status.INVALID_SIGNATURE,
                AadhaarSecureQr.verify(qr, List.of(uidai.getPublic())).status());
    }

    @Test
    @DisplayName("with no UIDAI certificate configured the details are read but NOT_CHECKED")
    void noKeys() throws Exception {
        AadhaarSecureQr.Result r = AadhaarSecureQr.verify(qr(V2_FIELDS, 64, uidai.getPrivate()), List.of());

        assertEquals(AadhaarSecureQr.Status.NOT_CHECKED, r.status());
        assertEquals("Ravi Kumar", r.name());
    }

    @Test
    @DisplayName("old XML QR codes, random numbers and blanks are NOT_SECURE_QR")
    void notSecureQr() {
        List<PublicKey> keys = List.of(uidai.getPublic());
        assertEquals(AadhaarSecureQr.Status.NOT_SECURE_QR,
                AadhaarSecureQr.verify("<?xml version=\"1.0\"?><PrintLetterBarcodeData uid=\"123412341234\"/>", keys).status());
        assertEquals(AadhaarSecureQr.Status.NOT_SECURE_QR, AadhaarSecureQr.verify("123456789012345678901234567890", keys).status());
        assertEquals(AadhaarSecureQr.Status.NOT_SECURE_QR, AadhaarSecureQr.verify("", keys).status());
        assertEquals(AadhaarSecureQr.Status.NOT_SECURE_QR, AadhaarSecureQr.verify(null, keys).status());
    }

    @Test
    @DisplayName("a gzip that inflates past the size cap is refused, not read into memory")
    void decompressionBomb() throws Exception {
        byte[] huge = new byte[2 * 1024 * 1024];
        Arrays.fill(huge, (byte) 'A');

        assertEquals(AadhaarSecureQr.Status.NOT_SECURE_QR,
                AadhaarSecureQr.verify(toQrText(huge), List.of(uidai.getPublic())).status());
    }

    // ---- building QR payloads -------------------------------------------------------------

    static String qr(String[] fields, int hashBytes, PrivateKey key) throws Exception {
        return toQrText(payload(fields, hashBytes, key));
    }

    /** fields, then the photo, then email/mobile hashes, then the signature over all of that. */
    static byte[] payload(String[] fields, int hashBytes, PrivateKey key) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String f : fields) {
            out.write(f.getBytes(StandardCharsets.ISO_8859_1));
            out.write(255);
        }
        out.write(PHOTO);
        out.write(new byte[hashBytes]);
        byte[] signed = out.toByteArray();

        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(key);
        s.update(signed);
        out.write(s.sign());
        return out.toByteArray();
    }

    static String toQrText(byte[] payload) throws Exception {
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
            z.write(payload);
        }
        return new BigInteger(1, gz.toByteArray()).toString();
    }
}
