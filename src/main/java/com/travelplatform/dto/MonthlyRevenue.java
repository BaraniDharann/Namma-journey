package com.travelplatform.dto;

import java.math.BigDecimal;

/** One bar of the owner's twelve-month chart, grouped in SQL (TravelBookingRepository.monthlyRevenue). */
public record MonthlyRevenue(int month, long trips, BigDecimal revenue, double distanceKm) {

    public MonthlyRevenue(Integer month, Long trips, BigDecimal revenue, Double distanceKm) {
        this(month == null ? 0 : month.intValue(), trips == null ? 0 : trips,
                revenue == null ? BigDecimal.ZERO : revenue,
                distanceKm == null ? 0.0 : distanceKm);
    }
}
