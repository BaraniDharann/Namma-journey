package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.dto.OwnerLoginRequest;
import com.travelplatform.dto.ResetPasswordRequest;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerStatus;
import com.travelplatform.exception.OwnerNotApprovedException;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Travels owners signing in. Owners no longer bootstrap themselves: they apply
 * ({@link OwnerSignupService}), an admin approves them ({@link OwnerReviewService}), and the
 * approval email carries their Owner ID and a one-time password.
 */
@Service
public class OwnerAuthService {

    /** Token role for an owner still on the emailed password: it reaches only change-password. */
    public static final String SETUP_ROLE = "ROLE_OWNER_SETUP";

    private static final Pattern OWNER_CODE = Pattern.compile("^NJO-\\d{4,}$", Pattern.CASE_INSENSITIVE);
    private static final int MIN_PASSWORD = 8;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OtpService otpService;

    @Autowired
    private com.travelplatform.security.TokenVersionService tokenVersionService;


    /** Signs in with the Owner ID (NJO-0002) or the email, plus the password. */
    public AuthResponse login(OwnerLoginRequest request) {
        Owner owner = find(request.getEmail()).orElse(null);
        // Same answer for an unknown account and a wrong password, and the same BCrypt cost, so the
        // owner login cannot be used to discover which emails or Owner IDs exist.
        if (!CredentialCheck.matches(passwordEncoder, request.getPassword(), owner == null ? null : owner.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (owner.getStatus() == OwnerStatus.PENDING_REVIEW) {
            throw new OwnerNotApprovedException("Your application is under review");
        }
        if (owner.getStatus() == OwnerStatus.REJECTED) {
            throw new OwnerNotApprovedException("Your application was rejected: " + owner.getRejectionReason());
        }
        return tokenFor(owner);
    }

    /**
     * The owner replaces the emailed one-time password (or simply changes theirs). Every earlier
     * token, the setup token included, stops working.
     */
    @Transactional
    public AuthResponse changePassword(Long ownerId, String currentPassword, String newPassword) {
        Owner owner = ownerRepository.findById(ownerId).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!CredentialCheck.matches(passwordEncoder, currentPassword, owner.getPassword())) {
            // 400, not 401: the web app reads 401 as "session expired" and would sign the owner out.
            throw new IllegalArgumentException("Current password is wrong");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Choose a password of at least " + MIN_PASSWORD + " characters");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("Choose a password different from the one we emailed");
        }
        owner.setPassword(passwordEncoder.encode(newPassword));
        owner.setMustChangePassword(false);
        tokenVersionService.revokeOwner(owner); // saves
        return tokenFor(owner);
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // Without the OTP gate, anyone who knew the owner's email could take over the
        // account. Verify the OTP against the email before mutating the password.
        if (!otpService.verifyOtp(request.getEmail(), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        // A valid code for an email with no approved owner reads exactly like a bad code.
        Owner owner = ownerRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .filter(o -> o.getStatus() == OwnerStatus.APPROVED)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));

        owner.setPassword(passwordEncoder.encode(request.getNewPassword()));
        owner.setMustChangePassword(false);
        tokenVersionService.revokeOwner(owner); // saves; ends every session issued before the reset
        return tokenFor(owner);
    }

    private Optional<Owner> find(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        String id = login.trim();
        return OWNER_CODE.matcher(id).matches()
                ? ownerRepository.findByOwnerCode(id.toUpperCase(Locale.ROOT))
                : ownerRepository.findByEmailIgnoreCase(id.toLowerCase(Locale.ROOT));
    }

    private AuthResponse tokenFor(Owner owner) {
        String role = owner.isMustChangePassword() ? SETUP_ROLE : owner.getRole();
        String token = jwtUtil.generateToken(owner.getId().toString(), role, owner.getTokenVersion());
        AuthResponse response = new AuthResponse(token, role, owner.getId());
        response.setEmail(owner.getEmail());
        response.setName(owner.getTravelsName() == null ? "Owner" : owner.getTravelsName());
        response.setOwnerCode(owner.getOwnerCode());
        response.setMustChangePassword(owner.isMustChangePassword());
        return response;
    }
}
