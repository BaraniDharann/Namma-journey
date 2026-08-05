package com.travelplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.config.ExternalHttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaceSearchService {

    private static final String INDIA_BBOX = "68.1,6.5,97.4,35.7";
    private static final String INDIA_LAT = "20.5937";
    private static final String INDIA_LON = "78.9629";

    /**
     * Geocoding provider. The default is Komoot's public Photon instance, which is a free
     * community service with no availability guarantee and no traffic entitlement — point this
     * at a self-hosted Photon or a commercial provider before going live.
     */
    @Value("${places.photon.base-url:https://photon.komoot.io}")
    private String photonBaseUrl;

    // Timeout-bounded: a hanging third party must not pin a request thread. See ExternalHttpClients.
    private final RestTemplate restTemplate = ExternalHttpClients.forThirdPartyApis();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Cached Photon API lookup. Identical queries hit Redis and skip the external HTTP call.
     * Key is normalized to lowercase so "Chennai" and "chennai" share the entry.
     */
    @Cacheable(value = "placeSearch", key = "#q.trim().toLowerCase()")
    public List<Map<String, Object>> searchPlaces(String q) {
        try {
            String encoded = URLEncoder.encode(q.trim(), StandardCharsets.UTF_8);
            String url = String.format(
                    "%s/api/?q=%s&lang=en&limit=8&bbox=%s&lat=%s&lon=%s",
                    photonBaseUrl, encoded, INDIA_BBOX, INDIA_LAT, INDIA_LON
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

            return suggestions;
        } catch (Exception e) {
            // Don't cache failures — return a marker that the controller can detect
            return Collections.emptyList();
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}
