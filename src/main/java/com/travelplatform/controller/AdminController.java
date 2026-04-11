package com.travelplatform.controller;

import com.travelplatform.dto.*;
import com.travelplatform.service.AdminDriverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/owner")
public class AdminController {
    
    @Autowired
    private AdminDriverService adminDriverService;
    
    /**
     * ADMIN CREATE DRIVER - Only accessible by OWNER role
     * POST /api/owner/drivers
     * Requires: Bearer token with ROLE_OWNER
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/drivers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverCreationResponse> createDriver(
            @RequestPart("name") String name,
            @RequestPart("mobile") String mobile,
            @RequestPart("email") String email,
            @RequestPart("licenseNumber") String licenseNumber,
            @RequestPart("aadhaarNumber") String aadhaarNumber,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "licensePhoto", required = false) MultipartFile licensePhoto,
            @RequestPart(value = "aadhaarPhoto", required = false) MultipartFile aadhaarPhoto) {
        
        DriverCreationResponse response = adminDriverService.createDriver(
            name, mobile, email, licenseNumber, aadhaarNumber, photo, licensePhoto, aadhaarPhoto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
//    /**
//     * VERIFY DRIVER EMAIL - Owner verifies driver email with OTP
//     * POST /api/owner/drivers/verify
//     * Requires: Bearer token with ROLE_OWNER
//     */
//    @PostMapping("/drivers/verify")
//    @PreAuthorize("hasRole('OWNER')")
//    public ResponseEntity<VerificationResponse> verifyDriverEmail(@Valid @RequestBody OwnerVerifyDriverRequest request) {
//        VerificationResponse response = adminDriverService.verifyDriverEmail(request);
//        return ResponseEntity.ok(response);
//    }
}
