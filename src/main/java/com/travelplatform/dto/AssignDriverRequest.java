package com.travelplatform.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignDriverRequest {
    @NotNull(message = "Driver ID is required")
    private Long driverId;

    /** The car for a Trip; left out, the driver's usual car. Not used for City rides. */
    private Long vehicleId;
}
