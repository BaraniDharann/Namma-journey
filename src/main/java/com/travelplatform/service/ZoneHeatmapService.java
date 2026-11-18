package com.travelplatform.service;

import com.travelplatform.entity.City;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.service.dispatch.DriverCard;
import com.travelplatform.service.dispatch.DriverState;
import com.travelplatform.service.dispatch.DriverStateStore;
import com.travelplatform.service.dispatch.RideQueue;
import com.travelplatform.service.dispatch.RideTicket;
import com.travelplatform.service.dispatch.ZoneGrid;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supply and demand per H3 hexagon in one zone, right now (Phase 4): riders waiting for a driver
 * and drivers online and free, counted per ~0.74 km² cell. This is the view Uber computes surge
 * and driver nudges from; here it tells the admin where riders go unserved ("five waiting at the
 * bus stand, no bikes within 2 km") and where drivers sit idle.
 */
@Service
public class ZoneHeatmapService {

    private static final int MAX_DRIVERS = 5000;
    private static final int MAX_RIDES = 2000;

    private final CityService cities;
    private final DriverPresenceService presence;
    private final DriverStateStore states;
    private final RideQueue queue;

    public ZoneHeatmapService(CityService cities, DriverPresenceService presence, DriverStateStore states, RideQueue queue) {
        this.cities = cities;
        this.presence = presence;
        this.states = states;
        this.queue = queue;
    }

    public Map<String, Object> heatmap(Long cityId) {
        City city = cities.activeCity(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("No active zone with that id"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("zoneId", city.getId());
        out.put("zoneName", city.getName());
        out.put("center", List.of(city.getCenterLat(), city.getCenterLon()));
        out.put("radiusKm", city.getRadiusKm());
        out.put("gridAvailable", ZoneGrid.available());
        if (!ZoneGrid.available()) {
            out.put("cells", List.of());
            return out;
        }

        Map<Long, int[]> counts = new HashMap<>(); // cell -> {waiting, idle, busy}
        int waitingTotal = 0;
        for (RideTicket t : queue.waiting(city.getId(), MAX_RIDES)) {
            Long cell = ZoneGrid.cellOf(t.pickup().lat(), t.pickup().lon());
            if (cell != null) {
                counts.computeIfAbsent(cell, k -> new int[3])[0]++;
                waitingTotal++;
            }
        }
        List<DriverPresenceService.Nearby> online = presence.findWithin(city.getId(), city.getCenterLat(),
                city.getCenterLon(), city.getRadiusKm() + 1, MAX_DRIVERS);
        Map<Long, DriverCard> cards = online.isEmpty() ? Map.of()
                : states.cards(online.stream().map(DriverPresenceService.Nearby::driverId).toList());
        int idleTotal = 0;
        for (DriverPresenceService.Nearby n : online) {
            DriverCard card = cards.get(n.driverId());
            if (card == null || !card.active() || !card.servesCity()) {
                continue;
            }
            Long cell = ZoneGrid.cellOf(n.lat(), n.lon());
            if (cell == null) {
                continue;
            }
            if (card.state() == DriverState.IDLE) {
                counts.computeIfAbsent(cell, k -> new int[3])[1]++;
                idleTotal++;
            } else {
                counts.computeIfAbsent(cell, k -> new int[3])[2]++;
            }
        }

        List<Map<String, Object>> cells = new ArrayList<>();
        counts.forEach((cell, c) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            double[] centre = ZoneGrid.centreOf(cell);
            m.put("cell", ZoneGrid.address(cell));
            m.put("center", List.of(centre[0], centre[1]));
            m.put("boundary", ZoneGrid.boundaryOf(cell).stream().map(p -> List.of(p[0], p[1])).toList());
            m.put("waiting", c[0]);
            m.put("idleDrivers", c[1]);
            m.put("busyDrivers", c[2]);
            // Positive: riders outnumber free drivers here; what a surge or a nudge would answer.
            m.put("shortfall", c[0] - c[1]);
            cells.add(m);
        });
        cells.sort(Comparator.comparingInt((Map<String, Object> m) -> (Integer) m.get("shortfall")).reversed());
        out.put("waiting", waitingTotal);
        out.put("idleDrivers", idleTotal);
        out.put("cells", cells);
        return out;
    }
}
