package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriverLocationDTO {
    private Long driverId;
    private String bookingId;
    private double latitude;
    private double longitude;
    private double heading;
    private long timestamp;
}
