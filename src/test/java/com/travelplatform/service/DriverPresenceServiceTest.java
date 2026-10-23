package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The in-memory presence store - what Ride Now searches when Redis is off or down. */
class DriverPresenceServiceTest {

    private static final double LAT = 12.9716;
    private static final double LON = 77.5946;

    private DriverPresenceService presence;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        presence = new DriverPresenceService(noRedis, "caffeine", new DispatchProperties());
        at(0);
    }

    private void at(long seconds) {
        presence.setClock(Clock.fixed(Instant.ofEpochSecond(1_800_000_000L + seconds), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("finds drivers inside the radius, nearest first")
    void nearestFirstWithinRadius() {
        presence.report(1L, LAT + 2.0 / 111.2, LON);
        presence.report(2L, LAT + 0.4 / 111.2, LON);
        presence.report(3L, LAT + 7.0 / 111.2, LON);

        List<DriverPresenceService.Nearby> found = presence.findWithin(LAT, LON, 5);

        assertEquals(List.of(2L, 1L), found.stream().map(DriverPresenceService.Nearby::driverId).toList());
        assertEquals(0.4, found.get(0).distanceKm(), 0.01);
    }

    @Test
    @DisplayName("a driver whose phone stopped reporting drops out after the presence window")
    void staleDriversExpire() {
        presence.report(1L, LAT, LON);
        at(59);
        assertEquals(1, presence.findWithin(LAT, LON, 1).size());
        at(61);
        assertTrue(presence.findWithin(LAT, LON, 1).isEmpty());
    }

    @Test
    @DisplayName("going offline removes the driver at once")
    void removeTakesDriverOut() {
        presence.report(1L, LAT, LON);
        presence.remove(1L);
        assertTrue(presence.findWithin(LAT, LON, 5).isEmpty());
    }

    @Test
    @DisplayName("a later report moves the driver; a missing fix is ignored")
    void reportsReplaceAndJunkIsIgnored() {
        presence.report(1L, LAT + 4.0 / 111.2, LON);
        presence.report(1L, LAT, LON);
        presence.report(2L, 0, 0);
        presence.report(3L, Double.NaN, LON);

        List<DriverPresenceService.Nearby> found = presence.findWithin(LAT, LON, 1);
        assertEquals(List.of(1L), found.stream().map(DriverPresenceService.Nearby::driverId).toList());
    }
}
