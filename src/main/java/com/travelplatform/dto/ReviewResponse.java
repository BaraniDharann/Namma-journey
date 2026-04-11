package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private UUID id;
    private UUID bookingId;
    private String userName;
    private String driverName;
    private Integer rating;
    private String feedback;
    private String fromPlace;
    private String toPlace;
    private LocalDateTime createdAt;
}
