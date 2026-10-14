package com.travelplatform.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The seven columns a revenue report lists per trip, selected directly instead of hydrating a
 * full TravelBooking entity (with its route_details text) for every row of a year.
 */
public record RevenueTrip(UUID id, String fromPlace, String toPlace, Double distanceKm,
                          Integer travelDays, BigDecimal totalAmount, LocalDateTime bookingDate) {
}
