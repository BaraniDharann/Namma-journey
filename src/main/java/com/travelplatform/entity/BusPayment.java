package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A Razorpay payment for a bus ticket (V30). Kept apart from the car {@link Payment}, which needs a
 * car booking and holds a single refund.
 */
@Entity
@Table(name = "bus_payments")
@Data
@NoArgsConstructor
public class BusPayment {

    public enum Status { CREATED, CAPTURED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.CREATED;

    @Column(name = "razorpay_order_id", unique = true, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 64)
    private String razorpayPaymentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;
}
