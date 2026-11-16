package com.travelplatform.service;

import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks an uploaded Aadhaar or PAN before anyone gets a login from it. Runs on the server, offline:
 * nothing is sent anywhere.
 *
 * <p>Aadhaar, in order: a real image, big enough to read; the card's colours ({@link CardLook});
 * the Secure QR with UIDAI's signature ({@link AadhaarQrVerifier}); the last 4 digits and the name
 * in the QR against what was typed. The signature is what proves a card genuine - an edited image
 * fails it - so a card is only {@link Outcome#PASS} when the signature was actually checked.
 *
 * <p>PAN has no signature that can be checked offline, so a PAN is never more than "looks right";
 * the admin confirms it on the Income Tax portal.
 */
@Service
public class IdentityDocumentChecker {

    /** Smaller than this and the QR or the printed text cannot be read reliably. */
    static final int MIN_BYTES = 10 * 1024;
    static final int MIN_LONG_SIDE = 500;

    public enum Outcome {
        /** Proven genuine and matching what was typed. */
        PASS,
        /** Nothing wrong found, but not proven either (no UIDAI certificate configured): a person decides. */
        REVIEW,
        /** Fake, unreadable or not matching: refused, with {@link Result#reason()} telling them why. */
        REJECT
    }

    /**
     * @param reason what to show the person when rejected; null otherwise
     * @param notes  what was found, for the admin's record
     * @param qr     the Aadhaar QR as read (null for PAN, or when the image could not be decoded)
     */
    public record Result(Outcome outcome, String reason, List<String> notes, AadhaarSecureQr.Result qr,
                         /* The password that opened a locked e-Aadhaar, so the same PDF can be stored as an
                            image; never stored or logged. Null for anything else. */
                         String pdfPassword) {

        public Result(Outcome outcome, String reason, List<String> notes, AadhaarSecureQr.Result qr) {
            this(outcome, reason, notes, qr, null);
        }

        @Override
        public String toString() {
            return "Result[" + outcome + ", " + summary() + "]"; // keeps pdfPassword out of logs
        }

        public boolean rejected() {
            return outcome == Outcome.REJECT;
        }

        /** The notes as one line, cut to fit a 500-character column. */
        public String summary() {
            String s = String.join("; ", notes);
            return s.length() <= 500 ? s : s.substring(0, 497) + "...";
        }
    }

    static final String CLEARER_PHOTO = "Upload a clear, straight photo of your Aadhaar showing the big QR code "
            + "(or a screenshot of your e-Aadhaar).";

    private final AadhaarQrVerifier qrVerifier;

    public IdentityDocumentChecker(AadhaarQrVerifier qrVerifier) {
        this.qrVerifier = qrVerifier;
    }

    /**
     * @param typedNumber the 12-digit number from the form
     * @param typedName   the person's name from the form
     */
    public Result aadhaar(byte[] image, String typedNumber, String typedName) {
        List<String> notes = new ArrayList<>();
        String pdfPassword = null;
        DocumentImages.Decoded decoded;
        if (image != null && DocumentImages.isPdf(image) && DocumentImages.decode(image).passwordProtected()) {
            // UIDAI's e-Aadhaar is locked with name + birth year: opened from the typed name.
            DocumentImages.Unlocked unlocked = DocumentImages.unlockEAadhaar(image, typedName);
            if (unlocked == null) {
                notes.add("e-Aadhaar PDF: locked, the typed name did not open it");
                return reject("Your e-Aadhaar PDF is locked and the name you typed didn't open it. Type your name "
                        + "exactly as printed on the Aadhaar, or upload a photo or screenshot of the card.", notes, null);
            }
            notes.add("e-Aadhaar PDF: unlocked with the typed name");
            pdfPassword = unlocked.password();
            decoded = checkedSize(image, unlocked.decoded(), "Aadhaar", notes);
        } else {
            decoded = readable(image, "Aadhaar", notes);
        }
        if (decoded == null) {
            return reject(notes.get(notes.size() - 1), notes, null);
        }
        Result r = aadhaar(decoded, typedNumber, typedName, notes);
        return new Result(r.outcome(), r.reason(), r.notes(), r.qr(), pdfPassword);
    }

    private Result aadhaar(DocumentImages.Decoded decoded, String typedNumber, String typedName, List<String> notes) {
        BufferedImage img = decoded.image();

        boolean looks = CardLook.looksLikeAadhaar(img);
        notes.add(looks ? "Aadhaar colours: yes" : "Aadhaar colours: no");

        AadhaarSecureQr.Result qr = readQr(decoded);
        notes.add("QR: " + qr.status());
        switch (qr.status()) {
            case UNREADABLE:
                return reject(looks
                        ? "We couldn't read the QR code on your Aadhaar. " + CLEARER_PHOTO
                        : "This doesn't look like an Aadhaar card. " + CLEARER_PHOTO, notes, qr);
            case NOT_SECURE_QR:
                return reject("This Aadhaar has an old QR code that can't be verified. Download a fresh "
                        + "e-Aadhaar from myaadhaar.uidai.gov.in and upload that.", notes, qr);
            case INVALID_SIGNATURE:
                return reject("This Aadhaar failed UIDAI's digital signature check - the card looks edited "
                        + "or fake. Upload your original Aadhaar.", notes, qr);
            default:
                break;
        }

        // VALID or NOT_CHECKED: the QR was read, so compare it with the form.
        String digits = typedNumber == null ? "" : typedNumber.replaceAll("\\D", "");
        if (qr.aadhaarLast4() == null || digits.length() < 4
                || !digits.endsWith(qr.aadhaarLast4())) {
            notes.add("Last 4 digits: do not match");
            return reject("The Aadhaar number you typed doesn't match the card you uploaded.", notes, qr);
        }
        notes.add("Last 4 digits: match");
        if (!NameMatch.sameName(typedName, qr.name())) {
            notes.add("Name: does not match");
            return reject("The name you typed doesn't match the name on this Aadhaar. Type your name "
                    + "as printed on the card.", notes, qr);
        }
        notes.add("Name: match");

        if (qr.status() == AadhaarSecureQr.Status.NOT_CHECKED) {
            notes.add("Signature not checked: UIDAI certificate not configured");
            return new Result(Outcome.REVIEW, null, notes, qr);
        }
        // A genuine signed QR on an image without the card's colours is usually a black-and-white
        // photocopy: the QR still proves the details, so it is noted rather than refused.
        if (!looks) {
            notes.add("Signed QR on a plain or black-and-white copy");
        }
        return new Result(Outcome.PASS, null, notes, qr);
    }

    /**
     * A PAN can only be checked for format and look offline, so the best this returns is
     * {@link Outcome#REVIEW}.
     */
    public Result pan(byte[] image, String typedPan) {
        List<String> notes = new ArrayList<>();
        DocumentImages.Decoded decoded = readable(image, "PAN", notes);
        if (decoded == null) {
            return reject(notes.get(notes.size() - 1), notes, null);
        }
        BufferedImage img = decoded.image();
        if (!OwnerApplicationChecks.validPan(typedPan)) {
            notes.add("PAN number: wrong format");
            return reject("PAN must look like ABCDE1234F", notes, null);
        }
        if (!CardLook.looksLikePan(img)) {
            notes.add("PAN colours: no");
            return reject("This doesn't look like a PAN card. Upload a clear photo of the front of your PAN card.",
                    notes, null);
        }
        notes.add("PAN colours: yes");
        notes.add("PAN number: confirm on the Income Tax portal");
        return new Result(Outcome.REVIEW, null, notes, null);
    }

    /** The QR on the page, or - for a PDF - on its own sharper QR picture (UIDAI's e-Aadhaar). */
    AadhaarSecureQr.Result readQr(DocumentImages.Decoded decoded) {
        AadhaarSecureQr.Result qr = qrVerifier.verifyDecoded(decoded.image());
        for (BufferedImage candidate : decoded.qrCandidates()) {
            if (qr.status() != AadhaarSecureQr.Status.UNREADABLE) {
                break;
            }
            qr = qrVerifier.verifyDecoded(candidate);
        }
        return qr;
    }

    /** The decoded upload, or null with the reason added to {@code notes}. */
    private static DocumentImages.Decoded readable(byte[] image, String what, List<String> notes) {
        if (image == null || image.length == 0) {
            notes.add("Upload your " + what + " card.");
            return null;
        }
        return checkedSize(image, DocumentImages.decode(image), what, notes);
    }

    /** {@code decoded} if it is an image big enough to read, or null with the reason added to {@code notes}. */
    private static DocumentImages.Decoded checkedSize(byte[] image, DocumentImages.Decoded decoded, String what,
                                                     List<String> notes) {
        BufferedImage img = decoded.image();
        if (img == null) {
            notes.add(decoded.passwordProtected()
                    ? "Your " + what + " PDF is password-protected. Upload the unlocked PDF, a screenshot or a photo of the card."
                    : "Upload your " + what + " as a JPG or PNG photo, or the PDF.");
            return null;
        }
        // Byte size says something about a photo's sharpness, nothing about a PDF's (vector pages are tiny).
        if ((!decoded.pdf() && image.length < MIN_BYTES) || Math.max(img.getWidth(), img.getHeight()) < MIN_LONG_SIDE) {
            notes.add("Your " + what + " photo is too small or blurry. Take a clearer, closer photo.");
            return null;
        }
        return decoded;
    }

    private static Result reject(String reason, List<String> notes, AadhaarSecureQr.Result qr) {
        return new Result(Outcome.REJECT, reason, notes, qr);
    }
}
