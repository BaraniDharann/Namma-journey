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

    /** Runs at once (not at flush), so the new fares can reuse the same keys. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BusRouteFare f WHERE f.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
