package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteInfo {
    private Double distanceKm;
    private Long timeMinutes;
    private String routeDetails;
}
