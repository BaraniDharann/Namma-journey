package com.travelplatform.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PDF uploads: rendering, and opening UIDAI's locked e-Aadhaar from the typed name. */
class DocumentImagesTest {

    @Test
    @DisplayName("Password prefixes follow UIDAI's rule: first 4 of the name as printed, in capitals")
    void prefixes() {
        assertTrue(DocumentImages.passwordPrefixes("Suresh Kumar").contains("SURE"));
        // "P. KUMAR" is P.KU1990 per UIDAI; dots-dropped kept as a fallback
        assertTrue(DocumentImages.passwordPrefixes("P. Kumar").containsAll(java.util.List.of("P.KU", "PKUM")));
        // Typed "Rajesh Kumar S", printed "S RAJESH KUMAR"
        assertTrue(DocumentImages.passwordPrefixes("Rajesh Kumar S").contains("SRAJ"));
        assertTrue(DocumentImages.passwordPrefixes("Ria").contains("RIA"));
        assertTrue(DocumentImages.passwordPrefixes("  ").isEmpty());
        assertTrue(DocumentImages.passwordPrefixes(null).isEmpty());
    }

    @Test
    @DisplayName("A locked e-Aadhaar opens from the typed name alone (birth year found by trying), quickly")
    void unlocksFromTypedName() throws IOException {
        byte[] pdf = lockedPdf("SURE1987");
        assertTrue(DocumentImages.decode(pdf).passwordProtected());

        long t0 = System.nanoTime();
        DocumentImages.Unlocked u = DocumentImages.unlockEAadhaar(pdf, "suresh kumar");
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertNotNull(u);
        assertEquals("SURE1987", u.password());
        assertNotNull(u.decoded().image());
        assertTrue(ms < 5_000, "took " + ms + " ms");
    }

    @Test
    @DisplayName("Someone else's name does not open it")
    void wrongNameStaysLocked() throws IOException {
        assertNull(DocumentImages.unlockEAadhaar(lockedPdf("SURE1987"), "Priya Lakshmi"));
    }

    @Test
    @DisplayName("Storage gets a JPEG of the page: plain PDF as is, locked one with the found password")
    void storableRendersPdf() throws IOException {
        MultipartFile plain = DocumentImages.storable(new MockMultipartFile("a", "card.pdf", "application/pdf", pdf(null)));
        assertEquals("image/jpeg", plain.getContentType());
        assertEquals("card.jpg", plain.getOriginalFilename());

        MockMultipartFile locked = new MockMultipartFile("a", "e.pdf", "application/pdf", lockedPdf("SURE1987"));
        assertEquals("image/jpeg", DocumentImages.storable(locked, "SURE1987").getContentType());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DocumentImages.storable(locked));
        assertTrue(e.getMessage().contains("password-protected"));
    }

    @Test
    @DisplayName("Photos pass through untouched; a broken PDF is refused, not crashed on")
    void photosUntouchedBrokenPdfRefused() {
        MockMultipartFile photo = new MockMultipartFile("a", "a.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2});
        assertTrue(DocumentImages.storable(photo) == photo);
        DocumentImages.Decoded broken = DocumentImages.decode("%PDF-1.7 not really".getBytes());
        assertNull(broken.image());
        assertFalse(broken.passwordProtected());
    }

    @Test
    @DisplayName("Checker: a locked e-Aadhaar is unlocked from the typed name; the wrong name is told what to do")
    void checkerUnlocks() throws IOException {
        AadhaarQrVerifier verifier = org.mockito.Mockito.mock(AadhaarQrVerifier.class);
        org.mockito.Mockito.when(verifier.verifyDecoded(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AadhaarSecureQr.Result.of(AadhaarSecureQr.Status.UNREADABLE));
        IdentityDocumentChecker checker = new IdentityDocumentChecker(verifier);
        byte[] pdf = lockedPdf("SURE1987");

        IdentityDocumentChecker.Result wrong = checker.aadhaar(pdf, TestDocs.VALID_AADHAAR, "Priya Lakshmi");
        assertTrue(wrong.reason().contains("locked"), wrong.reason());

        IdentityDocumentChecker.Result opened = checker.aadhaar(pdf, TestDocs.VALID_AADHAAR, "Suresh Kumar");
        // Opened, then judged on its QR like any card (none on this test page).
        assertTrue(opened.summary().contains("unlocked with the typed name"), opened.summary());
        assertEquals("SURE1987", opened.pdfPassword());
        assertFalse(opened.toString().contains("SURE1987"), "password must not reach logs");
    }

    /** An A4-ish page with the Aadhaar colour bands, optionally locked like UIDAI's e-Aadhaar. */
    static byte[] lockedPdf(String password) throws IOException {
        return pdf(password);
    }

    private static byte[] pdf(String password) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(new Color(255, 153, 51));
                cs.addRect(40, 700, 530, 30);
                cs.fill();
                cs.setNonStrokingColor(new Color(19, 136, 8));
                cs.addRect(40, 60, 530, 30);
                cs.fill();
                cs.setNonStrokingColor(new Color(90, 90, 90));
                for (int i = 0; i < 40; i++) {
                    cs.addRect(60 + (i * 37) % 480, 120 + (i * 53) % 540, 18, 6);
                }
                cs.fill();
            }
            if (password != null) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-" + password, password, new AccessPermission());
                policy.setEncryptionKeyLength(128);
                doc.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
