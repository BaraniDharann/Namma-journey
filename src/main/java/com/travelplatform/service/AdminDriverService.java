package com.travelplatform.service;

import com.travelplatform.dto.DriverCreationResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;

@Service
@Slf4j
public class AdminDriverService {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private FileStorageService fileStorageService;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$";

    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public DriverCreationResponse createDriver(
            String name, String mobile, String email, String licenseNumber, String aadhaarNumber,
            MultipartFile photo, MultipartFile licensePhoto, MultipartFile aadhaarPhoto) {

        // Cheap uniqueness checks first so we don't waste disk on a request that will fail.
        if (driverRepository.existsByMobile(mobile)) {
            throw new IllegalArgumentException("Mobile number already registered");
        }
        if (driverRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (driverRepository.existsByLicenseNumber(licenseNumber)) {
            throw new IllegalArgumentException("License number already registered");
        }
        if (driverRepository.existsByAadhaarNumber(aadhaarNumber)) {
            throw new IllegalArgumentException("Aadhaar number already registered");
        }

        // File IO happens BEFORE the @Transactional block — disk hiccups must not roll back
        // a successful DB insert, and a DB rollback must not orphan written files.
        String photoUrl = fileStorageService.storeFile(photo, "photo");
        String licensePhotoUrl = fileStorageService.storeFile(licensePhoto, "license");
        String aadhaarPhotoUrl = fileStorageService.storeFile(aadhaarPhoto, "aadhaar");

        String generatedPassword = generatePassword();
        Driver driver = persistDriver(
                name, mobile, email, licenseNumber, aadhaarNumber,
                photoUrl, licensePhotoUrl, aadhaarPhotoUrl, generatedPassword);

        // Email is @Async on EmailService, but dispatching it AFTER the DB commit means
        // a slow SMTP handshake never lengthens the API response.
        emailService.sendDriverCredentials(
                driver.getEmail(),
                driver.getName(),
                driver.getMobile(),
                generatedPassword
        );

        log.info("Driver created with ID: {}, credentials email dispatched to: {}", driver.getId(), email);

        return new DriverCreationResponse(
                driver.getId(),
                driver.getName(),
                driver.getEmail(),
                driver.getMobile(),
                "Driver created successfully. Login credentials sent to driver's email."
        );
    }

    // Spring Data JPA's repository.save() is already transactional; a single insert needs no outer @Transactional.
    private Driver persistDriver(String name, String mobile, String email, String licenseNumber,
                                   String aadhaarNumber, String photoUrl, String licensePhotoUrl,
                                   String aadhaarPhotoUrl, String generatedPassword) {
        Driver driver = new Driver();
        driver.setName(name);
        driver.setMobile(mobile);
        driver.setEmail(email);
        driver.setPassword(passwordEncoder.encode(generatedPassword));
        driver.setLicenseNumber(licenseNumber);
        driver.setAadhaarNumber(aadhaarNumber);
        driver.setPhoto(photoUrl);
        driver.setLicensePhoto(licensePhotoUrl);
        driver.setAadhaarPhoto(aadhaarPhotoUrl);
        driver.setRole("ROLE_DRIVER");
        driver.setStatus(Driver.Status.ACTIVE);
        driver.setFirstLogin(true);
        driver.setEmailVerified(false);
        return driverRepository.save(driver);
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return password.toString();
    }
}
