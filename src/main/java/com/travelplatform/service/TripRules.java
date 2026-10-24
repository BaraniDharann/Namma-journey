package com.travelplatform.service;

import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.repository.TravelBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a booking request must look like before it is priced and stored.
 *
 * <p>The fare is computed from the request, so the request has to describe a trip someone could
 * actually take:
 * <ul>
 *   <li>Dates: from today (India time), at most {@link #MAX_TRIP_DAYS} days, and an hourly booking
 *       on a single day. An hourly booking to 2099 used to reserve a driver for 75 years for ₹150.</li>
 *   <li>Coordinates: inside India and at least {@link #MIN_DISTANCE_KM} apart - identical points
 *       made a ₹0 booking that could never be paid.</li>
 *   <li>Each place name must match its coordinates within {@link #PLACE_TOLERANCE_KM}: coordinates
 *       1 km apart labelled "Chennai -> Bangalore" priced a 350 km trip at ₹10. A name the place
 *       search cannot resolve (or a search outage) is not held against the traveller.</li>
 *   <li>At most app.booking.max-open-per-user (default 5) open bookings per account, so one account cannot tie up
 *       the whole fleet with unpaid reservations.</li>
 * </ul>
 */
@Component
public class TripRules {

    private static final Logger log = LoggerFactory.getLogger(TripRules.class);

    static final int MAX_TRIP_DAYS = 30;
    static final double MIN_DISTANCE_KM = 0.5;
    static final double PLACE_TOLERANCE_KM = 50;
    static final long DEFAULT_MAX_OPEN_BOOKINGS = 5;

    // Mainland India plus islands, generously rounded.
    private static final double MIN_LAT = 6.0, MAX_LAT = 37.6, MIN_LON = 68.0, MAX_LON = 97.5;

    private final PlaceSearchService places;
    private final TravelBookingRepository bookings;
    private final Clock clock;
    private final long maxOpenBookings;

    @Autowired
    public TripRules(PlaceSearchService places, TravelBookingRepository bookings,
                     @org.springframework.beans.factory.annotation.Value("${app.booking.max-open-per-user:5}") long maxOpenBookings) {
        this(places, bookings, Clock.system(ZoneId.of("Asia/Kolkata")), maxOpenBookings);
    }

    TripRules(PlaceSearchService places, TravelBookingRepository bookings, Clock clock) {
        this(places, bookings, clock, DEFAULT_MAX_OPEN_BOOKINGS);
    }

    TripRules(PlaceSearchService places, TravelBookingRepository bookings, Clock clock, long maxOpenBookings) {
        this.places = places;
        this.bookings = bookings;
        this.clock = clock;
        this.maxOpenBookings = maxOpenBookings;
    }

    /** Throws IllegalArgumentException (a 400 with the message) when the trip is not bookable. */
    public void check(TravelBookingRequest r) {
        checkDates(r.getFromDate(), r.getToDate(), r.getBookingType());
        if (r.getFromLat() != null && r.getFromLon() != null && r.getToLat() != null && r.getToLon() != null) {
            checkCoordinates(r);
        }
    }

    public void checkOpenBookings(UUID userId) {
        if (bookings.countOpenBookingsByUserId(userId) >= maxOpenBookings) {
            throw new IllegalArgumentException("You already have " + maxOpenBookings
                    + " upcoming bookings. Complete or cancel one before booking another.");
        }
    }

    private void checkDates(LocalDate from, LocalDate to, String bookingType) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From date and to date are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("To date cannot be before from date");
        }
        if (from.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The trip date cannot be in the past");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_TRIP_DAYS) {
            throw new IllegalArgumentException("A booking can cover at most " + MAX_TRIP_DAYS + " days");
        }
        if ("HOUR_BASED".equalsIgnoreCase(bookingType) && !from.equals(to)) {
            throw new IllegalArgumentException("An hourly booking must start and end on the same day");
        }
    }

    private void checkCoordinates(TravelBookingRequest r) {
        double fromLat = r.getFromLat(), fromLon = r.getFromLon(), toLat = r.getToLat(), toLon = r.getToLon();
        if (!inIndia(fromLat, fromLon) || !inIndia(toLat, toLon)) {
            throw new IllegalArgumentException("Pickup and drop must both be in India");
        }
        if (distanceKm(fromLat, fromLon, toLat, toLon) < MIN_DISTANCE_KM) {
            throw new IllegalArgumentException("Pickup and drop are the same place");
        }
        checkPlaceMatches("Pickup", r.getFromPlace(), fromLat, fromLon);
        checkPlaceMatches("Drop", r.getToPlace(), toLat, toLon);
    }

    private void checkPlaceMatches(String which, String placeName, double lat, double lon) {
        if (placeName == null || placeName.isBlank()) {
            return;
        }
        List<Map<String, Object>> matches;
        try {
            matches = places.searchPlaces(placeName);
        } catch (RuntimeException e) {
            log.warn("Place check skipped for '{}': {}", placeName, e.toString());
            return;
        }
        if (matches == null || matches.isEmpty()) {
            return; // unknown to the search, or the search is down: never block a genuine booking
        }
        boolean anyNear = matches.stream().anyMatch(m -> {
            try {
                double mLat = Double.parseDouble(String.valueOf(m.get("lat")));
                double mLon = Double.parseDouble(String.valueOf(m.get("lon")));
                return distanceKm(lat, lon, mLat, mLon) <= PLACE_TOLERANCE_KM;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        if (!anyNear) {
            throw new IllegalArgumentException(which + " location does not match \"" + placeName
                    + "\". Please pick the place again from the suggestions.");
        }
    }

    private static boolean inIndia(double lat, double lon) {
        return lat >= MIN_LAT && lat <= MAX_LAT && lon >= MIN_LON && lon <= MAX_LON;
    }

    /** Great-circle distance; public so the matching engine (service.dispatch) measures the same way. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
