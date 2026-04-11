package com.travelplatform.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class TripSummaryResponse {
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
    private Double distanceKm;
    private String estimatedTimeFormatted;
    private Double totalAmount;
    private String status;
    private LocalDateTime bookingDate;
    private DriverDetailsResponse driver;
    // Payment info
    private String paymentMethod;
    private String paymentStatus;
    private LocalDateTime paymentVerifiedAt;
    // Review info
    private boolean reviewSubmitted;
}
