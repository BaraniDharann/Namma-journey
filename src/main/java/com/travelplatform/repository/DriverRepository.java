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

    // Conditional moves: the WHERE on the expected status is what refuses a stale decision (no @Version on Driver).
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Driver d SET d.verificationStatus = :to, d.rejectionReason = :reason, d.reviewedAt = :at, d.reviewedBy = :by "
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

    /**
     * The first car free for a Trip on these dates. Native so it can stop at one row: the old
     * query loaded every free driver to keep the first. Busy is a {@code NOT EXISTS} probe per
     * driver on {@code idx_travel_bookings_driver_active} (V19) rather than a {@code NOT IN} over
     * every active booking, and the AC match is here instead of in Java. Ordered by id so the
     * same request picks the same car. Public so the Docker test runs this exact text.
     */
    String FREE_TRIP_DRIVER_SQL = "SELECT d.* FROM drivers d"
            + " WHERE d.status = 'ACTIVE' AND d.vehicle_type = 'CAR' AND d.serves_trip = true"
            // Trips stay with the owner's own approved fleet; self-signed-up drivers take City rides only.
            + " AND d.verification_status = 'APPROVED' AND d.fleet = 'OWNER_FLEET'"
            + " AND (:needAc = false OR d.vehicle_ac = true)"
            + " AND d.id NOT IN (:excluded)"
            + " AND NOT EXISTS (SELECT 1 FROM travel_bookings tb WHERE tb.driver_id = d.id"
            + " AND tb.from_date <= :toDate AND tb.to_date >= :fromDate"
            + " AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.trip_ended_at IS NULL)"
            + " ORDER BY d.id LIMIT 1";

    /**
     * @param excluded drivers who lost a race for, or declined, this trip. Never empty:
     *                 {@code NOT IN ()} is not valid SQL, so callers pass {@code List.of(-1L)}
     *                 when nobody is excluded.
     */
    @Query(value = FREE_TRIP_DRIVER_SQL, nativeQuery = true)
    Optional<Driver> findFirstFreeTripDriver(@Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate,
                                             @Param("needAc") boolean needAc,
                                             @Param("excluded") java.util.Collection<Long> excluded);

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
           "AND d.servesCity = true AND d.vehicleType = :vehicleType AND d.vehicleSeats >= :seats " +
           "AND NOT EXISTS " +
           "(SELECT 1 FROM TravelBooking tb WHERE tb.driverId = d.id " +
           "AND tb.fromDate <= :day AND tb.toDate >= :day " +
           "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL)")
    List<Long> findDispatchableAmong(@Param("driverIds") java.util.Collection<Long> driverIds,
                                     @Param("day") LocalDate day,
                                     @Param("vehicleType") com.travelplatform.entity.VehicleType vehicleType,
                                     @Param("seats") int seats);
}
