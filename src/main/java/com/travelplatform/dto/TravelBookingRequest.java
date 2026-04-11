package com.travelplatform.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class TravelBookingRequest {
    
    @NotBlank(message = "User name is required")
    private String userName;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    private String userPhone;
    
    @NotBlank(message = "From place is required")
    private String fromPlace;
    
    @NotBlank(message = "To place is required")
    private String toPlace;
    
    @NotNull(message = "From date is required")
    private LocalDate fromDate;
    
    @NotNull(message = "To date is required")
    private LocalDate toDate;
    
    @NotNull(message = "Number of travel members is required")
    @Min(value = 1, message = "At least 1 travel member required")
    @Max(value = 20, message = "Maximum 20 travel members allowed")
    private Integer travelMembers;
    
    @NotBlank(message = "AC type is required")
    @Pattern(regexp = "^(AC|NON_AC)$", message = "AC type must be either AC or NON_AC")
    private String acType;

    @NotNull(message = "From latitude is required. Please select a location from suggestions.")
    private Double fromLat;

    @NotNull(message = "From longitude is required. Please select a location from suggestions.")
    private Double fromLon;

    @NotNull(message = "To latitude is required. Please select a location from suggestions.")
    private Double toLat;

    @NotNull(message = "To longitude is required. Please select a location from suggestions.")
    private Double toLon;

    private String bookingType; // DISTANCE_BASED or HOUR_BASED

    @Min(value = 1, message = "Minimum 1 hour required")
    @Max(value = 24, message = "Maximum 24 hours allowed")
    private Integer bookingHours;
}
