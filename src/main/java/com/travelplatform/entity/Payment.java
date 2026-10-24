package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    /** Optimistic lock: a write based on a stale read is refused instead of overwriting (V11). */
    @Version
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private UUID bookingId;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    
    @Column
    private String upiTransactionId;
    
    @Column
    private Long driverId;
    
    @Column
    private LocalDateTime paymentDate;
    
    @Column
    private LocalDateTime verifiedDate;

    /** JWT subject of the owner who verified this payment. Null while it is still pending. */
    @Column
    private String verifiedBy;

    @Column
    private LocalDateTime upiLinkExpiresAt;

    /** Razorpay order this payment is collected against (order_...). Reused across retries (V17). */
    @Column
    private String razorpayOrderId;

    /** Razorpay payment that settled the order (pay_...). Unique: one charge settles one fare. */
    @Column
    private String razorpayPaymentId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    public enum PaymentMethod {
        UPI, CASH,
        /** Paid online through Razorpay Checkout; confirmed by signature, never by hand. */
        RAZORPAY
    }
    
    public enum PaymentStatus {
        PENDING, VERIFIED, FAILED
    }
}
