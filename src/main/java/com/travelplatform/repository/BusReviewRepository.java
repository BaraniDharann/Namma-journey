package com.travelplatform.repository;

import com.travelplatform.entity.BusReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusReviewRepository extends JpaRepository<BusReview, Long> {

    Optional<BusReview> findByTicketId(UUID ticketId);
}
