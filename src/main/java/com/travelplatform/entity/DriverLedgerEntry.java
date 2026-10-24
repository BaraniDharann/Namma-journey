package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One movement of money between the platform and an independent driver (V21). Entries are only
 * ever added, never edited: a mistake is corrected by a new entry, so the balance always has a
 * trail. Balance = sum of {@link #amount}; positive means the platform owes the driver.
 */
@Entity
@Table(name = "driver_ledger_entries")
@Data
@NoArgsConstructor
public class DriverLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    /** Signed: what this entry adds to what the platform owes the driver. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal fare;

    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(precision = 12, scale = 2)
    private BigDecimal commission;

    /** The UPI reference (UTR) of a payout or collection. */
    @Column(length = 64)
    private String reference;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum Type {
        /** A ride paid to the platform: the driver is owed the fare less commission. */
        RIDE_EARNING,
        /** A ride paid in cash to the driver: they owe the platform its commission. */
        CASH_COMMISSION,
        /** The owner sent the driver money. */
        PAYOUT,
        /** The driver sent the owner the commission they owed. */
        COLLECTION
    }
}
