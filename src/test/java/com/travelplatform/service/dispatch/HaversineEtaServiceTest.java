package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stand-in ETA until OSRM: straight-line distance at the average city speed. */
class HaversineEtaServiceTest {

    private static final GeoPoint PICKUP = new GeoPoint(12.9716, 77.5946);

    @Test
    @DisplayName("2 km at 20 km/h is 360 seconds; a driver on the pickup is 0")
    void etaIsDistanceOverSpeed() {
        DispatchProperties properties = new DispatchProperties();
        properties.setCitySpeedKmh(20);
        EtaService eta = new HaversineEtaService(properties);

        Map<Long, GeoPoint> drivers = new HashMap<>();
        drivers.put(1L, new GeoPoint(PICKUP.lat() + 2.0 / 111.195, PICKUP.lon()));
        drivers.put(2L, PICKUP);

        Map<Long, Integer> result = eta.etaSeconds(PICKUP, drivers);

        assertTrue(Math.abs(result.get(1L) - 360) <= 1, "was " + result.get(1L));
        assertEquals(0, result.get(2L));
    }

    @Test
    @DisplayName("a faster city speed gives a shorter ETA, rounded up to the next second")
    void fasterSpeedShorterEta() {
        DispatchProperties properties = new DispatchProperties();
        properties.setCitySpeedKmh(30);
        EtaService eta = new HaversineEtaService(properties);

        int seconds = eta.etaSeconds(PICKUP, Map.of(7L, new GeoPoint(PICKUP.lat() + 1.0 / 111.195, PICKUP.lon()))).get(7L);

        assertTrue(seconds >= 120 && seconds <= 121, "was " + seconds);
    }

    @Test
    @DisplayName("no drivers, no ETAs")
    void emptyDrivers() {
        assertTrue(new HaversineEtaService(new DispatchProperties()).etaSeconds(PICKUP, Map.of()).isEmpty());
    }
}
