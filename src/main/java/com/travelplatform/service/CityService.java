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

    public CityService(CityRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void refresh() {
        active = repository.findAll().stream().filter(City::isActive).toList();
    }

    /**
     * The active city whose radius contains the point. Circles can overlap near a border; the one
     * whose centre is nearest wins, so a pickup always belongs to exactly one city.
     */
    public Optional<City> cityFor(double lat, double lon) {
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
        return save(existing);
    }

    /** The commission for a ride in this city right now; the default 10 % if the city is gone. */
    public java.math.BigDecimal commissionPercent(Long cityId) {
        return cityId == null ? new java.math.BigDecimal("10.00")
                : repository.findById(cityId).map(City::getCommissionPercent).orElse(new java.math.BigDecimal("10.00"));
    }
}
