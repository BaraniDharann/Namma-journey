package com.travelplatform.controller;

import com.travelplatform.dto.AssignDriverRequest;
import com.travelplatform.dto.DriverCreationResponse;
import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.dto.ReviewResponse;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.service.AdminDriverService;
import com.travelplatform.service.OwnerService;
import com.travelplatform.service.PaymentService;
import com.travelplatform.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerController {
    
    private final OwnerService ownerService;
    private final AdminDriverService adminDriverService;
    private final ReviewService reviewService;
    private final PaymentService paymentService;
    
    @GetMapping("/bookings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<TravelBooking>> getAllBookings() {
        return ResponseEntity.ok(ownerService.getAllBookings());
    }
    
    @PostMapping("/pricing/set")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> setPricePerKm(
            @RequestParam Double pricePerKm,
            @RequestParam Long ownerId) {
        if (pricePerKm == null || pricePerKm <= 0) {
            throw new IllegalArgumentException("Price per km must be greater than zero");
        }
        return ResponseEntity.ok(ownerService.setPricePerKm(pricePerKm, ownerId));
    }
    
    @GetMapping("/pricing/current")
    @PreAuthorize("hasAnyRole('OWNER','USER','DRIVER')")
    public ResponseEntity<Map<String, Object>> getCurrentPrice() {
        return ResponseEntity.ok(Map.of(
            "pricePerKm", ownerService.getCurrentPricePerKm(),
            "pricePerHour", ownerService.getCurrentPricePerHour()
        ));
    }

    @PostMapping("/pricing/set-hourly")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> setPricePerHour(
            @RequestParam Double pricePerHour,
            @RequestParam Long ownerId) {
        if (pricePerHour == null || pricePerHour <= 0) {
            throw new IllegalArgumentException("Price per hour must be greater than zero");
        }
        return ResponseEntity.ok(ownerService.setPricePerHour(pricePerHour, ownerId));
    }

    @GetMapping("/bookings/{bookingId}/driver-photo")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getDriverPhoto(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ownerService.getDriverPhotoForBooking(bookingId));
    }
    
    @GetMapping("/revenue/daily")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getDailyRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ownerService.getDailyRevenue(date));
    }
    
    @GetMapping("/revenue/monthly")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getMonthlyRevenue(
            @RequestParam int year, 
            @RequestParam int month) {
        return ResponseEntity.ok(ownerService.getMonthlyRevenue(year, month));
    }
    
    @GetMapping("/revenue/yearly")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getYearlyRevenue(@RequestParam int year) {
        return ResponseEntity.ok(ownerService.getYearlyRevenue(year));
    }
    
    @GetMapping("/revenue/trip/{tripId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getTripRevenue(@PathVariable UUID tripId) {
        return ResponseEntity.ok(ownerService.getTripRevenue(tripId));
    }
    
    @PostMapping("/drivers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverCreationResponse> createDriver(
            @Valid @RequestParam String name,
            @RequestParam String mobile,
            @RequestParam String email,
            @RequestParam String licenseNumber,
            @RequestParam String aadhaarNumber,
            @RequestParam(required = false) MultipartFile photo,
            @RequestParam(required = false) MultipartFile licensePhoto,
            @RequestParam(required = false) MultipartFile aadhaarPhoto) {
        DriverCreationResponse response = adminDriverService.createDriver(
            name, mobile, email, licenseNumber, aadhaarNumber,
            photo, licensePhoto, aadhaarPhoto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/reviews")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<ReviewResponse>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }
    
    @GetMapping("/payments/pending")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<PaymentResponse>> getPendingPayments() {
        return ResponseEntity.ok(paymentService.getPendingPayments());
    }
    
    @PostMapping("/payments/{paymentId}/verify")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PaymentResponse> verifyPayment(@PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.verifyPayment(paymentId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/bookings/{bookingId}/assign-driver")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TravelBookingResponse> assignDriver(
            @PathVariable UUID bookingId,
            @Valid @RequestBody AssignDriverRequest request) {
        TravelBookingResponse response = ownerService.assignDriver(bookingId, request.getDriverId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drivers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<DriverDetailsResponse>> getAllDrivers() {
        return ResponseEntity.ok(ownerService.getAllDrivers());
    }

    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> getDriverById(@PathVariable Long driverId) {
        return ResponseEntity.ok(ownerService.getDriverById(driverId));
    }
}
