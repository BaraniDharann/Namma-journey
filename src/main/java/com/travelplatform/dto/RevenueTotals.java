package com.travelplatform.dto;

import java.math.BigDecimal;

/**
 * One period's revenue, summed by Postgres (TravelBookingRepository.revenueTotals). SUM over no
 * rows is NULL in SQL; the compact constructor turns that into zero so callers never null-check.
 */
public record RevenueTotals(long trips, BigDecimal revenue, double distanceKm, long travelDays) {

    public RevenueTotals(Long trips, BigDecimal revenue, Double distanceKm, Long travelDays) {
        this(trips == null ? 0 : trips,
                revenue == null ? BigDecimal.ZERO : revenue,
                distanceKm == null ? 0.0 : distanceKm,
                travelDays == null ? 0 : travelDays);
    }
}
