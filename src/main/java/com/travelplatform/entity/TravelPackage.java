package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "travel_packages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PackageCategory category;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    private Integer durationNights;

    @Column(nullable = false)
    private Double pricePerPerson;

    @Column(nullable = false)
    private Integer maxGroupSize;

    // Places included in this package (stored as JSON array string)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String placesIncluded;

    // Inclusions
    @Column(nullable = false)
    private Boolean foodIncluded;

    @Column
    private String foodDetails;

    @Column(nullable = false)
    private Boolean accommodationIncluded;

    @Column
    private String accommodationDetails;

    @Column(nullable = false)
    private Boolean tollFree;

    @Column(nullable = false)
    private Boolean transportIncluded;

    @Column
    private String transportDetails;

    @Column(nullable = false)
    private Boolean guideIncluded;

    @Column(nullable = false)
    private Boolean sightseeingIncluded;

    // Image URL for the package card
    @Column
    private String imageUrl;

    // Highlights (stored as JSON array string)
    @Column(columnDefinition = "TEXT")
    private String highlights;

    // Itinerary (stored as JSON string)
    @Column(columnDefinition = "TEXT")
    private String itinerary;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Column
    private Long createdBy;

    public enum PackageCategory {
        TEMPLE,
        HONEYMOON,
        ADVENTURE,
        HILL_STATION,
        BEACH,
        HERITAGE,
        WILDLIFE,
        PILGRIMAGE,
        FAMILY,
        STATE_SPECIAL
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.active == null) this.active = true;
        if (this.foodIncluded == null) this.foodIncluded = false;
        if (this.accommodationIncluded == null) this.accommodationIncluded = false;
        if (this.tollFree == null) this.tollFree = false;
        if (this.transportIncluded == null) this.transportIncluded = false;
        if (this.guideIncluded == null) this.guideIncluded = false;
        if (this.sightseeingIncluded == null) this.sightseeingIncluded = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
