package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A place in a stop's town where passengers get on or off (V30), e.g. "Koyambedu Omni Bus Stand".
 * Its time on a trip is the trip's date plus {@link #dayOffset} days, at {@link #time}.
 */
@Entity
@Table(name = "bus_route_points")
@Data
@NoArgsConstructor
public class BusRoutePoint {

    public enum Kind {
        BOARDING, DROPPING, BOTH;

        public boolean boards() {
            return this != DROPPING;
        }

        public boolean drops() {
            return this != BOARDING;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stop_id", nullable = false)
    private Long stopId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 150)
    private String landmark;

    private Double lat;

    private Double lng;

    @Column(name = "day_offset", nullable = false)
    private int dayOffset;

    @Column(nullable = false)
    private LocalTime time;

    /** When the bus is at this point on a trip that runs on {@code travelDate}. */
    public LocalDateTime at(LocalDate travelDate) {
        return travelDate.plusDays(dayOffset).atTime(time);
    }
}
