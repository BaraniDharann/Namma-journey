package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;
    
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
        d.setLicensePhoto(driver.getLicensePhoto());
        d.setAadhaarNumber(driver.getAadhaarNumber());
        d.setAadhaarPhoto(driver.getAadhaarPhoto());
        d.setStatus(driver.getStatus().name());
        d.setRole(driver.getRole());
        d.setEmailVerified(driver.isEmailVerified());
        d.setFirstLogin(driver.isFirstLogin());
        d.setCreatedAt(driver.getCreatedAt());
        return d;
    }

    public List<TravelBookingResponse> getAssignedBookings(Long driverId) {
        // Indexed lookup; avoids a full table scan that previously degraded as the bookings table grew.
        return bookingRepository.findByDriverId(driverId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TravelBookingResponse acceptBooking(Long driverId, String bookingId) {
        TravelBooking booking = bookingRepository.findById(java.util.UUID.fromString(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("This booking is not assigned to you");
        }

        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        TravelBooking updated = bookingRepository.save(booking);

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        notificationService.notifyTripAccepted(updated, driver);

        return mapToResponse(updated);
    }

    @Transactional
    public TravelBookingResponse rejectBooking(Long driverId, String bookingId) {
        TravelBooking booking = bookingRepository.findById(java.util.UUID.fromString(bookingId))
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            throw new RuntimeException("This booking is not assigned to you");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        booking.setDriverId(null);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        TravelBooking updated = bookingRepository.save(booking);

        notificationService.notifyTripRejected(updated, driver);

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

        if (booking.getStatus() != TravelBooking.BookingStatus.STARTED
                && booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Photo can only be uploaded for active trips");
        }

        if (photo == null || photo.isEmpty()) {
            throw new RuntimeException("Photo is required");
        }

        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed");
        }

        try {
            String tripFolder = "trip_" + bookingId;
            Path dirPath = Paths.get("driverphoto", tripFolder);
            Files.createDirectories(dirPath);

            String filename = "driver_" + driverId + "_" + System.currentTimeMillis()
                    + getExtension(photo.getOriginalFilename());
            Path filePath = dirPath.resolve(filename);
            Files.copy(photo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String photoPath = "/driverphoto/" + tripFolder + "/" + filename;

            // Remove existing photo if re-uploading
            tripDriverPhotoRepository.findByBookingIdAndDriverId(bookingId, driverId)
                    .ifPresent(tripDriverPhotoRepository::delete);

            TripDriverPhoto tripPhoto = new TripDriverPhoto();
            tripPhoto.setBookingId(bookingId);
            tripPhoto.setDriverId(driverId);
            tripPhoto.setPhotoPath(photoPath);

            return tripDriverPhotoRepository.save(tripPhoto);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save photo: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf("."));
        }
        return ".jpg";
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
        response.setRouteDetails(booking.getRouteDetails());
        response.setBookingDate(booking.getBookingDate());
        response.setStatus(booking.getStatus().name());
        response.setBookingType(booking.getBookingType().name());
        response.setBookingHours(booking.getBookingHours());
        response.setPricePerHourAtBooking(booking.getPricePerHourAtBooking());
        response.setTotalAmount(booking.getTotalAmount());

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
}
