package com.travelplatform.repository;

import com.travelplatform.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    Optional<Driver> findByLicenseNumber(String licenseNumber);
    Optional<Driver> findByAadhaarNumber(String aadhaarNumber);

    /** The driver holding this plate, compared on its spaceless upper-case key (same key as the V16 unique index). */
    @Query("SELECT d FROM Driver d WHERE UPPER(REPLACE(d.vehicleNumber, ' ', '')) = :key")
    Optional<Driver> findByVehicleKey(@Param("key") String key);

    /** Only drivers who confirmed their email reach the owner's queue. */
    List<Driver> findByFleetAndVerificationStatusAndEmailVerifiedTrueOrderBySubmittedAtAsc(Driver.Fleet fleet, Driver.Verification verificationStatus);

    List<Driver> findByFleetAndEmailVerifiedFalseAndSubmittedAtBefore(Driver.Fleet fleet, LocalDateTime before);

    /** Self-signups whose automatic-approval time has come (V24). */
    List<Driver> findByFleetAndVerificationStatusAndEmailVerifiedTrueAndAutoApproveAtLessThanEqual(
            Driver.Fleet fleet, Driver.Verification verificationStatus, LocalDateTime at);

    /** Stops automatic approval of an application still under review; the owner decides it instead. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Driver d SET d.autoApproveAt = null WHERE d.id = :id AND d.verificationStatus = :under")
    int holdAutoApproval(@Param("id") Long id, @Param("under") Driver.Verification under);

    // Conditional moves: the WHERE on the expected status is what refuses a stale decision (no @Version on Driver).
    // The auto-approval deadline goes with any decision, so a re-upload after a rejection is never
    // approved on the old one.
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Driver d SET d.verificationStatus = :to, d.rejectionReason = :reason, d.reviewedAt = :at, d.reviewedBy = :by, "
            + "d.autoApproveAt = null "
            + "WHERE d.id = :id AND d.verificationStatus = :expected")
    int decide(@Param("id") Long id, @Param("expected") Driver.Verification expected, @Param("to") Driver.Verification to,
               @Param("reason") String reason, @Param("at") java.time.LocalDateTime at, @Param("by") Long by);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Driver d SET d.verificationStatus = :under, "
            + "d.rejectionReason = null, d.submittedAt = :at, d.photo = :photo, d.licensePhoto = :license, "
            + "d.aadhaarPhoto = :aadhaar, d.rcPhoto = :rc "
            + "WHERE d.id = :id AND d.verificationStatus = :rejected")
    int resubmitDocuments(@Param("id") Long id, @Param("under") Driver.Verification under, @Param("rejected") Driver.Verification rejected, @Param("at") java.time.LocalDateTime at, @Param("photo") String photo,
                          @Param("license") String license, @Param("aadhaar") String aadhaar, @Param("rc") String rc);

    // "Busy" everywhere below means an active trip the driver has not ended yet: an ended trip
    // awaiting payment no longer occupies them, matching no_driver_overlap since V13.

    /** A driver and a car to put on a Trip together. */
    interface TripPair {
        Long getDriverId();

        Long getVehicleId();
    }

    /**
     * The first free driver and car of one owner for a Trip on these dates (V27): the owner's
     * approved, active fleet driver and the owner's active car of the booked category, with enough
     * seats and AC when asked, neither on an unfinished trip those days. The driver's usual car
     * comes first, then the lowest driver id, then the lowest car id, so the same request picks
     * the same pair. Busy is a {@code NOT EXISTS} probe on idx_travel_bookings_driver_active (V19)
     * and the no_vehicle_overlap index. Public so tests can read this exact text.
     */
    String FREE_TRIP_PAIR_SQL = "SELECT d.id AS driverId, v.id AS vehicleId FROM drivers d"
            + " JOIN vehicles v ON v.owner_id = d.owner_id"
            // Trips stay with the owner's own approved fleet; self-signed-up drivers take City rides only.
            + " WHERE d.owner_id = :ownerId AND d.fleet = 'OWNER_FLEET' AND d.status = 'ACTIVE'"
            + " AND d.serves_trip = true AND d.verification_status = 'APPROVED'"
            + " AND v.active = true AND v.category = :category AND v.seats >= :seats"
            + " AND (:needAc = false OR v.ac = true)"
            // Automatic picks stay on Travel cars, and leave a City car's driver on City rides (V35);
            // the owner can still give a Travel booking to either by hand.
            + " AND v.mode = 'TRAVEL'"
            + " AND NOT EXISTS (SELECT 1 FROM vehicles cv WHERE cv.city_driver_id = d.id)"
            + " AND d.id NOT IN (:excluded)"
            + " AND NOT EXISTS (SELECT 1 FROM travel_bookings tb WHERE tb.driver_id = d.id"
            + " AND tb.from_date <= :toDate AND tb.to_date >= :fromDate"
            + " AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.trip_ended_at IS NULL)"
            + " AND NOT EXISTS (SELECT 1 FROM travel_bookings tb WHERE tb.vehicle_id = v.id"
            + " AND tb.from_date <= :toDate AND tb.to_date >= :fromDate"
            + " AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.trip_ended_at IS NULL)"
            + " ORDER BY (v.id = d.default_vehicle_id) DESC NULLS LAST, d.id, v.id LIMIT 1";

    /**
     * @param excluded drivers who lost a race for, or declined, this trip. Never empty:
     *                 {@code NOT IN ()} is not valid SQL, so callers pass {@code List.of(-1L)}
     *                 when nobody is excluded.
     */
    @Query(value = FREE_TRIP_PAIR_SQL, nativeQuery = true)
    Optional<TripPair> findFirstFreeTripPair(@Param("ownerId") Long ownerId,
                                            @Param("category") String category,
                                            @Param("seats") int seats,
                                            @Param("needAc") boolean needAc,
                                            @Param("fromDate") LocalDate fromDate,
                                            @Param("toDate") LocalDate toDate,
                                            @Param("excluded") java.util.Collection<Long> excluded);

    /** The owner's approved, active Trip drivers with no unfinished trip on these dates (the assign screen). */
    @Query("SELECT d FROM Driver d WHERE d.ownerId = :ownerId AND d.fleet = com.travelplatform.entity.Driver.Fleet.OWNER_FLEET "
            + "AND d.status = com.travelplatform.entity.Driver.Status.ACTIVE AND d.servesTrip = true "
            + "AND d.verificationStatus = com.travelplatform.entity.Driver.Verification.APPROVED "
            + "AND NOT EXISTS (SELECT 1 FROM TravelBooking tb WHERE tb.driverId = d.id "
            + "AND tb.fromDate <= :toDate AND tb.toDate >= :fromDate AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') "
            + "AND tb.tripEndedAt IS NULL AND (:excludeBookingId IS NULL OR tb.id <> :excludeBookingId)) "
            + "ORDER BY d.name, d.id")
    List<Driver> findFreeOwnerTripDrivers(@Param("ownerId") Long ownerId,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate,
                                          @Param("excludeBookingId") java.util.UUID excludeBookingId);

    /** One owner's fleet drivers, newest first (the owner's Drivers screen). */
    List<Driver> findByOwnerIdAndFleetOrderByCreatedAtDesc(Long ownerId, Driver.Fleet fleet);

    /** Independent drivers, newest first (the platform's drivers screen). */
    List<Driver> findByFleetOrderByCreatedAtDesc(Driver.Fleet fleet);

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
           "AND d.verificationStatus = com.travelplatform.entity.Driver.Verification.APPROVED " +
           "AND d.servesCity = true AND d.fleet = com.travelplatform.entity.Driver.Fleet.INDEPENDENT AND d.vehicleType = :vehicleType AND d.vehicleSeats >= :seats " +
           "AND NOT EXISTS " +
           "(SELECT 1 FROM TravelBooking tb WHERE tb.driverId = d.id " +
           "AND tb.fromDate <= :day AND tb.toDate >= :day " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL)")
    List<Long> findDispatchableAmong(@Param("driverIds") java.util.Collection<Long> driverIds,
                                     @Param("day") LocalDate day,
                                     @Param("vehicleType") com.travelplatform.entity.VehicleType vehicleType,
                                     @Param("seats") int seats);
}
