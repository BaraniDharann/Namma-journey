package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.City;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The in-memory presence store - what City rides search when Redis is off or down. Drivers are
 * kept per city, so these tests stub which city a point falls in: anything north of
 * {@link #CITY_B_FROM} is city B, anything south of it city A, and the far south is no city.
 */
class DriverPresenceServiceTest {

    private static final double LAT = 12.9716;
    private static final double LON = 77.5946;
    private static final long CITY_A = 1L;
    private static final long CITY_B = 2L;
    private static final double CITY_B_FROM = LAT + 50.0 / 111.2;
    private static final double NO_CITY_BELOW = LAT - 50.0 / 111.2;

    private DriverPresenceService presence;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        presence = new DriverPresenceService(noRedis, "caffeine", new DispatchProperties());
        CityService cities = mock(CityService.class);
        when(cities.cityFor(anyDouble(), anyDouble())).thenAnswer(i -> {
            double lat = i.getArgument(0);
            if (lat < NO_CITY_BELOW) {
                return Optional.empty();
            }
            return Optional.of(city(lat >= CITY_B_FROM ? CITY_B : CITY_A));
        });
        ReflectionTestUtils.setField(presence, "cityService", cities);
        at(0);
    }

    private static City city(long id) {
        City c = new City();
        c.setId(id);
        return c;
    }

    private void at(long seconds) {
        presence.setClock(Clock.fixed(Instant.ofEpochSecond(1_800_000_000L + seconds), ZoneOffset.UTC));
    }

    private List<Long> idsNear(long cityId, double lat, double radiusKm) {
        return presence.findWithin(cityId, lat, LON, radiusKm, 30).stream()
                .map(DriverPresenceService.Nearby::driverId).toList();
    }

    @Test
    @DisplayName("finds drivers inside the radius, nearest first")
    void nearestFirstWithinRadius() {
        presence.report(1L, LAT + 2.0 / 111.2, LON);
        presence.report(2L, LAT + 0.4 / 111.2, LON);
        presence.report(3L, LAT + 7.0 / 111.2, LON);

        List<DriverPresenceService.Nearby> found = presence.findWithin(CITY_A, LAT, LON, 5, 30);

        assertEquals(List.of(2L, 1L), found.stream().map(DriverPresenceService.Nearby::driverId).toList());
        assertEquals(0.4, found.get(0).distanceKm(), 0.01);
    }

    @Test
    @DisplayName("a driver is indexed in the city they report from, and only there")
    void driverIndexedInTheirCity() {
        assertEquals(Optional.of(CITY_A), presence.report(1L, LAT, LON));

        assertEquals(List.of(1L), idsNear(CITY_A, LAT, 1));
        assertTrue(idsNear(CITY_B, LAT, 1).isEmpty());
        assertEquals(Optional.of(CITY_A), presence.cityOf(1L));
    }

    @Test
    @DisplayName("a driver outside every city is not indexed anywhere")
    void driverOutsideEveryCityNotIndexed() {
        double nowhere = NO_CITY_BELOW - 1.0 / 111.2;
        assertEquals(Optional.empty(), presence.report(1L, nowhere, LON));

        assertTrue(idsNear(CITY_A, nowhere, 5).isEmpty());
        assertTrue(idsNear(CITY_B, nowhere, 5).isEmpty());
        assertEquals(Optional.empty(), presence.cityOf(1L));
    }

    @Test
    @DisplayName("a driver who drives into the next city leaves the old city's set")
    void driverMovingCityLeavesOldSet() {
        double border = CITY_B_FROM;
        presence.report(1L, border - 0.5 / 111.2, LON);
        assertEquals(List.of(1L), idsNear(CITY_A, border, 3));

        at(10);
        assertEquals(Optional.of(CITY_B), presence.report(1L, border + 0.5 / 111.2, LON));

        assertTrue(idsNear(CITY_A, border, 3).isEmpty());
        assertEquals(List.of(1L), idsNear(CITY_B, border, 3));
        assertEquals(Optional.of(CITY_B), presence.cityOf(1L));
    }

    @Test
    @DisplayName("a report under five seconds after the last one is dropped")
    void reportsUnderFiveSecondsApartAreDropped() {
        presence.report(1L, LAT + 4.0 / 111.2, LON);

        at(4);
        assertEquals(Optional.empty(), presence.report(1L, LAT, LON));
        assertTrue(idsNear(CITY_A, LAT, 1).isEmpty());

        at(6);
        assertEquals(Optional.of(CITY_A), presence.report(1L, LAT, LON));
        assertEquals(List.of(1L), idsNear(CITY_A, LAT, 1));
    }

    @Test
    @DisplayName("the limit caps how many drivers a search returns, keeping the nearest")
    void limitCapsResults() {
        for (long id = 1; id <= 5; id++) {
            presence.report(id, LAT + id * 0.1 / 111.2, LON);
        }

        List<DriverPresenceService.Nearby> found = presence.findWithin(CITY_A, LAT, LON, 5, 3);

        assertEquals(List.of(1L, 2L, 3L), found.stream().map(DriverPresenceService.Nearby::driverId).toList());
    }

    @Test
    @DisplayName("a driver whose phone stopped reporting drops out after the presence window")
    void staleDriversExpire() {
        presence.report(1L, LAT, LON);
        at(59);
        assertEquals(1, idsNear(CITY_A, LAT, 1).size());
        at(61);
        assertTrue(idsNear(CITY_A, LAT, 1).isEmpty());
        assertEquals(Optional.empty(), presence.cityOf(1L));
    }

    @Test
    @DisplayName("going offline removes the driver at once, and their next report counts at once")
    void removeTakesDriverOut() {
        presence.report(1L, LAT, LON);
        presence.remove(1L);
        assertTrue(idsNear(CITY_A, LAT, 5).isEmpty());
        assertEquals(Optional.empty(), presence.cityOf(1L));

        assertEquals(Optional.of(CITY_A), presence.report(1L, LAT, LON));
    }

    @Test
    @DisplayName("a later report moves the driver within the city; a missing fix is ignored")
    void reportsReplaceAndJunkIsIgnored() {
        presence.report(1L, LAT + 4.0 / 111.2, LON);
        at(6);
        presence.report(1L, LAT, LON);
        presence.report(2L, 0, 0);
        presence.report(3L, Double.NaN, LON);

        assertEquals(List.of(1L), idsNear(CITY_A, LAT, 1));
    }
}
