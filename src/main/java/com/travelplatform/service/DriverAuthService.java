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

    @Autowired
    private com.travelplatform.security.TokenVersionService tokenVersionService;
    
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
        
        String token = jwtUtil.generateToken(driver.getId().toString(), driver.getRole(), driver.getTokenVersion());
        AuthResponse response = new AuthResponse(token, driver.getRole(), driver.getId());
        response.setName(driver.getName());
        response.setMobile(driver.getMobile());
        return response;
    }
    
    public Map<String, Object> login(DriverLoginRequest request) {
        // Same answer, same BCrypt cost, for an unknown mobile and a wrong password.
        Driver driver = driverRepository.findByMobile(request.getMobile()).orElse(null);
        if (!CredentialCheck.matches(passwordEncoder, request.getPassword(), driver == null ? null : driver.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        
        // No status check here on purpose: Driver.status is the driver's own online/offline switch.
        // Refusing INACTIVE drivers locked out anyone who had gone offline - they could never log
        // back in to go online again. Offline drivers are simply never offered trips
        // (DriverRepository.findAvailableDrivers).
        
        if (driver.isFirstLogin()) {
            // Quiet during the resend cooldown: a driver retrying login keeps the code already sent.
            otpService.sendOtpUnlessRecent(driver.getEmail());
            
            Map<String, Object> response = new HashMap<>();
            response.put("firstLogin", true);
            response.put("email", driver.getEmail());
            response.put("message", "First login detected. OTP sent to your email. Please verify to set new password.");
            return response;
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
    
    @Transactional
    public Map<String, String> verifyOtpAndSetPassword(String email, String otp, String newPassword) {
        boolean verified = otpService.verifyOtp(email, otp);
        
        if (!verified) {
            throw new BadCredentialsException("Invalid or expired OTP");
        }
        
        // Only the first-login flow sets a password here. Without this check it was a second,
        // unguarded password reset: anyone could request a code for a driver's email via the
        // public /api/auth/otp/send and use it on an established account.
        Driver driver = driverRepository.findByEmail(email)
                .filter(Driver::isFirstLogin)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired OTP"));

        driver.setPassword(passwordEncoder.encode(newPassword));
        driver.setFirstLogin(false);
        driver.setEmailVerified(true);
        tokenVersionService.revokeDriver(driver); // saves; ends any session issued before the password was set
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password set successfully. You can now login with your new password.");
        return response;
    }
    
    /**
     * Public step 1 of driver password reset. Looks up the driver by mobile and dispatches
     * an OTP to the driver's registered email. Returns a generic message regardless of
     * whether the mobile matched, so an attacker can't probe for existing accounts.
     */
    public Map<String, String> sendPasswordResetOtp(String mobile) {
        driverRepository.findByMobile(mobile).ifPresent(driver -> {
            if (driver.getEmail() != null && !driver.getEmail().isBlank()) {
                otpService.sendOtpUnlessRecent(driver.getEmail());
            }
        });
        Map<String, String> response = new HashMap<>();
        response.put("message", "If this mobile is registered, an OTP has been sent to the associated email.");
        return response;
    }

    @Transactional
    public Map<String, String> resetPassword(DriverResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Unknown mobile, no email on file, or a wrong code all read the same, so this public
        // endpoint cannot be used to find out which mobiles belong to drivers.
        Driver driver = driverRepository.findByMobile(request.getMobile())
                .filter(d -> d.getEmail() != null && !d.getEmail().isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));

        // Without the OTP gate, anyone with a driver's mobile number could take over the
        // account. OTP is sent to (and verified against) the driver's registered email.
        if (!otpService.verifyOtp(driver.getEmail(), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        driver.setPassword(passwordEncoder.encode(request.getNewPassword()));
        driver.setFirstLogin(false);
        tokenVersionService.revokeDriver(driver); // saves; a reset ends every existing session

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset successfully");
        return response;
    }
}
