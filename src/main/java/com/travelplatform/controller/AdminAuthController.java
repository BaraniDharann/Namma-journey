package com.travelplatform.controller;

import com.travelplatform.dto.AdminLoginRequest;
import com.travelplatform.dto.AuthResponse;
import com.travelplatform.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin sign-in. Lives under /api/auth, so it gets the tight per-IP auth rate limit. */
@RestController
@RequestMapping("/api/auth/admin")
public class AdminAuthController {

    @Autowired
    private AdminAuthService adminAuthService;

    /** One-time creation of the first admin. Header X-Bootstrap-Secret = ADMIN_BOOTSTRAP_SECRET. */
    @PostMapping("/bootstrap")
    public ResponseEntity<AuthResponse> bootstrap(
            @Valid @RequestBody AdminLoginRequest request,
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String secret) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminAuthService.bootstrap(request, secret));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }
}
