package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.ForgotPasswordRequest;
import com.travelplatform.dto.RouteInfo;
import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.UserRepository;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.travelplatform.repository.TripDriverPhotoRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final TravelBookingRepository bookingRepository;
    private final RoutingService routingService;
    private final DriverRepository driverRepository;
    private final OwnerService ownerService;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final TripDriverPhotoRepository tripDriverPhotoRepository;
    private final TransactionTemplate transactionTemplate;

    // Field-injected (not in the constructor) so the many tests that build UserService directly
    // are unaffected; only the password-reset path uses it.
    @org.springframework.beans.factory.annotation.Autowired
    private com.travelplatform.security.TokenVersionService tokenVersionService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.travelplatform.repository.PaymentRepository paymentRepository;

    // Validates dates, distance, place names and the open-booking cap before anything is priced.
    // Required in the app; null only in unit tests that build UserService by hand.
    @org.springframework.beans.factory.annotation.Autowired
    private TripRules tripRules;

    // City rides: dispatch and fares. Null only in unit tests that build UserService by hand.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DispatchService dispatchService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CityFareService cityFareService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CityService cityService;

    // Refunds for cancelled prepaid trips. Null only in unit tests that build UserService by hand.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.outbox.Outbox outbox;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailService emailService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TripAdvancePolicy advancePolicy;
    // The matching engine's copy of driver state. Null only in unit tests that build UserService by hand.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.dispatch.DriverStateUpdates driverStates;

    /**
     * Shown to the traveller both as the booking refusal and in the profile prompt, so the two
     * always say the same thing. The frontend matches on it to route them to the profile page.
     */
    public static final String MOBILE_REQUIRED_MESSAGE =
            "Add your mobile number in your profile before booking a trip — your driver needs it to reach you.";

    /** Indian mobile numbers, same shape the signup and booking forms already enforce. */
    private static final java.util.regex.Pattern MOBILE_PATTERN = java.util.regex.Pattern.compile("^[6-9]\\d{9}$");

    /**
     * How many drivers a booking will try before it settles for no driver at all. Each retry only
     * happens because another booking claimed the candidate in the same instant, so needing more
     * than a couple means the fleet is effectively full for those dates.
     */
    private static final int MAX_DRIVER_ATTEMPTS = 3;

    /** Postgres SQLSTATE for "violates exclusion constraint" - here, no_driver_overlap. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    /**
     * Deliberately not {@code @Transactional}. The route lookup below calls a third-party map API
     * that can take up to 8s (ExternalHttpClients' timeouts), and inside a transaction that whole
     * wait pinned one of only 20 pooled DB connections. The database work runs afterwards in its
     * own short transaction per attempt, see {@link #saveWithDriver}.
     */
    public TravelBookingResponse createBooking(UUID userId, TravelBookingRequest request) {
        // A trip with no way to reach the traveller is not a trip anyone can drive. Accounts
        // created through Sign-in with Google never collect a number, so this is the first point
        // where it becomes mandatory. Enforced here rather than only in the form: the form is
        // just a convenience, this is the rule.
        com.travelplatform.entity.User booker = userRepository.findById(userId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("User not found"));
        if (booker.getPhone() == null || booker.getPhone().isBlank()) {
            throw new IllegalArgumentException(MOBILE_REQUIRED_MESSAGE);
        }

        // A City ride is for right now and distance-priced, whatever the form still held.
        boolean city = "CITY".equalsIgnoreCase(request.getServiceMode());
        Long cityId = null;
        if (city) {
            if (dispatchService == null || cityFareService == null || cityService == null) {
                throw new IllegalStateException("City rides are not available");
            }
            VehicleType vehicle = VehicleType.parse(request.getVehicleType());
            if (vehicle == null) {
                throw new IllegalArgumentException("Choose a bike, auto or car for your city ride");
            }
            if (request.getTravelMembers() != null && request.getTravelMembers() > vehicle.maxPassengers()) {
                throw new IllegalArgumentException("A " + vehicle.name().toLowerCase(java.util.Locale.ROOT)
                        + " ride carries at most " + vehicle.maxPassengers() + " passenger"
                        + (vehicle.maxPassengers() == 1 ? "" : "s") + ". Choose a bigger vehicle.");
            }
            // A City ride runs only inside a city the owner serves; anywhere else is a Trip.
            cityId = request.getFromLat() == null || request.getFromLon() == null ? null
                    : cityService.cityFor(request.getFromLat(), request.getFromLon()).map(com.travelplatform.entity.City::getId).orElse(null);
            if (cityId == null) {
                throw new IllegalArgumentException("City rides aren't available at this pickup yet. Book it as a Trip instead.");
            }
            java.time.LocalDate today = dispatchService.now().toLocalDate();
            request.setFromDate(today);
            request.setToDate(today);
            request.setBookingType(TravelBooking.BookingType.DISTANCE_BASED.name());
            request.setBookingHours(null);
        }

        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("From date and to date are required");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("To date cannot be before from date");
        }
        if (tripRules != null) {
            tripRules.checkOpenBookings(userId);
            tripRules.check(request);
        }

        // Use exact coordinates from frontend for accurate distance calculation
        RouteInfo routeInfo;
        if (request.getFromLat() != null && request.getFromLon() != null
                && request.getToLat() != null && request.getToLon() != null) {
            routeInfo = routingService.calculateRoute(
                    request.getFromLat(), request.getFromLon(),
                    request.getToLat(), request.getToLon());
        } else {
            routeInfo = routingService.calculateRoute(request.getFromPlace(), request.getToPlace());
        }

        // Two travellers can see the same free driver at the same moment. The database settles
        // it (EXCLUDE constraint no_driver_overlap, V5): the second insert is refused, and that
        // attempt's transaction rolls back. We then ask again, leaving out every driver already
        // lost, and after MAX_DRIVER_ATTEMPTS save the booking unassigned so the owner can place
        // it - the traveller's booking never fails because of a clash they cannot see.
        List<Long> lostDrivers = new ArrayList<>();
        if (city) {
            if (routeInfo.getDistanceKm() != null && routeInfo.getDistanceKm() > cityFareService.maxKm()) {
                throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,
                        "City rides go up to %.0f km, and this one is %.0f km. Book it as a Trip instead.",
                        cityFareService.maxKm(), routeInfo.getDistanceKm()));
            }
            // Saved with nobody; DispatchService offers it to the nearest online driver once it commits.
            return saveWithDriver(userId, request, routeInfo, lostDrivers, false, cityId);
        }
        for (int attempt = 0; attempt < MAX_DRIVER_ATTEMPTS; attempt++) {
            try {
                return saveWithDriver(userId, request, routeInfo, lostDrivers, true, cityId);
            } catch (DriverTakenException taken) {
                lostDrivers.add(taken.driverId);
                // How often two bookings collide on one driver: a rising rate means the fleet is
                // too small for demand on those dates.
                Metrics.counter("namma.bookings.driver_race_lost").increment();
            }
        }
        return saveWithDriver(userId, request, routeInfo, lostDrivers, false, cityId);
    }

    /**
     * One attempt: pick a driver (unless {@code assign} is false), insert, notify - all in one
     * short transaction, so a refused insert takes its notifications down with it.
     */
    private TravelBookingResponse saveWithDriver(UUID userId, TravelBookingRequest request, RouteInfo routeInfo,
                                                 List<Long> lostDrivers, boolean assign, Long cityId) {
        return transactionTemplate.execute(tx -> {
            Driver driver = null;
            if (assign) {
                driver = driverRepository.findFirstFreeTripDriver(request.getFromDate(), request.getToDate(),
                                "AC".equalsIgnoreCase(request.getAcType()), excludedOrNobody(lostDrivers))
                        .orElse(null);
            }

            TravelBooking savedBooking;
            try {
                // saveAndFlush, not save: the INSERT has to reach Postgres here, inside the try,
                // or the constraint would only fire at commit where it can no longer be caught
                // per attempt.
                savedBooking = bookingRepository.saveAndFlush(buildBooking(userId, request, routeInfo, driver, cityId));
            } catch (DataIntegrityViolationException ex) {
                if (driver != null && isDriverOverlap(ex)) {
                    throw new DriverTakenException(driver.getId());
                }
                throw ex;
            } catch (PessimisticLockingFailureException ex) {
                // Several bookings racing for one driver wait on each other's uncommitted rows,
                // and Postgres sometimes breaks that up as a deadlock (40P01) rather than the
                // constraint error. Same meaning: someone else is getting this driver.
                if (driver != null) {
                    throw new DriverTakenException(driver.getId());
                }
                throw ex;
            }

            notificationService.notifyBookingCreated(savedBooking);
            // A Trip with an advance due is only a hold: the driver hears about it once it is paid
            // (RazorpayPaymentService.settleAdvance), not about every hold that lapses unpaid.
            if (driver != null && !savedBooking.isAdvanceDue()) {
                notificationService.notifyDriverAssigned(savedBooking, driver);
            }
            if (driver != null && driverStates != null) {
                driverStates.took(savedBooking); // a Trip for today holds the car, paid or not (no_driver_overlap)
            }
            if (savedBooking.getDispatchMode() == TravelBooking.DispatchMode.RIDE_NOW) {
                dispatchService.dispatchAfterCommit(savedBooking.getId());
            }
            Metrics.counter("namma.bookings.placed", "assigned", String.valueOf(driver != null)).increment();
            return mapToResponse(savedBooking);
        });
    }

    private TravelBooking buildBooking(UUID userId, TravelBookingRequest request, RouteInfo routeInfo, Driver driver, Long cityId) {
        int travelDays = (int) ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;

        // Determine booking type and calculate amount
        TravelBooking.BookingType bookingType = TravelBooking.BookingType.DISTANCE_BASED;
        Integer bookingHours = null;
        BigDecimal pricePerHourAtBooking = null;
        BigDecimal totalAmount;

        boolean city = "CITY".equalsIgnoreCase(request.getServiceMode());
        VehicleType vehicle = city ? VehicleType.parse(request.getVehicleType()) : VehicleType.CAR;

        if (city) {
            totalAmount = cityFareService.fare(vehicle, routeInfo.getDistanceKm(),
                    routeInfo.getTimeMinutes() == null ? 0 : routeInfo.getTimeMinutes());
        } else if ("HOUR_BASED".equalsIgnoreCase(request.getBookingType()) && request.getBookingHours() != null) {
            bookingType = TravelBooking.BookingType.HOUR_BASED;
            bookingHours = request.getBookingHours();
            pricePerHourAtBooking = ownerService.getCurrentPricePerHour();
            totalAmount = Money.times(pricePerHourAtBooking, bookingHours);
        } else {
            // Rounded to paise once, here: distances carry metres (461.237 km), money does not.
            totalAmount = Money.times(ownerService.getCurrentPricePerKm(), routeInfo.getDistanceKm());
        }

        TravelBooking booking = new TravelBooking();
        booking.setServiceMode(city ? TravelBooking.ServiceMode.CITY : TravelBooking.ServiceMode.TRIP);
        booking.setVehicleType(vehicle);
        booking.setCityId(cityId);
        booking.setUserId(userId);
        booking.setUserName(request.getUserName());
        booking.setUserPhone(request.getUserPhone());
        booking.setFromPlace(request.getFromPlace());
        booking.setToPlace(request.getToPlace());
        booking.setFromLat(request.getFromLat());
        booking.setFromLon(request.getFromLon());
        booking.setToLat(request.getToLat());
        booking.setToLon(request.getToLon());
        booking.setFromDate(request.getFromDate());
        booking.setToDate(request.getToDate());
        booking.setTravelDays(travelDays);
        booking.setTravelMembers(request.getTravelMembers());
        booking.setAcType(request.getAcType());
        booking.setDistanceKm(routeInfo.getDistanceKm());
        booking.setEstimatedTimeMinutes(routeInfo.getTimeMinutes());
        booking.setRouteDetails(routeInfo.getRouteDetails());
        booking.setTotalAmount(totalAmount);
        booking.setBookingType(bookingType);
        booking.setBookingHours(bookingHours);
        booking.setPricePerHourAtBooking(pricePerHourAtBooking);
        booking.setBookingDate(LocalDateTime.now());
        booking.setDriverId(driver != null ? driver.getId() : null);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        if (city) {
            booking.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
            booking.setDispatchState(TravelBooking.DispatchState.SEARCHING);
            booking.setDispatchStartedAt(dispatchService.now());
        }
        if (advancePolicy != null && advancePolicy.applies(booking)) {
            booking.setAdvanceAmount(advancePolicy.advanceFor(totalAmount));
            booking.setHoldExpiresAt(advancePolicy.holdUntil(LocalDateTime.now()));
        }
        return booking;
    }

    /** {@code NOT IN ()} is not valid SQL, so "nobody excluded" is a list holding an id no driver has. */
    static java.util.Collection<Long> excludedOrNobody(java.util.Collection<Long> excluded) {
        return excluded == null || excluded.isEmpty() ? List.of(-1L) : excluded;
    }

    /** True when Postgres refused the insert because the driver already has an overlapping trip. */
    static boolean isDriverOverlap(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains("no_driver_overlap")) {
                return true;
            }
        }
        return false;
    }

    /** Signals, inside one attempt, that another booking claimed this driver first. */
    private static final class DriverTakenException extends RuntimeException {
        private final Long driverId;

        DriverTakenException(Long driverId) {
            super(null, null, false, false);
            this.driverId = driverId;
        }
    }

    /**
     * Every booking of a traveller, with driver and end-trip photo attached.
     *
     * <p>Three queries whatever the list size. Mapping each booking on its own looked up its photo
     * and its driver separately - two queries per booking, ~60 per page for a regular traveller -
     * and under load that alone exhausted the connection pool (see UserServiceBookingListTest).
     */
    public List<TravelBookingResponse> getAllBookings(UUID userId) {
        List<TravelBooking> bookings = bookingRepository.findByUserIdOrderByBookingDateDesc(userId);
        if (bookings.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> photoByBooking = new HashMap<>();
        tripDriverPhotoRepository.findByBookingIdIn(bookings.stream().map(TravelBooking::getId).toList())
                .forEach(photo -> photoByBooking.putIfAbsent(photo.getBookingId(), photo.getPhotoPath()));

        Set<Long> driverIds = bookings.stream()
                .map(TravelBooking::getDriverId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Driver> driverById = new HashMap<>();
        if (!driverIds.isEmpty()) {
            driverRepository.findAllById(driverIds).forEach(d -> driverById.put(d.getId(), d));
        }

        return bookings.stream()
                .map(b -> mapToResponse(b, photoByBooking.get(b.getId()),
                        b.getDriverId() == null ? null : driverById.get(b.getDriverId())))
                .collect(Collectors.toList());
    }
    
    public TravelBookingResponse getBookingById(UUID bookingId, UUID userId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to booking");
        }
        
        return mapToResponse(booking);
    }
    
    @Transactional
    public TravelBookingResponse updateBooking(UUID userId, UUID bookingId, TravelBookingRequest request) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to booking");
        }

        // Editing recomputes the fare, so it is only safe before anyone has committed to it: a
        // driver accepting, or a payment being raised. It used to accept edits in any state - a
        // passenger could set up a cash payment for a 10 km trip, edit it to 500 km, and the
        // driver would be told ₹100 settles it while revenue reported ₹5000.
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING) {
            throw new RuntimeException("Only a pending booking can be changed. Cancel it and book again instead.");
        }
        // A City ride is already being offered to drivers near the pickup; a new route or vehicle
        // is a new ride.
        if (booking.getServiceMode() == TravelBooking.ServiceMode.CITY) {
            throw new RuntimeException("A city ride can't be changed. Cancel it and book again instead.");
        }
        if (paymentRepository.findByBookingId(bookingId).isPresent() || booking.getAdvancePaidAt() != null) {
            throw new RuntimeException("A payment has been started for this booking, so it can no longer be changed.");
        }
        if (tripRules != null) {
            tripRules.check(request);
        }

        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("From date and to date are required");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("To date cannot be before from date");
        }

        int travelDays = (int) ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;

        RouteInfo routeInfo;
        if (request.getFromLat() != null && request.getFromLon() != null
                && request.getToLat() != null && request.getToLon() != null) {
            routeInfo = routingService.calculateRoute(
                    request.getFromLat(), request.getFromLon(),
                    request.getToLat(), request.getToLon());
        } else {
            routeInfo = routingService.calculateRoute(request.getFromPlace(), request.getToPlace());
        }

        TravelBooking.BookingType bookingType = TravelBooking.BookingType.DISTANCE_BASED;
        Integer bookingHours = null;
        BigDecimal pricePerHourAtBooking = null;
        BigDecimal totalAmount;

        if ("HOUR_BASED".equalsIgnoreCase(request.getBookingType()) && request.getBookingHours() != null) {
            bookingType = TravelBooking.BookingType.HOUR_BASED;
            bookingHours = request.getBookingHours();
            pricePerHourAtBooking = ownerService.getCurrentPricePerHour();
            totalAmount = Money.times(pricePerHourAtBooking, bookingHours);
        } else {
            // Rounded to paise once, here: distances carry metres (461.237 km), money does not.
            totalAmount = Money.times(ownerService.getCurrentPricePerKm(), routeInfo.getDistanceKm());
        }

        booking.setUserName(request.getUserName());
        booking.setUserPhone(request.getUserPhone());
        booking.setFromPlace(request.getFromPlace());
        booking.setToPlace(request.getToPlace());
        booking.setFromLat(request.getFromLat());
        booking.setFromLon(request.getFromLon());
        booking.setToLat(request.getToLat());
        booking.setToLon(request.getToLon());
        booking.setFromDate(request.getFromDate());
        booking.setToDate(request.getToDate());
        booking.setTravelDays(travelDays);
        booking.setTravelMembers(request.getTravelMembers());
        booking.setAcType(request.getAcType());
        booking.setDistanceKm(routeInfo.getDistanceKm());
        booking.setEstimatedTimeMinutes(routeInfo.getTimeMinutes());
        booking.setRouteDetails(routeInfo.getRouteDetails());
        booking.setTotalAmount(totalAmount);
        booking.setBookingType(bookingType);
        booking.setBookingHours(bookingHours);
        booking.setPricePerHourAtBooking(pricePerHourAtBooking);
        // Still unpaid (checked above), so the advance follows the new fare.
        if (booking.getAdvanceAmount() != null && advancePolicy != null) {
            booking.setAdvanceAmount(advancePolicy.advanceFor(totalAmount));
        }

        TravelBooking updatedBooking = bookingRepository.save(booking);
        return mapToResponse(updatedBooking);
    }
    
    @Transactional
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "dailyRevenue",   allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "monthlyRevenue", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public void deleteBooking(UUID userId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to booking");
        }

        // A passenger "delete" is a cancellation. It used to hard-delete the row in any state: a
        // driver mid-trip lost the booking before collecting cash, completed fares vanished from
        // revenue, and payments, reviews and photos were left pointing at nothing.
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING
                && booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Only a booking that has not started can be cancelled");
        }
        java.util.Optional<com.travelplatform.entity.Payment> fare = paymentRepository.findByBookingId(bookingId);
        java.util.Optional<com.travelplatform.entity.Payment> advance = paymentRepository.findAdvance(bookingId);

        BigDecimal refunded = BigDecimal.ZERO;
        if (fare.filter(this::isPaid).isPresent()) {
            // Paid online: the full fare goes back automatically, since the trip never started.
            // Paid by UPI transfer there is no payment id to refund against, so that stays a
            // conversation with the owner.
            if (fare.get().getPaymentMethod() != com.travelplatform.entity.Payment.PaymentMethod.RAZORPAY || outbox == null) {
                throw new RuntimeException("This booking is already paid for. Please contact us to cancel it and arrange a refund.");
            }
            refunded = refunded.add(refund(fare.get()));
        } else {
            fare.ifPresent(this::abandon);
        }

        if (advance.filter(this::isPaid).isPresent()) {
            // Refunded unless the car was committed and it is now too close to the trip
            // (TripAdvancePolicy). A kept advance stays VERIFIED: it is the owner's money now.
            if (outbox != null && (advancePolicy == null
                    || advancePolicy.refundsAdvanceOnCancel(booking, TripAdvancePolicy.nowInTripZone()))) {
                refunded = refunded.add(refund(advance.get()));
            }
        } else {
            advance.ifPresent(this::abandon);
        }

        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);
        booking.setHoldExpiresAt(null);
        bookingRepository.save(booking);
        notificationService.notifyBookingCancelled(booking);
        if (driverStates != null) {
            driverStates.freed(booking.getDriverId(), booking);
        }
        if (refunded.signum() > 0 && emailService != null) {
            BigDecimal amount = refunded;
            userRepository.findById(userId).ifPresent(u ->
                    emailService.sendRefundStartedEmail(u.getEmail(), u.getName(), booking, amount));
        }
    }

    private boolean isPaid(com.travelplatform.entity.Payment p) {
        return p.getStatus() == com.travelplatform.entity.Payment.PaymentStatus.VERIFIED;
    }

    /**
     * Marks a settled online payment for refund and queues the Razorpay call in this same
     * transaction: the refund is requested if and only if the cancellation commits, and retried
     * until Razorpay accepts it.
     */
    private BigDecimal refund(com.travelplatform.entity.Payment paid) {
        paid.setStatus(com.travelplatform.entity.Payment.PaymentStatus.REFUND_PENDING);
        paymentRepository.save(paid);
        outbox.enqueue(RefundJobHandler.TYPE, new RefundJobHandler.RefundRequest(paid.getId()));
        return paid.getAmount();
    }

    private void abandon(com.travelplatform.entity.Payment unpaid) {
        unpaid.setStatus(com.travelplatform.entity.Payment.PaymentStatus.FAILED);
        paymentRepository.save(unpaid);
    }
    
    /** One booking: looks up its own photo and driver. Lists use the batched getAllBookings path. */
    private TravelBookingResponse mapToResponse(TravelBooking booking) {
        String photoPath = tripDriverPhotoRepository.findByBookingId(booking.getId())
                .map(TripDriverPhoto::getPhotoPath)
                .orElse(null);
        Driver driver = booking.getDriverId() == null ? null
                : driverRepository.findById(booking.getDriverId()).orElse(null);
        return mapToResponse(booking, photoPath, driver);
    }

    private TravelBookingResponse mapToResponse(TravelBooking booking, String endTripPhotoPath, Driver driver) {
        TravelBookingResponse response = new TravelBookingResponse();
        response.setBookingId(booking.getId());
        response.setUserName(booking.getUserName());
        response.setUserPhone(booking.getUserPhone());
        response.setFromPlace(booking.getFromPlace());
        response.setToPlace(booking.getToPlace());
        response.setFromLat(booking.getFromLat());
        response.setFromLon(booking.getFromLon());
        response.setToLat(booking.getToLat());
        response.setToLon(booking.getToLon());
        response.setFromDate(booking.getFromDate());
        response.setToDate(booking.getToDate());
        response.setTravelDays(booking.getTravelDays());
        response.setTravelMembers(booking.getTravelMembers());
        response.setAcType(booking.getAcType());
        response.setDistanceKm(booking.getDistanceKm());
        response.setEstimatedTimeMinutes(booking.getEstimatedTimeMinutes());
        response.setEstimatedTimeFormatted(formatTime(booking.getEstimatedTimeMinutes()));
        response.setTotalAmount(booking.getTotalAmount());
        response.setRouteDetails(booking.getRouteDetails());
        response.setBookingDate(booking.getBookingDate());
        response.setStatus(booking.getStatus().name());
        response.setBookingType(booking.getBookingType().name());
        response.setBookingHours(booking.getBookingHours());
        response.setPricePerHourAtBooking(booking.getPricePerHourAtBooking());

        // Include driver end-trip photo if exists
        response.setDriverEndTripPhoto(endTripPhotoPath);

        if (driver != null) {
            DriverDetailsResponse driverDetails = new DriverDetailsResponse();
            driverDetails.setDriverId(driver.getId());
            driverDetails.setName(driver.getName());
            driverDetails.setMobile(driver.getMobile());
            driverDetails.setPhoto(driver.getPhoto());
            driverDetails.setLicenseNumber(driver.getLicenseNumber());
            response.setDriver(driverDetails.withVehicle(driver));
        }

        return response.withDispatch(booking);
    }
    
    private String formatTime(Long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%d hours %d minutes", hours, mins);
    }
    
    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        // Code first, account second, one message for both failures: "User not found with email"
        // told anyone which emails have accounts.
        if (!otpService.verifyOtp(request.getEmail(), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        com.travelplatform.entity.User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // Saves the user and ends every existing session - the point of a reset is often that
        // someone else has a token.
        tokenVersionService.revokeUser(user);
        return java.util.Map.of("message", "Password reset successfully");
    }

    /**
     * A traveller who joined through Google has no password. They may save one, once, so the
     * browser can remember it and the email form works for them too. Changing an existing
     * password stays behind the OTP-checked forgot-password flow.
     */
    @Transactional
    public void setPassword(UUID userId, String password) {
        com.travelplatform.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            throw new IllegalArgumentException("This account already has a password. Use Forgot password to change it.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
    }

    @Transactional
    public com.travelplatform.entity.User updateProfile(UUID userId, com.travelplatform.dto.UserUpdateRequest request) {
        com.travelplatform.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = request.getPhone().trim();
            // Booking now depends on this number being reachable, so it is checked on the way in
            // rather than discovered to be junk when a driver tries to call.
            if (!MOBILE_PATTERN.matcher(phone).matches()) {
                throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number starting with 6-9");
            }
            // phone is a unique column; catching the clash here gives the traveller something they
            // can act on instead of a 409 about a database constraint.
            if (!phone.equals(user.getPhone())
                    && userRepository.findByPhone(phone).filter(other -> !other.getId().equals(userId)).isPresent()) {
                throw new IllegalArgumentException("That mobile number is already registered to another account");
            }
            user.setPhone(phone);
        }
        return userRepository.save(user);
    }
}
