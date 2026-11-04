package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.DriverVerificationCheck;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.DriverVerificationCheckRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A travels owner's review of a driver they added: the licence checked on Sarathi and the RC on
 * Vahan / mParivahan (both free), recorded here. Approval needs both VERIFIED and emails the
 * driver their login. Self-signup (independent) drivers are the platform's and are not reviewed here.
 */
@Service
public class OwnerDriverReviewService {

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final DriverRepository drivers;
    private final DriverVerificationCheckRepository checks;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final OwnerScope ownerScope = new OwnerScope();
    private final SecureRandom random = new SecureRandom();

    public OwnerDriverReviewService(DriverRepository drivers, DriverVerificationCheckRepository checks,
                                    EmailService emailService, PasswordEncoder passwordEncoder) {
        this.drivers = drivers;
        this.checks = checks;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<DriverVerificationCheck> checksFor(Long driverId) {
        mine(driverId);
        return checks.findByDriverId(driverId);
    }

    @Transactional
    public DriverVerificationCheck recordCheck(Long driverId, DriverVerificationCheck.Type type,
                                               DriverVerificationCheck.Result result, String note) {
        mine(driverId);
        if (type == null || result == null) {
            throw new IllegalArgumentException("Say which check and whether it passed");
        }
        String trimmed = note == null ? "" : note.trim();
        if (result == DriverVerificationCheck.Result.FAILED && trimmed.isEmpty()) {
            throw new IllegalArgumentException("Say what was wrong");
        }
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("Keep the note under 500 characters");
        }
        DriverVerificationCheck c = new DriverVerificationCheck();
        c.setDriverId(driverId);
        c.setType(type);
        c.setResult(result);
        c.setNote(trimmed.isEmpty() ? null : trimmed);
        c.setCheckedByOwnerId(ownerScope.ownerId());
        c.setCheckedAt(LocalDateTime.now());
        return checks.save(c);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public Driver approve(Long driverId) {
        Driver d = mine(driverId);
        if (d.getVerificationStatus() == Driver.Verification.APPROVED) {
            throw new ConflictException(d.getName() + " is already approved.");
        }
        Set<DriverVerificationCheck.Type> verified = EnumSet.noneOf(DriverVerificationCheck.Type.class);
        for (DriverVerificationCheck c : checks.findByDriverId(driverId)) {
            if (c.getResult() == DriverVerificationCheck.Result.VERIFIED) {
                verified.add(c.getType());
            }
        }
        if (!verified.containsAll(EnumSet.allOf(DriverVerificationCheck.Type.class))) {
            throw new ConflictException("Check the licence on Sarathi and the RC on Vahan, and mark both verified first.");
        }
        // The login is made and sent now, not when the driver was added: nobody drives unchecked.
        String password = newPassword();
        d.setPassword(passwordEncoder.encode(password));
        d.setFirstLogin(true);
        d.setVerificationStatus(Driver.Verification.APPROVED);
        d.setRejectionReason(null);
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewedBy(ownerScope.ownerId());
        Driver saved = drivers.save(d);
        emailService.sendDriverCredentials(saved.getEmail(), saved.getName(), saved.getMobile(), password);
        return saved;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public Driver reject(Long driverId, String reason) {
        Driver d = mine(driverId);
        String r = reason == null ? "" : reason.trim();
        if (r.length() < 5 || r.length() > 500) {
            throw new IllegalArgumentException("Give a reason of 5 to 500 characters");
        }
        d.setVerificationStatus(Driver.Verification.REJECTED);
        d.setRejectionReason(r);
        d.setReviewedAt(LocalDateTime.now());
        d.setReviewedBy(ownerScope.ownerId());
        return drivers.save(d);
    }

    /** Whether this driver is in the calling owner's own fleet. */
    public boolean isMine(Long driverId) {
        Long me = ownerScope.ownerId();
        return drivers.findById(driverId).map(d -> me.equals(d.getOwnerId())).orElse(false);
    }

    /** One of my fleet drivers, or "not found". */
    private Driver mine(Long driverId) {
        Long me = ownerScope.ownerId();
        return drivers.findById(driverId).filter(d -> me.equals(d.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
    }

    private String newPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
