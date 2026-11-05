package com.travelplatform.entity;

import java.util.Locale;

/**
 * The kinds of car a Trip is booked and priced by. Each owner sets a per-km and per-hour rate for
 * the categories they have cars in (owner_trip_rates). City rides use {@link VehicleType} instead.
 */
public enum CarCategory {
    HATCHBACK, SEDAN, SUV, TEMPO_TRAVELLER;

    /** Accepts any case and spaces or hyphens for underscores; null or blank gives null. */
    public static CarCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Car type must be Hatchback, Sedan, SUV or Tempo Traveller");
        }
    }
}
