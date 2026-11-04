package com.travelplatform.repository;

import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByOwnerIdOrderByIdAsc(Long ownerId);

    /** Compared on the spaceless upper-case key, same as the uq_vehicles_registration index (V27). */
    @Query("SELECT v FROM Vehicle v WHERE UPPER(REPLACE(v.registrationNumber, ' ', '')) = :key")
    Optional<Vehicle> findByRegistrationKey(@Param("key") String key);

    /** The owner's active cars of a category, with enough seats and AC when asked, free on these dates. */
    @Query("SELECT v FROM Vehicle v WHERE v.ownerId = :ownerId AND v.active = true AND v.category = :category "
            + "AND v.seats >= :seats AND (:needAc = false OR v.ac = true) "
            + "AND NOT EXISTS (SELECT 1 FROM TravelBooking tb WHERE tb.vehicleId = v.id "
            + "AND tb.fromDate <= :toDate AND tb.toDate >= :fromDate AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') "
            + "AND tb.tripEndedAt IS NULL AND (:excludeBookingId IS NULL OR tb.id <> :excludeBookingId)) "
            + "ORDER BY v.registrationNumber, v.id")
    List<Vehicle> findFreeVehicles(@Param("ownerId") Long ownerId,
                                   @Param("category") CarCategory category,
                                   @Param("seats") int seats,
                                   @Param("needAc") boolean needAc,
                                   @Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate,
                                   @Param("excludeBookingId") UUID excludeBookingId);

    /** Unfinished trips this car is on that overlap these dates, other than the given booking. */
    @Query("SELECT COUNT(tb) FROM TravelBooking tb WHERE tb.vehicleId = :vehicleId "
            + "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL "
            + "AND tb.fromDate <= :toDate AND tb.toDate >= :fromDate "
            + "AND (:excludeBookingId IS NULL OR tb.id <> :excludeBookingId)")
    long countOverlappingActiveBookings(@Param("vehicleId") Long vehicleId,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate,
                                        @Param("excludeBookingId") UUID excludeBookingId);

    /** Unfinished trips this car is on at all (a car on a trip cannot be deactivated). */
    @Query("SELECT COUNT(tb) FROM TravelBooking tb WHERE tb.vehicleId = :vehicleId "
            + "AND tb.status IN ('PENDING', 'CONFIRMED', 'STARTED') AND tb.tripEndedAt IS NULL")
    long countActiveBookings(@Param("vehicleId") Long vehicleId);
}
