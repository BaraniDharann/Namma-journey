package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * The fare, without GST, for one seat class from one stop to a later one (V30). A seat class with
 * no fare for a stop pair is not sold for that pair.
 */
@Entity
@Table(name = "bus_route_fares")
@IdClass(BusRouteFare.Key.class)
@Data
@NoArgsConstructor
public class BusRouteFare {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long routeId;
        private int fromSeq;
        private int toSeq;
        private BusSeatClass seatClass;
    }

    @Id
    @Column(name = "route_id")
    private Long routeId;

    @Id
    @Column(name = "from_seq")
    private int fromSeq;

    @Id
    @Column(name = "to_seq")
    private int toSeq;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", length = 14)
    private BusSeatClass seatClass;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;
}
