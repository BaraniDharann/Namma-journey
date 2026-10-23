package com.travelplatform.entity;

/** What a driver drives, and what a City ride asks for. Trips are always by car. */
public enum VehicleType {
    BIKE(1),
    AUTO(3),
    CAR(7);

    /** Most passengers one City ride in this vehicle may carry. A car ride also needs a car with that many seats. */
    private final int maxPassengers;

    VehicleType(int maxPassengers) {
        this.maxPassengers = maxPassengers;
    }

    public int maxPassengers() {
        return maxPassengers;
    }

    /** Seats a new driver's vehicle is assumed to have when the owner does not say. */
    public int defaultSeats() {
        return this == CAR ? 4 : maxPassengers;
    }

    /** Lenient parse for request input: null or blank means "not given". */
    public static VehicleType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Vehicle type must be BIKE, AUTO or CAR");
        }
    }
}
