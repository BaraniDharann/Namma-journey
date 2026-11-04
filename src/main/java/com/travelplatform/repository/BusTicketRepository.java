package com.travelplatform.repository;

import com.travelplatform.entity.BusTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BusTicketRepository extends JpaRepository<BusTicket, UUID> {

    List<BusTicket> findByTripIdAndStatusIn(Long tripId, Collection<BusTicket.Status> statuses);

    /** Tickets ever sold, in any state, on any trip of this bus: once there is one the layout is fixed. */
    @Query("SELECT COUNT(t) FROM BusTicket t WHERE t.tripId IN (SELECT tr.id FROM BusTrip tr WHERE tr.busId = :busId)")
    long countByBusId(@Param("busId") Long busId);

    /** Tickets that use this boarding or dropping point (such a point cannot be removed). */
    @Query("SELECT COUNT(t) FROM BusTicket t WHERE t.boardingPointId = :pointId OR t.droppingPointId = :pointId")
    long countUsingPoint(@Param("pointId") Long pointId);
}
