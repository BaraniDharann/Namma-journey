package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelBookingResponse {
    private UUID bookingId;
    private String userName;
    private String userPhone;
    private String fromPlace;
    private String toPlace;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer travelDays;
    private Integer travelMembers;
    private String acType;
    private Double fromLat;
    private Double fromLon;
    private Double toLat;
    private Double toLon;
    private Double distanceKm;
    private Long estimatedTimeMinutes;
    private String estimatedTimeFormatted;
    private Double totalAmount;
    private String routeDetails;
    private LocalDateTime bookingDate;
    private String status;
    private String bookingType;
    private Integer bookingHours;
    private Double pricePerHourAtBooking;
    private String driverEndTripPhoto;
    private DriverDetailsResponse driver;
}
