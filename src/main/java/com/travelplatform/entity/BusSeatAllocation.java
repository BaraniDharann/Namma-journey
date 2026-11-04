package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A seat taken on a trip for the legs from stop {@link #fromSeq} up to (not including) stop
 * {@link #toSeq} (V30). The database's {@code legs} range is computed from the two and its
 * exclusion constraint refuses two live (HELD, CONFIRMED, BLOCKED) allocations of one seat that
 * share a leg. A BLOCKED seat is held back by the owner over the whole route.
 */
@Entity
@Table(name = "bus_seat_allocations")
@Data
@NoArgsConstructor
public class BusSeatAllocation {

    public enum Status {
        HELD, CONFIRMED, BLOCKED, CANCELLED, EXPIRED;

        public boolean live() {
            return this == HELD || this == CONFIRMED || this == BLOCKED;
        }
    }

    public enum Gender { M, F }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "seat_label", nullable = false, length = 8)
    private String seatLabel;

    @Column(name = "from_seq", nullable = false)
    private int fromSeq;

    @Column(name = "to_seq", nullable = false)
    private int toSeq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    /** The passenger's gender; null for a BLOCKED seat. */
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(length = 1)
    private Gender gender;

    @Column(name = "ticket_id")
    private UUID ticketId;

    /** When a HELD seat is released if not paid for. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
