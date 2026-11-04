package com.travelplatform.repository;

import com.travelplatform.entity.BusTrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BusTripRepository extends JpaRepository<BusTrip, Long> {

    List<BusTrip> findByOwnerIdAndTravelDateBetweenOrderByDepartureAtAscIdAsc(Long ownerId, LocalDate from, LocalDate to);

    /**
     * Creates the route's trip for a date unless it already exists (uq_bus_trips_route_date), so trip
     * creation can run any number of times. Returns 1 if a trip was created.
     */
    @Modifying
    @Query(value = "INSERT INTO bus_trips (route_id, bus_id, owner_id, travel_date, departure_at, arrival_at, status, created_at) "
            + "VALUES (:routeId, :busId, :ownerId, :travelDate, :departureAt, :arrivalAt, 'SCHEDULED', now()) "
            + "ON CONFLICT (route_id, travel_date) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("routeId") Long routeId,
                       @Param("busId") Long busId,
                       @Param("ownerId") Long ownerId,
                       @Param("travelDate") LocalDate travelDate,
                       @Param("departureAt") LocalDateTime departureAt,
                       @Param("arrivalAt") LocalDateTime arrivalAt);
}
