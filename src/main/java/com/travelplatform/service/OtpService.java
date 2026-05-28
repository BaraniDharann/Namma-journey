package com.travelplatform.service;

import com.travelplatform.entity.Otp;
import com.travelplatform.repository.OtpRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
public class OtpService {

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private EmailService emailService;

    @Value("${otp.expiry.minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.test.mode:false}")
    private boolean testMode;

    @Transactional
    public boolean sendOtp(String emailAddress) {
        String trimmedEmail = emailAddress != null ? emailAddress.trim().toLowerCase() : "";
        String otp = generateOtp();

        log.info("Generating OTP for email: {}", trimmedEmail);

        Otp otpEntity = new Otp();
        otpEntity.setEmail(trimmedEmail);
        otpEntity.setOtp(otp);
        otpEntity.setCreatedAt(LocalDateTime.now());
        otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        Otp savedOtp = otpRepository.save(otpEntity);

        log.info("OTP saved to DB - ID: {}, Email: {}, Expiry: {}",
            savedOtp.getId(), savedOtp.getEmail(), savedOtp.getExpiryTime());

        if (testMode) {
            log.warn("\n=================================================\n🔐 TEST MODE - OTP FOR: {}\n📧 OTP CODE: {}\n⏰ Valid for {} minutes\n=================================================\n",
                trimmedEmail, otp, otpExpiryMinutes);
            return true;
        }

        emailService.sendOtpEmail(trimmedEmail, otp, otpExpiryMinutes);
        return true;
    }

    @Transactional
    public boolean verifyOtp(String emailAddress, String otp) {
        String trimmedEmail = emailAddress != null ? emailAddress.trim().toLowerCase() : "";
        String trimmedOtp = otp != null ? otp.trim() : "";

        log.info("Verifying OTP for email: {}, provided OTP: {}", trimmedEmail, trimmedOtp);

        Optional<Otp> otpOpt = otpRepository.findTopByEmailAndVerifiedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
            trimmedEmail, LocalDateTime.now());

        if (otpOpt.isEmpty()) {
            log.warn("No valid OTP found for email: {} at time: {}", trimmedEmail, LocalDateTime.now());
            return false;
        }

        Otp otpEntity = otpOpt.get();
        if (otpEntity.getOtp().equals(trimmedOtp)) {
            otpEntity.setVerified(true);
            otpRepository.save(otpEntity);
            log.info("OTP verified successfully for email: {}", trimmedEmail);
            return true;
        }

        log.warn("Invalid OTP for email: {}. Expected: {}, Got: {}", trimmedEmail, otpEntity.getOtp(), trimmedOtp);
        return false;
    }

    private String generateOtp() {
        int otp = 100000 + new Random().nextInt(900000);
        return String.valueOf(otp);
    }

    @Transactional
    public void cleanupExpiredOtps() {
        otpRepository.deleteByExpiryTimeBefore(LocalDateTime.now());
        log.info("Expired OTPs cleaned up");
    }
}
