package com.travelplatform.repository;

import com.travelplatform.entity.RideLocationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RideLocationPointRepository extends JpaRepository<RideLocationPoint, Long> {
    List<RideLocationPoint> findByBookingIdAndRecordedAtAfterOrderByRecordedAtAsc(UUID bookingId, LocalDateTime after);
    Optional<RideLocationPoint> findFirstByBookingIdOrderByRecordedAtDesc(UUID bookingId);
}
