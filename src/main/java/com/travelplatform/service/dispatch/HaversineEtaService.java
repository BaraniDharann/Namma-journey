package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.service.TripRules;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Straight-line distance at an average city speed ({@code app.dispatch.city-speed-kmh}). Wrong in
 * absolute terms - roads are not straight and traffic is not average - but it ranks drivers in the
 * same order a road ETA usually would, which is all the solver needs until OSRM (phase C).
 */
@Service
public class HaversineEtaService implements EtaService {

    private final DispatchProperties properties;

    public HaversineEtaService(DispatchProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<Long, Integer> etaSeconds(GeoPoint pickup, Map<Long, GeoPoint> drivers) {
        double speed = Math.max(1, properties.getCitySpeedKmh());
        Map<Long, Integer> eta = new HashMap<>(drivers.size() * 2);
        drivers.forEach((driverId, at) -> {
            double km = TripRules.distanceKm(at.lat(), at.lon(), pickup.lat(), pickup.lon());
            eta.put(driverId, (int) Math.ceil(km / speed * 3600));
        });
        return eta;
    }
}
