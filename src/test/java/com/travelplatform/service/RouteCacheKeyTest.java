package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The "routes" cache key: coordinates rounded to 3 decimals (about 110 m), so two fare checks
 * from the same street share one routing call, while genuinely different trips never do.
 */
class RouteCacheKeyTest {

    // One degree of latitude is about 111.2 km. Chosen mid-cell so a 30 m step cannot cross a
    // rounding boundary.
    private static final double LAT = 13.0821;
    private static final double LON = 80.2707;
    private static final double TO_LAT = 13.0500;
    private static final double TO_LON = 80.2824;

    @Test
    @DisplayName("points 30 m apart share a key")
    void nearbyPointsShareAKey() {
        assertEquals(RoutingService.routeKey(LAT, LON, TO_LAT, TO_LON),
                RoutingService.routeKey(LAT + 0.030 / 111.2, LON, TO_LAT, TO_LON));
    }

    @Test
    @DisplayName("points 300 m apart get different keys")
    void pointsFarApartDiffer() {
        assertNotEquals(RoutingService.routeKey(LAT, LON, TO_LAT, TO_LON),
                RoutingService.routeKey(LAT + 0.300 / 111.2, LON, TO_LAT, TO_LON));
    }

    @Test
    @DisplayName("the key is the same whatever the server's locale")
    void keyIsLocaleIndependent() {
        Locale before = Locale.getDefault();
        String root = RoutingService.routeKey(LAT, LON, TO_LAT, TO_LON);
        try {
            Locale.setDefault(Locale.GERMANY);
            String german = RoutingService.routeKey(LAT, LON, TO_LAT, TO_LON);
            assertFalse(german.contains(","), german);
            assertEquals(root, german);
        } finally {
            Locale.setDefault(before);
        }
    }
}
