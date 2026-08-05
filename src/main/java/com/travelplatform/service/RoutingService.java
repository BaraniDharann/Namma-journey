package com.travelplatform.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.util.PointList;
import com.graphhopper.util.CustomModel;
import com.travelplatform.config.ExternalHttpClients;
import com.travelplatform.dto.RouteInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.Locale;

@Service
public class RoutingService {
    
    private static final Logger logger = LoggerFactory.getLogger(RoutingService.class);
    
    @Value("${osm.file.path:india-latest.osm.pbf}")
    private String osmFilePath;
    
    @Value("${graphhopper.cache.path:graphhopper-cache}")
    private String cachePath;
    
    @Value("${graphhopper.enabled:false}")
    private boolean graphhopperEnabled;
    
    /**
     * Base URL of the OSRM route service. Defaults to the public demo server, which is rate
     * limited and explicitly not intended for production traffic — point this at a self-hosted
     * OSRM (or an equivalent provider) before going live.
     */
    @Value("${routing.osrm.base-url:http://router.project-osrm.org}")
    private String osrmBaseUrl;

    private GraphHopper hopper;
    private boolean initialized = false;
    // Timeout-bounded: a hanging third party must not pin a request thread. See ExternalHttpClients.
    private final RestTemplate restTemplate = ExternalHttpClients.forThirdPartyApis();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        if (!graphhopperEnabled) {
            logger.warn("GraphHopper is disabled. Set graphhopper.enabled=true to enable routing.");
            return;
        }
        
        File osmFile = new File(osmFilePath);
        if (!osmFile.exists()) {
            logger.error("OSM file not found: {}. GraphHopper routing will be disabled.", osmFilePath);
            logger.info("To enable routing, download OSM file from: https://download.geofabrik.de/asia/india-latest.osm.pbf");
            return;
        }
        
