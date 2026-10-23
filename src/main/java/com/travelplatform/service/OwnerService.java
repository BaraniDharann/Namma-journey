package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.MonthlyRevenue;
import com.travelplatform.dto.RevenueTotals;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OwnerService {

    /** Used until the owner sets prices for the first time. */
    private static final BigDecimal DEFAULT_PRICE_PER_KM = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_PRICE_PER_HOUR = new BigDecimal("150.00");
    
    private final TravelBookingRepository travelBookingRepository;
    private final PricingConfigRepository pricingConfigRepository;
    private final DriverRepository driverRepository;
    private final TripDriverPhotoRepository tripDriverPhotoRepository;
    private final NotificationService notificationService;

    // Field-injected so existing constructor-built tests are unaffected; null-guarded where used.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.repository.PaymentRepository paymentRepository;

    // Identity-document links handed to the owner expire and are server-signed (SignedUploadUrls).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.storage.SignedUploadUrls signedUploadUrls;
    
    public List<TravelBooking> getAllBookings() {
        return travelBookingRepository.findAll();
    }
    
    // Every price change is a NEW row (history), carrying the other price forward. A per-km change
    // used to save a row with no hourly price, so the hourly price silently fell back to the default
    // once its cache expired; both caches are evicted so every instance sees both prices at once.
    @Caching(evict = {
            @CacheEvict(value = "pricing", allEntries = true),
            @CacheEvict(value = "hourlyPricing", allEntries = true)
    })
    public Map<String, Object> setPricePerKm(BigDecimal requestedPricePerKm, Long ownerId) {
        BigDecimal pricePerKm = Money.positivePrice(requestedPricePerKm, "Price per km");
        BigDecimal currentHourly = pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
                .map(PricingConfig::getPricePerHour)
                .orElse(null);
        PricingConfig config = new PricingConfig();
        config.setPricePerKm(pricePerKm);
        config.setPricePerHour(currentHourly);
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
    public BigDecimal getCurrentPricePerKm() {
        return pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
            .map(PricingConfig::getPricePerKm)
            .orElse(DEFAULT_PRICE_PER_KM);
    }

    @Caching(evict = {
            @CacheEvict(value = "pricing", allEntries = true),
            @CacheEvict(value = "hourlyPricing", allEntries = true)
    })
    public Map<String, Object> setPricePerHour(BigDecimal requestedPricePerHour, Long ownerId) {
        BigDecimal pricePerHour = Money.positivePrice(requestedPricePerHour, "Price per hour");
        // A new row, never an edit of the latest one: editing in place destroyed pricing history.
        BigDecimal currentKm = pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
                .map(PricingConfig::getPricePerKm)
                .orElse(DEFAULT_PRICE_PER_KM);
        PricingConfig config = new PricingConfig();
        config.setPricePerHour(pricePerHour);
        config.setPricePerKm(currentKm);
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
    public BigDecimal getCurrentPricePerHour() {
        return pricingConfigRepository.findTopByOrderByUpdatedAtDesc()
            .map(PricingConfig::getPricePerHour)
            .orElse(DEFAULT_PRICE_PER_HOUR);
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
    
    /*
     * Revenue is summed by Postgres over NUMERIC money (TravelBookingRepository.revenueTotals,
     * monthlyRevenue, revenueTrips) instead of loading every completed booking as a full entity
     * and adding doubles in Java: exact to the paisa, and memory no longer grows with a year of
     * trips. Every window is half-open [start, end), so a booking at exactly midnight on the 1st
     * is counted once, in the new period. The JSON shape is unchanged.
     */

    @Cacheable(value = "dailyRevenue", key = "#date.toString()")
    public Map<String, Object> getDailyRevenue(LocalDate date) {
        return revenueReport(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), "Daily", date.toString());
    }

    @Cacheable(value = "monthlyRevenue", key = "#year + '-' + #month")
    public Map<String, Object> getMonthlyRevenue(int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        return revenueReport(startDate.atStartOfDay(), startDate.plusMonths(1).atStartOfDay(),
                "Monthly", year + "-" + String.format("%02d", month));
    }

    /**
     * Revenue for all twelve months of a year: one GROUP BY query, one row per month that had
     * trips. The revenue screen's twelve-bar chart used to cost 12 HTTP requests, then a full
     * year of entities in memory; now it is a dozen rows at most.
     */
    @Cacheable(value = "monthlyRevenueSeries", key = "#year")
    public Map<String, Object> getMonthlyRevenueSeries(int year) {
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        Map<Integer, MonthlyRevenue> byMonth = new HashMap<>();
        for (MonthlyRevenue row : travelBookingRepository.monthlyRevenue(start, end)) {
            byMonth.put(row.month(), row);
        }

        List<Map<String, Object>> months = new ArrayList<>(12);
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalTrips = 0;
        for (int m = 1; m <= 12; m++) {
            MonthlyRevenue row = byMonth.getOrDefault(m, new MonthlyRevenue(m, 0, BigDecimal.ZERO, 0.0));
            Map<String, Object> month = new HashMap<>();
            month.put("month", m);
            month.put("periodValue", year + "-" + String.format("%02d", m));
            month.put("totalTrips", row.trips());
            month.put("totalRevenue", row.revenue());
            month.put("totalDistance", row.distanceKm());
            months.add(month);
            totalRevenue = totalRevenue.add(row.revenue());
            totalTrips += row.trips();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("period", "MonthlySeries");
        response.put("periodValue", String.valueOf(year));
        response.put("year", year);
        response.put("months", months);
        response.put("totalRevenue", totalRevenue);
        response.put("totalTrips", totalTrips);
        return response;
    }

    @Cacheable(value = "yearlyRevenue", key = "#year")
    public Map<String, Object> getYearlyRevenue(int year) {
        return revenueReport(LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year + 1, 1, 1).atStartOfDay(),
                "Yearly", String.valueOf(year));
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
    
    private Map<String, Object> revenueReport(LocalDateTime start, LocalDateTime end, String period, String periodValue) {
        RevenueTotals totals = travelBookingRepository.revenueTotals(start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);
        response.put("periodValue", periodValue);
        response.put("totalTrips", totals.trips());
        response.put("totalDistance", totals.distanceKm());
        response.put("totalRevenue", totals.revenue());
        response.put("totalTravelDays", totals.travelDays());
        response.put("trips", travelBookingRepository.revenueTrips(start, end).stream().map(t -> {
            Map<String, Object> trip = new HashMap<>();
            trip.put("id", t.id());
            trip.put("fromPlace", t.fromPlace());
            trip.put("toPlace", t.toPlace());
            trip.put("distanceKm", t.distanceKm());
            trip.put("travelDays", t.travelDays());
            trip.put("totalAmount", t.totalAmount());
            trip.put("bookingDate", t.bookingDate());
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
        d.setLicensePhoto(signedUploadUrls == null ? driver.getLicensePhoto() : signedUploadUrls.signIfSensitive(driver.getLicensePhoto()));
        d.setAadhaarNumber(driver.getAadhaarNumber());
        d.setAadhaarPhoto(signedUploadUrls == null ? driver.getAadhaarPhoto() : signedUploadUrls.signIfSensitive(driver.getAadhaarPhoto()));
        d.setStatus(driver.getStatus().name());
        d.setRole(driver.getRole());
        d.setEmailVerified(driver.isEmailVerified());
        d.setFirstLogin(driver.isFirstLogin());
        d.setTelegramLinked(driver.getTelegramChatId() != null);
        d.setCreatedAt(driver.getCreatedAt());
        return d;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public TravelBookingResponse assignDriver(UUID bookingId, Long driverId) {
        TravelBooking booking = travelBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Only before the trip starts. Reassigning a STARTED trip threw it back to CONFIRMED with a
        // new driver while the old one was still driving the passenger.
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING
                && booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Cannot assign a driver to a " + booking.getStatus() + " booking");
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

        Long previousDriverId = booking.getDriverId();
        booking.setDriverId(driverId);
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        // The owner's choice ends a Ride Now search; the sweep leaves MATCHED bookings alone.
        DispatchService.markMatched(booking);
        TravelBooking updatedBooking = travelBookingRepository.save(booking);

        // An unpaid payment follows the trip to its new driver, so the payment record and the
        // booking never name different drivers.
        if (paymentRepository != null) {
            paymentRepository.findByBookingId(bookingId)
                    .filter(p -> p.getStatus() != com.travelplatform.entity.Payment.PaymentStatus.VERIFIED)
                    .ifPresent(p -> {
                        p.setDriverId(driverId);
                        paymentRepository.save(p);
                    });
        }

        // A driver given a trip by hand must hear about it the same way an auto-assigned one does
        // (in-app alert, email, Telegram card). Re-saving the same driver is not news.
        if (!driverId.equals(previousDriverId)) {
            notificationService.notifyDriverAssigned(updatedBooking, driver);
        }

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
        
        return response.withDispatch(booking);
    }
    
    private String formatTime(Long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%d hours %d minutes", hours, mins);
    }
}
