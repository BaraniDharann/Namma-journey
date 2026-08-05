package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A driver's refusal of a specific booking.
 *
 * <p>Exists so automatic reassignment can skip drivers who have already declined. The
 * availability query selects drivers with no overlapping active trip, and a driver who
 * rejects stops overlapping the moment their id is cleared from the booking - so without
 * these rows the same trip would be offered back to the person who just turned it down.
 */
@Entity
@Table(name = "booking_driver_rejections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDriverRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private Long driverId;

    @Column(nullable = false)
    private LocalDateTime rejectedAt = LocalDateTime.now();
}
