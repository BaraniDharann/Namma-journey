package com.travelplatform.service.dispatch;

import java.util.UUID;

/** An open offer: this driver is holding this ride. */
public record OfferRef(UUID bookingId, long driverId) {
}
