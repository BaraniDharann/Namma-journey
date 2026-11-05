package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One owner's Trip prices for one car category. A category with no row cannot be booked from that owner. */
@Entity
@Table(name = "owner_trip_rates")
@IdClass(OwnerTripRate.Key.class)
@Data
@NoArgsConstructor
public class OwnerTripRate {

    @Id
    @Column(name = "owner_id")
    private Long ownerId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CarCategory category;

    @Column(name = "per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKm;

    @Column(name = "per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal perHour;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long ownerId;
        private CarCategory category;

        public Key(Long ownerId, CarCategory category) {
            this.ownerId = ownerId;
            this.category = category;
        }
    }
}
