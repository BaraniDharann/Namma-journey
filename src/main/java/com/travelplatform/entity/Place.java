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
 * A town a driver can say they work in (V38): State -> District -> Town. Seeded for Tamil Nadu,
 * Puducherry and Bengaluru; elsewhere a driver adds their town from a map search, and it stays
 * {@code verified = false} until the admin looks. Picking a place creates its City zone.
 */
@Entity
@Table(name = "places")
@Data
@NoArgsConstructor
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    /** METRO or TOWN; decides the zone's search radii. */
    @Column(nullable = false, length = 8)
    private String kind = City.KIND_TOWN;

    /** The radius the town's zone starts with. */
    @Column(name = "radius_km", nullable = false)
    private double radiusKm = 10;

    @Column(nullable = false)
    private boolean verified = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
