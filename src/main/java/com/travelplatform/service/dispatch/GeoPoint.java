package com.travelplatform.service.dispatch;

/** A position on the map: a pickup, or where a driver last reported from. */
public record GeoPoint(double lat, double lon) {
}
