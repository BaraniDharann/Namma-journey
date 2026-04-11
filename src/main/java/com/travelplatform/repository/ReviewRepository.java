package com.travelplatform.repository;

import com.travelplatform.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByBookingId(UUID bookingId);
    List<Review> findByDriverId(Long driverId);
    List<Review> findAllByOrderByCreatedAtDesc();
}
