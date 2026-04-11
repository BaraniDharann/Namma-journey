package com.travelplatform.service;

import com.travelplatform.dto.DriverCreationResponse;
import com.travelplatform.dto.OwnerVerifyDriverRequest;
import com.travelplatform.dto.VerificationResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private OtpService otpService;
    
    @Autowired
    private FileStorageService fileStorageService;
    
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$";
    
    @Transactional
    public DriverCreationResponse createDriver(
            String name, String mobile, String email, String licenseNumber, String aadhaarNumber,
            MultipartFile photo, MultipartFile licensePhoto, MultipartFile aadhaarPhoto) {
        
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
        
        String photoUrl = fileStorageService.storeFile(photo, "photo");
        String licensePhotoUrl = fileStorageService.storeFile(licensePhoto, "license");
        String aadhaarPhotoUrl = fileStorageService.storeFile(aadhaarPhoto, "aadhaar");
        
        String generatedPassword = generatePassword();
        
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
        
        driver = driverRepository.save(driver);
        
        boolean credentialsSent = emailService.sendDriverCredentials(
            driver.getEmail(),
            driver.getName(),
            driver.getMobile(),
            generatedPassword
        );
        
        if (!credentialsSent) {
            throw new RuntimeException("Failed to send credentials to driver email");
        }
        
        log.info("Driver created with ID: {}, credentials sent to email: {}", driver.getId(), email);
        
        return new DriverCreationResponse(
            driver.getId(),
            driver.getName(),
            driver.getEmail(),
            driver.getMobile(),
            "Driver created successfully. Login credentials sent to driver's email."
        );
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
