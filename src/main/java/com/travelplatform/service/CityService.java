package com.travelplatform.service;

import com.travelplatform.entity.City;
import com.travelplatform.repository.CityRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The owner's cities, and the answer to "which city is this pickup in?". The active cities are
 * kept in memory because every City booking asks, and there are tens of cities, not thousands:
 * a linear scan is cheaper than a query. The list is replaced after each save.
 */
@Service
public class CityService {

    private final CityRepository repository;

    /** Active cities only. Replaced wholesale, never mutated, so readers need no lock. */
    private volatile List<City> active = List.of();
    /** The same cities by id. */
    private volatile java.util.Map<Long, City> activeById = java.util.Map.of();
    /** H3 hexagons over the active cities (Phase 4); disabled when H3 is unavailable. */
    private volatile com.travelplatform.service.dispatch.ZoneGrid grid =
            com.travelplatform.service.dispatch.ZoneGrid.build(List.of());

    public CityService(CityRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void refresh() {
        List<City> now = repository.findAll().stream().filter(City::isActive).toList();
        java.util.Map<Long, City> byId = new java.util.HashMap<>();
        now.forEach(c -> byId.put(c.getId(), c));
        grid = com.travelplatform.service.dispatch.ZoneGrid.build(now);
        activeById = java.util.Map.copyOf(byId);
        active = now;
    }

    /** An active city by id, from memory. */
    public Optional<City> activeCity(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(activeById.get(id));
    }

    /** True when the city searches town radii (2/5/8 km) rather than metro ones. */
    public boolean isTown(long cityId) {
        City c = activeById.get(cityId);
        return c != null && c.isTown();
    }

    /**
     * The active city whose radius contains the point. Circles can overlap near a border; the one
     * whose centre is nearest wins, so a pickup always belongs to exactly one city. The H3 grid
     * answers first; a point it has no cell for (an edge sliver) falls back to the circles.
     */
    public Optional<City> cityFor(double lat, double lon) {
        Optional<City> fromGrid = grid.zoneAt(lat, lon).map(activeById::get);
        if (fromGrid.isPresent()) {
            return fromGrid;
        }
        return cityByCircles(lat, lon);
    }

    /**
     * Every active city whose circle comes within {@code km} of the point, nearest centre first.
     * A ride near a border searches these too: the nearest free driver may be across it.
     */
    public List<City> citiesNear(double lat, double lon, double km) {
        return active.stream()
                .filter(c -> TripRules.distanceKm(lat, lon, c.getCenterLat(), c.getCenterLon()) <= c.getRadiusKm() + km)
                .sorted(Comparator.comparingDouble(c -> TripRules.distanceKm(lat, lon, c.getCenterLat(), c.getCenterLon())))
                .toList();
    }

    /** The active city nearest the point by centre, with how far its edge is; for "nearest zone is X". */
    public Optional<City> nearestCity(double lat, double lon) {
        return active.stream().min(Comparator.comparingDouble(c ->
                TripRules.distanceKm(lat, lon, c.getCenterLat(), c.getCenterLon()) - c.getRadiusKm()));
    }

    /** How many hexagons the grid holds; 0 means it is off and every lookup scans the circles. */
    public int gridCells() {
        return grid.cells();
    }

    private Optional<City> cityByCircles(double lat, double lon) {
        City best = null;
        double bestKm = Double.MAX_VALUE;
        for (City c : active) {
            double km = TripRules.distanceKm(lat, lon, c.getCenterLat(), c.getCenterLon());
            if (km <= c.getRadiusKm() && km < bestKm) {
                best = c;
                bestKm = km;
            }
        }
        return Optional.ofNullable(best);
    }

    public List<City> all() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(City::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Long> activeIds() {
        return active.stream().map(City::getId).toList();
    }

    @Transactional
    public City save(City city) {
        if (city.getName() == null || city.getName().isBlank()) {
            throw new IllegalArgumentException("City name is required");
        }
        city.setName(city.getName().trim());
        if (!(city.getRadiusKm() >= 1 && city.getRadiusKm() <= 100)) {
            throw new IllegalArgumentException("Radius must be between 1 and 100 km");
        }
        if (!(city.getCenterLat() >= -90 && city.getCenterLat() <= 90)
                || !(city.getCenterLon() >= -180 && city.getCenterLon() <= 180)) {
            throw new IllegalArgumentException("Choose the city's centre on the map");
        }
        if (city.getCommissionPercent() == null) {
            city.setCommissionPercent(new java.math.BigDecimal("10.00"));
        }
        if (city.getCommissionPercent().signum() < 0 || city.getCommissionPercent().compareTo(java.math.BigDecimal.valueOf(50)) > 0) {
            throw new IllegalArgumentException("Commission must be between 0 and 50 %");
        }
        city.setCommissionPercent(city.getCommissionPercent().setScale(2, java.math.RoundingMode.HALF_UP));
        if (city.getKind() == null || city.getKind().isBlank()) {
            city.setKind(City.KIND_METRO);
        }
        city.setKind(city.getKind().trim().toUpperCase(java.util.Locale.ROOT));
        if (!City.KIND_METRO.equals(city.getKind()) && !City.KIND_TOWN.equals(city.getKind())) {
            throw new IllegalArgumentException("Zone type must be METRO or TOWN");
        }
        if (city.getCreatedAt() == null) {
            city.setCreatedAt(LocalDateTime.now());
        }
        City saved;
        try {
            saved = repository.saveAndFlush(city);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("A city called " + city.getName() + " already exists");
        }
        refresh();
        return saved;
    }

    /** Edits an existing city in place; its id and creation time stay. */
    @Transactional
    public City update(Long id, City changes) {
        City existing = repository.findById(id)
                .orElseThrow(() -> new com.travelplatform.exception.ResourceNotFoundException("City not found"));
        existing.setName(changes.getName());
        existing.setCenterLat(changes.getCenterLat());
        existing.setCenterLon(changes.getCenterLon());
        existing.setRadiusKm(changes.getRadiusKm());
        existing.setActive(changes.isActive());
        existing.setCommissionPercent(changes.getCommissionPercent());
        // Older admin screens send no type; keep the zone's own rather than resetting it.
        if (changes.getKind() != null && !changes.getKind().isBlank()) {
            existing.setKind(changes.getKind());
        }
        // Edited by the admin: theirs now, so a driver joining never switches it back on.
        existing.setAutoCreated(false);
        return save(existing);
    }

    /**
     * The City zone for a directory town, made now if it has none. A town an admin already drew
     * under the same name is linked rather than copied. A new zone is switched on only when
     * {@code activate} is true; an existing zone keeps its on/off, so an admin's "off" sticks.
     */
    @Transactional
    public City zoneForPlace(com.travelplatform.entity.Place place, boolean activate) {
        Optional<City> linked = repository.findByPlaceId(place.getId());
        if (linked.isPresent()) {
            City zone = linked.get();
            // A zone made inactive for an unapproved driver is switched on once one is approved,
            // unless the admin drew or edited it (then it is theirs to switch).
            if (activate && !zone.isActive() && zone.isAutoCreated()) {
                zone.setActive(true);
                return save(zone);
            }
            return zone;
        }
        Optional<City> sameName = repository.findByNameIgnoreCase(place.getName());
        if (sameName.isPresent() && sameName.get().getPlaceId() == null) {
            City zone = sameName.get();
            zone.setPlaceId(place.getId());
            zone.setState(place.getState());
            zone.setDistrict(place.getDistrict());
            return save(zone);
        }
        City zone = new City();
        // Two towns can share a name across districts or states; the second gets its district.
        zone.setName(sameName.isPresent() ? truncate(place.getName() + ", " + place.getDistrict(), 80) : place.getName());
        zone.setCenterLat(place.getLat());
        zone.setCenterLon(place.getLon());
        zone.setRadiusKm(place.getRadiusKm());
        zone.setKind(place.getKind());
        zone.setPlaceId(place.getId());
        zone.setState(place.getState());
        zone.setDistrict(place.getDistrict());
        zone.setAutoCreated(true);
        zone.setActive(activate);
        return save(zone);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** The commission for a ride in this city right now; the default 10 % if the city is gone. */
    public java.math.BigDecimal commissionPercent(Long cityId) {
        return cityId == null ? new java.math.BigDecimal("10.00")
                : repository.findById(cityId).map(City::getCommissionPercent).orElse(new java.math.BigDecimal("10.00"));
    }
}
