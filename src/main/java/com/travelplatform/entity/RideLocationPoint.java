package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One sampled position of the car on a started trip (see V23, ride_location_trail). */
@Entity
@Table(name = "ride_location_trail")
@Data
@NoArgsConstructor
public class RideLocationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public RideLocationPoint(UUID bookingId, double latitude, double longitude, LocalDateTime recordedAt) {
        this.bookingId = bookingId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }
}
