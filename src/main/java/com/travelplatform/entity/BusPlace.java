package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A town a bus route can stop at (V30). Shared by every owner; names are unique ignoring case. */
@Entity
@Table(name = "bus_places")
@Data
@NoArgsConstructor
public class BusPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 80)
    private String district;

    private Double lat;

    private Double lng;

    /** The owner who added a town that was missing; null for the seeded ones. */
    @Column(name = "created_by_owner_id")
    private Long createdByOwnerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
