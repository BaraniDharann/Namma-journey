package com.travelplatform.controller;

import com.travelplatform.service.PlaceSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    /**
     * Place autocomplete using Photon API (Komoot) — results are cached in Redis for 24h.
     * GET /api/places/search?q=tamabarm
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchPlaces(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query must be at least 2 characters"));
        }
        List<Map<String, Object>> suggestions = placeSearchService.searchPlaces(q);
        return ResponseEntity.ok(suggestions);
    }
}
