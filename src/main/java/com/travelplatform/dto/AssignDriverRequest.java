package com.travelplatform.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignDriverRequest {
    @NotNull(message = "Driver ID is required")
    private Long driverId;
}
