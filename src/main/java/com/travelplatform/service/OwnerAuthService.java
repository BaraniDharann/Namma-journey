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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerAuthService {
    
    @Autowired
    private OwnerRepository ownerRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;
    
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
    
    @Transactional
    public AuthResponse createAdmin(AdminCreateRequest request) {
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
        admin.setRole(request.getRole());
        
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
