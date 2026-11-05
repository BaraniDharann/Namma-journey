package com.travelplatform.repository;

import com.travelplatform.entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusRepository extends JpaRepository<Bus, Long> {

    List<Bus> findByOwnerIdOrderByIdAsc(Long ownerId);

    List<Bus> findByStatusOrderBySubmittedAtAscIdAsc(Bus.Status status);

    List<Bus> findAllByOrderByIdDesc();

    /** Compared on the spaceless upper-case key, same as the uq_buses_registration index (V30). */
    @Query("SELECT b FROM Bus b WHERE UPPER(REPLACE(b.registrationNumber, ' ', '')) = :key")
    Optional<Bus> findByRegistrationKey(@Param("key") String key);
}
