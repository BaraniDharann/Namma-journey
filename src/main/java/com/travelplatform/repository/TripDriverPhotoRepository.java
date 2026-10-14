package com.travelplatform.repository;

import com.travelplatform.entity.TripDriverPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripDriverPhotoRepository extends JpaRepository<TripDriverPhoto, UUID> {
    Optional<TripDriverPhoto> findByBookingId(UUID bookingId);

    /** One query for a whole list of bookings, instead of one per booking. */
    List<TripDriverPhoto> findByBookingIdIn(Collection<UUID> bookingIds);
    Optional<TripDriverPhoto> findByBookingIdAndDriverId(UUID bookingId, Long driverId);
}
