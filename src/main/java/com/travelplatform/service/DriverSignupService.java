package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.DriverSelfSignupForm;
import com.travelplatform.dto.VehicleRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** A city driver signing themselves up: documents in, email code to confirm the contact, then the owner's review. */
@Service
public class DriverSignupService {

    private static final Logger log = LoggerFactory.getLogger(DriverSignupService.class);

    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern AADHAAR = Pattern.compile("^\\d{12}$");

    /** Unconfirmed signups hold the mobile, email, licence, Aadhaar and plate; they are dropped after this long. */
    @Value("${app.drivers.unverified-ttl-hours:48}")
    private long unverifiedTtlHours = 48;

    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private OtpService otpService;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AfterCommitDispatcher dispatcher;
    @Autowired
    private NotificationService notificationService;

    /**
     * One transaction: an unverified conflicting signup is deleted and the new row saved together, so a
     * failed save keeps the old one. The old row's files go only after the commit.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public Map<String, Object> signup(DriverSelfSignupForm f, MultipartFile photo, MultipartFile license,
                                      MultipartFile aadhaar, MultipartFile rc) {
        if (blank(f.getName()) || blank(f.getMobile()) || blank(f.getEmail())
                || blank(f.getLicenseNumber()) || blank(f.getAadhaarNumber())) {
            throw new IllegalArgumentException("Name, mobile, email, licence number and Aadhaar number are required");
        }
        if (f.getPassword() == null || f.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (blank(f.getVehicleNumber())) {
            throw new IllegalArgumentException("Vehicle number plate is required");
        }
        if (missing(photo) || missing(license) || missing(aadhaar) || missing(rc)) {
            throw new IllegalArgumentException("All four documents are required: photo, licence, Aadhaar and vehicle RC");
        }
        String mobile = f.getMobile().trim();
        if (!MOBILE.matcher(mobile).matches()) {
            throw new IllegalArgumentException("Invalid Indian mobile number");
        }
        String aadhaarNumber = f.getAadhaarNumber().replaceAll("\\s+", "");
        if (!AADHAAR.matcher(aadhaarNumber).matches()) {
            throw new IllegalArgumentException("Invalid Aadhaar number");
        }
        String email = f.getEmail().trim().toLowerCase(Locale.ROOT);
        String licence = f.getLicenseNumber().trim().toUpperCase(Locale.ROOT);

        // Validates the plate and vehicle fields on the new driver first, so a bad form leaves no files behind.
        Driver driver = new Driver();
        driver.setFleet(Driver.Fleet.INDEPENDENT);
        DriverVehicles.apply(driver, new VehicleRequest(f.getVehicleType(), f.getVehicleNumber(),
                f.getVehicleModel(), f.getVehicleAc(), f.getVehicleSeats(), true, false));

        // An earlier signup that never confirmed its email is replaced; anything verified, or owner-made, is refused.
        List<Driver> replaced = new ArrayList<>();
        conflict(driverRepository.findByMobile(mobile), "Mobile number already registered", replaced);
        conflict(driverRepository.findByEmail(email), "Email already registered", replaced);
        conflict(driverRepository.findByLicenseNumber(licence), "License number already registered", replaced);
        conflict(driverRepository.findByAadhaarNumber(aadhaarNumber), "Aadhaar number already registered", replaced);
        // Checked on the spaceless plate; the unique index (V16) is the backstop for races.
        conflict(driverRepository.findByVehicleKey(plateKey(driver.getVehicleNumber())),
                "Vehicle number already registered", replaced);

        // Files before the DB write, as in AdminDriverService: a disk failure must not leave a half-made driver.
        // These are identity documents on a public endpoint, so whatever was stored is removed again
        // if a later file is refused or the save fails (e.g. a race on a unique index).
        List<String> stored = new ArrayList<>();
        Driver saved;
        try {
            driver.setPhoto(store(photo, "photo", stored));
            driver.setLicensePhoto(store(license, "license", stored));
            driver.setAadhaarPhoto(store(aadhaar, "aadhaar", stored));
            driver.setRcPhoto(store(rc, "rc", stored));

            driver.setName(f.getName().trim());
            driver.setMobile(mobile);
            driver.setEmail(email);
            driver.setPassword(passwordEncoder.encode(f.getPassword()));
            driver.setLicenseNumber(licence);
            driver.setAadhaarNumber(aadhaarNumber);
            driver.setRole("ROLE_DRIVER");
            driver.setStatus(Driver.Status.INACTIVE);
            driver.setFirstLogin(false);
            driver.setEmailVerified(false);
            driver.setServesTrip(false);
            driver.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
            driver.setSubmittedAt(LocalDateTime.now());
            // Old rows out and flushed first, or the new one would hit the unique indexes they still hold.
            for (Driver old : replaced) {
                driverRepository.delete(old);
            }
            if (!replaced.isEmpty()) {
                driverRepository.flush();
            }
            saved = driverRepository.save(driver);
            driverRepository.flush();
        } catch (RuntimeException e) {
            deleteFiles(stored);
            throw e;
        }

        // After commit: the code goes out, and the replaced signups' documents are removed.
        dispatcher.dispatch("email", () -> otpService.sendOtp(email));
        if (!replaced.isEmpty()) {
            dispatcher.dispatch("cleanup", () -> replaced.forEach(this::deleteDocuments));
        }
        // The owner hears of the application only once the email is confirmed (see verifyEmail).
        log.info("Driver signup {} awaiting email confirmation", saved.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("email", email);
        response.put("message", "We've emailed you a code to confirm your email.");
        return response;
    }

    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public Map<String, Object> verifyEmail(String rawEmail, String otp) {
        String email = rawEmail == null ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email == null || otp == null) {
            throw new BadCredentialsException("Invalid or expired OTP");
        }
        // Looked up first: a driver whose email is already confirmed must not get a login out of this
        // endpoint (with the public OTP sender it would be a passwordless sign-in). Same generic error.
        Driver driver = driverRepository.findByEmail(email)
                .filter(d -> d.getFleet() == Driver.Fleet.INDEPENDENT && !d.isEmailVerified())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP"));
        if (!otpService.verifyOtp(email, otp)) {
            throw new BadCredentialsException("Invalid or expired OTP");
        }
        driver.setEmailVerified(true);
        driverRepository.save(driver);
        try {
            notificationService.notifyDriverApplication(driver, false);
        } catch (RuntimeException e) {
            // The email is confirmed; a missed in-app notice must not turn it into an error.
            log.warn("Owner notice for driver {} failed: {}", driver.getId(), e.toString());
        }

        String token = jwtUtil.generateToken(driver.getId().toString(), driver.getRole(), driver.getTokenVersion());
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("role", driver.getRole());
        response.put("userId", driver.getId());
        response.put("name", driver.getName());
        response.put("mobile", driver.getMobile());
        response.put("email", driver.getEmail());
        response.put("firstLogin", false);
        response.put("message", "Authentication successful");
        return response;
    }

    /** Removes signups that never confirmed their email, with their documents, once the TTL has passed. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 300_000L)
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public int purgeUnverified() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(unverifiedTtlHours);
        int removed = 0;
        for (Driver d : driverRepository.findByFleetAndEmailVerifiedFalseAndSubmittedAtBefore(Driver.Fleet.INDEPENDENT, cutoff)) {
            if (d.getFleet() != Driver.Fleet.INDEPENDENT || d.isEmailVerified()) {
                continue;
            }
            try {
                driverRepository.delete(d);
                deleteDocuments(d);
                removed++;
            } catch (RuntimeException e) {
                log.warn("Could not purge unverified driver {}: {}", d.getId(), e.toString());
            }
        }
        if (removed > 0) {
            log.info("Purged {} unverified driver signup(s)", removed);
        }
        return removed;
    }

    private void conflict(Optional<Driver> existing, String message, List<Driver> replaced) {
        if (existing.isEmpty()) {
            return;
        }
        Driver d = existing.get();
        if (d.getFleet() == Driver.Fleet.INDEPENDENT && !d.isEmailVerified()) {
            if (replaced.stream().noneMatch(r -> r == d || (r.getId() != null && r.getId().equals(d.getId())))) {
                replaced.add(d);
            }
            return;
        }
        throw new IllegalArgumentException(message);
    }

    /** Upper-case with no spaces: the key the unique index (V16) and the duplicate checks compare on. */
    static String plateKey(String plate) {
        return plate == null ? "" : plate.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private void deleteDocuments(Driver d) {
        deleteFiles(java.util.Arrays.asList(d.getPhoto(), d.getLicensePhoto(), d.getAadhaarPhoto(), d.getRcPhoto()));
    }

    private void deleteFiles(List<String> paths) {
        for (String path : paths) {
            if (blank(path)) continue;
            try {
                fileStorageService.delete(path);
            } catch (RuntimeException ignored) {
                // Cleanup is best-effort and must not hide the real outcome.
            }
        }
    }

    private String store(MultipartFile file, String kind, List<String> stored) {
        String path = fileStorageService.storeFile(file, kind);
        stored.add(path);
        return path;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean missing(MultipartFile f) {
        return f == null || f.isEmpty();
    }
}
