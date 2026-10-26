package com.travelplatform.service;

import com.travelplatform.dto.RouteInfo;
import com.travelplatform.entity.CityFare;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.CityFareRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * City ride fares: one row per vehicle type, {@code max(minimum, base + perKm·km + perMinute·min)}
 * over the road distance and time. Trips keep their own per-km / per-hour pricing.
 */
@Service
public class CityFareService {

    private final CityFareRepository repository;
    private final RoutingService routingService;

    /** Longest City ride by road. Anything further is a Trip. */
    private final double maxKm;

    public CityFareService(CityFareRepository repository, RoutingService routingService,
                           @Value("${app.city.max-km:50}") double maxKm) {
        this.repository = repository;
        this.routingService = routingService;
        this.maxKm = maxKm;
    }

    public double maxKm() {
        return maxKm;
    }

    /** Bike, auto, car - the order the traveller sees them in. */
    public List<CityFare> all() {
        List<CityFare> fares = new ArrayList<>(repository.findAll());
        fares.sort(Comparator.comparing(CityFare::getVehicleType));
        return fares;
    }

    @Transactional
    public CityFare update(VehicleType type, BigDecimal baseFare, BigDecimal perKm, BigDecimal perMinute,
                           BigDecimal minimumFare, Long ownerId) {
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type is required");
        }
        CityFare fare = repository.findById(type).orElseGet(() -> {
            CityFare f = new CityFare();
            f.setVehicleType(type);
            return f;
        });
        fare.setBaseFare(amount(baseFare, "Base fare"));
        fare.setPerKm(amount(perKm, "Price per km"));
        fare.setPerMinute(amount(perMinute, "Price per minute"));
        fare.setMinimumFare(amount(minimumFare, "Minimum fare"));
        fare.setUpdatedAt(LocalDateTime.now());
        fare.setUpdatedBy(ownerId);
        return repository.save(fare);
    }

    /** The fare for one ride, or an error if this vehicle type has no fare set. */
    public BigDecimal fare(VehicleType type, double distanceKm, long minutes) {
        CityFare fare = repository.findById(type)
                .orElseThrow(() -> new IllegalStateException("No city fare is set for " + type));
        return priced(fare, distanceKm, minutes);
    }

    static BigDecimal priced(CityFare fare, double distanceKm, long minutes) {
        BigDecimal metered = fare.getBaseFare()
                .add(fare.getPerKm().multiply(BigDecimal.valueOf(Math.max(0, distanceKm))))
                .add(fare.getPerMinute().multiply(BigDecimal.valueOf(Math.max(0, minutes))));
        return Money.paise(metered.max(fare.getMinimumFare()));
    }

    /**
     * Distance, time and the fare in every vehicle type for one pickup and drop - what the
     * traveller compares before booking. The booking itself prices the ride again from its own route.
     */
    public Map<String, Object> estimate(double fromLat, double fromLon, double toLat, double toLon) {
        RouteInfo route = routingService.calculateRoute(fromLat, fromLon, toLat, toLon);
        double km = route.getDistanceKm();
        long minutes = route.getTimeMinutes();
        List<Map<String, Object>> options = new ArrayList<>();
        for (CityFare f : all()) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("vehicleType", f.getVehicleType().name());
            option.put("fare", priced(f, km, minutes));
            option.put("maxPassengers", f.getVehicleType().maxPassengers());
            options.add(option);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distanceKm", Math.round(km * 10.0) / 10.0);
        result.put("minutes", minutes);
        result.put("maxKm", maxKm);
        result.put("withinCity", km <= maxKm);
        result.put("options", options);
        return result;
    }

    /**
     * The rate card the home page shows before anyone signs in: the rates per vehicle and the
     * longest City ride. Leaves out who last changed a fare and when.
     */
    public Map<String, Object> publicRates() {
        List<Map<String, Object>> rates = new ArrayList<>();
        for (CityFare f : all()) {
            Map<String, Object> rate = new LinkedHashMap<>();
            rate.put("vehicleType", f.getVehicleType().name());
            rate.put("baseFare", f.getBaseFare());
            rate.put("perKm", f.getPerKm());
            rate.put("perMinute", f.getPerMinute());
            rate.put("minimumFare", f.getMinimumFare());
            rate.put("maxPassengers", f.getVehicleType().maxPassengers());
            rates.add(rate);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxKm", maxKm);
        result.put("rates", rates);
        return result;
    }

    private static BigDecimal amount(BigDecimal value, String what) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(what + " must be zero or more");
        }
        if (value.compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalArgumentException(what + " is too large");
        }
        return Money.paise(value);
    }
}
