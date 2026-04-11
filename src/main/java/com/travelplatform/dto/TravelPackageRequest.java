package com.travelplatform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class TravelPackageRequest {

    @NotBlank(message = "Package name is required")
    private String name;

    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "State is required")
    private String state;

    @NotNull @Min(1)
    private Integer durationDays;

    @NotNull @Min(0)
    private Integer durationNights;

    @NotNull @Min(1)
    private Double pricePerPerson;

    @NotNull @Min(1)
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
    private List<ItineraryDay> itinerary;

    @Data
    public static class ItineraryDay {
        private Integer day;
        private String title;
        private String description;
        private List<String> activities;
    }
}
