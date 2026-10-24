package com.travelplatform.repository;

import com.travelplatform.dto.MonthlyRevenue;
import com.travelplatform.dto.RevenueTotals;
import com.travelplatform.dto.RevenueTrip;
import com.travelplatform.entity.TravelBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TravelBookingRepository extends JpaRepository<TravelBooking, UUID> {
    /** Newest first. Unordered, Postgres returned rows in storage order and an UPDATE moves a row
     *  to the end - a just-confirmed trip landed on the last page of the passenger's list. */
    List<TravelBooking> findByUserIdOrderByBookingDateDesc(UUID userId);

    Page<TravelBooking> findByUserId(UUID userId, Pageable pageable);

    /**
     * The open offer a driver is holding, if any. Served by the {@code driver_id} index and at
     * most one row: dispatch never offers a driver two rides at once ({@code no_driver_overlap}).
     */
    java.util.Optional<TravelBooking> findFirstByDriverIdAndDispatchStateAndStatus(
            Long driverId, TravelBooking.DispatchState dispatchState, TravelBooking.BookingStatus status);

    /*
     * Revenue = money actually received: VERIFIED payments, on the day they were verified, at the
     * amount collected. These used to add up booked fares of COMPLETED bookings by booking date -
     * the wrong month, and the (then editable) booking total instead of what was paid.
     * Half-open windows [start, end): a payment at exactly midnight on the 1st counts once.
     */

    /**
     * Trips are counted by their FARE payment only (V20): money sums every verified payment, but a
     * Trip paid as advance + balance is still one trip, and a kept advance on a cancelled trip is
     * revenue without being a trip.
     */
    String FARE_COUNT = "SUM(CASE WHEN p.purpose = com.travelplatform.entity.Payment.PaymentPurpose.FARE THEN 1 ELSE 0 END)";

    /** Totals for one period, summed by Postgres over NUMERIC money. */
    @Query("SELECT new com.travelplatform.dto.RevenueTotals(" + FARE_COUNT + ", SUM(p.amount), " +
           "SUM(CASE WHEN p.purpose = com.travelplatform.entity.Payment.PaymentPurpose.FARE THEN b.distanceKm ELSE 0.0 END), " +
           "SUM(CASE WHEN p.purpose = com.travelplatform.entity.Payment.PaymentPurpose.FARE THEN b.travelDays ELSE 0 END)) " +
           "FROM Payment p, TravelBooking b WHERE p.bookingId = b.id " +
           "AND p.status = com.travelplatform.entity.Payment.PaymentStatus.VERIFIED " +
           "AND p.verifiedDate >= :start AND p.verifiedDate < :end")
    RevenueTotals revenueTotals(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** One row per month that received money, in month order. Months with none are absent. */
    @Query("SELECT new com.travelplatform.dto.MonthlyRevenue(CAST(EXTRACT(MONTH FROM p.verifiedDate) AS Integer), " +
           FARE_COUNT + ", SUM(p.amount), " +
           "SUM(CASE WHEN p.purpose = com.travelplatform.entity.Payment.PaymentPurpose.FARE THEN b.distanceKm ELSE 0.0 END)) " +
           "FROM Payment p, TravelBooking b WHERE p.bookingId = b.id " +
           "AND p.status = com.travelplatform.entity.Payment.PaymentStatus.VERIFIED " +
           "AND p.verifiedDate >= :start AND p.verifiedDate < :end " +
           "GROUP BY EXTRACT(MONTH FROM p.verifiedDate) ORDER BY EXTRACT(MONTH FROM p.verifiedDate)")
    List<MonthlyRevenue> monthlyRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** The per-trip lines of a revenue report, at the amount paid, in order of payment. */
    @Query("SELECT new com.travelplatform.dto.RevenueTrip(b.id, b.fromPlace, b.toPlace, b.distanceKm, b.travelDays, " +
           "p.amount, b.bookingDate) " +
           "FROM Payment p, TravelBooking b WHERE p.bookingId = b.id " +
           "AND p.status = com.travelplatform.entity.Payment.PaymentStatus.VERIFIED " +
           "AND p.verifiedDate >= :start AND p.verifiedDate < :end " +
           "ORDER BY p.verifiedDate")
    List<RevenueTrip> revenueTrips(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Trips whose unpaid advance hold has run out (AdvanceHoldSweeper); uses idx_travel_bookings_advance_hold. */
    @Query("SELECT b.id FROM TravelBooking b WHERE b.status = 'PENDING' AND b.advancePaidAt IS NULL " +
           "AND b.holdExpiresAt IS NOT NULL AND b.holdExpiresAt < :now")
    List<UUID> findExpiredAdvanceHolds(@Param("now") LocalDateTime now);

    /** Bookings a traveller still has open - the cap in TripRules stops one account tying up the fleet. */
    @Query("SELECT COUNT(b) FROM TravelBooking b WHERE b.userId = :userId " +
           "AND b.status IN ('PENDING', 'CONFIRMED', 'STARTED')")
    long countOpenBookingsByUserId(@Param("userId") UUID userId);

    @Query("SELECT b FROM TravelBooking b WHERE b.driverId = :driverId ORDER BY b.bookingDate DESC") // newest first, as for passengers
    List<TravelBooking> findByDriverId(@Param("driverId") Long driverId);

    @Query("SELECT COUNT(b) FROM TravelBooking b WHERE b.driverId = :driverId " +
           "AND b.status IN ('PENDING', 'CONFIRMED', 'STARTED')")
    long countActiveBookingsByDriverId(@Param("driverId") Long driverId);

    Page<TravelBooking> findAll(Pageable pageable);

    /** Ride Now bookings the dispatch sweep has to act on: offers past their deadline, and searches still running. */
    @Query("SELECT b.id FROM TravelBooking b WHERE b.status = 'PENDING' AND (" +
           "(b.dispatchState = com.travelplatform.entity.TravelBooking.DispatchState.OFFERED AND b.offerExpiresAt < :now) " +
           "OR b.dispatchState = com.travelplatform.entity.TravelBooking.DispatchState.SEARCHING)")
    List<UUID> findDispatchWork(@Param("now") LocalDateTime now);
}
