package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AdminCreateRequest;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.dto.OwnerLoginRequest;
import com.travelplatform.dto.ResetPasswordRequest;
import com.travelplatform.entity.Owner;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class OwnerAuthService {

    /**
     * Shared secret gating first-run owner creation. Empty by default, which disables the
     * bootstrap endpoint entirely — the safe state for any deployment that already has an owner.
     */
    @Value("${app.bootstrap.owner-secret:}")
    private String bootstrapSecret;

    @Autowired
    private OwnerRepository ownerRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OtpService otpService;
    
    public AuthResponse login(OwnerLoginRequest request) {
        Owner owner = ownerRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found with email: " + request.getEmail()));
        
        if (!passwordEncoder.matches(request.getPassword(), owner.getPassword())) {
            throw new BadCredentialsException("Invalid password");
        }
        
        String token = jwtUtil.generateToken(owner.getId().toString(), owner.getRole());
        AuthResponse response = new AuthResponse(token, owner.getRole(), owner.getId());
        response.setEmail(owner.getEmail());
        response.setName("Owner");
        return response;
    }
    
    /**
     * Bootstraps the single owner account on a brand-new deployment.
     *
     * <p>This endpoint is unauthenticated (it has to be — there is nobody to authenticate as
     * yet) and it hands out an owner token, which is total control of the platform: every
     * booking, every driver, pricing and revenue. The only thing standing between the internet
     * and that token used to be "no owner row exists", which is exactly the state a freshly
     * deployed database is in. Whoever called it first — legitimate operator or a scanner that
     * happened past — became the owner.
     *
     * <p>So it now also requires a secret that only whoever deployed the app knows, and is
     * disabled outright when that secret is not configured. The role is fixed rather than taken
     * from the request; it was being read straight off the wire.
     */
    @Transactional
    public AuthResponse createAdmin(AdminCreateRequest request, String providedSecret) {
        if (bootstrapSecret == null || bootstrapSecret.isBlank()) {
            throw new IllegalStateException(
                    "Owner bootstrap is disabled. Set app.bootstrap.owner-secret to enable it.");
        }
        if (providedSecret == null || !MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                bootstrapSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new BadCredentialsException("Invalid bootstrap secret");
        }

        long ownerCount = ownerRepository.count();
        if (ownerCount >= 1) {
            throw new IllegalArgumentException("Owner already exists. Only one owner is allowed initially.");
        }

        if (ownerRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        Owner admin = new Owner();
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        // Fixed, not request.getRole(): the caller does not get to choose their own authority.
        admin.setRole("ROLE_OWNER");

        Owner savedAdmin = ownerRepository.save(admin);
        String token = jwtUtil.generateToken(savedAdmin.getId().toString(), savedAdmin.getRole());
        AuthResponse response = new AuthResponse(token, savedAdmin.getRole(), savedAdmin.getId());
        response.setEmail(savedAdmin.getEmail());
        response.setName("Owner");
        return response;
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

        Owner owner = ownerRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found with email: " + request.getEmail()));

        owner.setPassword(passwordEncoder.encode(request.getNewPassword()));
        ownerRepository.save(owner);
        
        String token = jwtUtil.generateToken(owner.getId().toString(), owner.getRole());
        AuthResponse response = new AuthResponse(token, owner.getRole(), owner.getId());
        response.setEmail(owner.getEmail());
        response.setName("Owner");
        return response;
    }
}
