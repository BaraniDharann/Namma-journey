package com.travelplatform.repository;

import com.travelplatform.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByPlaceId(Long placeId);

    Optional<City> findByNameIgnoreCase(String name);
}
