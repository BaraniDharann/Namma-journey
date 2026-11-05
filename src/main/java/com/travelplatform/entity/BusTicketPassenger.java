package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One traveller on a ticket, in one seat (V30). Seats on a ticket can be cancelled one by one. */
@Entity
@Table(name = "bus_ticket_passengers")
@Data
@NoArgsConstructor
public class BusTicketPassenger {

    public enum Status { CONFIRMED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "seat_label", nullable = false, length = 8)
    private String seatLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", nullable = false, length = 14)
    private BusSeatClass seatClass;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 1)
    private BusSeatAllocation.Gender gender;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal gst;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.CONFIRMED;

    @Column(name = "cancel_charge", precision = 10, scale = 2)
    private BigDecimal cancelCharge;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
