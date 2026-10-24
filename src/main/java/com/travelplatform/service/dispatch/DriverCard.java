package com.travelplatform.service.dispatch;

import com.travelplatform.entity.VehicleType;

/**
 * Everything a round needs to know about a driver to decide whether they may take a ride, so the
 * round never reads the drivers table. {@code active} means online and approved: an offline or
 * unverified driver keeps a card but is never a candidate.
 */
public record DriverCard(long driverId, DriverState state, VehicleType vehicle, int seats,
                         boolean servesCity, boolean active) {

    DriverCard withState(DriverState next) {
        return new DriverCard(driverId, next, vehicle, seats, servesCity, active);
    }
}
