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
import com.travelplatform.service.TelegramLinkService;
import com.travelplatform.config.TelegramProperties;
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

    @Autowired
    private com.travelplatform.config.BookingAccessGuard bookingAccessGuard;

    @Autowired
    private TelegramLinkService telegramLinkService;

    @Autowired
    private com.travelplatform.service.DriverLedgerService ledgerService;

    /** Balance with the platform, recent ledger lines, and the masked payout UPI ID. */
    @GetMapping("/{driverId}/earnings")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<com.travelplatform.service.DriverLedgerService.EarningsView> getEarnings(@PathVariable Long driverId) {
        return ResponseEntity.ok(ledgerService.earnings(driverId));
    }

    @PutMapping("/{driverId}/payout-upi")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<com.travelplatform.service.DriverLedgerService.EarningsView> setPayoutUpi(
            @PathVariable Long driverId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ledgerService.setPayoutUpi(driverId, body.get("upiId")));
    }

    @Autowired
    private com.travelplatform.service.DriverDuesService duesService;

    /** Commission owed, the owner's UPI ID with a ready-filled payment link, and recent claims. */
    @GetMapping("/{driverId}/dues")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<com.travelplatform.service.DriverDuesService.DuesView> getDues(@PathVariable Long driverId) {
        return ResponseEntity.ok(duesService.view(driverId));
    }

    /** "I paid": {amount, utr}. Waits for the owner to confirm before the balance moves. */
    @PostMapping("/{driverId}/dues/payments")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<com.travelplatform.service.DriverDuesService.DuesView> submitDuesPayment(
            @PathVariable Long driverId, @RequestBody Map<String, String> body) {
        java.math.BigDecimal amount;
        try {
            amount = new java.math.BigDecimal(String.valueOf(body.get("amount")).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter the amount you paid, in rupees");
        }
        return ResponseEntity.ok(duesService.submit(driverId, amount, body.get("utr")));
    }

    @Autowired
    private TelegramProperties telegramProperties;

    @Autowired
    private com.travelplatform.service.DriverPresenceService driverPresenceService;

    @Autowired
    private com.travelplatform.service.RideSafetyService rideSafetyService;

    @Autowired
    private com.travelplatform.service.DriverVerificationService driverVerificationService;

    // SpEL guard reused on per-driver endpoints. Without it any logged-in driver could read
    // or mutate another driver's bookings simply by changing the URL.
    private static final String SAME_DRIVER = "hasRole('DRIVER') and #driverId.toString() == authentication.principal";

    /** A rejected driver sends corrected documents; any subset of the four. */
    @PutMapping(value = "/{driverId}/documents", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<DriverDetailsResponse> resubmitDocuments(
            @PathVariable Long driverId,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "licensePhoto", required = false) MultipartFile licensePhoto,
            @RequestPart(value = "aadhaarPhoto", required = false) MultipartFile aadhaarPhoto,
            @RequestPart(value = "rcPhoto", required = false) MultipartFile rcPhoto) {
        return ResponseEntity.ok(driverVerificationService.resubmit(driverId, photo, licensePhoto, aadhaarPhoto, rcPhoto));
    }

    @GetMapping("/{driverId}/verification")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, Object>> verification(@PathVariable Long driverId) {
        return ResponseEntity.ok(driverVerificationService.status(driverId));
    }

    /**
     * The ride this driver is being offered, if any: 200 with it, 204 without. Online phones poll
     * this as the safety net under the socket push, so it is one row, not the trip list.
     */
    @GetMapping("/{driverId}/offer")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<com.travelplatform.dto.DriverOfferView> currentOffer(@PathVariable Long driverId) {
        return driverService.currentOffer(driverId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

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

    /**
     * Telegram trip alerts are mandatory for drivers. The driver app reads this after login and
     * shows only the connect screen until {@code linked} is true. {@code required} is false when
     * the Telegram integration is switched off, so drivers are never locked out by a missing bot.
     */
    @GetMapping("/{driverId}/telegram")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, Object>> getTelegramStatus(@PathVariable Long driverId) {
        boolean required = telegramProperties.isOperational() && !telegramProperties.getBotUsername().isBlank();
        return ResponseEntity.ok(Map.of(
                "required", required,
                "linked", telegramLinkService.isLinked(driverId)));
    }

    /** The driver's own one-time connect link, so they can connect without waiting for the owner. */
    @PostMapping("/{driverId}/telegram-link")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, String>> createOwnTelegramLink(@PathVariable Long driverId) {
        return ResponseEntity.ok(Map.of("linkUrl", telegramLinkService.createLinkUrl(driverId)));
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
        if (!bookingAccessGuard.canReportLocation(dto.getDriverId(), dto.getBookingId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not assigned to this trip");
        }
        // A driver on a trip is online too; their live position is also where Ride Now finds them.
        driverService.requireApproved(dto.getDriverId());
        driverPresenceService.report(dto.getDriverId(), dto.getLatitude(), dto.getLongitude());
        DriverLocationDTO stored = locationTrackingService.updateLocation(dto);
        if (rideSafetyService != null) {
            rideSafetyService.onLocation(dto);
        }
        return ResponseEntity.ok(stored);
    }

    /**
     * An online driver's phone reporting where it is, every few seconds, so Ride Now can offer
     * them trips near them. Only positions are kept, and only for a minute past the last report.
     */
    @PutMapping("/{driverId}/presence")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, Object>> reportPresence(@PathVariable Long driverId,
                                                              @RequestBody(required = false) Map<String, Double> body) {
        Double lat = body != null ? body.get("latitude") : null;
        Double lon = body != null ? body.get("longitude") : null;
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("latitude and longitude are required");
        }
        driverService.requireApproved(driverId);
        driverPresenceService.report(driverId, lat, lon);
        // Tell the phone whether it can be found: a driver outside every zone used to get a 204
        // and believe they were online while no search could see them.
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        java.util.Optional<com.travelplatform.entity.City> zone = cityService == null
                ? java.util.Optional.empty() : cityService.cityFor(lat, lon);
        status.put("inZone", zone.isPresent());
        zone.ifPresent(c -> {
            status.put("zoneId", c.getId());
            status.put("zoneName", c.getName());
        });
        if (zone.isEmpty() && cityService != null) {
            cityService.nearestCity(lat, lon).ifPresent(c -> status.put("nearestZone", c.getName()));
        }
        return ResponseEntity.ok(status);
    }

    // Optional so controller tests need not supply them.
    @Autowired(required = false)
    private com.travelplatform.service.CityService cityService;
    @Autowired(required = false)
    private com.travelplatform.service.PlaceService placeService;

    /** Where this driver works: State -> District -> Town, with its City zone. */
    @GetMapping("/{driverId}/home-area")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, Object>> homeArea(@PathVariable Long driverId) {
        return ResponseEntity.ok(placeService.homeAreaOf(driverId));
    }

    /** Sets where this driver works; the town's City zone is created if it had none. */
    @PutMapping("/{driverId}/home-area")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, Object>> setHomeArea(@PathVariable Long driverId,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        return ResponseEntity.ok(placeService.setHomeArea(driverId, new com.travelplatform.service.PlaceService.HomeAreaRequest(
                asLong(b.get("placeId")), asString(b.get("state")), asString(b.get("district")), asString(b.get("town")),
                asDouble(b.get("lat")), asDouble(b.get("lon")))));
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null || v.toString().isBlank() ? null : Long.valueOf(v.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("That town is not in the list. Choose it again.");
        }
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null || v.toString().isBlank() ? null : Double.valueOf(v.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Find your town on the map search so we know where it is");
        }
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    /** The driver's own City rides / Trips switches. */
    @PutMapping("/{driverId}/modes")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<DriverDetailsResponse> updateModes(
            @PathVariable Long driverId,
            @RequestBody(required = false) Map<String, Boolean> body) {
        return ResponseEntity.ok(driverService.updateModes(driverId,
                body != null ? body.get("servesCity") : null,
                body != null ? body.get("servesTrip") : null));
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

    @org.springframework.beans.factory.annotation.Autowired
    private com.travelplatform.service.ProfileEditService profileEditService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.travelplatform.service.AccountClosureService accountClosureService;

    /** Name, mobile and email. Changing the mobile (login ID) or email needs the current password. */
    @PutMapping("/{driverId}/profile")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<DriverDetailsResponse> updateProfile(
            @PathVariable Long driverId,
            @RequestBody com.travelplatform.service.ProfileEditService.DriverDetails request) {
        profileEditService.updateDriverDetails(driverId, request);
        return ResponseEntity.ok(driverService.getDriverProfile(driverId));
    }

    /** Closes the driver's own account (see AccountClosureService). */
    @PostMapping("/{driverId}/close")
    @PreAuthorize(SAME_DRIVER)
    public ResponseEntity<Map<String, String>> closeAccount(
            @PathVariable Long driverId,
            @RequestBody(required = false) Map<String, String> body) {
        accountClosureService.closeDriver(driverId, body == null ? null : body.get("password"));
        return ResponseEntity.ok(Map.of("message", "Your account is closed"));
    }
}
