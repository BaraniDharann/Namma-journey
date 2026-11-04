package com.travelplatform.repository;

import com.travelplatform.entity.BusOwnerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusOwnerPayoutRepository extends JpaRepository<BusOwnerPayout, Long> {

    List<BusOwnerPayout> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<BusOwnerPayout> findByStatusOrderByCreatedAtAsc(BusOwnerPayout.Status status);

    Optional<BusOwnerPayout> findByTripId(Long tripId);

    List<BusOwnerPayout> findAllByOrderByCreatedAtDesc();
}
