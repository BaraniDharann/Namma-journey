package com.travelplatform.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TravelPackageResponse {
    private Long id;
    private String name;
    private String description;
    private String category;
    private String state;
    private Integer durationDays;
    private Integer durationNights;
    private Double pricePerPerson;
    private Integer maxGroupSize;
    private List<String> placesIncluded;
    private Boolean foodIncluded;
    private String foodDetails;
    private Boolean accommodationIncluded;
    private String accommodationDetails;
    private Boolean tollFree;
    private Boolean transportIncluded;
    private String transportDetails;
    private Boolean guideIncluded;
    private Boolean sightseeingIncluded;
    private String imageUrl;
    private List<String> highlights;
    private List<TravelPackageRequest.ItineraryDay> itinerary;
    private Boolean active;
    private LocalDateTime createdAt;
    private Integer totalBookings;
}
