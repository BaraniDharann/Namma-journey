package com.travelplatform.repository;

import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BusTicketPassengerRepository extends JpaRepository<BusTicketPassenger, Long> {

    List<BusTicketPassenger> findByTicketIdIn(Collection<UUID> ticketIds);

    List<BusTicketPassenger> findByTicketIdOrderByIdAsc(UUID ticketId);

    /**
     * Per trip: passengers still travelling on tickets in these states and the sum of their fares
     * (without GST), as [tripId, count, fares] rows.
     */
    @Query("SELECT t.tripId, COUNT(p), COALESCE(SUM(p.fare), 0) FROM BusTicketPassenger p, BusTicket t "
            + "WHERE p.ticketId = t.id AND t.tripId IN :tripIds AND t.status IN :statuses "
            + "AND p.status = com.travelplatform.entity.BusTicketPassenger.Status.CONFIRMED GROUP BY t.tripId")
    List<Object[]> soldPerTrip(@Param("tripIds") Collection<Long> tripIds,
                               @Param("statuses") Collection<BusTicket.Status> statuses);
}
