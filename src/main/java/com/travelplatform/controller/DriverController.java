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

    @GetMapping("/{driverId}/bookings")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<TravelBookingResponse>> getAssignedBookings(@PathVariable Long driverId) {
        return ResponseEntity.ok(driverService.getAssignedBookings(driverId));
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/action")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<TravelBookingResponse> handleBookingAction(
            @PathVariable Long driverId,
            @PathVariable String bookingId,
            @RequestBody BookingActionRequest request) {

        if ("ACCEPT".equalsIgnoreCase(request.getAction())) {
            return ResponseEntity.ok(driverService.acceptBooking(driverId, bookingId));
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            return ResponseEntity.ok(driverService.rejectBooking(driverId, bookingId));
        } else {
            throw new RuntimeException("Invalid action. Use ACCEPT or REJECT");
        }
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/end-trip")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<PaymentResponse> endTripAndGenerateQr(
            @PathVariable Long driverId,
            @PathVariable String bookingId) {
        PaymentResponse response = paymentService.generateTripEndQr(driverId, UUID.fromString(bookingId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/end-trip-photo")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<Map<String, Object>> uploadEndTripPhoto(
            @PathVariable Long driverId,
            @PathVariable String bookingId,
            @RequestParam("photo") MultipartFile photo) {
        var saved = driverService.uploadEndTripPhoto(driverId, UUID.fromString(bookingId), photo);
        return ResponseEntity.ok(Map.of(
            "message", "Photo uploaded successfully",
            "photoPath", saved.getPhotoPath(),
            "capturedAt", saved.getCapturedAt().toString()
        ));
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/cash-payment")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<PaymentResponse> markCashReceived(
            @PathVariable Long driverId,
            @PathVariable String bookingId,
            @RequestBody CashPaymentRequest request) {
        PaymentResponse response = paymentService.markCashReceived(driverId,
                UUID.fromString(bookingId), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{driverId}/profile")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverDetailsResponse> getDriverProfile(@PathVariable Long driverId) {
        return ResponseEntity.ok(driverService.getDriverProfile(driverId));
    }

    @PostMapping("/{driverId}/bookings/{bookingId}/start-trip")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<TravelBookingResponse> startTrip(
            @PathVariable Long driverId,
            @PathVariable String bookingId) {
        return ResponseEntity.ok(driverService.startTrip(driverId, bookingId));
    }

    @GetMapping("/location/{bookingId}")
    @PreAuthorize("hasRole('DRIVER') or hasRole('USER')")
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
        return ResponseEntity.ok(locationTrackingService.updateLocation(dto));
    }

    @PutMapping("/{driverId}/availability")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<java.util.Map<String, String>> toggleAvailability(
            @PathVariable Long driverId,
            @RequestBody java.util.Map<String, String> request) {
        String status = request.get("status");
        driverService.updateAvailability(driverId, status);
        return ResponseEntity.ok(java.util.Map.of("message", "Availability updated", "status", status));
    }
}
