package com.travelplatform.repository;

import com.travelplatform.entity.BusPlace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusPlaceRepository extends JpaRepository<BusPlace, Long> {

    /** Same key as the uq_bus_places_name index (V30): the name ignoring case. */
    @Query("SELECT p FROM BusPlace p WHERE LOWER(p.name) = LOWER(:name)")
    Optional<BusPlace> findByNameKey(@Param("name") String name);

    /** Towns whose name or district contains the text, names starting with it first. */
    @Query("SELECT p FROM BusPlace p WHERE LOWER(p.name) LIKE CONCAT('%', :q, '%') "
            + "OR LOWER(COALESCE(p.district, '')) LIKE CONCAT('%', :q, '%') "
            + "ORDER BY CASE WHEN LOWER(p.name) LIKE CONCAT(:q, '%') THEN 0 "
            + "WHEN LOWER(p.name) LIKE CONCAT('%', :q, '%') THEN 1 ELSE 2 END, p.name")
    List<BusPlace> search(@Param("q") String lowerCaseText, Pageable page);

    List<BusPlace> findAllByOrderByNameAsc(Pageable page);
}
