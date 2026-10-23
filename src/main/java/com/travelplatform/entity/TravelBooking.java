package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "travel_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelBooking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private String userName;
    
    @Column(nullable = false)
    private String userPhone;
    
    @Column(nullable = false)
    private String fromPlace;
    
    @Column(nullable = false)
    private String toPlace;

    @Column
    private Double fromLat;

    @Column
    private Double fromLon;

    @Column
    private Double toLat;

    @Column
    private Double toLon;

    @Column(nullable = false)
    private LocalDate fromDate;
    
    @Column(nullable = false)
    private LocalDate toDate;
    
    @Column(nullable = false)
    private Integer travelDays;
    
    @Column(nullable = false)
    private Integer travelMembers;
    
    @Column(nullable = false)
    private String acType;
    
    @Column(nullable = false)
    private Double distanceKm;
    
    @Column(nullable = false)
    private Long estimatedTimeMinutes;
    
    @Column(columnDefinition = "TEXT")
    private String routeDetails;
    
    @Column(nullable = false)
    private LocalDateTime bookingDate;
    
    @Column
    private Long driverId;
    
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingType bookingType = BookingType.DISTANCE_BASED;

    @Column
    private Integer bookingHours;

    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerHourAtBooking;

    @Version
    @Column
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_mode", nullable = false, length = 16)
    private DispatchMode dispatchMode = DispatchMode.SCHEDULED;

    /** Null for scheduled bookings: only a Ride Now booking searches for a nearby driver. */
    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_state", length = 16)
    private DispatchState dispatchState;

    @Column(name = "dispatch_started_at")
    private LocalDateTime dispatchStartedAt;

    @Column(name = "offer_expires_at")
    private LocalDateTime offerExpiresAt;

    @Column(name = "offer_distance_km")
    private Double offerDistanceKm;

    /** Set when the driver ends the trip; from then on it no longer occupies them (V13). */
    @Column(name = "trip_ended_at")
    private LocalDateTime tripEndedAt;

    /** City ride (now, nearest driver) or Trip (dates, by car). City rides are always RIDE_NOW. */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode", nullable = false, length = 8)
    private ServiceMode serviceMode = ServiceMode.TRIP;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 8)
    private VehicleType vehicleType = VehicleType.CAR;

    /** The city a City ride was booked in. Always null for a Trip. */
    @Column(name = "city_id")
    private Long cityId;

    public enum ServiceMode {
        CITY, TRIP
    }

    public enum BookingStatus {
        PENDING, CONFIRMED, STARTED, COMPLETED, CANCELLED
    }

    public enum BookingType {
        DISTANCE_BASED, HOUR_BASED
    }

    public enum DispatchMode {
        /** A trip for a date, assigned to any driver free on those days. */
        SCHEDULED,
        /** A trip wanted now, offered to the nearest online driver one at a time. */
        RIDE_NOW
    }

    public enum DispatchState {
        /** Looking for a driver; nobody holds the offer. */
        SEARCHING,
        /** {@code driverId} holds the offer until {@code offerExpiresAt}. */
        OFFERED,
        /** A driver accepted, or the owner assigned one. */
        MATCHED,
        /** Nobody nearby within the search time; left to the owner. */
        NO_DRIVER
    }
}
