package com.travelplatform.controller;

import com.travelplatform.dto.BookingActionRequest;
import com.travelplatform.dto.CashPaymentRequest;
import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.DriverLocationDTO;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.service.DriverService;
import com.travelplatform.service.LocationTrackingService;
import com.travelplatform.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/driver")
public class DriverController {

    @Autowired
    private DriverService driverService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private LocationTrackingService locationTrackingService;

    // SpEL guard reused on per-driver endpoints. Without it any logged-in driver could read
    // or mutate another driver's bookings simply by changing the URL.
    private static final String SAME_DRIVER = "hasRole('DRIVER') and #driverId.toString() == authentication.principal";

    @GetMapping("/{driverId}/bookings")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<List<TravelBookingResponse>> getAssignedBookings(@PathVariable Long driverId) {
        return ResponseEntity.ok(driverService.getAssignedBookings(driverId));
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/action")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<TravelBookingResponse> handleBookingAction(
            @PathVariable Long driverId,
            @PathVariable String bookingId,
            @RequestBody(required = false) BookingActionRequest request) {

        String action = request != null ? request.getAction() : null;
        if ("ACCEPT".equalsIgnoreCase(action)) {
            return ResponseEntity.ok(driverService.acceptBooking(driverId, bookingId));
        } else if ("REJECT".equalsIgnoreCase(action)) {
            return ResponseEntity.ok(driverService.rejectBooking(driverId, bookingId));
        } else {
            throw new IllegalArgumentException("Invalid action. Use ACCEPT or REJECT");
        }
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/end-trip")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<PaymentResponse> endTripAndGenerateQr(
            @PathVariable Long driverId,
            @PathVariable String bookingId) {
        PaymentResponse response = paymentService.generateTripEndQr(driverId, UUID.fromString(bookingId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/end-trip-photo")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, Object>> uploadEndTripPhoto(
            @PathVariable Long driverId,
            @PathVariable String bookingId,
            @RequestParam("photo") MultipartFile photo) {
        var saved = driverService.uploadEndTripPhoto(driverId, UUID.fromString(bookingId), photo);
        // capturedAt is set by @CreationTimestamp at flush; on rare flushes that haven't happened
        // yet (or legacy rows without it) we don't want a serialization NPE to mask the success.
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("message", "Photo uploaded successfully");
        body.put("photoPath", saved.getPhotoPath());
        body.put("capturedAt", saved.getCapturedAt() != null ? saved.getCapturedAt().toString() : null);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/cash-payment")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<PaymentResponse> markCashReceived(
            @PathVariable Long driverId,
            @PathVariable String bookingId,
            @RequestBody CashPaymentRequest request) {
        PaymentResponse response = paymentService.markCashReceived(driverId,
                UUID.fromString(bookingId), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{driverId}/profile")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<DriverDetailsResponse> getDriverProfile(@PathVariable Long driverId) {
        return ResponseEntity.ok(driverService.getDriverProfile(driverId));
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/start-trip")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<TravelBookingResponse> startTrip(
            @PathVariable Long driverId,
            @PathVariable String bookingId) {
        return ResponseEntity.ok(driverService.startTrip(driverId, bookingId));
    }

    @GetMapping("/location/{bookingId}")
    // Only the booking's customer, its assigned driver, or an owner may read the
    // live location — matches the WebSocket topic guard so both polling and push
    // enforce the same rule.
    @PreAuthorize("@bookingAccessGuard.canAccess(authentication, #bookingId)")
    public ResponseEntity<DriverLocationDTO> getDriverLocation(@PathVariable String bookingId) {
        DriverLocationDTO location = locationTrackingService.getLocation(bookingId);
        if (location == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(location);
    }

    @PostMapping("/location/update")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverLocationDTO> updateLocationRest(@RequestBody DriverLocationDTO dto) {
        // The dto carries its own driverId; we just confirm it matches the JWT subject so a
        // driver can't post location updates impersonating someone else.
        String principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        if (dto == null || dto.getDriverId() == null || !principal.equals(dto.getDriverId().toString())) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot update location for another driver");
        }
        return ResponseEntity.ok(locationTrackingService.updateLocation(dto));
    }

    @PutMapping("/{driverId}/availability")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<java.util.Map<String, String>> toggleAvailability(
            @PathVariable Long driverId,
            @RequestBody(required = false) java.util.Map<String, String> request) {
        String status = request != null ? request.get("status") : null;
        driverService.updateAvailability(driverId, status);
        return ResponseEntity.ok(java.util.Map.of("message", "Availability updated", "status", status));
    }
}
