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
    List<TravelBooking> findByUserId(UUID userId);

    Page<TravelBooking> findByUserId(UUID userId, Pageable pageable);

    @Query("SELECT b FROM TravelBooking b WHERE b.status = :status AND b.bookingDate BETWEEN :start AND :end")
    List<TravelBooking> findCompletedBookingsBetween(
            @Param("status") TravelBooking.BookingStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Totals for one period, summed by Postgres. Half-open window [start, end): a booking at
     * exactly midnight on the 1st belongs to the new month only - the BETWEEN above counts it in
     * both. Rides on idx_travel_bookings_status_date (V6).
     */
    @Query("SELECT new com.travelplatform.dto.RevenueTotals(COUNT(b), SUM(b.totalAmount), SUM(b.distanceKm), SUM(b.travelDays)) " +
           "FROM TravelBooking b WHERE b.status = :status AND b.bookingDate >= :start AND b.bookingDate < :end")
    RevenueTotals revenueTotals(@Param("status") TravelBooking.BookingStatus status,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    /** One row per month that has revenue, in month order. Months with none are simply absent. */
    @Query("SELECT new com.travelplatform.dto.MonthlyRevenue(CAST(EXTRACT(MONTH FROM b.bookingDate) AS Integer), " +
           "COUNT(b), SUM(b.totalAmount), SUM(b.distanceKm)) " +
           "FROM TravelBooking b WHERE b.status = :status AND b.bookingDate >= :start AND b.bookingDate < :end " +
           "GROUP BY EXTRACT(MONTH FROM b.bookingDate) ORDER BY EXTRACT(MONTH FROM b.bookingDate)")
    List<MonthlyRevenue> monthlyRevenue(@Param("status") TravelBooking.BookingStatus status,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    /** The per-trip lines of a revenue report: only the columns it shows, oldest first. */
    @Query("SELECT new com.travelplatform.dto.RevenueTrip(b.id, b.fromPlace, b.toPlace, b.distanceKm, b.travelDays, " +
           "b.totalAmount, b.bookingDate) " +
           "FROM TravelBooking b WHERE b.status = :status AND b.bookingDate >= :start AND b.bookingDate < :end " +
           "ORDER BY b.bookingDate")
    List<RevenueTrip> revenueTrips(@Param("status") TravelBooking.BookingStatus status,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    @Query("SELECT b FROM TravelBooking b WHERE b.driverId = :driverId")
    List<TravelBooking> findByDriverId(@Param("driverId") Long driverId);

    @Query("SELECT COUNT(b) FROM TravelBooking b WHERE b.driverId = :driverId " +
           "AND b.status IN ('PENDING', 'CONFIRMED', 'STARTED')")
    long countActiveBookingsByDriverId(@Param("driverId") Long driverId);

    Page<TravelBooking> findAll(Pageable pageable);
}
