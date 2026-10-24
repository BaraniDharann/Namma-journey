package com.travelplatform.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The one ride an online driver is being offered, and only what the offer card shows. Polled by
 * every online phone, so it is one indexed row and a dozen fields, not the driver's whole trip
 * list with routes and payment state.
 *
 * <p>{@code secondsLeft} rather than the deadline itself: the phone's clock is not the server's,
 * and the card counts down from what is left when it opens.
 */
public record DriverOfferView(UUID bookingId, long secondsLeft,
                              String fromPlace, String toPlace,
                              Double fromLat, Double fromLon, Double toLat, Double toLon,
                              BigDecimal totalAmount, String vehicleType, String serviceMode,
                              Double offerDistanceKm, String userName, Integer travelMembers,
                              Double distanceKm) {
}
