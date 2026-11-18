package com.travelplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A place the owner serves City rides in: a centre and a radius. Simple on purpose - a circle
 * is enough to say "inside or outside", and tens of them can be checked in microseconds.
 */
@Entity
@Table(name = "cities")
@Data
@NoArgsConstructor
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lon", nullable = false)
    private double centerLon;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    @Column(nullable = false)
    private boolean active = true;

    /** What the platform keeps from an independent driver's ride here, 0-50 % (V21). */
    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal commissionPercent = new java.math.BigDecimal("10.00");

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static final String KIND_METRO = "METRO";
    public static final String KIND_TOWN = "TOWN";

    /** The directory town this zone was made for (V38); null for a zone the admin drew freehand. */
    @Column(name = "place_id")
    private Long placeId;

    @Column(length = 60)
    private String state;

    @Column(length = 80)
    private String district;

    /** METRO searches 1/3/5 km, TOWN 2/5/8 km (app.dispatch.radii-km / town-radii-km). */
    @Column(nullable = false, length = 8)
    private String kind = KIND_METRO;

    /** Made when a driver picked the town, not drawn by the admin. */
    @Column(name = "auto_created", nullable = false)
    private boolean autoCreated;

    public boolean isTown() {
        return KIND_TOWN.equals(kind);
    }
}