        try {
            hopper = new GraphHopper();
            hopper.setOSMFile(osmFilePath);
            hopper.setGraphHopperLocation(cachePath);
            hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("custom").setCustomModel(new CustomModel()));
            hopper.importOrLoad();
            initialized = true;
            logger.info("GraphHopper initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize GraphHopper: {}", e.getMessage());
        }
    }
    
    /**
     * Calculate route using exact coordinates from frontend (accurate).
     */
    public RouteInfo calculateRoute(double fromLat, double fromLon, double toLat, double toLon) {
        double[] fromCoords = {fromLat, fromLon};
        double[] toCoords = {toLat, toLon};

        if (initialized) {
            try {
                GHRequest request = new GHRequest(fromLat, fromLon, toLat, toLon)
                        .setProfile("car")
                        .setLocale(Locale.ENGLISH);
                GHResponse response = hopper.route(request);
                if (!response.hasErrors()) {
                    ResponsePath path = response.getBest();
                    double distanceKm = path.getDistance() / 1000.0;
                    long timeMinutes = path.getTime() / 60000;
                    return new RouteInfo(distanceKm, timeMinutes, buildRouteDetails(path));
                }
                logger.warn("GraphHopper routing error, falling back to OSRM: {}", response.getErrors());
            } catch (Exception e) {
                logger.warn("GraphHopper failed, falling back to OSRM: {}", e.getMessage());
            }
        }

        // Fallback: OSRM then Haversine
        try {
            return calculateRoadDistanceOSRM(fromCoords, toCoords);
        } catch (Exception osrmError) {
            logger.warn("OSRM API failed, using Haversine: {}", osrmError.getMessage());
            double distanceKm = haversineDistance(fromLat, fromLon, toLat, toLon);
            long timeMinutes = (long) (distanceKm / 60.0 * 60);
            return new RouteInfo(distanceKm, timeMinutes,
                    String.format("Estimated: %.2f km, %d min (straight-line)", distanceKm, timeMinutes));
        }
    }

    /**
     * Calculate route using place names (legacy fallback with geocoding).
     */
    public RouteInfo calculateRoute(String fromPlace, String toPlace) {
        if (!initialized) {
            return calculateFallbackRoute(fromPlace, toPlace);
        }
        
        try {
            double[] fromCoords = geocode(fromPlace);
            double[] toCoords = geocode(toPlace);
            
            GHRequest request = new GHRequest(fromCoords[0], fromCoords[1], toCoords[0], toCoords[1])
                    .setProfile("car")
                    .setLocale(Locale.ENGLISH);
            
            GHResponse response = hopper.route(request);
            
            if (response.hasErrors()) {
                logger.warn("GraphHopper routing error, using fallback: {}", response.getErrors());
                return calculateFallbackRoute(fromPlace, toPlace);
            }
            
            ResponsePath path = response.getBest();
            double distanceKm = path.getDistance() / 1000.0;
            long timeMinutes = path.getTime() / 60000;
            
            String routeDetails = buildRouteDetails(path);
            
            return new RouteInfo(distanceKm, timeMinutes, routeDetails);
            
        } catch (Exception e) {
            logger.error("Failed to calculate route: {}", e.getMessage());
            return calculateFallbackRoute(fromPlace, toPlace);
        }
    }
    
    private RouteInfo calculateFallbackRoute(String fromPlace, String toPlace) {
        try {
            double[] fromCoords = geocode(fromPlace);
            double[] toCoords = geocode(toPlace);
            
            // Try OSRM API for road distance
            try {
                return calculateRoadDistanceOSRM(fromCoords, toCoords);
            } catch (Exception osrmError) {
                logger.warn("OSRM API failed, using Haversine fallback: {}", osrmError.getMessage());
                double distanceKm = haversineDistance(fromCoords[0], fromCoords[1], toCoords[0], toCoords[1]);
                long timeMinutes = (long) (distanceKm / 60.0 * 60);
                
                String routeDetails = String.format("Estimated route: %.2f km, %d minutes (straight-line calculation)", 
                        distanceKm, timeMinutes);
                
                return new RouteInfo(distanceKm, timeMinutes, routeDetails);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate route: " + e.getMessage());
        }
    }
    
    private RouteInfo calculateRoadDistanceOSRM(double[] fromCoords, double[] toCoords) throws Exception {
        String url = String.format(
            "%s/route/v1/driving/%f,%f;%f,%f?overview=false",
            osrmBaseUrl, fromCoords[1], fromCoords[0], toCoords[1], toCoords[0]
        );
        
        String response = restTemplate.getForObject(url, String.class);
        JsonNode root = objectMapper.readTree(response);
        
        if (!"Ok".equals(root.path("code").asText())) {
            throw new RuntimeException("OSRM routing failed");
        }
        
        JsonNode route = root.path("routes").get(0);
        double distanceKm = route.path("distance").asDouble() / 1000.0;
        long timeMinutes = route.path("duration").asLong() / 60;
        
        String routeDetails = String.format("Road route: %.2f km, %d minutes (via OSRM)", 
                distanceKm, timeMinutes);
        
        return new RouteInfo(distanceKm, timeMinutes, routeDetails);
    }
    
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    private double[] geocode(String place) {
        // Use Photon API (fuzzy search, handles misspellings, covers all India villages)
        String trimmed = place.trim();
        try {
            String encoded = java.net.URLEncoder.encode(trimmed, java.nio.charset.StandardCharsets.UTF_8);
            String url = String.format(
                "https://photon.komoot.io/api/?q=%s&lang=en&limit=5&bbox=68.1,6.5,97.4,35.7&lat=20.5937&lon=78.9629",
                encoded
            );

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");

            for (JsonNode feature : features) {
                JsonNode props = feature.path("properties");
                String country = props.path("country").asText("");
                if (!country.isEmpty() && !country.equalsIgnoreCase("India")) continue;

                JsonNode coords = feature.path("geometry").path("coordinates");
                double lon = coords.get(0).asDouble();
                double lat = coords.get(1).asDouble();
                String name = props.path("name").asText("");
                logger.info("Geocoded '{}' -> [{}, {}] ({})", place, lat, lon, name);
                return new double[]{lat, lon};
            }
        } catch (Exception e) {
            logger.warn("Photon geocoding failed for '{}': {}", place, e.getMessage());
        }

        throw new IllegalArgumentException(
            "Location '" + place + "' not found. Please select a location from the search suggestions."
        );
    }
    
    private String buildRouteDetails(ResponsePath path) {
        StringBuilder details = new StringBuilder();
        PointList points = path.getPoints();
        
        details.append("Route: ");
        details.append(String.format("%.2f km, ", path.getDistance() / 1000.0));
        details.append(String.format("%d minutes", path.getTime() / 60000));
        details.append("\nWaypoints: ").append(points.size());
        
        return details.toString();
    }
    
    @PreDestroy
    public void cleanup() {
        if (hopper != null && initialized) {
            hopper.close();
        }
    }
}
