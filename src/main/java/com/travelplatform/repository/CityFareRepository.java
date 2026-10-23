package com.travelplatform.repository;

import com.travelplatform.entity.CityFare;
import com.travelplatform.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CityFareRepository extends JpaRepository<CityFare, VehicleType> {
}
