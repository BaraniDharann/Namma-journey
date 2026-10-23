package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.DriverSelfSignupForm;
import com.travelplatform.dto.VehicleRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A city driver signing themselves up: documents in, owner review next, email code to confirm the contact. */
@Service
public class DriverSignupService {

    private static final Logger log = LoggerFactory.getLogger(DriverSignupService.class);

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

        // Validates the plate and vehicle fields on the new driver first, so a bad form leaves no files behind.
        Driver driver = new Driver();
        driver.setFleet(Driver.Fleet.INDEPENDENT);
        DriverVehicles.apply(driver, new VehicleRequest(f.getVehicleType(), f.getVehicleNumber(),
                f.getVehicleModel(), f.getVehicleAc(), f.getVehicleSeats(), true, false));

        String email = f.getEmail().trim();
        if (driverRepository.existsByMobile(f.getMobile())) {
            throw new IllegalArgumentException("Mobile number already registered");
        }
        if (driverRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (driverRepository.existsByLicenseNumber(f.getLicenseNumber())) {
            throw new IllegalArgumentException("License number already registered");
        }
        if (driverRepository.existsByAadhaarNumber(f.getAadhaarNumber())) {
            throw new IllegalArgumentException("Aadhaar number already registered");
        }
        // Checked on the normalised plate; the unique index (V16) is the backstop for races.
        if (driverRepository.existsByVehicleNumberIgnoreCase(driver.getVehicleNumber())) {
            throw new IllegalArgumentException("Vehicle number already registered");
        }

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
            driver.setMobile(f.getMobile());
            driver.setEmail(email);
            driver.setPassword(passwordEncoder.encode(f.getPassword()));
            driver.setLicenseNumber(f.getLicenseNumber());
            driver.setAadhaarNumber(f.getAadhaarNumber());
            driver.setRole("ROLE_DRIVER");
            driver.setStatus(Driver.Status.INACTIVE);
            driver.setFirstLogin(false);
            driver.setEmailVerified(false);
            driver.setServesTrip(false);
            driver.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
            driver.setSubmittedAt(LocalDateTime.now());
            saved = driverRepository.save(driver);
        } catch (RuntimeException e) {
            for (String path : stored) {
                try {
                    fileStorageService.delete(path);
                } catch (RuntimeException ignored) {
                    // Cleanup is best-effort and must not hide the original failure.
                }
            }
            throw e;
        }

        // The save above has committed (repository calls are their own transaction), so the code goes out now.
        dispatcher.dispatch("email", () -> otpService.sendOtp(email));
        try {
            notificationService.notifyDriverApplication(saved, false);
        } catch (RuntimeException e) {
            // The application is saved; a missed in-app notice must not turn it into an error.
            log.warn("Owner notice for driver {} failed: {}", saved.getId(), e.toString());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("email", email);
        response.put("message", "We've emailed you a code to confirm your email.");
        return response;
    }

    public Map<String, Object> verifyEmail(String email, String otp) {
        if (email == null || otp == null || !otpService.verifyOtp(email, otp)) {
            throw new BadCredentialsException("Invalid or expired OTP");
        }
        Driver driver = driverRepository.findByEmail(email)
                .filter(d -> d.getFleet() == Driver.Fleet.INDEPENDENT)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP"));
        driver.setEmailVerified(true);
        driverRepository.save(driver);

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
