package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    
    @Column(nullable = false)
    private Double totalAmount;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingType bookingType = BookingType.DISTANCE_BASED;

    @Column
    private Integer bookingHours;

    @Column
    private Double pricePerHourAtBooking;

    public enum BookingStatus {
        PENDING, CONFIRMED, STARTED, COMPLETED, CANCELLED
    }

    public enum BookingType {
        DISTANCE_BASED, HOUR_BASED
    }
}
