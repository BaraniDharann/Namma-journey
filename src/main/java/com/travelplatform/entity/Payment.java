package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private UUID bookingId;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private Double amount;
    
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
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    public enum PaymentMethod {
        UPI, CASH
    }
    
    public enum PaymentStatus {
        PENDING, VERIFIED, FAILED
    }
}
