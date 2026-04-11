package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.dto.DriverChangePasswordRequest;
import com.travelplatform.dto.DriverLoginRequest;
import com.travelplatform.dto.DriverResetPasswordRequest;
import com.travelplatform.dto.DriverSignupRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class DriverAuthService {
    
    @Autowired
    private DriverRepository driverRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private OtpService otpService;
    
    public AuthResponse signup(DriverSignupRequest request) {
        if (driverRepository.existsByMobile(request.getMobile())) {
            throw new IllegalArgumentException("Mobile number already registered");
        }
        
        if (driverRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw new IllegalArgumentException("License number already registered");
        }
        
        if (driverRepository.existsByAadhaarNumber(request.getAadhaarNumber())) {
            throw new IllegalArgumentException("Aadhaar number already registered");
        }
        
        Driver driver = new Driver();
        driver.setName(request.getName());
        driver.setMobile(request.getMobile());
        driver.setPassword(passwordEncoder.encode(request.getPassword()));
        driver.setLicenseNumber(request.getLicenseNumber());
        driver.setAadhaarNumber(request.getAadhaarNumber());
        driver.setRole("ROLE_DRIVER");
        driver.setStatus(Driver.Status.ACTIVE);
        
        driver = driverRepository.save(driver);
        
        String token = jwtUtil.generateToken(driver.getId().toString(), driver.getRole());
        AuthResponse response = new AuthResponse(token, driver.getRole(), driver.getId());
        response.setName(driver.getName());
        response.setMobile(driver.getMobile());
        return response;
    }
    
    public Map<String, Object> login(DriverLoginRequest request) {
        Driver driver = driverRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with mobile: " + request.getMobile()));
        
        if (!passwordEncoder.matches(request.getPassword(), driver.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }
        
        if (driver.getStatus() == Driver.Status.INACTIVE) {
            throw new IllegalArgumentException("Driver account is inactive");
        }
        
        if (driver.isFirstLogin()) {
            boolean otpSent = otpService.sendOtp(driver.getEmail());
            if (!otpSent) {
                throw new RuntimeException("Failed to send OTP");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("firstLogin", true);
            response.put("email", driver.getEmail());
            response.put("message", "First login detected. OTP sent to your email. Please verify to set new password.");
            return response;
        }
        
        String token = jwtUtil.generateToken(driver.getId().toString(), driver.getRole());
        
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
    
    @Transactional
    public Map<String, String> verifyOtpAndSetPassword(String email, String otp, String newPassword) {
        boolean verified = otpService.verifyOtp(email, otp);
        
        if (!verified) {
            throw new BadCredentialsException("Invalid or expired OTP");
        }
        
        Driver driver = driverRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        
        driver.setPassword(passwordEncoder.encode(newPassword));
        driver.setFirstLogin(false);
        driver.setEmailVerified(true);
        driverRepository.save(driver);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password set successfully. You can now login with your new password.");
        return response;
    }
    
    @Transactional
    public Map<String, String> resetPassword(DriverResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        
        Driver driver = driverRepository.findByMobile(request.getMobile())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with mobile: " + request.getMobile()));
        
        driver.setPassword(passwordEncoder.encode(request.getNewPassword()));
        driver.setFirstLogin(false);
        driverRepository.save(driver);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset successfully");
        return response;
    }
}
