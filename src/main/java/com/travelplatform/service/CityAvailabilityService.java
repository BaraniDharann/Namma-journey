package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.City;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.service.dispatch.DriverCard;
import com.travelplatform.service.dispatch.DriverState;
import com.travelplatform.service.dispatch.DriverStateStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "Can I get a City ride here, and how soon?" - answered before the rider taps Book (Phase 2).
 *
 * <p>Before this the booking page showed fares for any pickup, and only the booking itself said
 * City rides did not run there. Now the page asks first: is the pickup in a zone, and how many
 * free drivers of each vehicle are within the zone's widest search radius, with the nearest one's
 * rough pickup time. A vehicle with nobody near can be greyed out; a pickup outside every zone
 * names the nearest zone so the rider knows it is the place, not the app.
 */
@Service
public class CityAvailabilityService {

    /** How many nearby drivers are read for the counts; enough to say "10+" honestly. */
    private static final int SAMPLE = 60;

    private final CityService cities;
    private final DriverPresenceService presence;
    private final DriverStateStore states;
    private final DispatchProperties properties;

    public CityAvailabilityService(CityService cities, DriverPresenceService presence,
                                   DriverStateStore states, DispatchProperties properties) {
        this.cities = cities;
        this.presence = presence;
        this.states = states;
        this.properties = properties;
    }

    public Map<String, Object> check(double lat, double lon) {
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<City> zone = cities.cityFor(lat, lon);
        if (zone.isEmpty()) {
            out.put("available", false);
            out.put("reason", "OUTSIDE_ZONES");
            cities.nearestCity(lat, lon).ifPresent(c -> {
                double edgeKm = Math.max(0, TripRules.distanceKm(lat, lon, c.getCenterLat(), c.getCenterLon()) - c.getRadiusKm());
                out.put("nearestZone", c.getName());
                out.put("nearestZoneKm", Math.round(edgeKm * 10) / 10.0);
            });
            out.put("vehicles", Map.of());
            return out;
        }
        City city = zone.get();
        List<Double> radii = properties.radiiFor(city.isTown());
        double radius = radii.stream().mapToDouble(Double::doubleValue).max().orElse(5.0);

        List<DriverPresenceService.Nearby> near = presence.findNear(city.getId(), lat, lon, radius, SAMPLE,
                properties.isSearchNeighbourZones());
        Map<Long, DriverCard> cards = near.isEmpty() ? Map.of()
                : states.cards(near.stream().map(DriverPresenceService.Nearby::driverId).toList());

        Map<String, Object> vehicles = new LinkedHashMap<>();
        int total = 0;
        for (VehicleType v : VehicleType.values()) {
            int count = 0;
            Double nearestKm = null;
            for (DriverPresenceService.Nearby n : near) {
                DriverCard card = cards.get(n.driverId());
                if (card == null || !card.active() || !card.servesCity() || card.vehicle() != v
                        || card.state() != DriverState.IDLE) {
                    continue;
                }
                count++;
                if (nearestKm == null || n.distanceKm() < nearestKm) {
                    nearestKm = n.distanceKm();
                }
            }
            total += count;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("drivers", count);
            m.put("etaMinutes", nearestKm == null ? null : etaMinutes(nearestKm));
            vehicles.put(v.name(), m);
        }
        out.put("available", true);
        out.put("zoneId", city.getId());
        out.put("zoneName", city.getName());
        out.put("zoneKind", city.getKind() == null ? City.KIND_METRO : city.getKind().toUpperCase(Locale.ROOT));
        out.put("searchRadiusKm", radius);
        out.put("driversNearby", total);
        out.put("vehicles", vehicles);
        return out;
    }

    /** Straight-line km at city speed, rounded up, at least one minute. */
    int etaMinutes(double km) {
        double speed = Math.max(5, properties.getCitySpeedKmh());
        return Math.max(1, (int) Math.ceil(km / speed * 60.0));
    }
}
