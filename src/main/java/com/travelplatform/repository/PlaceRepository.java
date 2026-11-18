package com.travelplatform.repository;

import com.travelplatform.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query("SELECT DISTINCT p.state FROM Place p ORDER BY p.state")
    List<String> findStates();

    @Query("SELECT p FROM Place p WHERE lower(p.state) = lower(:state) ORDER BY p.district, p.name")
    List<Place> findByState(@Param("state") String state);

    @Query("SELECT p FROM Place p WHERE lower(p.state) = lower(:state) AND lower(p.district) = lower(:district) "
            + "AND lower(p.name) = lower(:name)")
    Optional<Place> findExact(@Param("state") String state, @Param("district") String district,
                              @Param("name") String name);
}
