package com.travelplatform.controller;

import com.travelplatform.dto.ReviewResponse;
import com.travelplatform.service.CityFareService;
import com.travelplatform.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final ReviewService reviewService;
    private final com.travelplatform.service.TripRateService tripRateService;
    private final CityFareService cityFareService;
    private final com.travelplatform.service.CityService cityService;

    @GetMapping("/city-fares")
    public ResponseEntity<Map<String, Object>> getCityFares() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(cityFareService.publicRates());
    }

    /** Active cities, for a travels owner choosing where they serve at signup. */
    @GetMapping("/cities")
    public ResponseEntity<List<Map<String, Object>>> getCities() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(cityService.all().stream()
                        .filter(com.travelplatform.entity.City::isActive)
                        .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName()))
                        .toList());
    }

    @GetMapping("/reviews")
    public ResponseEntity<List<ReviewResponse>> getPublicReviews() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(reviewService.getAllReviews());
    }

    /**
     * The car categories a Trip can be booked in, with their per-km and per-hour prices: the
     * platform owner's, since every Trip goes to them until customers can choose an owner.
     */
    @GetMapping("/trip-rates")
    public ResponseEntity<List<com.travelplatform.service.TripRateView>> getTripRates() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(tripRateService.ratesFor(tripRateService.platformOwnerId()));
    }
}
