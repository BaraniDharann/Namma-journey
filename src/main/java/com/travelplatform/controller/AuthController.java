package com.travelplatform.controller;

import com.travelplatform.dto.*;
import com.travelplatform.service.AuthService;
import com.travelplatform.service.DriverAuthService;
import com.travelplatform.service.OtpService;
import com.travelplatform.service.OwnerAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private OtpService otpService;
    
    @Autowired
    private DriverAuthService driverAuthService;
    
    @Autowired
    private OwnerAuthService ownerAuthService;

    @Autowired
    private com.travelplatform.service.DriverSignupService driverSignupService;

    @Autowired
    private com.travelplatform.security.RevokedTokenStore revokedTokens;

    @Autowired
    private com.travelplatform.config.JwtUtil jwtUtil;
    
    /**
     * SEND OTP - Send OTP to email
     * POST /api/auth/otp/send
     */
    /**
     * POST /api/auth/logout - ends THIS session, server-side.
     *
     * <p>Logging out used to only delete the token from the browser; a copy taken earlier (shared
     * computer, XSS) kept working for its whole lifetime. The token's own id is now revoked until
     * it would have expired. Only this session ends: logging out on a phone must not sign the same
     * person out of their laptop (an earlier version did exactly that by bumping the account's
     * token_version, which is reserved for password resets). Always 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            if (jwtUtil.validateToken(token)) {
                revokedTokens.revoke(jwtUtil.extractJti(token), jwtUtil.extractExpiry(token));
            }
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/send")
    public ResponseEntity<OtpResponse> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        boolean sent = otpService.sendOtp(request.getEmail());
        if (sent) {
            return ResponseEntity.ok(new OtpResponse("OTP sent successfully to email", true));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new OtpResponse("Failed to send OTP", false));
    }
    
    /**
     * USER SIGNUP - Phone-based registration
     * POST /api/auth/user/signup
     */
    @PostMapping("/user/signup")
    public ResponseEntity<AuthResponse> userSignup(@Valid @RequestBody UserSignupRequest request) {
        AuthResponse response = authService.userSignup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * USER LOGIN - Google OAuth or Phone OTP
     * POST /api/auth/user/login
     */
    @PostMapping("/user/login")
    public ResponseEntity<AuthResponse> userLogin(@Valid @RequestBody UserLoginRequest request) {
        AuthResponse response = authService.userLogin(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * DRIVER LOGIN - Mobile + Password authentication
     * POST /api/auth/driver/login
     */
    @PostMapping("/driver/login")
    public ResponseEntity<Map<String, Object>> driverLogin(@Valid @RequestBody DriverLoginRequest request) {
        Map<String, Object> response = driverAuthService.login(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * DRIVER VERIFY OTP AND SET PASSWORD - After first login
     * POST /api/auth/driver/verify-otp
     */
    @PostMapping("/driver/verify-otp")
    public ResponseEntity<Map<String, String>> driverVerifyOtp(@Valid @RequestBody DriverSetPasswordRequest request) {
        Map<String, String> response = driverAuthService.verifyOtpAndSetPassword(
            request.getEmail(), 
            request.getOtp(), 
            request.getNewPassword()
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * OWNER LOGIN - Email + Password authentication
     * POST /api/auth/owner/login
     */
    @PostMapping("/owner/login")
    public ResponseEntity<AuthResponse> ownerLogin(@Valid @RequestBody OwnerLoginRequest request) {
        AuthResponse response = authService.ownerLogin(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * CREATE ADMIN - Owner creates admin account
     * POST /api/auth/owner/create-admin
     */
    @PostMapping("/owner/create-admin")
    public ResponseEntity<AuthResponse> createAdmin(
            @Valid @RequestBody AdminCreateRequest request,
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String bootstrapSecret) {
        AuthResponse response = authService.createAdmin(request, bootstrapSecret);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * OWNER FORGOT PASSWORD - Reset password for owner
     * POST /api/auth/owner/forgot-password
     */
    @PostMapping("/owner/forgot-password")
    public ResponseEntity<AuthResponse> ownerForgotPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AuthResponse response = ownerAuthService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * DRIVER FORGOT PASSWORD - Reset password for driver
     * POST /api/auth/driver/forgot-password
     */
    @PostMapping("/driver/forgot-password")
    public ResponseEntity<Map<String, String>> driverForgotPassword(@Valid @RequestBody DriverResetPasswordRequest request) {
        Map<String, String> response = driverAuthService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    /**
     * DRIVER REQUEST RESET OTP - Send an OTP to the driver's registered email,
     * looked up via the supplied mobile. Returns a generic message so this can't
     * be used to enumerate registered drivers.
     * POST /api/auth/driver/request-reset-otp
     */
    @PostMapping("/driver/request-reset-otp")
    public ResponseEntity<Map<String, String>> driverRequestResetOtp(@RequestBody Map<String, String> body) {
        String mobile = body != null ? body.get("mobile") : null;
        if (mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("Mobile is required");
        }
        return ResponseEntity.ok(driverAuthService.sendPasswordResetOtp(mobile));
    }

    /**
     * DRIVER SELF SIGN-UP - details and four document images; the owner reviews afterwards.
     * POST /api/auth/driver/signup
     */
    @PostMapping(value = "/driver/signup", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> driverSignup(
            @ModelAttribute DriverSelfSignupForm form,
            @RequestPart(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo,
            @RequestPart(value = "licensePhoto", required = false) org.springframework.web.multipart.MultipartFile licensePhoto,
            @RequestPart(value = "aadhaarPhoto", required = false) org.springframework.web.multipart.MultipartFile aadhaarPhoto,
            @RequestPart(value = "rcPhoto", required = false) org.springframework.web.multipart.MultipartFile rcPhoto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverSignupService.signup(form, photo, licensePhoto, aadhaarPhoto, rcPhoto));
    }

    /**
     * DRIVER SIGN-UP EMAIL CHECK - confirms the emailed code and signs the driver in.
     * POST /api/auth/driver/signup/verify-email
     */
    @PostMapping("/driver/signup/verify-email")
    public ResponseEntity<Map<String, Object>> driverSignupVerifyEmail(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(driverSignupService.verifyEmail(body.get("email"), body.get("otp")));
    }
}
