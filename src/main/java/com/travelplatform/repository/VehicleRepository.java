package com.travelplatform.repository;

import com.travelplatform.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByOwnerIdOrderByIdAsc(Long ownerId);

    /** Compared on the spaceless upper-case key, same as the uq_vehicles_registration index (V27). */
    @Query("SELECT v FROM Vehicle v WHERE UPPER(REPLACE(v.registrationNumber, ' ', '')) = :key")
    Optional<Vehicle> findByRegistrationKey(@Param("key") String key);
}
