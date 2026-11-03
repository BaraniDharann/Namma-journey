package com.travelplatform.controller;

import com.travelplatform.dto.ReviewResponse;
import com.travelplatform.service.CityFareService;
import com.travelplatform.service.ReviewService;
import com.travelplatform.service.OwnerService;
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
    private final OwnerService ownerService;
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

    @GetMapping("/pricing")
    public ResponseEntity<Map<String, Object>> getPublicPricing() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(Map.of(
                    "pricePerKm", ownerService.getCurrentPricePerKm(),
                    "pricePerHour", ownerService.getCurrentPricePerHour()
                ));
    }
}
