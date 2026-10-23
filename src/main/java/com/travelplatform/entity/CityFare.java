package com.travelplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What a City ride costs in one vehicle type: max(minimum, base + per km + per minute). */
@Entity
@Table(name = "city_fares")
@Data
@NoArgsConstructor
public class CityFare {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 8)
    private VehicleType vehicleType;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKm;

    @Column(name = "per_minute", nullable = false, precision = 10, scale = 2)
    private BigDecimal perMinute;

    @Column(name = "minimum_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumFare;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
