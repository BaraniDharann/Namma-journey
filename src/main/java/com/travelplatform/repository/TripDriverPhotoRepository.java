package com.travelplatform.repository;

import com.travelplatform.entity.TripDriverPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripDriverPhotoRepository extends JpaRepository<TripDriverPhoto, UUID> {
    Optional<TripDriverPhoto> findByBookingId(UUID bookingId);
    Optional<TripDriverPhoto> findByBookingIdAndDriverId(UUID bookingId, Long driverId);
}
