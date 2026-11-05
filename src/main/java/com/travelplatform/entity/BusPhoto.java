package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A photo of a bus shown to customers (V30), in {@link #position} order. */
@Entity
@Table(name = "bus_photos")
@Data
@NoArgsConstructor
public class BusPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bus_id", nullable = false)
    private Long busId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(nullable = false)
    private int position;
}
