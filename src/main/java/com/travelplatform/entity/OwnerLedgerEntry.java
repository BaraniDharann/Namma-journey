package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One movement of money between the platform and a travels owner for the City rides their cars
 * do (V35). Same rules as {@link DriverLedgerEntry}: only ever added, and the balance is the sum of
 * {@link #amount}, positive when the platform owes the owner.
 */
@Entity
@Table(name = "owner_ledger_entries")
@Data
@NoArgsConstructor
public class OwnerLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "booking_id")
    private UUID bookingId;

    /** Who drove the ride, and in which car: for the owner's own records. */
    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverLedgerEntry.Type type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal fare;

    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(precision = 12, scale = 2)
    private BigDecimal commission;

    @Column(length = 64)
    private String reference;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
