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
import com.travelplatform.service.TelegramLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
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
    private final TelegramLinkService telegramLinkService;
    private final com.travelplatform.service.CityFareService cityFareService;
    private final com.travelplatform.service.CityService cityService;
    private final com.travelplatform.service.DriverVerificationService driverVerificationService;

    /** Self-signed-up drivers waiting for (or past) review; oldest first. */
    @GetMapping("/driver-applications")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<DriverDetailsResponse>> driverApplications(
            @RequestParam(required = false) com.travelplatform.entity.Driver.Verification status) {
        return ResponseEntity.ok(driverVerificationService.applications(status));
    }

    @PostMapping("/drivers/{id}/approve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> approveDriver(
            @PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(driverVerificationService.approve(id, reviewerId(authentication)));
    }

    @PostMapping("/drivers/{id}/reject")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> rejectDriver(
            @PathVariable Long id, @RequestBody Map<String, String> body,
            org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(driverVerificationService.reject(id, body.get("reason"), reviewerId(authentication)));
    }

    private static Long reviewerId(org.springframework.security.core.Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return null; // the audit column is best-effort
        }
    }

    @GetMapping("/city-fares")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.entity.CityFare>> getCityFares() {
        return ResponseEntity.ok(cityFareService.all());
    }

    @PutMapping("/city-fares/{vehicleType}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.CityFare> setCityFare(
            @PathVariable String vehicleType,
            @RequestBody Map<String, BigDecimal> body,
            org.springframework.security.core.Authentication authentication) {
        Long ownerId = null;
        try {
            ownerId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException ignored) {
            // the audit column is best-effort
        }
        return ResponseEntity.ok(cityFareService.update(
                com.travelplatform.entity.VehicleType.parse(vehicleType),
                body.get("baseFare"), body.get("perKm"), body.get("perMinute"), body.get("minimumFare"),
                ownerId));
    }

    @GetMapping("/cities")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.entity.City>> getCities() {
        return ResponseEntity.ok(cityService.all());
    }

    @PostMapping("/cities")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.City> createCity(@RequestBody com.travelplatform.entity.City city) {
        // This endpoint only creates: never trust an id or timestamp from the body.
        city.setId(null);
        city.setCreatedAt(null);
        return ResponseEntity.ok(cityService.save(city));
    }

    @PutMapping("/cities/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.City> updateCity(
            @PathVariable Long id, @RequestBody com.travelplatform.entity.City city) {
        return ResponseEntity.ok(cityService.update(id, city));
    }

    /** Vehicle details and City/Trip switches for one driver. */
    @PutMapping("/drivers/{driverId}/vehicle")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> updateDriverVehicle(
            @PathVariable Long driverId,
            @RequestBody com.travelplatform.dto.VehicleRequest vehicle) {
        adminDriverService.updateVehicle(driverId, vehicle);
        return ResponseEntity.ok(ownerService.getDriverById(driverId));
    }
    
    @GetMapping("/bookings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<TravelBooking>> getAllBookings() {
        return ResponseEntity.ok(ownerService.getAllBookings());
    }
    
    @PostMapping("/pricing/set")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> setPricePerKm(
            @RequestParam BigDecimal pricePerKm,
            @RequestParam Long ownerId) {
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
            @RequestParam BigDecimal pricePerHour,
            @RequestParam Long ownerId) {
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
    
    /**
     * All twelve months of a year in one call, for the revenue chart. Replaces twelve separate
     * /revenue/monthly requests per page view.
     */
    @GetMapping("/revenue/monthly-series")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getMonthlyRevenueSeries(@RequestParam int year) {
        return ResponseEntity.ok(ownerService.getMonthlyRevenueSeries(year));
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
            @RequestParam(required = false) MultipartFile aadhaarPhoto,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) String vehicleNumber,
            @RequestParam(required = false) String vehicleModel,
            @RequestParam(required = false) Boolean vehicleAc,
            @RequestParam(required = false) Integer vehicleSeats,
            @RequestParam(required = false) Boolean servesCity,
            @RequestParam(required = false) Boolean servesTrip) {
        DriverCreationResponse response = adminDriverService.createDriver(
            name, mobile, email, licenseNumber, aadhaarNumber,
            photo, licensePhoto, aadhaarPhoto,
            new com.travelplatform.dto.VehicleRequest(vehicleType, vehicleNumber, vehicleModel,
                    vehicleAc, vehicleSeats, servesCity, servesTrip));
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
    public ResponseEntity<PaymentResponse> verifyPayment(
            @PathVariable UUID paymentId,
            java.security.Principal principal) {
        // Verifying a payment settles a trip and books the fare into revenue, so the row records
        // which owner did it. Principal is the JWT subject, populated by JwtAuthenticationFilter.
        PaymentResponse response = paymentService.verifyPayment(paymentId, principal.getName());
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

    @DeleteMapping("/drivers/{driverId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> deleteDriver(@PathVariable Long driverId) {
        adminDriverService.deleteDriver(driverId);
        return ResponseEntity.ok(Map.of("message", "Driver deleted successfully"));
    }

    /**
     * Mints a one-time Telegram onboarding link for a driver.
     *
     * <p>Owner-only, and POST rather than GET, because the response is a credential: whoever
     * opens it gets the ability to accept and reject that driver's trips until it is redeemed.
     * Each call revokes the previous link for that driver.
     */
    @PostMapping("/drivers/{driverId}/telegram-link")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> createTelegramLink(@PathVariable Long driverId) {
        return ResponseEntity.ok(Map.of(
                "linkUrl", telegramLinkService.createLinkUrl(driverId),
                "expiresInHours", "24"));
    }
}
