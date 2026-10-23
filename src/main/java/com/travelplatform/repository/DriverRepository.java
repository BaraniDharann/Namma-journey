package com.travelplatform.repository;

import com.travelplatform.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByMobile(String mobile);
    Optional<Driver> findByEmail(String email);
    Optional<Driver> findByTelegramChatId(String telegramChatId);
    Optional<Driver> findByTelegramLinkToken(String telegramLinkToken);
    boolean existsByMobile(String mobile);
    boolean existsByEmail(String email);
    boolean existsByLicenseNumber(String licenseNumber);
    boolean existsByAadhaarNumber(String aadhaarNumber);
    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);
    List<Driver> findByFleetAndVerificationStatusOrderBySubmittedAtAsc(Driver.Fleet fleet, Driver.Verification verificationStatus);

    // "Busy" everywhere below means an active trip the driver has not ended yet: an ended trip
    // awaiting payment no longer occupies them, matching no_driver_overlap since V13.

    /** Trips are driven by cars whose driver takes trips (V14). The AC match is applied by the caller. */
    String TRIP_CAR = "d.vehicleType = com.travelplatform.entity.VehicleType.CAR AND d.servesTrip = true";

    @Query("SELECT d FROM Driver d WHERE d.status = 'ACTIVE' AND " + TRIP_CAR + " AND d.id NOT IN " +
           "(SELECT tb.driverId FROM TravelBooking tb WHERE tb.driverId IS NOT NULL " +
           "AND ((tb.fromDate <= :toDate AND tb.toDate >= :fromDate) " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL))")
    List<Driver> findAvailableDrivers(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /**
     * Availability, minus a caller-supplied exclusion list - used when reassigning a trip
     * that one or more drivers have already declined.
     *
     * <p>The exclusion list must never be empty: {@code NOT IN ()} is not valid SQL. Callers
     * reach this method only after recording at least one rejection, so it always has a member.
     */
    @Query("SELECT d FROM Driver d WHERE d.status = 'ACTIVE' AND " + TRIP_CAR + " " +
           "AND d.id NOT IN :excludedDriverIds AND d.id NOT IN " +
           "(SELECT tb.driverId FROM TravelBooking tb WHERE tb.driverId IS NOT NULL " +
           "AND ((tb.fromDate <= :toDate AND tb.toDate >= :fromDate) " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL))")
    List<Driver> findAvailableDriversExcluding(@Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate,
                                               @Param("excludedDriverIds") List<Long> excludedDriverIds);

    @Query("SELECT COUNT(tb) FROM TravelBooking tb WHERE tb.driverId = :driverId " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL " +
           "AND tb.fromDate <= :toDate AND tb.toDate >= :fromDate " +
           "AND (:excludeBookingId IS NULL OR tb.id <> :excludeBookingId)")
    long countOverlappingActiveBookings(@Param("driverId") Long driverId,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate,
                                        @Param("excludeBookingId") java.util.UUID excludeBookingId);

    /**
     * Of these drivers, the ones a City ride may be offered to on {@code day}: ACTIVE, taking City
     * rides, in the right vehicle with enough seats, and not busy that day. A pending offer is
     * itself a PENDING booking with their id, so a driver already holding one is left out and
     * never juggles two offers.
     */
    @Query("SELECT d.id FROM Driver d WHERE d.id IN :driverIds AND d.status = 'ACTIVE' " +
           "AND d.servesCity = true AND d.vehicleType = :vehicleType AND d.vehicleSeats >= :seats " +
           "AND d.id NOT IN " +
           "(SELECT tb.driverId FROM TravelBooking tb WHERE tb.driverId IS NOT NULL " +
           "AND tb.fromDate <= :day AND tb.toDate >= :day " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL)")
    List<Long> findDispatchableAmong(@Param("driverIds") java.util.Collection<Long> driverIds,
                                     @Param("day") LocalDate day,
                                     @Param("vehicleType") com.travelplatform.entity.VehicleType vehicleType,
                                     @Param("seats") int seats);
}
