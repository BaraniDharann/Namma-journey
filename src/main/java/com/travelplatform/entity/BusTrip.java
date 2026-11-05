package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One run of a route on one date (V30), created by BusTripGenerator. {@link #departureAt} is the
 * earliest point time at the first stop, {@link #arrivalAt} the latest at the last stop. One per
 * route per date.
 */
@Entity
@Table(name = "bus_trips")
@Data
@NoArgsConstructor
public class BusTrip {

    public enum Status { SCHEDULED, DEPARTED, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "bus_id", nullable = false)
    private Long busId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    @Column(name = "arrival_at", nullable = false)
    private LocalDateTime arrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.SCHEDULED;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "driver_name", length = 80)
    private String driverName;

    @Column(name = "driver_phone", length = 15)
    private String driverPhone;

    @Column(name = "conductor_name", length = 80)
    private String conductorName;

    @Column(name = "conductor_phone", length = 15)
    private String conductorPhone;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
