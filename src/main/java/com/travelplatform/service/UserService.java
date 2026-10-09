package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.ForgotPasswordRequest;
import com.travelplatform.dto.RouteInfo;
import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.travelplatform.repository.TripDriverPhotoRepository;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("From date and to date are required");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("To date cannot be before from date");
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
        for (int attempt = 0; attempt < MAX_DRIVER_ATTEMPTS; attempt++) {
            try {
                return saveWithDriver(userId, request, routeInfo, lostDrivers, true);
            } catch (DriverTakenException taken) {
                lostDrivers.add(taken.driverId);
            }
        }
        return saveWithDriver(userId, request, routeInfo, lostDrivers, false);
    }

    /**
     * One attempt: pick a driver (unless {@code assign} is false), insert, notify - all in one
     * short transaction, so a refused insert takes its notifications down with it.
     */
    private TravelBookingResponse saveWithDriver(UUID userId, TravelBookingRequest request, RouteInfo routeInfo,
                                                 List<Long> lostDrivers, boolean assign) {
        return transactionTemplate.execute(tx -> {
            Driver driver = null;
            if (assign) {
                List<Driver> candidates = lostDrivers.isEmpty()
                        ? driverRepository.findAvailableDrivers(request.getFromDate(), request.getToDate())
                        : driverRepository.findAvailableDriversExcluding(
                                request.getFromDate(), request.getToDate(), lostDrivers);
                driver = candidates.isEmpty() ? null : candidates.get(0);
            }

            TravelBooking savedBooking;
            try {
                // saveAndFlush, not save: the INSERT has to reach Postgres here, inside the try,
                // or the constraint would only fire at commit where it can no longer be caught
                // per attempt.
                savedBooking = bookingRepository.saveAndFlush(buildBooking(userId, request, routeInfo, driver));
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
            if (driver != null) {
                notificationService.notifyDriverAssigned(savedBooking, driver);
            }
            return mapToResponse(savedBooking);
        });
    }

    private TravelBooking buildBooking(UUID userId, TravelBookingRequest request, RouteInfo routeInfo, Driver driver) {
        int travelDays = (int) ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;

        // Determine booking type and calculate amount
        TravelBooking.BookingType bookingType = TravelBooking.BookingType.DISTANCE_BASED;
        Integer bookingHours = null;
        Double pricePerHourAtBooking = null;
        Double totalAmount;

        if ("HOUR_BASED".equalsIgnoreCase(request.getBookingType()) && request.getBookingHours() != null) {
            bookingType = TravelBooking.BookingType.HOUR_BASED;
            bookingHours = request.getBookingHours();
            pricePerHourAtBooking = ownerService.getCurrentPricePerHour();
            totalAmount = bookingHours * pricePerHourAtBooking;
        } else {
            Double pricePerKm = ownerService.getCurrentPricePerKm();
            totalAmount = routeInfo.getDistanceKm() * pricePerKm;
        }

        TravelBooking booking = new TravelBooking();
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
        return booking;
    }

    /** True when Postgres refused the insert because the driver already has an overlapping trip. */
    private static boolean isDriverOverlap(DataIntegrityViolationException ex) {
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

    public List<TravelBookingResponse> getAllBookings(UUID userId) {
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
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
        Double pricePerHourAtBooking = null;
        Double totalAmount;

        if ("HOUR_BASED".equalsIgnoreCase(request.getBookingType()) && request.getBookingHours() != null) {
            bookingType = TravelBooking.BookingType.HOUR_BASED;
            bookingHours = request.getBookingHours();
            pricePerHourAtBooking = ownerService.getCurrentPricePerHour();
            totalAmount = bookingHours * pricePerHourAtBooking;
        } else {
            Double pricePerKm = ownerService.getCurrentPricePerKm();
            totalAmount = routeInfo.getDistanceKm() * pricePerKm;
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

        TravelBooking updatedBooking = bookingRepository.save(booking);
        return mapToResponse(updatedBooking);
    }
    
    @Transactional
    public void deleteBooking(UUID userId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to booking");
        }
        
        bookingRepository.delete(booking);
    }
    
    private TravelBookingResponse mapToResponse(TravelBooking booking) {
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
        tripDriverPhotoRepository.findByBookingId(booking.getId()).ifPresent(photo ->
            response.setDriverEndTripPhoto(photo.getPhotoPath())
        );

        if (booking.getDriverId() != null) {
            driverRepository.findById(booking.getDriverId()).ifPresent(driver -> {
                DriverDetailsResponse driverDetails = new DriverDetailsResponse();
                driverDetails.setDriverId(driver.getId());
                driverDetails.setName(driver.getName());
                driverDetails.setMobile(driver.getMobile());
                driverDetails.setPhoto(driver.getPhoto());
                driverDetails.setLicenseNumber(driver.getLicenseNumber());
                response.setDriver(driverDetails);
            });
        }

        return response;
    }
    
    private String formatTime(Long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%d hours %d minutes", hours, mins);
    }
    
    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        com.travelplatform.entity.User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with email: " + request.getEmail()));
        if (!otpService.verifyOtp(request.getEmail(), request.getOtp())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
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

    @Transactional
    public TravelBookingResponse confirmBooking(UUID userId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to booking");
        }
        
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING) {
            throw new RuntimeException("Only pending bookings can be confirmed");
        }
        
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        TravelBooking confirmedBooking = bookingRepository.save(booking);
        return mapToResponse(confirmedBooking);
    }
}
