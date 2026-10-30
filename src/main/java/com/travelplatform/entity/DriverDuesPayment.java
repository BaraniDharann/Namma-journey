package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Commission a driver says they paid the owner by UPI (V24), waiting for the owner to find it in
 * their bank and confirm. Only a confirmation touches the ledger.
 */
@Entity
@Table(name = "driver_dues_payments")
@Data
@NoArgsConstructor
public class DriverDuesPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** The UPI reference from the driver's payment app, stored upper-case. */
    @Column(nullable = false, length = 64)
    private String utr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    /** The owner-group message with Confirm / Not received, rewritten once decided. */
    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    public enum Status {
        PENDING,
        CONFIRMED,
        /** The owner could not find the money. Frees the UTR for a corrected claim. */
        REJECTED
    }
}
