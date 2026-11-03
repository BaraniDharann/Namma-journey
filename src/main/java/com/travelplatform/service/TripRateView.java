package com.travelplatform.service;

import com.travelplatform.entity.CarCategory;

import java.math.BigDecimal;

/** One car category an owner offers, with its Trip prices. */
public record TripRateView(CarCategory category, BigDecimal perKm, BigDecimal perHour) {
}
