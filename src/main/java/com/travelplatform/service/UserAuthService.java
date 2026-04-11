package com.travelplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.config.JwtUtil;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.dto.UserLoginRequest;
import com.travelplatform.dto.UserSignupRequest;
import com.travelplatform.entity.User;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@Slf4j
public class UserAuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private OtpService otpService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public AuthResponse signup(UserSignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        
        if (!otpService.verifyOtp(request.getEmail(), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getMobile());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setLoginType(User.LoginType.EMAIL);
        user.setRole("ROLE_USER");

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId().toString(), user.getRole());
        AuthResponse response = new AuthResponse(token, user.getRole(), user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setMobile(user.getPhone());
        return response;
    }
    
    public AuthResponse login(UserLoginRequest request) {
        User user;
        
        if (request.getLoginType() == User.LoginType.EMAIL) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid credentials");
            }
        } else if (request.getLoginType() == User.LoginType.GOOGLE) {
            if (request.getToken() == null || request.getToken().isEmpty()) {
                throw new IllegalArgumentException("Google token is required");
            }
            
            String email = extractEmailFromGoogleToken(request.getToken());
            String name = extractNameFromGoogleToken(request.getToken());
            
            user = userRepository.findByEmail(email).orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setName(name != null ? name : "Google User");
                newUser.setLoginType(User.LoginType.GOOGLE);
                newUser.setRole("ROLE_USER");
                return userRepository.save(newUser);
            });
        } else {
            throw new IllegalArgumentException("Invalid login type");
        }
        
        String token = jwtUtil.generateToken(user.getId().toString(), user.getRole());
        AuthResponse response = new AuthResponse(token, user.getRole(), user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setMobile(user.getPhone());
        return response;
    }

    private String extractEmailFromGoogleToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid Google token format");
            }
            String payload = parts[1];
            // Add padding if needed
            int padding = 4 - payload.length() % 4;
            if (padding < 4) payload += "=".repeat(padding);
            String decoded = new String(Base64.getUrlDecoder().decode(payload));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(decoded);
            if (!jsonNode.has("email")) {
                throw new IllegalArgumentException("Email not found in Google token");
            }
            return jsonNode.get("email").asText();
        } catch (Exception e) {
            log.error("Failed to extract email from Google token: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid Google token");
        }
    }
    
    private String extractNameFromGoogleToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "Google User";
            String payload = parts[1];
            int padding = 4 - payload.length() % 4;
            if (padding < 4) payload += "=".repeat(padding);
            String decoded = new String(Base64.getUrlDecoder().decode(payload));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(decoded);
            if (jsonNode.has("name")) return jsonNode.get("name").asText();
            if (jsonNode.has("given_name")) return jsonNode.get("given_name").asText();
            return "Google User";
        } catch (Exception e) {
            log.warn("Failed to extract name from Google token: {}", e.getMessage());
            return "Google User";
        }
    }
}
