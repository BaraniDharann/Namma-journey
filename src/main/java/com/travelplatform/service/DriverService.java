package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final TravelBookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final NotificationService notificationService;
    private final TripDriverPhotoRepository tripDriverPhotoRepository;
    private final BookingDriverRejectionRepository rejectionRepository;
    private final FileStorageService fileStorageService;

    /** Trip dates are Indian calendar days. Not final so tests can fix "today". */
    private java.time.Clock clock = java.time.Clock.system(java.time.ZoneId.of("Asia/Kolkata"));

    /** Days before its date a trip may be started. 0 in production; the end-to-end suite raises it. */
    @org.springframework.beans.factory.annotation.Value("${app.trips.early-start-days:0}")
    private long earlyStartDays = 0;

    // A driver viewing their own documents gets the same expiring signed links the owner does.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.storage.SignedUploadUrls signedUploadUrls;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // Field-injected, like signedUploadUrls, so the tests that build DriverService by hand still
    // compile; null there, and Ride Now handling is skipped.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DispatchService dispatchService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DriverPresenceService presenceService;
    
    @Transactional
    public void updateAvailability(Long driverId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        Driver.Status parsed;
        try {
            parsed = Driver.Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status. Use ACTIVE or INACTIVE");
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        driver.setStatus(parsed);
        driverRepository.save(driver);
        // Offline means no Ride Now offers, at once - not when the last position report lapses.
        if (parsed == Driver.Status.INACTIVE && presenceService != null) {
            presenceService.remove(driverId);
        }
    }

    /** The driver's own City rides / Trips switches. Bike and auto drivers cannot take trips. */
    @Transactional
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "allDrivers", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "driverById", key = "#driverId")
    })
    public DriverDetailsResponse updateModes(Long driverId, Boolean servesCity, Boolean servesTrip) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        DriverVehicles.setModes(driver, servesCity, servesTrip);
        driverRepository.save(driver);
        return getDriverProfile(driverId);
    }

    public DriverDetailsResponse getDriverProfile(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        DriverDetailsResponse d = new DriverDetailsResponse();
        d.setDriverId(driver.getId());
        d.setName(driver.getName());
        d.setMobile(driver.getMobile());
        d.setEmail(driver.getEmail());
        d.setPhoto(driver.getPhoto());
        d.setLicenseNumber(driver.getLicenseNumber());
        d.setLicensePhoto(signedUploadUrls == null ? driver.getLicensePhoto() : signedUploadUrls.signIfSensitive(driver.getLicensePhoto()));
        d.setAadhaarNumber(driver.getAadhaarNumber());
        d.setAadhaarPhoto(signedUploadUrls == null ? driver.getAadhaarPhoto() : signedUploadUrls.signIfSensitive(driver.getAadhaarPhoto()));
        d.setStatus(driver.getStatus().name());
        d.setRole(driver.getRole());
        d.setEmailVerified(driver.isEmailVerified());
        d.setFirstLogin(driver.isFirstLogin());
        d.setCreatedAt(driver.getCreatedAt());
        return d.withVehicle(driver).withVerification(driver,
                path -> signedUploadUrls == null ? path : signedUploadUrls.signIfSensitive(path));
    }

    public List<TravelBookingResponse> getAssignedBookings(Long driverId) {
        // Indexed lookup; avoids a full table scan that previously degraded as the bookings table grew.
        List<TravelBooking> trips = bookingRepository.findByDriverId(driverId);
        if (trips.isEmpty()) {
            return List.of();
        }
        // Three queries for the whole list: the trips, all their end-trip photos, and the driver -
        // who is the same person on every row. Mapping each trip alone ran two queries per trip.
        java.util.Map<UUID, String> photoByTrip = new java.util.HashMap<>();
        tripDriverPhotoRepository.findByBookingIdIn(trips.stream().map(TravelBooking::getId).toList())
                .forEach(p -> photoByTrip.putIfAbsent(p.getBookingId(), p.getPhotoPath()));
        Driver driver = driverRepository.findById(driverId).orElse(null);
        return trips.stream()
                .map(t -> mapToResponse(t, photoByTrip.get(t.getId()), driver))
                .collect(Collectors.toList());
    }

    @Transactional
    public TravelBookingResponse acceptBooking(Long driverId, String bookingId) {
        TravelBooking booking = bookingRepository.findById(java.util.UUID.fromString(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("This booking is not assigned to you");
        }

        // Accepting again is a no-op, not an error: Telegram redelivers webhook updates and a
        // driver double-taps, and neither should re-notify the passenger.
        if (booking.getStatus() == TravelBooking.BookingStatus.CONFIRMED) {
            return mapToResponse(booking);
        }
        // Anything past PENDING is settled. Without this a stale button could drag a cancelled
        // or completed trip back to CONFIRMED.
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING) {
            throw new RuntimeException("Only pending bookings can be accepted");
        }
        // A Ride Now offer is only theirs until its deadline; after that it is on its way to the
        // next driver, and a late tap must not snatch it back.
        if (dispatchService != null && dispatchService.offerExpired(booking)) {
            throw new RuntimeException("This ride offer has expired");
        }

        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        DispatchService.markMatched(booking);
        TravelBooking updated = bookingRepository.save(booking);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        notificationService.notifyTripAccepted(updated, driver);

        return mapToResponse(updated);
    }

    /**
     * A driver declines a trip, and the trip is immediately offered to someone else.
     *
     * <p>Rejection used to leave the booking PENDING with no driver and nothing scheduled to
     * pick it back up: it sat there until the owner happened to read the notification. The
     * booking now goes to the next available driver in the same transaction, and only falls
     * back to the owner when there genuinely is nobody left to offer it to.
     *
     * <p>The replacement is offered the trip, not given it - the booking stays PENDING with a
     * new {@code driverId}, exactly as it does when a booking is first created, so the new
     * driver still has to accept.
     */
    @Transactional
    public TravelBookingResponse rejectBooking(Long driverId, String bookingId) {
        TravelBooking booking = bookingRepository.findById(java.util.UUID.fromString(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("This booking is not assigned to you");
        }

        // A driver may decline any trip that has not started - including one the owner assigned
        // by hand, which arrives already CONFIRMED. A trip under way or finished cannot be handed
        // to someone else: reassigning it would reset it to PENDING mid-journey.
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING
                && booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("A trip that has started or ended can no longer be declined");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        // A Ride Now trip goes back to the nearest-driver search, not to whoever is free by date.
        if (booking.getDispatchMode() == TravelBooking.DispatchMode.RIDE_NOW && dispatchService != null) {
            dispatchService.release(booking, driverId);
            return mapToResponse(booking);
        }

        // Record the refusal before looking for a replacement. Availability is "no overlapping
        // active trip", which this driver satisfies again the moment their id leaves the
        // booking - so this row is the only thing stopping the trip bouncing back to them.
        if (!rejectionRepository.existsByBookingIdAndDriverId(booking.getId(), driverId)) {
            BookingDriverRejection rejection = new BookingDriverRejection();
            rejection.setBookingId(booking.getId());
            rejection.setDriverId(driverId);
            rejectionRepository.save(rejection);
        }

        List<Long> declined = new ArrayList<>(rejectionRepository.findDriverIdsByBookingId(booking.getId()));
        if (!declined.contains(driverId)) {
            declined.add(driverId);
        }

        Driver replacement = driverRepository
                .findAvailableDriversExcluding(booking.getFromDate(), booking.getToDate(), declined)
                .stream()
                .filter(d -> UserService.suitsAc(d, booking.getAcType()))
                .findFirst()
                .orElse(null);

        booking.setDriverId(replacement != null ? replacement.getId() : null);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        TravelBooking updated = bookingRepository.save(booking);

        notificationService.notifyTripRejected(updated, driver, replacement);
        if (replacement != null) {
            notificationService.notifyDriverAssigned(updated, replacement);
        }

        return mapToResponse(updated);
    }

    @Transactional
    public TravelBookingResponse startTrip(Long driverId, String bookingId) {
        TravelBooking booking = bookingRepository.findById(java.util.UUID.fromString(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("This booking is not assigned to you");
        }

        if (booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Only confirmed bookings can be started");
        }
        // A December trip could be started in October: tracking switched on, the passenger told
        // the car was on its way, and the trip blocked from being cancelled.
        if (booking.getFromDate() != null
                && java.time.LocalDate.now(clock).isBefore(booking.getFromDate().minusDays(earlyStartDays))) {
            throw new RuntimeException("This trip starts on " + booking.getFromDate() + " and cannot be started before then");
        }

        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        TravelBooking updated = bookingRepository.save(booking);
        return mapToResponse(updated);
    }

    @Transactional
    public TripDriverPhoto uploadEndTripPhoto(Long driverId, UUID bookingId, MultipartFile photo) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("This booking is not assigned to you");
        }

        // The end-of-trip photo proves a ride happened, so the ride must have started.
        if (booking.getStatus() != TravelBooking.BookingStatus.STARTED) {
            throw new RuntimeException("The end-of-trip photo can only be uploaded once the trip has started");
        }

        if (photo == null || photo.isEmpty()) {
            throw new RuntimeException("Photo is required");
        }

        // Through the shared FileStore (disk volume or S3/R2), not ./driverphoto: that folder sat
        // outside the Docker uploads volume, so every redeploy deleted the photos, and a second
        // instance could never see them. Type checking and naming live in FileStorageService.
        String photoPath = fileStorageService.storeImage(photo, "trips/trip_" + bookingId, "driver_" + driverId);

        // Replace an earlier photo: the row AND the file, which used to stay in storage forever.
        tripDriverPhotoRepository.findByBookingIdAndDriverId(bookingId, driverId)
                .ifPresent(old -> {
                    tripDriverPhotoRepository.delete(old);
                    fileStorageService.delete(old.getPhotoPath());
                });

        TripDriverPhoto tripPhoto = new TripDriverPhoto();
        tripPhoto.setBookingId(bookingId);
        tripPhoto.setDriverId(driverId);
        tripPhoto.setPhotoPath(photoPath);

        return tripDriverPhotoRepository.save(tripPhoto);
    }

    /** One trip: looks up its own photo and driver. The list uses the batched getAssignedBookings path. */
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
        response.setRouteDetails(booking.getRouteDetails());
        response.setBookingDate(booking.getBookingDate());
        response.setStatus(booking.getStatus().name());
        response.setBookingType(booking.getBookingType().name());
        response.setBookingHours(booking.getBookingHours());
        response.setPricePerHourAtBooking(booking.getPricePerHourAtBooking());
        response.setTotalAmount(booking.getTotalAmount());

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
}
