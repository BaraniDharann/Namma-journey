package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Money an owner spent running one of their cars or buses (V33). */
@Entity
@Table(name = "vehicle_expenses")
@Data
@NoArgsConstructor
public class VehicleExpense {

    public enum Kind { CAR, BUS }

    public enum Category { FUEL, TOLL, DRIVER_PAY, MAINTENANCE, PERMIT_TAX, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_kind", nullable = false, length = 3)
    private Kind vehicleKind;

    /** A {@code vehicles.id} for a car, a {@code buses.id} for a bus. */
    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    /** The bus trip this was spent on, when it was for one run. */
    @Column(name = "bus_trip_id")
    private Long busTripId;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Category category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
