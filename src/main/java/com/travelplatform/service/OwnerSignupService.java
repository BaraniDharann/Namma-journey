package com.travelplatform.service;

import com.travelplatform.dto.OwnerSignupForm;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerAadhaarQr;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.repository.CityRepository;
import com.travelplatform.repository.OwnerAadhaarQrRepository;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A travels owner applying to join NammaJourney. Everything is checked before anything is stored;
 * then the Aadhaar upload's Secure QR is verified (UIDAI offline verification, free), the
 * documents stored as signed-link-only uploads, and the owner saved as PENDING_REVIEW with no
 * password. An admin decides within {@link #REVIEW_WINDOW}.
 */
@Service
public class OwnerSignupService {

    static final Duration REVIEW_WINDOW = Duration.ofHours(48);
    static final long MAX_DOCUMENT_BYTES = 5L * 1024 * 1024;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern ACCOUNT = Pattern.compile("^\\d{9,18}$");
    private static final Pattern UPI = Pattern.compile("^[\\w.-]{2,}@[a-zA-Z]{2,}$");

    public record SignupResult(Long ownerId, LocalDateTime reviewBy) {
    }

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private OwnerAadhaarQrRepository aadhaarQrRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AadhaarQrVerifier aadhaarQrVerifier;

    @Autowired
    private EmailService emailService;

    @Autowired
    private com.travelplatform.repository.OwnerVerificationCheckRepository checkRepository;

    @Transactional
    public SignupResult signup(OwnerSignupForm form) {
        Owner owner = new Owner();
        AadhaarSecureQr.Result qr = applyForm(owner, form, false);
        return submit(owner, qr);
    }

    @Transactional(readOnly = true)
    public com.travelplatform.dto.OwnerResubmitPrefill prefill(String token) {
        Owner o = byResubmitToken(token);
        return new com.travelplatform.dto.OwnerResubmitPrefill(o.getTravelsName(), o.getContactName(), o.getEmail(),
                o.getPhone(), o.getCities().stream().map(City::getId).sorted().toList(), o.getBankAccountHolder(),
                o.getBankIfsc(), o.getBankName(), OwnerApplicationChecks.maskPan(o.getPanNumber()),
                o.getRejectionReason());
    }

    /**
     * A rejected owner fixes their application through the emailed link: the same row goes back
     * to PENDING_REVIEW with the earlier checks cleared, and the link is used up.
     */
    @Transactional
    public SignupResult resubmit(String token, OwnerSignupForm form) {
        Owner owner = byResubmitToken(token);
        AadhaarSecureQr.Result qr = applyForm(owner, form, true);
        owner.setResubmitTokenHash(null);
        owner.setResubmitTokenExpiresAt(null);
        owner.setRejectionReason(null);
        checkRepository.deleteByOwnerId(owner.getId());
        return submit(owner, qr);
    }

    private Owner byResubmitToken(String token) {
        return (token == null || token.isBlank() ? java.util.Optional.<Owner>empty()
                : ownerRepository.findByResubmitTokenHash(OwnerCredentials.hash(token)))
                .filter(o -> o.getStatus() == OwnerStatus.REJECTED)
                .filter(o -> o.getResubmitTokenExpiresAt() != null && o.getResubmitTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("This link has expired. Please contact support."));
    }

    /** Saves the application as pending, records the QR result and tells the admin and the owner. */
    SignupResult submit(Owner owner, AadhaarSecureQr.Result qr) {
        LocalDateTime now = LocalDateTime.now();
        owner.setStatus(OwnerStatus.PENDING_REVIEW);
        owner.setSubmittedAt(now);
        owner.setAdminRemindedAt(null);
        Owner saved = ownerRepository.save(owner);
        if (qr != null) {
            aadhaarQrRepository.save(toEntity(saved.getId(), qr, now));
        }

        LocalDateTime reviewBy = now.plus(REVIEW_WINDOW);
        emailService.sendAdminNewOwnerApplication(saved, reviewBy);
        emailService.sendOwnerApplicationReceived(saved.getEmail(), saved.getTravelsName(), reviewBy);
        return new SignupResult(saved.getId(), reviewBy);
    }

    /**
     * Validates the form and copies it onto {@code owner}. Shared with resubmission, where the
     * documents already on file may be kept ({@code documentsOptional}).
     *
     * @return the Aadhaar QR result, or null when no new Aadhaar image was sent
     */
    AadhaarSecureQr.Result applyForm(Owner owner, OwnerSignupForm form, boolean documentsOptional) {
        String email = required(form.getEmail(), "Email").toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        String travelsName = required(form.getTravelsName(), "Travels name");
        String contactName = required(form.getContactName(), "Contact person's name");
        String phone = required(form.getPhone(), "Mobile number");
        if (!PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("Enter a 10-digit Indian mobile phone number");
        }
        List<City> cities = cities(form.getCityIds());
        if (!OwnerApplicationChecks.validPan(form.getPanNumber())) {
            throw new IllegalArgumentException("PAN must look like ABCDE1234F");
        }
        if (!DriverApplicationChecks.validAadhaar(form.getAadhaarNumber())) {
            throw new IllegalArgumentException("Aadhaar number is not valid - check the 12 digits");
        }
        String holder = required(form.getBankAccountHolder(), "Bank account holder name");
        String account = required(form.getBankAccountNumber(), "Bank account number");
        if (!ACCOUNT.matcher(account).matches()) {
            throw new IllegalArgumentException("Bank account number must be 9 to 18 digits");
        }
        if (!OwnerApplicationChecks.validIfsc(form.getBankIfsc())) {
            throw new IllegalArgumentException("IFSC must look like SBIN0001234");
        }
        String bankName = required(form.getBankName(), "Bank name");
        String upi = required(form.getUpiId(), "UPI ID");
        if (!UPI.matcher(upi).matches()) {
            throw new IllegalArgumentException("UPI ID must look like name@bank");
        }
        if (!form.isAadhaarConsent()) {
            throw new IllegalArgumentException("Please give consent for offline Aadhaar verification");
        }
        checkDocument(form.getRegistrationDoc(), "Business registration document", documentsOptional);
        checkDocument(form.getPanDoc(), "PAN card", documentsOptional);
        checkDocument(form.getAadhaarDoc(), "Aadhaar card", documentsOptional);

        String pan = OwnerApplicationChecks.normalisePan(form.getPanNumber());
        String panHash = OwnerApplicationChecks.panHash(pan);
        ownerRepository.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(owner.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException(other.getStatus() == OwnerStatus.REJECTED
                            ? "This email already has an application. Use the link in your rejection email to resubmit."
                            : "This email is already registered");
                });
        ownerRepository.findByPanHash(panHash)
                .filter(other -> !other.getId().equals(owner.getId()))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("This PAN is already registered");
                });

        // Read before storing: storeImage re-compresses, which can blur a QR past reading.
        AadhaarSecureQr.Result qr = present(form.getAadhaarDoc())
                ? aadhaarQrVerifier.verifyImage(bytes(form.getAadhaarDoc()))
                : null;

        owner.setEmail(email);
        owner.setTravelsName(travelsName);
        owner.setContactName(contactName);
        owner.setPhone(phone);
        owner.setCities(new HashSet<>(cities));
        owner.setPanNumber(pan);
        owner.setPanHash(panHash);
        owner.setAadhaarLast4(OwnerApplicationChecks.aadhaarLast4(form.getAadhaarNumber()));
        owner.setAadhaarConsentAt(LocalDateTime.now());
        owner.setBankAccountHolder(holder);
        owner.setBankAccountNumber(account);
        owner.setBankIfsc(form.getBankIfsc().trim().toUpperCase(Locale.ROOT));
        owner.setBankName(bankName);
        owner.setUpiId(upi);
        if (present(form.getRegistrationDoc())) {
            owner.setRegistrationDocPath(fileStorageService.storeImage(form.getRegistrationDoc(), "owners", "registration"));
        }
        if (present(form.getPanDoc())) {
            owner.setPanDocPath(fileStorageService.storeImage(form.getPanDoc(), "owners", "pan"));
        }
        if (present(form.getAadhaarDoc())) {
            owner.setAadhaarDocPath(fileStorageService.storeImage(form.getAadhaarDoc(), "owners", "aadhaar"));
        }
        return qr;
    }

    static OwnerAadhaarQr toEntity(Long ownerId, AadhaarSecureQr.Result qr, LocalDateTime checkedAt) {
        OwnerAadhaarQr e = new OwnerAadhaarQr();
        e.setOwnerId(ownerId);
        e.setStatus(qr.status().name());
        e.setName(qr.name());
        e.setDob(qr.dob());
        e.setGender(qr.gender());
        e.setState(qr.state());
        e.setPincode(qr.pincode());
        e.setLast4(qr.aadhaarLast4());
        e.setPhotoJpeg(AadhaarPhotoConverter.toJpeg(qr.photoJpeg2000()));
        e.setCheckedAt(checkedAt);
        return e;
    }

    private List<City> cities(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one city you serve");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        List<City> found = cityRepository.findAllById(distinct);
        if (found.size() != distinct.size() || found.stream().anyMatch(c -> !c.isActive())) {
            throw new IllegalArgumentException("A chosen city is not available");
        }
        return found;
    }

    private static void checkDocument(MultipartFile file, String what, boolean optional) {
        if (!present(file)) {
            if (!optional) {
                throw new IllegalArgumentException(what + " image is required");
            }
            return;
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException(what + " must be 5 MB or smaller");
        }
    }

    private static boolean present(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is required");
        }
        return value.trim();
    }
}
