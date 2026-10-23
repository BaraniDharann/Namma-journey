package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A driver's vehicle and which modes they take, as the owner enters it. Blank fields take defaults. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {
    private String vehicleType;
    private String vehicleNumber;
    private String vehicleModel;
    private Boolean vehicleAc;
    private Integer vehicleSeats;
    private Boolean servesCity;
    private Boolean servesTrip;
}
