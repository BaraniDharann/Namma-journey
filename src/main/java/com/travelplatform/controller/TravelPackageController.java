package com.travelplatform.controller;

import com.travelplatform.dto.*;
import com.travelplatform.service.PackageBookingService;
import com.travelplatform.service.TravelPackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TravelPackageController {

    private final TravelPackageService packageService;
    private final PackageBookingService bookingService;

    // ==================== PUBLIC ENDPOINTS ====================

    @GetMapping("/api/public/packages")
    public ResponseEntity<List<TravelPackageResponse>> getActivePackages(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String state) {
        List<TravelPackageResponse> packages;
        if (category != null && state != null) {
            packages = packageService.getPackagesByCategoryAndState(category, state);
        } else if (category != null) {
            packages = packageService.getPackagesByCategory(category);
        } else if (state != null) {
            packages = packageService.getPackagesByState(state);
        } else {
            packages = packageService.getAllActivePackages();
        }
        return ResponseEntity.ok(packages);
    }

    @GetMapping("/api/public/packages/{id}")
    public ResponseEntity<TravelPackageResponse> getPackageById(@PathVariable Long id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    // ==================== USER ENDPOINTS ====================

    @PostMapping("/api/user/{userId}/package-bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PackageBookingResponse> bookPackage(
            @PathVariable UUID userId,
            @Valid @RequestBody PackageBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(userId, request));
    }

    @GetMapping("/api/user/{userId}/package-bookings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PackageBookingResponse>> getUserPackageBookings(@PathVariable UUID userId) {
        return ResponseEntity.ok(bookingService.getUserBookings(userId));
    }

    @PostMapping("/api/user/{userId}/package-bookings/{bookingId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PackageBookingResponse> cancelUserPackageBooking(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId, reason));
    }

    // ==================== OWNER ENDPOINTS ====================

    @GetMapping("/api/owner/packages")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<TravelPackageResponse>> getAllPackages() {
        return ResponseEntity.ok(packageService.getAllPackages());
    }

    @PostMapping("/api/owner/packages")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TravelPackageResponse> createPackage(
            @Valid @RequestBody TravelPackageRequest request,
            @RequestParam Long ownerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(packageService.createPackage(request, ownerId));
    }

    @PutMapping("/api/owner/packages/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TravelPackageResponse> updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody TravelPackageRequest request) {
        return ResponseEntity.ok(packageService.updatePackage(id, request));
    }

    @PostMapping("/api/owner/packages/{id}/toggle")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> togglePackage(@PathVariable Long id) {
        packageService.togglePackageStatus(id);
        return ResponseEntity.ok(Map.of("message", "Package status toggled"));
    }

    @DeleteMapping("/api/owner/packages/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> deletePackage(@PathVariable Long id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok(Map.of("message", "Package deleted"));
    }

    @GetMapping("/api/owner/package-bookings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<PackageBookingResponse>> getAllPackageBookings(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(bookingService.getBookingsByStatus(status));
        }
        return ResponseEntity.ok(bookingService.getAllBookings());
    }

    @PostMapping("/api/owner/package-bookings/{bookingId}/confirm")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PackageBookingResponse> confirmBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.confirmBooking(bookingId));
    }

    @PostMapping("/api/owner/package-bookings/{bookingId}/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PackageBookingResponse> cancelBooking(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : "Cancelled by owner";
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId, reason));
    }

    @PostMapping("/api/owner/package-bookings/{bookingId}/complete")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PackageBookingResponse> completeBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.completeBooking(bookingId));
    }
}
