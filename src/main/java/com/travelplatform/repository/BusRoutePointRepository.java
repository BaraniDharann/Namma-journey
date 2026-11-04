package com.travelplatform.repository;

import com.travelplatform.entity.BusRoutePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BusRoutePointRepository extends JpaRepository<BusRoutePoint, Long> {

    List<BusRoutePoint> findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(Collection<Long> stopIds);
}
