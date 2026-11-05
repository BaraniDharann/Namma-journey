package com.travelplatform.repository;

import com.travelplatform.entity.BusRouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BusRouteStopRepository extends JpaRepository<BusRouteStop, Long> {

    List<BusRouteStop> findByRouteIdOrderBySeqAsc(Long routeId);

    List<BusRouteStop> findByRouteIdInOrderByRouteIdAscSeqAsc(Collection<Long> routeIds);

    /** Runs at once; the database removes the stops' points with them (ON DELETE CASCADE). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BusRouteStop s WHERE s.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
