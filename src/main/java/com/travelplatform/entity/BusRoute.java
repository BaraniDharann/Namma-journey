package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A service an owner runs with one bus (V30): ordered stops ({@link BusRouteStop}), boarding and
 * dropping points at each, fares per stop pair and seat class, the days it runs and its
 * cancellation slabs. A trip ({@link BusTrip}) is created for each running date while PUBLISHED;
 * PAUSED stops new trips. Stops cannot change once published.
 */
@Entity
@Table(name = "bus_routes")
@Data
@NoArgsConstructor
public class BusRoute {

    public enum Status { DRAFT, PUBLISHED, PAUSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "bus_id", nullable = false)
    private Long busId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Bit 0 = Monday ... bit 6 = Sunday. */
    @Column(name = "run_days", nullable = false)
    private short runDays;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    /** Null: runs until the owner sets an end date. */
    @Column(name = "active_to")
    private LocalDate activeTo;

    @Column(name = "max_seats_per_booking", nullable = false)
    private int maxSeatsPerBooking = 6;

    /** JSON slabs [{"minHours":48,"percent":10},...], minHours descending; see BusCancellationPolicy. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cancellation_policy", nullable = false, columnDefinition = "jsonb")
    private String cancellationPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
