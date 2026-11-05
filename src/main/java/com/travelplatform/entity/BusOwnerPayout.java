package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the platform owes a bus owner for one completed trip (V30): gross = fares travelled +
 * cancellation charges kept, less the owner's commission. The admin pays it by UPI or bank and
 * records the UTR.
 */
@Entity
@Table(name = "bus_owner_payouts")
@Data
@NoArgsConstructor
public class BusOwnerPayout {

    public enum Status { DUE, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "fares_travelled", nullable = false, precision = 12, scale = 2)
    private BigDecimal faresTravelled;

    @Column(name = "charges_kept", nullable = false, precision = 12, scale = 2)
    private BigDecimal chargesKept;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commission;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal net;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Status status = Status.DUE;

    @Column(length = 40)
    private String utr;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "paid_by_admin_id")
    private Long paidByAdminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
