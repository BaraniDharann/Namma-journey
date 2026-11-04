package com.travelplatform.repository;

import com.travelplatform.entity.BusSeatAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BusSeatAllocationRepository extends JpaRepository<BusSeatAllocation, Long> {

    List<BusSeatAllocation> findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(Long tripId,
                                                                                Collection<BusSeatAllocation.Status> statuses);

    /** Holds on a trip that ran out become EXPIRED, which frees their seats (the exclusion constraint ignores them). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BusSeatAllocation a SET a.status = com.travelplatform.entity.BusSeatAllocation.Status.EXPIRED "
            + "WHERE a.tripId = :tripId AND a.status = com.travelplatform.entity.BusSeatAllocation.Status.HELD "
            + "AND a.expiresAt < :now")
    int expireStaleHolds(@Param("tripId") Long tripId, @Param("now") LocalDateTime now);

    /** Every live allocation on the trip becomes CANCELLED (the trip itself was cancelled). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BusSeatAllocation a SET a.status = com.travelplatform.entity.BusSeatAllocation.Status.CANCELLED "
            + "WHERE a.tripId = :tripId AND a.status IN (com.travelplatform.entity.BusSeatAllocation.Status.HELD, "
            + "com.travelplatform.entity.BusSeatAllocation.Status.CONFIRMED, com.travelplatform.entity.BusSeatAllocation.Status.BLOCKED)")
    int cancelLive(@Param("tripId") Long tripId);

    /** Allocations of several trips at once (the search results), in these states. */
    List<BusSeatAllocation> findByTripIdInAndStatusIn(Collection<Long> tripIds, Collection<BusSeatAllocation.Status> statuses);

    List<BusSeatAllocation> findByTicketId(UUID ticketId);

    /** Every hold that ran out, on any trip, becomes EXPIRED (the sweeper). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BusSeatAllocation a SET a.status = com.travelplatform.entity.BusSeatAllocation.Status.EXPIRED "
            + "WHERE a.status = com.travelplatform.entity.BusSeatAllocation.Status.HELD AND a.expiresAt < :now")
    int expireAllStaleHolds(@Param("now") LocalDateTime now);
}
