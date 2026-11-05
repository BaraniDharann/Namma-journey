package com.travelplatform.service;

import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Owner;
import com.travelplatform.entity.OwnerTripRate;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.OwnerTripRateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trip prices: each owner's per-km and per-hour rate for each car category (V27). Replaces the one
 * platform-wide price (pricing_config), which is no longer read.
 */
@Service
public class TripRateService {

    private final OwnerTripRateRepository rates;
    private final OwnerRepository owners;
    private final String platformOwnerCode;
    private volatile Long platformOwnerId;

    public TripRateService(OwnerTripRateRepository rates, OwnerRepository owners,
                           @Value("${app.owners.platform-owner-code:NJO-0001}") String platformOwnerCode) {
        this.rates = rates;
        this.owners = owners;
        this.platformOwnerCode = platformOwnerCode;
    }

    /** The owner every Trip goes to until customers can choose one (sub-project 3). */
    public Long platformOwnerId() {
        Long id = platformOwnerId;
        if (id == null) {
            id = owners.findByOwnerCode(platformOwnerCode).map(Owner::getId)
                    .orElseThrow(() -> new IllegalStateException("Trips are not open yet: no platform owner " + platformOwnerCode));
            platformOwnerId = id;
        }
        return id;
    }

    @Cacheable(value = "tripRates", key = "#ownerId")
    public List<TripRateView> ratesFor(Long ownerId) {
        return rates.findByOwnerIdOrderByCategoryAsc(ownerId).stream()
                .map(r -> new TripRateView(r.getCategory(), r.getPerKm(), r.getPerHour()))
                .toList();
    }

    /** The owner's rate for a category; refused (400) when they do not offer it. */
    public OwnerTripRate rate(Long ownerId, CarCategory category) {
        return rates.findById(new OwnerTripRate.Key(ownerId, category))
                .orElseThrow(() -> new IllegalArgumentException("This car type is not available"));
    }

    @CacheEvict(value = "tripRates", key = "#ownerId")
    public TripRateView set(Long ownerId, CarCategory category, BigDecimal perKm, BigDecimal perHour) {
        if (category == null) {
            throw new IllegalArgumentException("Choose a car type");
        }
        BigDecimal km = Money.positivePrice(perKm, "Price per km");
        BigDecimal hour = Money.positivePrice(perHour, "Price per hour");
        OwnerTripRate r = rates.findById(new OwnerTripRate.Key(ownerId, category)).orElseGet(OwnerTripRate::new);
        r.setOwnerId(ownerId);
        r.setCategory(category);
        r.setPerKm(km);
        r.setPerHour(hour);
        r.setUpdatedAt(LocalDateTime.now());
        rates.save(r);
        return new TripRateView(category, km, hour);
    }
}
