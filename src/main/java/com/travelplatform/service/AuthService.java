package com.travelplatform.service;

import com.travelplatform.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    
    @Autowired
    private UserAuthService userAuthService;
    
    @Autowired
    private DriverAuthService driverAuthService;
    
    @Autowired
    private OwnerAuthService ownerAuthService;
    
    public AuthResponse userSignup(UserSignupRequest request) {
        return userAuthService.signup(request);
    }
    
    public AuthResponse userLogin(UserLoginRequest request) {
        return userAuthService.login(request);
    }
    
    public AuthResponse ownerLogin(OwnerLoginRequest request) {
        return ownerAuthService.login(request);
    }
    
    public AuthResponse createAdmin(AdminCreateRequest request) {
        return ownerAuthService.createAdmin(request);
    }
}
