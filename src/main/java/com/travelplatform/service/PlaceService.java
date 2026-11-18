package com.travelplatform.service;

import com.travelplatform.entity.City;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Place;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PlaceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Where drivers work: the State -> District -> Town directory, and a driver's home area.
 *
 * <p>Picking a town is what makes City rides run there. The town's zone is created if it has none
 * ({@link CityService#zoneForPlace}), so a rider booking in that town is matched with the drivers
 * who chose it, with no admin step. Seeded towns are trusted geography and their zones switch on at
 * once; a town a driver added from a map search switches on only once an approved driver works there.
 */
@Service
public class PlaceService {

    /** Letters (any script), spaces and . ' ( ) - ; the same shape as owner service areas. */
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'()\\-]{0,79}$");

    /** India's bounding box: a town outside it is a mistake, not a new market. */
    private static final double MIN_LAT = 6.0, MAX_LAT = 37.5, MIN_LON = 68.0, MAX_LON = 97.5;

    /** The radius a driver-added town's zone starts with; the admin can widen it. */
    private static final double CUSTOM_TOWN_RADIUS_KM = 10;

    private final PlaceRepository places;
    private final DriverRepository drivers;
    private final CityService cities;

    public PlaceService(PlaceRepository places, DriverRepository drivers, CityService cities) {
        this.places = places;
        this.drivers = drivers;
        this.cities = cities;
    }

    /** All 36 states and UTs; the ones with listed towns first, so a Tamil Nadu driver finds theirs at the top. */
    public Map<String, Object> states() {
        List<String> listed = places.findStates();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listed", listed);
        out.put("all", ServiceAreas.STATES);
        return out;
    }

    /** Every listed town in a state, for the District and Town dropdowns. */
    public List<Map<String, Object>> placesIn(String state) {
        return places.findByState(state == null ? "" : state.trim()).stream().map(PlaceService::view).toList();
    }

    /** What a driver is choosing: a listed town by id, or a town found on the map. */
    public record HomeAreaRequest(Long placeId, String state, String district, String town, Double lat, Double lon) {
    }

    /**
     * Sets where this driver works and returns the area with its zone. Called from signup and from
     * the driver's profile.
     */
    @Transactional
    public Map<String, Object> setHomeArea(Long driverId, HomeAreaRequest request) {
        Driver driver = drivers.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        Place place = resolve(request);
        City zone = cities.zoneForPlace(place, place.isVerified() || driver.isApproved());
        driver.setHomePlaceId(place.getId());
        driver.setHomeZoneId(zone.getId());
        drivers.save(driver);
        return homeArea(place, zone);
    }

    /** The driver's area for their profile; empty values when they have not chosen one. */
    @Transactional // not read-only: a missing zone is recreated
    public Map<String, Object> homeAreaOf(Long driverId) {
        Driver driver = drivers.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        if (driver.getHomePlaceId() == null) {
            return Map.of("chosen", false);
        }
        Place place = places.findById(driver.getHomePlaceId()).orElse(null);
        if (place == null) {
            return Map.of("chosen", false);
        }
        City zone = cities.zoneForPlace(place, false);
        return homeArea(place, zone);
    }

    /**
     * A driver who was just approved: a zone made inactive for their driver-added town switches on.
     * Never throws; approval must not fail over a zone.
     */
    @Transactional
    public void onDriverApproved(Driver driver) {
        if (driver == null || driver.getHomePlaceId() == null) {
            return;
        }
        places.findById(driver.getHomePlaceId()).ifPresent(place -> {
            City zone = cities.zoneForPlace(place, true);
            if (!zone.getId().equals(driver.getHomeZoneId())) {
                driver.setHomeZoneId(zone.getId());
                drivers.save(driver);
            }
        });
    }

    Place resolve(HomeAreaRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Choose your state, district and town");
        }
        if (request.placeId() != null) {
            return places.findById(request.placeId())
                    .orElseThrow(() -> new IllegalArgumentException("That town is not in the list. Choose it again."));
        }
        String state = officialState(request.state());
        String district = clean(request.district(), "district");
        String town = clean(request.town(), "town");
        if (request.lat() == null || request.lon() == null
                || request.lat() < MIN_LAT || request.lat() > MAX_LAT
                || request.lon() < MIN_LON || request.lon() > MAX_LON) {
            throw new IllegalArgumentException("Find your town on the map search so we know where it is");
        }
        return places.findExact(state, district, town).orElseGet(() -> {
            Place p = new Place();
            p.setState(state);
            p.setDistrict(district);
            p.setName(town);
            p.setLat(request.lat());
            p.setLon(request.lon());
            p.setKind(City.KIND_TOWN);
            p.setRadiusKm(CUSTOM_TOWN_RADIUS_KM);
            p.setVerified(false);
            try {
                return places.saveAndFlush(p);
            } catch (DataIntegrityViolationException e) {
                // Another driver added the same town in the same instant.
                return places.findExact(state, district, town).orElseThrow(() -> e);
            }
        });
    }

    private static String officialState(String raw) {
        String wanted = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return ServiceAreas.STATES.stream().filter(s -> s.toLowerCase(Locale.ROOT).equals(wanted)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose your state from the list"));
    }

    private static String clean(String raw, String what) {
        String v = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (!NAME.matcher(v).matches()) {
            throw new IllegalArgumentException("Enter your " + what + " name using letters only");
        }
        return v;
    }

    private static Map<String, Object> view(Place p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("state", p.getState());
        m.put("district", p.getDistrict());
        m.put("name", p.getName());
        m.put("kind", p.getKind());
        m.put("lat", p.getLat());
        m.put("lon", p.getLon());
        return m;
    }

    private static Map<String, Object> homeArea(Place place, City zone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chosen", true);
        m.put("place", view(place));
        m.put("zoneId", zone.getId());
        m.put("zoneName", zone.getName());
        m.put("zoneActive", zone.isActive());
        m.put("zoneRadiusKm", zone.getRadiusKm());
        return m;
    }
}
