package com.travelplatform.service;

import com.travelplatform.validation.IndianFormats;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Owner;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Edits a driver or owner makes to their own Profile (travellers use UserService#updateProfile).
 *
 * <p>Plain contact details change freely. Anything that moves money or a login - an owner's bank
 * and UPI, a driver's mobile (their login ID) or email (where reset codes go) - needs the current
 * password, so a session left open on a shared phone is not enough to take the account over.
 */
@Service
public class ProfileEditService {


    private final OwnerRepository owners;
    private final DriverRepository drivers;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public ProfileEditService(OwnerRepository owners, DriverRepository drivers, PasswordEncoder passwordEncoder,
                              EmailService emailService) {
        this.owners = owners;
        this.drivers = drivers;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // ---------------------------------------------------------------- owner

    public record OwnerDetails(String travelsName, String contactName, String phone) {
    }

    public record OwnerPayout(String currentPassword, String bankAccountHolder, String bankAccountNumber,
                              String bankIfsc, String bankName, String upiId) {
    }

    @Transactional
    public void updateOwnerDetails(Long ownerId, OwnerDetails req) {
        Owner owner = owner(ownerId);
        String travelsName = IndianFormats.requireBusinessName(req.travelsName(), "Travels name");
        String contactName = IndianFormats.requireName(req.contactName(), "Contact person's name");
        String phone = IndianFormats.requireMobile(req.phone());
        owner.setTravelsName(limit(travelsName, 255, "Travels name"));
        owner.setContactName(limit(contactName, 255, "Contact person's name"));
        owner.setPhone(phone);
        owners.save(owner);
    }

    @Transactional
    public void updateOwnerPayout(Long ownerId, OwnerPayout req) {
        Owner owner = owner(ownerId);
        requirePassword(req.currentPassword(), owner.getPassword());

        String holder = IndianFormats.requireName(req.bankAccountHolder(), "Bank account holder name");
        String account = IndianFormats.requireAccount(req.bankAccountNumber());
        String ifsc = IndianFormats.requireIfsc(req.bankIfsc());
        String bankName = IndianFormats.requireBusinessName(req.bankName(), "Bank name");
        String upi = IndianFormats.requireUpi(req.upiId());

        owner.setBankAccountHolder(limit(holder, 255, "Bank account holder name"));
        owner.setBankAccountNumber(account);
        owner.setBankIfsc(ifsc);
        owner.setBankName(limit(bankName, 255, "Bank name"));
        owner.setUpiId(upi);
        owners.save(owner);
        emailService.sendOwnerPayoutDetailsChanged(owner.getEmail(), owner.getTravelsName());
    }

    // ---------------------------------------------------------------- driver

    public record DriverDetails(String name, String mobile, String email, String currentPassword) {
    }

    @Transactional
    public void updateDriverDetails(Long driverId, DriverDetails req) {
        Driver driver = drivers.findById(driverId)
                .filter(d -> d.getClosedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));

        String name = IndianFormats.requireName(req.name(), "Name");
        String mobile = IndianFormats.requireMobile(req.mobile());
        String email = req.email() == null || req.email().isBlank() ? null : IndianFormats.requireEmail(req.email());
        if (email == null && driver.getEmail() != null) {
            // Password reset and first-login codes go to this address; it can be changed, not removed.
            throw new IllegalArgumentException("Email is needed for password reset codes");
        }

        boolean mobileChanged = !mobile.equals(driver.getMobile());
        boolean emailChanged = !Objects.equals(email, driver.getEmail());
        if (mobileChanged || emailChanged) {
            requirePassword(req.currentPassword(), driver.getPassword());
        }
        if (mobileChanged && drivers.findByMobile(mobile).filter(o -> !o.getId().equals(driverId)).isPresent()) {
            throw new IllegalArgumentException("That mobile number is already registered to another driver");
        }
        if (emailChanged && drivers.findByEmail(email).filter(o -> !o.getId().equals(driverId)).isPresent()) {
            throw new IllegalArgumentException("That email is already registered to another driver");
        }

        driver.setName(limit(name, 255, "Name"));
        driver.setMobile(mobile);
        driver.setEmail(email);
        drivers.save(driver);
    }

    // ---------------------------------------------------------------- helpers

    private Owner owner(Long ownerId) {
        return owners.findById(ownerId)
                .filter(o -> o.getClosedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));
    }

    private void requirePassword(String given, String stored) {
        // 400, not 401: the web app reads 401 as "session expired" and would sign the person out.
        if (given == null || given.isBlank() || !CredentialCheck.matches(passwordEncoder, given, stored)) {
            throw new IllegalArgumentException("Current password is wrong");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String limit(String value, int max, String label) {
        if (value.length() > max) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return value;
    }
}
