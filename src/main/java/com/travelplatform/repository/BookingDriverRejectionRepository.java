package com.travelplatform.repository;

import com.travelplatform.entity.BookingDriverRejection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingDriverRejectionRepository extends JpaRepository<BookingDriverRejection, UUID> {

    /** Every driver who has declined this booking - the exclusion list for reassignment. */
    @Query("SELECT r.driverId FROM BookingDriverRejection r WHERE r.bookingId = :bookingId")
    List<Long> findDriverIdsByBookingId(@Param("bookingId") UUID bookingId);

    boolean existsByBookingIdAndDriverId(UUID bookingId, Long driverId);
}
