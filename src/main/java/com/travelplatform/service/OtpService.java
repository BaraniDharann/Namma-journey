package com.travelplatform.service;

import com.travelplatform.entity.Otp;
import com.travelplatform.repository.OtpRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * One-time codes for signup, driver first login and every password reset.
 *
 * <p>These codes are the only thing between an attacker and an account, so:
 * <ul>
 *   <li>generated with {@link SecureRandom} (java.util.Random is predictable from its output);</li>
 *   <li>dead after {@link #MAX_ATTEMPTS} wrong guesses - a 6-digit code was otherwise guessable
 *       for its whole lifetime;</li>
 *   <li>never logged. The old code logged every guess and, on a miss, the expected code - one
 *       deliberate wrong guess plus log access was an account takeover;</li>
 *   <li>at most one new code per email every {@link #RESEND_COOLDOWN}, so nobody can flood an
 *       inbox or churn codes to dodge the attempt cap.</li>
 * </ul>
 */
@Service
@Slf4j
public class OtpService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private EmailService emailService;

    @Value("${otp.expiry.minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.test.mode:false}")
    private boolean testMode;

    /** An explicit "send me a code" request: refused with an error during the cooldown. */
    @Transactional
    public boolean sendOtp(String emailAddress) {
        String email = normalise(emailAddress);
        if (sentRecently(email)) {
            throw new com.travelplatform.exception.TooManyRequestsException("A code was just sent. Please wait 30 seconds before requesting another.");
        }
        return issue(email);
    }

    /**
     * A code sent as a side effect (driver first login, reset by mobile): during the cooldown it
     * quietly sends nothing - the code already sent is still valid. An error here would break a
     * driver retrying login, and on the reset path would reveal which mobiles have accounts.
     *
     * @return true if a new code was sent
     */
    @Transactional
    public boolean sendOtpUnlessRecent(String emailAddress) {
        String email = normalise(emailAddress);
        return !sentRecently(email) && issue(email);
    }

    private boolean sentRecently(String email) {
        return otpRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .map(Otp::getCreatedAt)
                .filter(created -> created.isAfter(LocalDateTime.now().minus(RESEND_COOLDOWN)))
                .isPresent();
    }

    private boolean issue(String email) {
        String otp = generateOtp();
        Otp otpEntity = new Otp();
        otpEntity.setEmail(email);
        otpEntity.setOtp(otp);
        otpEntity.setCreatedAt(LocalDateTime.now());
        otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        otpRepository.save(otpEntity);

        if (testMode) {
            // Never the code itself: tests read it from the otps table.
            log.warn("OTP test mode: code for {} stored but not emailed", mask(email));
            return true;
        }

        emailService.sendOtpEmail(email, otp, otpExpiryMinutes);
        log.info("OTP sent to {}", mask(email));
        return true;
    }

    /**
     * Checks a code. Runs in its OWN transaction: callers such as signup throw (and roll back)
     * when this returns false, which would otherwise undo the attempt counter and make the cap
     * worthless. The flip side - a correct code is consumed even if the caller then fails - just
     * means requesting a fresh code.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verifyOtp(String emailAddress, String otp) {
        String email = normalise(emailAddress);
        String guess = otp != null ? otp.trim() : "";

        Optional<Otp> found = otpRepository.findTopByEmailAndVerifiedFalseAndExpiryTimeAfterOrderByCreatedAtDesc(
                email, LocalDateTime.now());
        if (found.isEmpty()) {
            log.info("OTP verification for {}: no live code", mask(email));
            return false;
        }

        Otp code = found.get();
        if (code.getAttempts() >= MAX_ATTEMPTS) {
            log.warn("OTP verification for {}: code locked after {} wrong attempts", mask(email), MAX_ATTEMPTS);
            return false;
        }

        if (MessageDigest.isEqual(code.getOtp().getBytes(StandardCharsets.UTF_8), guess.getBytes(StandardCharsets.UTF_8))) {
            code.setVerified(true);
            otpRepository.save(code);
            log.info("OTP verified for {}", mask(email));
            return true;
        }

        code.setAttempts(code.getAttempts() + 1);
        otpRepository.save(code);
        log.warn("OTP verification for {}: wrong code ({}/{})", mask(email), code.getAttempts(), MAX_ATTEMPTS);
        return false;
    }

    private static String generateOtp() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    private static String normalise(String email) {
        return email != null ? email.trim().toLowerCase() : "";
    }

    /** "kavya@example.com" -> "ka***@example.com": enough to correlate, not to harvest. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    @Transactional
    public void cleanupExpiredOtps() {
        otpRepository.deleteByExpiryTimeBefore(LocalDateTime.now());
        log.info("Expired OTPs cleaned up");
    }
}
