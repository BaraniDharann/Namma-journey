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
    private final com.travelplatform.service.DriverLedgerService ledgerService;
    private final com.travelplatform.service.OwnerLedgerService ownerLedgerService;
    private final com.travelplatform.service.DriverDuesService duesService;
    private final com.travelplatform.service.TripRateService tripRateService;
    private final com.travelplatform.config.OwnerScope ownerScope;
    private final com.travelplatform.service.OwnerDriverReviewService ownerDriverReviewService;

    /** Self-signed-up drivers waiting for (or past) review; oldest first. */
    @GetMapping("/driver-applications")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<DriverDetailsResponse>> driverApplications(
            @RequestParam(required = false) com.travelplatform.entity.Driver.Verification status) {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(driverVerificationService.applications(status));
    }

    @PostMapping("/drivers/{id}/approve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> approveDriver(
            @PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        // My own driver: my checklist decides. An independent driver: the platform's review.
        if (ownerDriverReviewService.isMine(id)) {
            return ResponseEntity.ok(ownerService.mapToDriverDetails(ownerDriverReviewService.approve(id)));
        }
        ownerScope.requirePlatform();
        return ResponseEntity.ok(driverVerificationService.approve(id, reviewerId(authentication)));
    }

    /** The owner's licence (Sarathi) and RC (Vahan) checks for one of their drivers. */
    @GetMapping("/drivers/{id}/checks")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.entity.DriverVerificationCheck>> driverChecks(@PathVariable Long id) {
        return ResponseEntity.ok(ownerDriverReviewService.checksFor(id));
    }

    /** Records one check: {result: VERIFIED|FAILED, note}. A failed check needs a note. */
    @PutMapping("/drivers/{id}/checks/{type}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.DriverVerificationCheck> recordDriverCheck(
            @PathVariable Long id, @PathVariable String type, @RequestBody Map<String, String> body) {
        com.travelplatform.entity.DriverVerificationCheck.Type t;
        com.travelplatform.entity.DriverVerificationCheck.Result r;
        try {
            t = com.travelplatform.entity.DriverVerificationCheck.Type.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
            r = com.travelplatform.entity.DriverVerificationCheck.Result.valueOf(
                    String.valueOf(body == null ? null : body.get("result")).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Check LICENCE or RC, with result VERIFIED or FAILED");
        }
        return ResponseEntity.ok(ownerDriverReviewService.recordCheck(id, t, r, body.get("note")));
    }

    /** Sets a driver's usual car: {vehicleId}. */
    @PutMapping("/drivers/{id}/default-vehicle")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> setDefaultVehicle(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        adminDriverService.setDefaultVehicle(id, body == null ? null : body.get("vehicleId"));
        return ResponseEntity.ok(ownerService.getDriverById(ownerScope.ownerId(), ownerScope.isPlatform(), id));
    }

    @PostMapping("/drivers/{id}/reject")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> rejectDriver(
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
            org.springframework.security.core.Authentication authentication) {
        if (body == null) {
            throw new IllegalArgumentException("Give a reason of 5 to 500 characters");
        }
        if (ownerDriverReviewService.isMine(id)) {
            return ResponseEntity.ok(ownerService.mapToDriverDetails(ownerDriverReviewService.reject(id, body.get("reason"))));
        }
        ownerScope.requirePlatform();
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
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(cityFareService.all());
    }

    @PutMapping("/city-fares/{vehicleType}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.CityFare> setCityFare(
            @PathVariable String vehicleType,
            @RequestBody Map<String, BigDecimal> body,
            org.springframework.security.core.Authentication authentication) {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
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
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(cityService.all());
    }

    @PostMapping("/cities")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.City> createCity(@RequestBody com.travelplatform.entity.City city) {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        // This endpoint only creates: never trust an id or timestamp from the body.
        city.setId(null);
        city.setCreatedAt(null);
        return ResponseEntity.ok(cityService.save(city));
    }

    @PutMapping("/cities/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.City> updateCity(
            @PathVariable Long id, @RequestBody com.travelplatform.entity.City city) {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(cityService.update(id, city));
    }

    /** Vehicle details and City/Trip switches for one driver. */
    @PutMapping("/drivers/{driverId}/vehicle")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> updateDriverVehicle(
            @PathVariable Long driverId,
            @RequestBody com.travelplatform.dto.VehicleRequest vehicle) {
        adminDriverService.updateVehicle(driverId, vehicle);
        return ResponseEntity.ok(ownerService.getDriverById(ownerScope.ownerId(), ownerScope.isPlatform(), driverId));
    }
    
    @GetMapping("/bookings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<TravelBooking>> getAllBookings() {
        return ResponseEntity.ok(ownerService.getTripBookings(ownerScope.ownerId()));
    }

    /** The platform's City rides: a platform screen. */
    @GetMapping("/platform/city-rides")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<TravelBooking>> getCityRides() {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(ownerService.getCityRides());
    }

    /** City-ride revenue for a year, month by month: a platform screen. */
    @GetMapping("/platform/revenue/monthly-series")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getCityRevenueSeries(@RequestParam int year) {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(ownerService.getMonthlyRevenueSeries(null, year));
    }
    
    /** My Trip prices, one row per car category I offer. */
    @GetMapping("/trip-rates")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.service.TripRateView>> getTripRates() {
        return ResponseEntity.ok(tripRateService.ratesFor(ownerScope.ownerId()));
    }

    /** Sets my per-km and per-hour price for one car category: {perKm, perHour}. */
    @PutMapping("/trip-rates/{category}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.service.TripRateView> setTripRate(
            @PathVariable String category, @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(tripRateService.set(ownerScope.ownerId(),
                com.travelplatform.entity.CarCategory.parse(category), body.get("perKm"), body.get("perHour")));
    }

    /** AUTO: my Trips go to my first free driver and car. MANUAL: I assign each Trip myself. Body {mode}. */
    @PutMapping("/assignment-mode")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> setAssignmentMode(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("assignmentMode",
                ownerService.setAssignmentMode(ownerScope.ownerId(), body == null ? null : body.get("mode")).name()));
    }

    /** What I run, for my menu (V30). Body {fleetType: CARS | BUSES | BOTH}. */
    @PutMapping("/fleet-type")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> setFleetType(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("fleetType",
                ownerService.setFleetType(ownerScope.ownerId(), body == null ? null : body.get("fleetType")).name()));
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
        return ResponseEntity.ok(ownerService.getDailyRevenue(ownerScope.ownerId(), date));
    }
    
    @GetMapping("/revenue/monthly")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getMonthlyRevenue(
            @RequestParam int year, 
            @RequestParam int month) {
        return ResponseEntity.ok(ownerService.getMonthlyRevenue(ownerScope.ownerId(), year, month));
    }
    
    /**
     * All twelve months of a year in one call, for the revenue chart. Replaces twelve separate
     * /revenue/monthly requests per page view.
     */
    @GetMapping("/revenue/monthly-series")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getMonthlyRevenueSeries(@RequestParam int year) {
        return ResponseEntity.ok(ownerService.getMonthlyRevenueSeries(ownerScope.ownerId(), year));
    }

    @GetMapping("/revenue/yearly")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, Object>> getYearlyRevenue(@RequestParam int year) {
        return ResponseEntity.ok(ownerService.getYearlyRevenue(ownerScope.ownerId(), year));
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
            @RequestParam(required = false) Boolean servesTrip,
            @RequestParam(required = false) Long defaultVehicleId) {
        DriverCreationResponse response = adminDriverService.createDriver(
            name, mobile, email, licenseNumber, aadhaarNumber,
            photo, licensePhoto, aadhaarPhoto,
            new com.travelplatform.dto.VehicleRequest(vehicleType, vehicleNumber, vehicleModel,
                    vehicleAc, vehicleSeats, servesCity, servesTrip), defaultVehicleId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/reviews")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<ReviewResponse>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getReviewsFor(ownerScope.ownerId()));
    }

    /** City-ride reviews: a platform screen. */
    @GetMapping("/platform/reviews")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<ReviewResponse>> getCityReviews() {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(reviewService.getReviewsFor(null));
    }
    
    /** Independent drivers, their payout UPI IDs (decrypted for the owner only) and balances. */
    @GetMapping("/payouts")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.service.DriverLedgerService.PayoutRow>> getPayouts() {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(ledgerService.payoutSummary());
    }

    /** Records a UPI transfer made outside the app: {type: PAYOUT|COLLECTION, amount, reference, note}. */
    @PostMapping("/payouts/{driverId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.DriverLedgerEntry> recordPayout(
            @PathVariable Long driverId, @RequestBody Map<String, String> body, java.security.Principal principal) {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        com.travelplatform.entity.DriverLedgerEntry.Type type;
        java.math.BigDecimal amount;
        try {
            type = com.travelplatform.entity.DriverLedgerEntry.Type.valueOf(String.valueOf(body.get("type")).trim().toUpperCase(java.util.Locale.ROOT));
            amount = new java.math.BigDecimal(String.valueOf(body.get("amount")).trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Give a type (PAYOUT or COLLECTION) and an amount");
        }
        return ResponseEntity.ok(ledgerService.recordTransfer(driverId, type, amount,
                body.get("reference"), body.get("note"), "owner:" + principal.getName()));
    }

    /** My City-ride money with the platform (V35): balance and latest entries. */
    @GetMapping("/city-earnings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.service.OwnerLedgerService.EarningsView> getCityEarnings() {
        return ResponseEntity.ok(ownerLedgerService.earnings(ownerScope.ownerId()));
    }

    /** Travels owners' City-ride balances, for the platform to settle. */
    @GetMapping("/owner-payouts")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.service.OwnerLedgerService.PayoutRow>> getOwnerPayouts() {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(ownerLedgerService.payoutSummary());
    }

    /** Records a UPI transfer with a travels owner: {type: PAYOUT|COLLECTION, amount, reference, note}. */
    @PostMapping("/owner-payouts/{ownerId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.entity.OwnerLedgerEntry> recordOwnerPayout(
            @PathVariable Long ownerId, @RequestBody Map<String, String> body, java.security.Principal principal) {
        ownerScope.requirePlatform();
        com.travelplatform.entity.DriverLedgerEntry.Type type;
        java.math.BigDecimal amount;
        try {
            type = com.travelplatform.entity.DriverLedgerEntry.Type.valueOf(String.valueOf(body.get("type")).trim().toUpperCase(java.util.Locale.ROOT));
            amount = new java.math.BigDecimal(String.valueOf(body.get("amount")).trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Give a type (PAYOUT or COLLECTION) and an amount");
        }
        return ResponseEntity.ok(ownerLedgerService.recordTransfer(ownerId, type, amount,
                body.get("reference"), body.get("note"), "owner:" + principal.getName()));
    }

    /** Commission payments drivers submitted from the app, waiting for the owner to check the bank. */
    @GetMapping("/dues/pending")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<com.travelplatform.service.DriverDuesService.PendingRow>> getPendingDues() {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(duesService.pending());
    }

    @PostMapping("/dues/{paymentId}/confirm")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.service.DriverDuesService.PaymentRow> confirmDues(
            @PathVariable Long paymentId, java.security.Principal principal) {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(duesService.confirm(paymentId, "owner:" + principal.getName()));
    }

    @PostMapping("/dues/{paymentId}/reject")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.service.DriverDuesService.PaymentRow> rejectDues(
            @PathVariable Long paymentId, java.security.Principal principal) {
        ownerScope.requirePlatform(); // a platform screen (City rides, independent drivers)
        return ResponseEntity.ok(duesService.reject(paymentId, "owner:" + principal.getName()));
    }

    @GetMapping("/payments/pending")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<PaymentResponse>> getPendingPayments() {
        return ResponseEntity.ok(paymentService.getPendingPayments(ownerScope.ownerId()));
    }

    /** City-ride UPI payments waiting for confirmation: a platform screen. */
    @GetMapping("/platform/payments/pending")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<PaymentResponse>> getPendingCityPayments() {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(paymentService.getPendingPayments(null));
    }
    
    @PostMapping("/payments/{paymentId}/verify")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @PathVariable UUID paymentId,
            java.security.Principal principal) {
        paymentService.requireVisibleTo(paymentId, ownerScope.ownerId(), ownerScope.isPlatform());
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
        TravelBookingResponse response = ownerService.assignDriver(bookingId, request.getDriverId(), request.getVehicleId());
        return ResponseEntity.ok(response);
    }

    /** My free drivers and suitable free cars for one of my Trips (each driver's usual car marked). */
    @GetMapping("/bookings/{bookingId}/assign-options")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<com.travelplatform.dto.AssignOptions> assignOptions(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ownerService.assignOptions(bookingId));
    }

    @GetMapping("/drivers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<DriverDetailsResponse>> getAllDrivers() {
        return ResponseEntity.ok(ownerService.getAllDrivers(ownerScope.ownerId()));
    }

    /** Independent (self-signup) City-ride drivers: a platform screen. */
    @GetMapping("/platform/drivers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<List<DriverDetailsResponse>> getIndependentDrivers() {
        ownerScope.requirePlatform();
        return ResponseEntity.ok(ownerService.getIndependentDrivers());
    }

    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<DriverDetailsResponse> getDriverById(@PathVariable Long driverId) {
        return ResponseEntity.ok(ownerService.getDriverById(ownerScope.ownerId(), ownerScope.isPlatform(), driverId));
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
        // Only for a driver this owner may see (their own, or an independent one for the platform).
        ownerService.getDriverById(ownerScope.ownerId(), ownerScope.isPlatform(), driverId);
        return ResponseEntity.ok(Map.of(
                "linkUrl", telegramLinkService.createLinkUrl(driverId),
                "expiresInHours", "24"));
    }
}
