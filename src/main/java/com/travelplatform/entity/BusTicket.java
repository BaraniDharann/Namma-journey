package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A customer's booking of one or more seats on a trip, from one stop to a later one (V30). PENDING
 * while its seats are held for payment, CONFIRMED once paid. Fares, GST, commission and the
 * cancellation slabs are copied onto it at booking, so later edits by the owner do not change it.
 */
@Entity
@Table(name = "bus_tickets")
@Data
@NoArgsConstructor
public class BusTicket {

    public enum Status { PENDING, CONFIRMED, PARTIALLY_CANCELLED, CANCELLED, SERVICE_CANCELLED, EXPIRED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** "NJB-" and 8 characters. */
    @Column(name = "ticket_no", nullable = false, unique = true, length = 12)
    private String ticketNo;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "from_seq", nullable = false)
    private int fromSeq;

    @Column(name = "to_seq", nullable = false)
    private int toSeq;

    @Column(name = "boarding_point_id", nullable = false)
    private Long boardingPointId;

    @Column(name = "dropping_point_id", nullable = false)
    private Long droppingPointId;

    @Column(name = "contact_phone", nullable = false, length = 15)
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "extra_phone", length = 15)
    private String extraPhone;

    @Column(name = "fare_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal fareTotal;

    @Column(name = "gst_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Column(name = "gst_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal gstTotal;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cancellation_policy", nullable = false, columnDefinition = "jsonb")
    private String cancellationPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** A payment confirming and a sweeper expiring at once: only one of them wins. */
    @Version
    @Column(nullable = false)
    private long version;
}
