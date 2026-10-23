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
            @RequestPart(value = "aadhaarPhoto", required = false) MultipartFile aadhaarPhoto,
            @RequestPart(value = "vehicleType", required = false) String vehicleType,
            @RequestPart(value = "vehicleNumber", required = false) String vehicleNumber,
            @RequestPart(value = "vehicleModel", required = false) String vehicleModel,
            @RequestPart(value = "vehicleAc", required = false) String vehicleAc,
            @RequestPart(value = "vehicleSeats", required = false) String vehicleSeats,
            @RequestPart(value = "servesCity", required = false) String servesCity,
            @RequestPart(value = "servesTrip", required = false) String servesTrip) {

        // Multipart text parts arrive as strings; blank means "use the default".
        com.travelplatform.dto.VehicleRequest vehicle = new com.travelplatform.dto.VehicleRequest(
                vehicleType, vehicleNumber, vehicleModel, flag(vehicleAc), seats(vehicleSeats),
                flag(servesCity), flag(servesTrip));
        DriverCreationResponse response = adminDriverService.createDriver(
            name, mobile, email, licenseNumber, aadhaarNumber, photo, licensePhoto, aadhaarPhoto, vehicle);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static Boolean flag(String raw) {
        return raw == null || raw.isBlank() ? null : Boolean.valueOf(raw.trim());
    }

    private static Integer seats(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seats must be a number");
        }
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
