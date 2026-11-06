package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.MonthlyRevenue;
import com.travelplatform.dto.RevenueTotals;
import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
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

    private final TravelBookingRepository travelBookingRepository;
    private final com.travelplatform.repository.OwnerRepository ownerRepository;
    private final DriverRepository driverRepository;
    private final TripDriverPhotoRepository tripDriverPhotoRepository;
    private final NotificationService notificationService;

    // Field-injected so existing constructor-built tests are unaffected; null-guarded where used.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TripCars tripCars;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.repository.PaymentRepository paymentRepository;

    // Identity-document links handed to the owner expire and are server-signed (SignedUploadUrls).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.storage.SignedUploadUrls signedUploadUrls;

    // The matching engine's copy of driver state; null in tests that build this service by hand.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.dispatch.DriverStateUpdates driverStates;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DispatchService dispatchService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.repository.VehicleRepository vehicleRepository;

    // Stateless: reads the request's owner (OwnerScopeFilter), so tests bind OwnerScope directly.
    private final com.travelplatform.config.OwnerScope ownerScope = new com.travelplatform.config.OwnerScope();

    /** One owner's Trips, newest first (their Bookings screen). */
    public List<TravelBooking> getTripBookings(Long ownerId) {
        return travelBookingRepository.findByOwnerIdAndServiceModeOrderByBookingDateDesc(ownerId, TravelBooking.ServiceMode.TRIP);
    }

    /** The platform's City rides, newest first (a platform screen). */
    public List<TravelBooking> getCityRides() {
        return travelBookingRepository.findByOwnerIdIsNullAndServiceModeOrderByBookingDateDesc(TravelBooking.ServiceMode.CITY);
    }
    
    /** The calling owner's own profile, with whether they are the platform owner (NJO-0001). */
    @Transactional(readOnly = true)
    public com.travelplatform.dto.OwnerProfileView profile(Long ownerId, boolean platform) {
        com.travelplatform.entity.Owner o = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Owner not found"));
        List<String> cities = ServiceAreas.describe(o);
        return new com.travelplatform.dto.OwnerProfileView(o.getId(), o.getOwnerCode(), o.getTravelsName(),
                o.getContactName(), o.getPhone(), o.getEmail(), cities, o.getBankAccountHolder(),
                o.getBankAccountNumber() == null ? null : OwnerApplicationChecks.maskAccount(o.getBankAccountNumber()),
                o.getBankIfsc(), o.getBankName(), o.getUpiId(),
                o.getAssignmentMode() == null ? null : o.getAssignmentMode().name(), platform,
                (o.getFleetType() == null ? com.travelplatform.entity.Owner.FleetType.CARS : o.getFleetType()).name());
    }

    /** What the owner runs (V30): CARS, BUSES or BOTH; it decides which screens their menu shows. */
    @Transactional
    public com.travelplatform.entity.Owner.FleetType setFleetType(Long ownerId, String fleetType) {
        com.travelplatform.entity.Owner.FleetType t;
        try {
            t = com.travelplatform.entity.Owner.FleetType.valueOf(String.valueOf(fleetType).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Fleet type must be CARS, BUSES or BOTH");
        }
        com.travelplatform.entity.Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Owner not found"));
        owner.setFleetType(t);
        ownerRepository.save(owner);
        return t;
    }

    /** How this owner's Trips get a driver: AUTO (first free driver and car) or MANUAL (the owner assigns). */
    @Transactional
    public com.travelplatform.entity.Owner.AssignmentMode setAssignmentMode(Long ownerId, String mode) {
        com.travelplatform.entity.Owner.AssignmentMode m;
        try {
            m = com.travelplatform.entity.Owner.AssignmentMode.valueOf(String.valueOf(mode).trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Assignment mode must be AUTO or MANUAL");
        }
        com.travelplatform.entity.Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Owner not found"));
        owner.setAssignmentMode(m);
        ownerRepository.save(owner);
        return m;
    }

    public Map<String, Object> getDriverPhotoForBooking(UUID bookingId) {
        ownBooking(bookingId, ownerScope.ownerId());
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

    // Revenue is per owner (their Trips), or with ownerId null the platform's City rides. Every
    // cache key starts with whose revenue it is, so one owner's figures never answer another.

    @Cacheable(value = "dailyRevenue", key = "(#ownerId == null ? 'platform' : #ownerId) + ':' + #date.toString()")
    public Map<String, Object> getDailyRevenue(Long ownerId, LocalDate date) {
        return revenueReport(ownerId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), "Daily", date.toString());
    }

    @Cacheable(value = "monthlyRevenue", key = "(#ownerId == null ? 'platform' : #ownerId) + ':' + #year + '-' + #month")
    public Map<String, Object> getMonthlyRevenue(Long ownerId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        return revenueReport(ownerId, startDate.atStartOfDay(), startDate.plusMonths(1).atStartOfDay(),
                "Monthly", year + "-" + String.format("%02d", month));
    }

    /**
     * Revenue for all twelve months of a year: one GROUP BY query, one row per month that had
     * trips. The revenue screen's twelve-bar chart used to cost 12 HTTP requests, then a full
     * year of entities in memory; now it is a dozen rows at most.
     */
    @Cacheable(value = "monthlyRevenueSeries", key = "(#ownerId == null ? 'platform' : #ownerId) + ':' + #year")
    public Map<String, Object> getMonthlyRevenueSeries(Long ownerId, int year) {
        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        Map<Integer, MonthlyRevenue> byMonth = new HashMap<>();
        for (MonthlyRevenue row : travelBookingRepository.monthlyRevenue(start, end, ownerId)) {
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

    @Cacheable(value = "yearlyRevenue", key = "(#ownerId == null ? 'platform' : #ownerId) + ':' + #year")
    public Map<String, Object> getYearlyRevenue(Long ownerId, int year) {
        return revenueReport(ownerId, LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year + 1, 1, 1).atStartOfDay(),
                "Yearly", String.valueOf(year));
    }
    
    public Map<String, Object> getTripRevenue(UUID tripId) {
        TravelBooking booking = ownBooking(tripId, ownerScope.ownerId());
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
    
    private Map<String, Object> revenueReport(Long ownerId, LocalDateTime start, LocalDateTime end, String period, String periodValue) {
        RevenueTotals totals = travelBookingRepository.revenueTotals(start, end, ownerId);

        Map<String, Object> response = new HashMap<>();
        response.put("period", period);
        response.put("periodValue", periodValue);
        response.put("totalTrips", totals.trips());
        response.put("totalDistance", totals.distanceKm());
        response.put("totalRevenue", totals.revenue());
        response.put("totalTravelDays", totals.travelDays());
        response.put("trips", travelBookingRepository.revenueTrips(start, end, ownerId).stream().map(t -> {
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
    
    /** One owner's fleet drivers (their Drivers screen). Cached per owner: one list never serves another. */
    @Cacheable(value = "allDrivers", key = "#ownerId")
    public List<DriverDetailsResponse> getAllDrivers(Long ownerId) {
        return driverRepository.findByOwnerIdAndFleetOrderByCreatedAtDesc(ownerId, Driver.Fleet.OWNER_FLEET).stream()
                .map(this::mapToDriverDetails)
                .collect(Collectors.toList());
    }

    /** Independent (self-signup) drivers: the platform's City-ride drivers. */
    @Cacheable(value = "allDrivers", key = "'independent'")
    public List<DriverDetailsResponse> getIndependentDrivers() {
        return driverRepository.findByFleetOrderByCreatedAtDesc(Driver.Fleet.INDEPENDENT).stream()
                .map(this::mapToDriverDetails)
                .collect(Collectors.toList());
    }

    /**
     * One driver, for the owner whose fleet it is - or for the platform owner, an independent
     * driver. Anyone else gets "not found". Cached per owner and driver.
     */
    @Cacheable(value = "driverById", key = "#ownerId + ':' + #platform + ':' + #driverId")
    public DriverDetailsResponse getDriverById(Long ownerId, boolean platform, Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .filter(d -> ownerId.equals(d.getOwnerId()) || (platform && d.getFleet() == Driver.Fleet.INDEPENDENT))
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Driver not found"));
        return mapToDriverDetails(driver);
    }

    /** Owner-facing view with signed document links; also used by DriverVerificationService. */
    public DriverDetailsResponse mapToDriverDetails(Driver driver) {
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
        return d.withVehicle(driver).withVerification(driver,
                path -> signedUploadUrls == null ? path : signedUploadUrls.signIfSensitive(path));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public TravelBookingResponse assignDriver(UUID bookingId, Long driverId) {
        return assignDriver(bookingId, driverId, null);
    }

    /**
     * Puts one of my drivers and one of my cars on one of my Trips ({@code vehicleId} null: the
     * driver's usual car). City rides are the platform's: only NJO-0001 assigns them, and only to
     * an independent driver. Another owner's booking, driver or car is "not found".
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public TravelBookingResponse assignDriver(UUID bookingId, Long driverId, Long vehicleId) {
        Long me = ownerScope.ownerId();
        TravelBooking booking = ownBooking(bookingId, me);
        boolean trip = booking.getServiceMode() == TravelBooking.ServiceMode.TRIP;

        // Only before the trip starts. Reassigning a STARTED trip threw it back to CONFIRMED with a
        // new driver while the old one was still driving the passenger.
        if (booking.getStatus() != TravelBooking.BookingStatus.PENDING
                && booking.getStatus() != TravelBooking.BookingStatus.CONFIRMED) {
            throw new RuntimeException("Cannot assign a driver to a " + booking.getStatus() + " booking");
        }
        // Assigning confirms the trip, and an unpaid hold may still lapse (V20).
        if (booking.isAdvanceDue()) {
            throw new RuntimeException("Waiting for the passenger's advance payment");
        }

        Driver driver = driverRepository.findById(driverId)
                .filter(d -> trip ? me.equals(d.getOwnerId()) : d.getOwnerId() == null || me.equals(d.getOwnerId()))
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Driver not found"));

        if (driver.getStatus() != Driver.Status.ACTIVE) {
            throw new RuntimeException("Driver is not active and cannot be assigned");
        }
        if (!driver.isApproved()) {
            throw new IllegalArgumentException(driver.getName() + " has not been approved yet.");
        }
        if (trip && driver.getFleet() != Driver.Fleet.OWNER_FLEET) {
            throw new IllegalArgumentException("Trips go to your own fleet; " + driver.getName() + " is an independent driver.");
        }
        if (!trip && driver.getFleet() != Driver.Fleet.INDEPENDENT) {
            throw new IllegalArgumentException("City rides go to independent drivers; " + driver.getName()
                    + " is a travels owner's driver.");
        }
        // A City ride needs the driver's own vehicle type: a bike ride is not driven by car.
        if (!trip && booking.getVehicleType() != null && driver.getVehicleType() != booking.getVehicleType()) {
            throw new IllegalArgumentException("This ride needs a " + vehicleName(booking.getVehicleType())
                    + ", and " + driver.getName() + " drives a " + vehicleName(driver.getVehicleType()) + ".");
        }
        Long carId = trip ? suitableCar(booking, driver, vehicleId).getId() : null;

        long overlapping = driverRepository.countOverlappingActiveBookings(
                driverId, booking.getFromDate(), booking.getToDate(), bookingId);
        if (overlapping > 0) {
            throw new com.travelplatform.exception.ConflictException(
                    driver.getName() + " already has an active trip overlapping these dates ("
                            + booking.getFromDate() + " to " + booking.getToDate() + ")");
        }

        Long previousDriverId = booking.getDriverId();
        booking.setDriverId(driverId);
        booking.setVehicleId(carId);
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        // The owner's choice ends a Ride Now search: out of the matching queue, the new driver
        // busy, and a different driver who held its offer freed and told it is gone.
        if (dispatchService != null) {
            dispatchService.ownerAssigned(booking, previousDriverId);
        } else {
            DispatchService.settle(booking);
            if (driverStates != null) {
                if (previousDriverId != null && !previousDriverId.equals(driverId)) {
                    driverStates.freed(previousDriverId, booking);
                }
                driverStates.took(booking);
            }
        }
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
    
    /** My free drivers and my free cars that suit this Trip, for the assign screen. */
    @Transactional(readOnly = true)
    public com.travelplatform.dto.AssignOptions assignOptions(UUID bookingId) {
        Long me = ownerScope.ownerId();
        TravelBooking booking = ownBooking(bookingId, me);
        if (booking.getServiceMode() != TravelBooking.ServiceMode.TRIP) {
            throw new IllegalArgumentException("Only Trips are assigned from here");
        }
        List<com.travelplatform.dto.AssignOptions.DriverOption> freeDrivers = driverRepository
                .findFreeOwnerTripDrivers(me, booking.getFromDate(), booking.getToDate(), bookingId).stream()
                .map(d -> new com.travelplatform.dto.AssignOptions.DriverOption(d.getId(), d.getName(), d.getDefaultVehicleId()))
                .toList();
        List<com.travelplatform.dto.AssignOptions.VehicleOption> freeCars = vehicleRepository
                .findFreeVehicles(me, categoryOf(booking), seatsOf(booking), needsAc(booking),
                        booking.getFromDate(), booking.getToDate(), bookingId).stream()
                .map(v -> new com.travelplatform.dto.AssignOptions.VehicleOption(
                        v.getId(), v.getRegistrationNumber(), v.getModel(), v.getSeats(), v.isAc()))
                .toList();
        return new com.travelplatform.dto.AssignOptions(freeDrivers, freeCars);
    }

    /**
     * A booking the calling owner may act on: their own Trip, or - for the platform owner - a
     * City ride. Anything else is "not found", never "forbidden", so ids reveal nothing.
     */
    private TravelBooking ownBooking(UUID bookingId, Long me) {
        return travelBookingRepository.findById(bookingId)
                .filter(b -> b.getOwnerId() != null ? me.equals(b.getOwnerId()) : ownerScope.isPlatform())
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Booking not found"));
    }

    /** The car for a Trip: the one chosen, else the driver's usual one; mine, active and suitable. */
    private com.travelplatform.entity.Vehicle suitableCar(TravelBooking booking, Driver driver, Long vehicleId) {
        Long id = vehicleId != null ? vehicleId : driver.getDefaultVehicleId();
        if (id == null) {
            throw new IllegalArgumentException("Choose a car for this trip");
        }
        com.travelplatform.entity.Vehicle car = vehicleRepository.findById(id)
                .filter(v -> booking.getOwnerId().equals(v.getOwnerId()))
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("Car not found"));
        String plate = car.getRegistrationNumber();
        if (!car.isActive()) {
            throw new IllegalArgumentException(plate + " is not in use. Choose another car.");
        }
        if (car.getCategory() != categoryOf(booking)) {
            throw new IllegalArgumentException("This trip was booked as " + label(categoryOf(booking))
                    + ", and " + plate + " is " + label(car.getCategory()) + ".");
        }
        if (car.getSeats() < seatsOf(booking)) {
            throw new IllegalArgumentException(plate + " seats " + car.getSeats() + "; this trip is for "
                    + seatsOf(booking) + ".");
        }
        if (needsAc(booking) && !car.isAc()) {
            throw new IllegalArgumentException("This is an AC trip, and " + plate + " has no AC.");
        }
        if (vehicleRepository.countOverlappingActiveBookings(car.getId(), booking.getFromDate(), booking.getToDate(),
                booking.getId()) > 0) {
            throw new com.travelplatform.exception.ConflictException(plate + " is already on a trip on these dates.");
        }
        return car;
    }

    private static com.travelplatform.entity.CarCategory categoryOf(TravelBooking b) {
        return b.getCarCategory() != null ? b.getCarCategory() : com.travelplatform.entity.CarCategory.SEDAN;
    }

    private static int seatsOf(TravelBooking b) {
        return b.getTravelMembers() == null ? 1 : Math.max(1, b.getTravelMembers());
    }

    private static boolean needsAc(TravelBooking b) {
        return "AC".equalsIgnoreCase(b.getAcType());
    }

    private static String label(com.travelplatform.entity.CarCategory c) {
        return switch (c) {
            case HATCHBACK -> "a hatchback";
            case SEDAN -> "a sedan";
            case SUV -> "an SUV";
            case TEMPO_TRAVELLER -> "a tempo traveller";
        };
    }

    private static String vehicleName(com.travelplatform.entity.VehicleType type) {
        return type == null ? "vehicle" : type.name().toLowerCase(java.util.Locale.ROOT);
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
            // A Trip shows the car its owner put on it, which may not be the driver's own (TripCars).
            response.setDriver(tripCars == null ? driverDetails.withVehicle(driver)
                    : tripCars.apply(driverDetails.withVehicle(driver), booking));
        }
        
        return response.withDispatch(booking);
    }
    
    private String formatTime(Long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        return String.format("%d hours %d minutes", hours, mins);
    }
}
