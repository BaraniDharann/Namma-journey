package com.travelplatform.repository;

import com.travelplatform.entity.VehicleExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface VehicleExpenseRepository extends JpaRepository<VehicleExpense, Long> {

    List<VehicleExpense> findByOwnerIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(Long ownerId, LocalDate from, LocalDate to);

    List<VehicleExpense> findByOwnerIdAndBusTripIdIn(Long ownerId, Collection<Long> busTripIds);

    /**
     * Car revenue per car: verified payments on the owner's Trips that had this car, on the day
     * they were verified, as [vehicleId, amount] rows. Same money rule as the owner Revenue page.
     */
    @Query("SELECT b.vehicleId, SUM(p.amount) FROM Payment p, TravelBooking b WHERE p.bookingId = b.id "
            + "AND p.status = com.travelplatform.entity.Payment.PaymentStatus.VERIFIED "
            + "AND b.ownerId = :ownerId AND b.vehicleId IS NOT NULL "
            + "AND p.verifiedDate >= :start AND p.verifiedDate < :end GROUP BY b.vehicleId")
    List<Object[]> carRevenue(@Param("ownerId") Long ownerId,
                              @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Bus revenue per bus: fares (without GST) of passengers still travelling on sold tickets, for
     * trips that run in the window, as [busId, amount] rows. Same rule as the trips list.
     */
    @Query("SELECT tr.busId, COALESCE(SUM(p.fare), 0) FROM BusTicketPassenger p, BusTicket t, BusTrip tr "
            + "WHERE p.ticketId = t.id AND t.tripId = tr.id AND tr.ownerId = :ownerId "
            + "AND tr.travelDate BETWEEN :from AND :to "
            + "AND t.status IN (com.travelplatform.entity.BusTicket.Status.CONFIRMED, "
            + "com.travelplatform.entity.BusTicket.Status.PARTIALLY_CANCELLED, "
            + "com.travelplatform.entity.BusTicket.Status.COMPLETED) "
            + "AND p.status = com.travelplatform.entity.BusTicketPassenger.Status.CONFIRMED GROUP BY tr.busId")
    List<Object[]> busRevenue(@Param("ownerId") Long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
