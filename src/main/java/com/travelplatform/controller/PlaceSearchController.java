package com.travelplatform.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // India bounding box
    private static final String INDIA_BBOX = "68.1,6.5,97.4,35.7";
    private static final String INDIA_LAT = "20.5937";
    private static final String INDIA_LON = "78.9629";

    /**
     * Place autocomplete using Photon API (Komoot) — free, fuzzy search, handles misspellings,
     * covers all India cities, towns, villages, hamlets, and remote areas.
     *
     * GET /api/places/search?q=tamabarm
     * Returns: [{shortName, displayName, lat, lon, type}]
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchPlaces(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query must be at least 2 characters"));
        }

        try {
            String encoded = URLEncoder.encode(q.trim(), StandardCharsets.UTF_8);
            String url = String.format(
                    "https://photon.komoot.io/api/?q=%s&lang=en&limit=8&bbox=%s&lat=%s&lon=%s",
                    encoded, INDIA_BBOX, INDIA_LAT, INDIA_LON
            );

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode features = root.path("features");

            List<Map<String, Object>> suggestions = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode props = feature.path("properties");
                JsonNode coords = feature.path("geometry").path("coordinates");

                String country = props.path("country").asText("");
                if (!country.isEmpty() && !country.equalsIgnoreCase("India")) continue;

                String name = props.path("name").asText("");
                String city = firstNonEmpty(props.path("city").asText(""), props.path("county").asText(""));
                String state = props.path("state").asText("");

                StringBuilder shortName = new StringBuilder(name);
                if (!city.isEmpty() && !city.equalsIgnoreCase(name)) shortName.append(", ").append(city);
                if (!state.isEmpty()) shortName.append(", ").append(state);

                StringBuilder displayName = new StringBuilder(name);
                if (!city.isEmpty() && !city.equalsIgnoreCase(name)) displayName.append(", ").append(city);
                if (!state.isEmpty()) displayName.append(", ").append(state);
                if (!country.isEmpty()) displayName.append(", ").append(country);

                Map<String, Object> place = new LinkedHashMap<>();
                place.put("shortName", shortName.toString());
                place.put("displayName", displayName.toString());
                place.put("lat", String.valueOf(coords.get(1).asDouble()));
                place.put("lon", String.valueOf(coords.get(0).asDouble()));
                place.put("type", props.path("osm_value").asText(props.path("type").asText("")));
                suggestions.add(place);
            }

            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Place search temporarily unavailable. Please try again."));
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}
