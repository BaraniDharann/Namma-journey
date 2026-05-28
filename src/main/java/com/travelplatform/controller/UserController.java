package com.travelplatform.controller;

import com.travelplatform.dto.*;
import com.travelplatform.service.PaymentService;
import com.travelplatform.service.ReviewService;
import com.travelplatform.service.RoutingService;
import com.travelplatform.service.TripSummaryService;
import com.travelplatform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ReviewService reviewService;
    private final PaymentService paymentService;
    private final TripSummaryService tripSummaryService;
    private final RoutingService routingService;

    // SpEL guard reused on every per-user endpoint: the authenticated principal (JWT subject)
    // must match the {userId} path variable, otherwise a logged-in user could read or mutate
    // any other user's data simply by changing the URL.
    private static final String SAME_USER = "hasRole('USER') and #userId.toString() == authentication.principal";

    @PostMapping("/{userId}/bookings")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<TravelBookingResponse> createBooking(
            @PathVariable UUID userId,
            @Valid @RequestBody TravelBookingRequest request) {
        TravelBookingResponse response = userService.createBooking(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}/bookings")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<List<TravelBookingResponse>> getAllBookings(@PathVariable UUID userId) {
        List<TravelBookingResponse> bookings = userService.getAllBookings(userId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/{userId}/bookings/{bookingId}")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<TravelBookingResponse> getBookingById(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId) {
        TravelBookingResponse booking = userService.getBookingById(bookingId, userId);
        return ResponseEntity.ok(booking);
    }

    @PutMapping("/{userId}/bookings/{bookingId}")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<TravelBookingResponse> updateBooking(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody TravelBookingRequest request) {
        TravelBookingResponse response = userService.updateBooking(userId, bookingId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}/bookings/{bookingId}")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<Void> deleteBooking(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId) {
        userService.deleteBooking(userId, bookingId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/bookings/{bookingId}/reviews")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<ReviewResponse> submitReview(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse response = reviewService.submitReview(userId, bookingId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{userId}/bookings/{bookingId}/payment")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<PaymentResponse> initiatePayment(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(userId, bookingId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}/payments")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<List<PaymentResponse>> getUserPayments(@PathVariable UUID userId) {
        List<PaymentResponse> payments = paymentService.getUserPayments(userId);
        return ResponseEntity.ok(payments);
    }

    /**
     * Returns full trip summary after trip is completed — includes booking, driver, payment info, and review status.
     */
    @GetMapping("/{userId}/bookings/{bookingId}/summary")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<TripSummaryResponse> getTripSummary(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId) {
        TripSummaryResponse summary = tripSummaryService.getTripSummary(userId, bookingId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/{userId}/bookings/{bookingId}/confirm")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<TravelBookingResponse> confirmBooking(
            @PathVariable UUID userId,
            @PathVariable UUID bookingId) {
        TravelBookingResponse response = userService.confirmBooking(userId, bookingId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/profile")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<java.util.Map<String, Object>> updateProfile(
            @PathVariable UUID userId,
            @RequestBody com.travelplatform.dto.UserUpdateRequest request) {
        com.travelplatform.entity.User updated = userService.updateProfile(userId, request);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("userId", updated.getId());
        response.put("name", updated.getName());
        response.put("email", updated.getEmail());
        response.put("mobile", updated.getPhone());
        response.put("role", updated.getRole());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/route-preview")
    public ResponseEntity<RouteInfo> getRoutePreview(
            @RequestParam double fromLat,
            @RequestParam double fromLon,
            @RequestParam double toLat,
            @RequestParam double toLon) {
        RouteInfo routeInfo = routingService.calculateRoute(fromLat, fromLon, toLat, toLon);
        return ResponseEntity.ok(routeInfo);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @Valid @RequestBody com.travelplatform.dto.ForgotPasswordRequest request) {
        return ResponseEntity.ok(userService.forgotPassword(request));
    }
}
