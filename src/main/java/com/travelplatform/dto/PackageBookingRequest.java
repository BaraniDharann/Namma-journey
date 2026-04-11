package com.travelplatform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class PackageBookingRequest {

    @NotNull(message = "Package ID is required")
    private Long packageId;

    @NotBlank(message = "Name is required")
    private String userName;

    @NotBlank(message = "Email is required")
    private String userEmail;

    @NotBlank(message = "Phone is required")
    private String userPhone;

    @NotNull @Min(1)
    private Integer numberOfPersons;

    @NotNull(message = "Travel date is required")
    private LocalDate travelDate;

    private String specialRequests;
}
