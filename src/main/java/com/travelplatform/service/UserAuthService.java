package com.travelplatform.service;

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

    @Autowired
    private GoogleTokenVerifier googleTokenVerifier;

    public AuthResponse signup(UserSignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        // Mobile is also a unique column in `users`; checking up-front gives a friendly
        // message instead of bubbling a DataIntegrityViolationException (which the
        // GlobalExceptionHandler turns into a generic 409 the user can't act on).
        if (request.getMobile() != null && !request.getMobile().isBlank()
                && userRepository.existsByPhone(request.getMobile())) {
            throw new IllegalArgumentException("Mobile number already registered");
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
            // Signature, audience, issuer and expiry are all checked here. Anything short of that
            // and the token is just attacker-supplied JSON naming whichever account they like.
            GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(request.getToken());
            final String email = identity.email();
            final String name = identity.name();

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

}
