package com.travelplatform.repository;

import com.travelplatform.entity.OwnerTripRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OwnerTripRateRepository extends JpaRepository<OwnerTripRate, OwnerTripRate.Key> {

    List<OwnerTripRate> findByOwnerIdOrderByCategoryAsc(Long ownerId);
}
