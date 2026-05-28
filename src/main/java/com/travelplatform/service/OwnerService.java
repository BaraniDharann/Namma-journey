package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.PricingConfig;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PricingConfigRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.TripDriverPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerService {
    
    private final TravelBookingRepository travelBookingRepository;
    private final PricingConfigRepository pricingConfigRepository;
    private final DriverRepository driverRepository;
    private final TripDriverPhotoRepository tripDriverPhotoRepository;
    
    public List<TravelBooking> getAllBookings() {
        return travelBookingRepository.findAll();
    }
    
    @CacheEvict(value = "pricing", allEntries = true)
    public Map<String, Object> setPricePerKm(Double pricePerKm, Long ownerId) {
        PricingConfig config = new PricingConfig();
        config.setPricePerKm(pricePerKm);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(ownerId);
        pricingConfigRepository.save(config);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Price per km updated successfully");
        response.put("pricePerKm", pricePerKm);
        response.put("updatedAt", config.getUpdatedAt());
        return response;
    }
    
    @Cacheable(value = "pricing")
    public Double getCurrentPricePerKm() {
        return pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
            .map(PricingConfig::getPricePerKm)
            .orElse(10.0);
    }

    @CacheEvict(value = "hourlyPricing", allEntries = true)
    public Map<String, Object> setPricePerHour(Double pricePerHour, Long ownerId) {
        PricingConfig config = pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
            .orElse(new PricingConfig());
        config.setPricePerHour(pricePerHour);
        if (config.getPricePerKm() == null) config.setPricePerKm(10.0);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(ownerId);
        pricingConfigRepository.save(config);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Price per hour updated successfully");
        response.put("pricePerHour", pricePerHour);
        response.put("updatedAt", config.getUpdatedAt());
        return response;
    }

    @Cacheable(value = "hourlyPricing")
    public Double getCurrentPricePerHour() {
        return pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
            .map(PricingConfig::getPricePerHour)
            .orElse(150.0); // default ₹150/hour
    }

    public Map<String, Object> getDriverPhotoForBooking(UUID bookingId) {
        TripDriverPhoto photo = tripDriverPhotoRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new RuntimeException("No driver photo found for this booking"));
        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", bookingId);
        response.put("driverId", photo.getDriverId());
        response.put("photoPath", photo.getPhotoPath());
        response.put("capturedAt", photo.getCapturedAt());
        return response;
    }
    
    @Cacheable(value = "dailyRevenue", key = "#date.toString()")
    public Map<String, Object> getDailyRevenue(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<TravelBooking> bookings = travelBookingRepository.findCompletedBookingsBetween(
                TravelBooking.BookingStatus.COMPLETED, start, end);
        return buildRevenueResponse(bookings, "Daily", date.toString());
    }

    @Cacheable(value = "monthlyRevenue", key = "#year + '-' + #month")
    public Map<String, Object> getMonthlyRevenue(int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = startDate.plusMonths(1).atStartOfDay();
        List<TravelBooking> bookings = travelBookingRepository.findCompletedBookingsBetween(
                TravelBooking.BookingStatus.COMPLETED, start, end);
        return buildRevenueResponse(bookings, "Monthly", year + "-" + String.format("%02d", month));
    }

    @Cacheable(value = "yearlyRevenue", key = "#year")
    public Map<String, Object> getYearlyRevenue(int year) {
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        List<TravelBooking> bookings = travelBookingRepository.findCompletedBookingsBetween(
                TravelBooking.BookingStatus.COMPLETED, start, end);
        return buildRevenueResponse(bookings, "Yearly", String.valueOf(year));
    }
    
    public Map<String, Object> getTripRevenue(UUID tripId) {
        TravelBooking booking = travelBookingRepository.findById(tripId)
            .orElseThrow(() -> new RuntimeException("Trip not found"));
        Map<String, Object> response = new HashMap<>();
        response.put("tripId", booking.getId());
        response.put("fromPlace", booking.getFromPlace());
        response.put("toPlace", booking.getToPlace());
        response.put("distanceKm", booking.getDistanceKm());
        response.put("travelDays", booking.getTravelDays());
        response.put("totalAmount", booking.getTotalAmount());
        response.put("status", booking.getStatus());
        response.put("bookingDate", booking.getBookingDate());
        return response;
    }
    
    private Map<String, Object> buildRevenueResponse(List<TravelBooking> bookings, String period, String periodValue) {
        Map<String, Object> response = new HashMap<>();
        response.put("period", period);
        response.put("periodValue", periodValue);
        response.put("totalTrips", bookings.size());
        // Null-safe aggregates: legacy bookings may have null distance/amount/days.
        response.put("totalDistance", bookings.stream().mapToDouble(b -> b.getDistanceKm() != null ? b.getDistanceKm() : 0.0).sum());
        response.put("totalRevenue", bookings.stream().mapToDouble(b -> b.getTotalAmount() != null ? b.getTotalAmount() : 0.0).sum());
        response.put("totalTravelDays", bookings.stream().mapToInt(b -> b.getTravelDays() != null ? b.getTravelDays() : 0).sum());
        response.put("trips", bookings.stream().map(b -> {
            Map<String, Object> trip = new HashMap<>();
            trip.put("id", b.getId());
            trip.put("fromPlace", b.getFromPlace());
            trip.put("toPlace", b.getToPlace());
            trip.put("distanceKm", b.getDistanceKm());
            trip.put("travelDays", b.getTravelDays());
            trip.put("totalAmount", b.getTotalAmount());
            trip.put("bookingDate", b.getBookingDate());
            return trip;
        }).collect(Collectors.toList()));
        return response;
    }
    
    @Cacheable(value = "allDrivers")
    public List<DriverDetailsResponse> getAllDrivers() {
        return driverRepository.findAll().stream()
                .map(this::mapToDriverDetails)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "driverById", key = "#driverId")
    public DriverDetailsResponse getDriverById(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        return mapToDriverDetails(driver);
    }

    private DriverDetailsResponse mapToDriverDetails(Driver driver) {
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

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true)
    })
    public TravelBookingResponse assignDriver(UUID bookingId, Long driverId) {
        TravelBooking booking = travelBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getStatus() == TravelBooking.BookingStatus.COMPLETED
                || booking.getStatus() == TravelBooking.BookingStatus.CANCELLED) {
            throw new RuntimeException("Cannot assign driver to a " + booking.getStatus() + " booking");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        if (driver.getStatus() != Driver.Status.ACTIVE) {
            throw new RuntimeException("Driver is not active and cannot be assigned");
        }

        long overlapping = driverRepository.countOverlappingActiveBookings(
                driverId, booking.getFromDate(), booking.getToDate(), bookingId);
        if (overlapping > 0) {
            throw new RuntimeException(
                    "Driver already has an active trip overlapping these dates ("
                            + booking.getFromDate() + " to " + booking.getToDate() + ")");
        }

        booking.setDriverId(driverId);
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        TravelBooking updatedBooking = travelBookingRepository.save(booking);

        return mapToResponse(updatedBooking, driver);
    }
    
    private TravelBookingResponse mapToResponse(TravelBooking booking, Driver driver) {
        TravelBookingResponse response = new TravelBookingResponse();
        response.setBookingId(booking.getId());
        response.setUserName(booking.getUserName());
        response.setUserPhone(booking.getUserPhone());
        response.setFromPlace(booking.getFromPlace());
        response.setToPlace(booking.getToPlace());
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
        
        if (driver != null) {
            DriverDetailsResponse driverDetails = new DriverDetailsResponse();
            driverDetails.setDriverId(driver.getId());
            driverDetails.setName(driver.getName());
            driverDetails.setMobile(driver.getMobile());
            driverDetails.setPhoto(driver.getPhoto());
            driverDetails.setLicenseNumber(driver.getLicenseNumber());
            response.setDriver(driverDetails);
        }
        
        return response;
    }
    
    private String formatTime(Long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%d hours %d minutes", hours, mins);
    }
}
