package com.travelplatform.repository;

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

    @Query("SELECT b FROM TravelBooking b WHERE b.driverId = :driverId")
    List<TravelBooking> findByDriverId(@Param("driverId") Long driverId);

    @Query("SELECT COUNT(b) FROM TravelBooking b WHERE b.driverId = :driverId " +
           "AND b.status IN ('PENDING', 'CONFIRMED', 'STARTED')")
    long countActiveBookingsByDriverId(@Param("driverId") Long driverId);

    Page<TravelBooking> findAll(Pageable pageable);
}
