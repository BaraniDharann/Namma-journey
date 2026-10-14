package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerKm;

    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerHour;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Long updatedBy;
}
