package com.travelplatform.repository;

import com.travelplatform.entity.BusTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /** The ticket an earlier hold request with the same Idempotency-Key created. */
    Optional<BusTicket> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    boolean existsByTicketNo(String ticketNo);

    /** The customer's tickets, newest first. */
    List<BusTicket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable page);

    /** Unpaid holds of a user that are still running, on any trip. */
    @Query("SELECT COUNT(t) FROM BusTicket t WHERE t.userId = :userId "
            + "AND t.status = com.travelplatform.entity.BusTicket.Status.PENDING AND t.holdExpiresAt > :now")
    long countActiveHolds(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /** Unpaid holds of a user that are still running on one trip. */
    @Query("SELECT COUNT(t) FROM BusTicket t WHERE t.userId = :userId AND t.tripId = :tripId "
            + "AND t.status = com.travelplatform.entity.BusTicket.Status.PENDING AND t.holdExpiresAt > :now")
    long countActiveHoldsOnTrip(@Param("userId") UUID userId, @Param("tripId") Long tripId, @Param("now") LocalDateTime now);

    /** Locks the ticket row: two cancellations of one ticket run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM BusTicket t WHERE t.id = :id")
    Optional<BusTicket> lockById(@Param("id") UUID id);

    /** PENDING tickets whose hold ran out. */
    @Query("SELECT t.id FROM BusTicket t WHERE t.status = com.travelplatform.entity.BusTicket.Status.PENDING "
            + "AND t.holdExpiresAt < :now")
    List<UUID> findExpiredHolds(@Param("now") LocalDateTime now);

    /**
     * Confirmed tickets still waiting for their reminder on trips that leave their first stop by
     * {@code horizon} and have not arrived yet. Each ticket's own boarding time is checked by the caller.
     */
    @Query("SELECT t FROM BusTicket t, BusTrip tr WHERE tr.id = t.tripId AND t.reminderSentAt IS NULL "
            + "AND t.status IN (com.travelplatform.entity.BusTicket.Status.CONFIRMED, "
            + "com.travelplatform.entity.BusTicket.Status.PARTIALLY_CANCELLED) "
            + "AND tr.status IN (com.travelplatform.entity.BusTrip.Status.SCHEDULED, com.travelplatform.entity.BusTrip.Status.DEPARTED) "
            + "AND tr.departureAt <= :horizon AND tr.arrivalAt >= :now")
    List<BusTicket> findReminderCandidates(@Param("now") LocalDateTime now, @Param("horizon") LocalDateTime horizon);

    /** The trips arrived: their travelling tickets are COMPLETED (version bumped, like an entity update). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BusTicket t SET t.status = com.travelplatform.entity.BusTicket.Status.COMPLETED, t.version = t.version + 1 "
            + "WHERE t.tripId IN :tripIds AND t.status IN (com.travelplatform.entity.BusTicket.Status.CONFIRMED, "
            + "com.travelplatform.entity.BusTicket.Status.PARTIALLY_CANCELLED)")
    int completeForTrips(@Param("tripIds") Collection<Long> tripIds);
}
