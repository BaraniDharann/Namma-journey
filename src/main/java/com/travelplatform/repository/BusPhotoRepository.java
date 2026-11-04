package com.travelplatform.repository;

import com.travelplatform.entity.BusPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusPhotoRepository extends JpaRepository<BusPhoto, Long> {

    List<BusPhoto> findByBusIdOrderByPositionAscIdAsc(Long busId);

    long countByBusId(Long busId);
}
