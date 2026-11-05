package com.travelplatform.service;

import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AdminLoginRequest;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.entity.Admin;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * NammaJourney admins: the people who verify travels owners. The first one is created once with
 * {@code ADMIN_BOOTSTRAP_SECRET}; the endpoint does not exist while that secret is empty, and
 * refuses once any admin exists - the same guard the old owner bootstrap had, for the same reason.
 */
@Service
public class AdminAuthService {

    public static final String ROLE = "ROLE_ADMIN";

    @Value("${app.bootstrap.admin-secret:}")
    private String bootstrapSecret;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Transactional
    public AuthResponse bootstrap(AdminLoginRequest request, String providedSecret) {
        if (bootstrapSecret == null || bootstrapSecret.isBlank()) {
            throw new ResourceNotFoundException("Not found");
        }
        if (providedSecret == null || !MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                bootstrapSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new BadCredentialsException("Invalid bootstrap secret");
        }
        if (adminRepository.count() >= 1) {
            throw new IllegalArgumentException("An admin already exists");
        }

        Admin admin = new Admin();
        admin.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        return tokenFor(adminRepository.save(admin));
    }

    public AuthResponse login(AdminLoginRequest request) {
        // Unknown email and wrong password answer alike, at the same BCrypt cost.
        Admin admin = adminRepository.findByEmailIgnoreCase(request.getEmail().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (!CredentialCheck.matches(passwordEncoder, request.getPassword(), admin == null ? null : admin.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return tokenFor(admin);
    }

    private AuthResponse tokenFor(Admin admin) {
        String token = jwtUtil.generateToken(admin.getId().toString(), ROLE, admin.getTokenVersion());
        AuthResponse response = new AuthResponse(token, ROLE, admin.getId());
        response.setEmail(admin.getEmail());
        response.setName("Admin");
        return response;
    }
}
