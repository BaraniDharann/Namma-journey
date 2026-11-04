package com.travelplatform.repository;

import com.travelplatform.entity.BusRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusRouteRepository extends JpaRepository<BusRoute, Long> {

    List<BusRoute> findByOwnerIdOrderByIdDesc(Long ownerId);

    List<BusRoute> findByStatus(BusRoute.Status status);

    List<BusRoute> findByBusIdAndStatus(Long busId, BusRoute.Status status);
}
