package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A town on a route, in order: seq 0 is where the bus starts (V30). */
@Entity
@Table(name = "bus_route_stops")
@Data
@NoArgsConstructor
public class BusRouteStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "place_id", nullable = false)
    private Long placeId;
}
