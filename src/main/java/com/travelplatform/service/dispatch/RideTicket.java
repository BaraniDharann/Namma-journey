package com.travelplatform.service.dispatch;

import com.travelplatform.entity.VehicleType;

import java.util.UUID;

/**
 * A waiting City ride as a round sees it: everything needed to find and filter candidates, so a
 * round reads no booking rows. {@code searchStartedEpochMs} drives both the widening search radius
 * and the waiting bonus that lets a long-waiting ride win a contested driver.
 */
public record RideTicket(UUID bookingId, long cityId, GeoPoint pickup, VehicleType vehicle, int passengers,
                         long searchStartedEpochMs) {
}
