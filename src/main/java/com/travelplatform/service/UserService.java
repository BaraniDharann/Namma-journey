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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelplatform.repository.TripDriverPhotoRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    
    @Transactional
    public TravelBookingResponse createBooking(UUID userId, TravelBookingRequest request) {
        if (request.getFromDate() == null || request.getToDate() == null) {
            throw new IllegalArgumentException("From date and to date are required");
        }
        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new IllegalArgumentException("To date cannot be before from date");
        }
        
        int travelDays = (int) ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;

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

        List<Driver> availableDrivers = driverRepository.findAvailableDrivers(request.getFromDate(), request.getToDate());
        Long assignedDriverId = availableDrivers.isEmpty() ? null : availableDrivers.get(0).getId();

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
        booking.setDriverId(assignedDriverId);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        
        TravelBooking savedBooking = bookingRepository.save(booking);

        // Send notifications
        notificationService.notifyBookingCreated(savedBooking);
        if (assignedDriverId != null) {
            Driver assignedDriver = availableDrivers.get(0);
            notificationService.notifyDriverAssigned(savedBooking, assignedDriver);
        }

        return mapToResponse(savedBooking);
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

    @Transactional
    public com.travelplatform.entity.User updateProfile(UUID userId, com.travelplatform.dto.UserUpdateRequest request) {
        com.travelplatform.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
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
