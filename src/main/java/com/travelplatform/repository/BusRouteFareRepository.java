package com.travelplatform.repository;

import com.travelplatform.entity.BusRouteFare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusRouteFareRepository extends JpaRepository<BusRouteFare, BusRouteFare.Key> {

    List<BusRouteFare> findByRouteIdOrderByFromSeqAscToSeqAscSeatClassAsc(Long routeId);

    long countByRouteId(Long routeId);

    List<BusRouteFare> findByRouteIdIn(java.util.Collection<Long> routeIds);

    /** The fares for one stop pair, one per seat class that is sold for it. */
    List<BusRouteFare> findByRouteIdAndFromSeqAndToSeq(Long routeId, int fromSeq, int toSeq);

    /** Runs at once (not at flush), so the new fares can reuse the same keys. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BusRouteFare f WHERE f.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
