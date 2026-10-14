package com.travelplatform.service;

import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The fare is computed from what the client sends, so the request has to describe a trip someone
 * could actually take. Coordinates 1 km apart labelled "Chennai -> Bangalore" priced a 350 km
 * trip at ₹10; identical coordinates made an unpayable ₹0 booking; an hourly booking from today
 * to 2099 reserved a driver for 75 years for ₹150, and a few of those emptied the fleet.
 */
class TripRulesTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final double[] CHENNAI = {13.0827, 80.2707};
    private static final double[] VELLORE = {12.9165, 79.1325};
    private static final double[] BANGALORE = {12.9716, 77.5946};

    private PlaceSearchService places;
    private TravelBookingRepository bookings;
    private TripRules rules;

    @BeforeEach
    void setUp() {
        places = mock(PlaceSearchService.class);
        bookings = mock(TravelBookingRepository.class);
        rules = new TripRules(places, bookings, Clock.fixed(TODAY.atTime(10, 0).atZone(IST).toInstant(), IST));
        when(places.searchPlaces(anyString())).thenReturn(List.of());
    }

    private static Map<String, Object> place(double[] at) {
        return Map.of("lat", String.valueOf(at[0]), "lon", String.valueOf(at[1]));
    }

    private static TravelBookingRequest trip(String from, double[] fromAt, String to, double[] toAt,
                                            LocalDate start, LocalDate end, String type) {
        TravelBookingRequest r = new TravelBookingRequest();
        r.setFromPlace(from);
        r.setToPlace(to);
        r.setFromLat(fromAt[0]);
        r.setFromLon(fromAt[1]);
        r.setToLat(toAt[0]);
        r.setToLon(toAt[1]);
        r.setFromDate(start);
        r.setToDate(end);
        r.setBookingType(type);
        return r;
    }

    private TravelBookingRequest normal() {
        return trip("Chennai", CHENNAI, "Vellore", VELLORE, TODAY.plusDays(2), TODAY.plusDays(2), "DISTANCE_BASED");
    }

    @Test
    @DisplayName("an ordinary upcoming trip passes")
    void ordinaryTripPasses() {
        assertDoesNotThrow(() -> rules.check(normal()));
    }

    @Test
    @DisplayName("past dates, trips longer than 30 days and multi-day hourly bookings are refused")
    void datesAreBounded() {
        TravelBookingRequest past = normal();
        past.setFromDate(TODAY.minusDays(1));
        past.setToDate(TODAY.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> rules.check(past));

        TravelBookingRequest forever = normal();
        forever.setToDate(LocalDate.of(2099, 12, 31));
        assertThrows(IllegalArgumentException.class, () -> rules.check(forever));

        TravelBookingRequest hourlyLong = normal();
        hourlyLong.setBookingType("HOUR_BASED");
        hourlyLong.setToDate(TODAY.plusDays(4));
        assertThrows(IllegalArgumentException.class, () -> rules.check(hourlyLong));

        TravelBookingRequest today = normal();
        today.setFromDate(TODAY);
        today.setToDate(TODAY);
        assertDoesNotThrow(() -> rules.check(today));
    }

    @Test
    @DisplayName("pickup and drop at the same spot is refused - it would be a ₹0 booking")
    void zeroDistanceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules.check(
                trip("Chennai", CHENNAI, "Chennai", CHENNAI, TODAY.plusDays(1), TODAY.plusDays(1), "DISTANCE_BASED")));
    }

    @Test
    @DisplayName("coordinates outside India are refused")
    void outsideIndiaIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> rules.check(
                trip("London", new double[] {51.5, -0.12}, "Vellore", VELLORE, TODAY.plusDays(1), TODAY.plusDays(1), "DISTANCE_BASED")));
    }

    @Test
    @DisplayName("a place name far from its coordinates is refused (Bangalore with Chennai's coordinates)")
    void mislabelledCoordinatesAreRefused() {
        when(places.searchPlaces(eq("Bangalore"))).thenReturn(List.of(place(BANGALORE)));
        TravelBookingRequest cheat = trip("Chennai", CHENNAI, "Bangalore", new double[] {13.09, 80.28},
                TODAY.plusDays(1), TODAY.plusDays(1), "DISTANCE_BASED");
        assertThrows(IllegalArgumentException.class, () -> rules.check(cheat));
    }

    @Test
    @DisplayName("an ambiguous name passes if any match is near; a search outage never blocks booking")
    void geocodingIsTolerant() {
        when(places.searchPlaces(eq("Vellore"))).thenReturn(List.of(place(BANGALORE), place(VELLORE)));
        assertDoesNotThrow(() -> rules.check(normal()));

        when(places.searchPlaces(any())).thenReturn(List.of()); // Photon down or no result
        assertDoesNotThrow(() -> rules.check(normal()));
    }

    @Test
    @DisplayName("one account cannot hold more than 5 open bookings")
    void openBookingsAreCapped() {
        UUID user = UUID.randomUUID();
        when(bookings.countOpenBookingsByUserId(user)).thenReturn(5L);
        assertThrows(IllegalArgumentException.class, () -> rules.checkOpenBookings(user));
        when(bookings.countOpenBookingsByUserId(user)).thenReturn(4L);
        assertDoesNotThrow(() -> rules.checkOpenBookings(user));
    }
}
