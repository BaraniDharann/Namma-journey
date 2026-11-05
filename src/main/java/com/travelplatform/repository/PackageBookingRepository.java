package com.travelplatform.repository;

import com.travelplatform.entity.PackageBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface PackageBookingRepository extends JpaRepository<PackageBooking, UUID> {

    List<PackageBooking> findByUserIdOrderByBookingDateDesc(UUID userId);

    List<PackageBooking> findByPackageIdOrderByBookingDateDesc(Long packageId);

    List<PackageBooking> findByStatusOrderByBookingDateDesc(PackageBooking.BookingStatus status);

    List<PackageBooking> findAllByOrderByBookingDateDesc();

    /** One owner's package bookings (V27), newest first, optionally of one status. */
    List<PackageBooking> findByOwnerIdOrderByBookingDateDesc(Long ownerId);

    List<PackageBooking> findByOwnerIdAndStatusOrderByBookingDateDesc(Long ownerId, PackageBooking.BookingStatus status);
}
