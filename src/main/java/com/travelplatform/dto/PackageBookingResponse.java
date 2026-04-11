package com.travelplatform.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PackageBookingResponse {
    private UUID id;
    private Long packageId;
    private String packageName;
    private String packageCategory;
    private Integer durationDays;
    private Integer durationNights;
    private Double pricePerPerson;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private Integer numberOfPersons;
    private LocalDate travelDate;
    private String specialRequests;
    private Double totalAmount;
    private String status;
    private LocalDateTime bookingDate;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
}
