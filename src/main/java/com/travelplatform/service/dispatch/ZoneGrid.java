package com.travelplatform.service.dispatch;

import com.travelplatform.entity.City;
import com.uber.h3core.H3Core;
import com.uber.h3core.LengthUnit;
import com.uber.h3core.util.LatLng;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uber's H3 hexagon grid over the City-ride zones. Every hexagon (resolution 8, about 0.74 km²,
 * centres about 0.8 km apart) whose centre lies inside an active zone is mapped to that zone, the
 * nearest centre winning where circles overlap - the same rule as the circle scan. A pickup's zone
 * is then one cell computation and one map read, however many zones there are.
 *
 * <p>The same cells key the supply/demand heatmap ({@link #cellOf}, {@link #centreOf}), so the
 * admin sees where riders wait and where drivers idle on one consistent grid instead of on zones
 * that change whenever someone redraws them.
 *
 * <p>H3 is a native library. If it cannot load (an unusual CPU or a locked-down temp directory)
 * the grid stays off, {@link #enabled()} is false, and callers keep the circle scan.
 */
public final class ZoneGrid {

    private static final Logger log = LoggerFactory.getLogger(ZoneGrid.class);

    public static final int RESOLUTION = 8;

    /** Loaded once per JVM; null when the native library is unavailable. */
    private static final H3Core H3 = load();

    /** Built whole and swapped in, never mutated, so readers need no lock. */
    private final Map<Long, Long> zoneByCell;

    private ZoneGrid(Map<Long, Long> zoneByCell) {
        this.zoneByCell = zoneByCell;
    }

    private static H3Core load() {
        try {
            return H3Core.newInstance();
        } catch (Throwable e) { // IOException, UnsatisfiedLinkError
            log.warn("H3 native library unavailable; City-ride zones use the circle scan: {}", e.toString());
            return null;
        }
    }

    public static boolean available() {
        return H3 != null;
    }

    /** The grid for these zones; an empty, disabled grid when H3 is unavailable. */
    public static ZoneGrid build(Collection<City> zones) {
        if (H3 == null || zones.isEmpty()) {
            return new ZoneGrid(Map.of());
        }
        double spacingKm = H3.getHexagonEdgeLengthAvg(RESOLUTION, LengthUnit.km) * Math.sqrt(3);
        Map<Long, Long> zone = new HashMap<>();
        Map<Long, Double> bestKm = new HashMap<>();
        for (City c : zones) {
            int k = (int) Math.ceil(c.getRadiusKm() / spacingKm) + 1;
            long centre = H3.latLngToCell(c.getCenterLat(), c.getCenterLon(), RESOLUTION);
            for (Long cell : H3.gridDisk(centre, k)) {
                LatLng at = H3.cellToLatLng(cell);
                double km = haversineKm(at.lat, at.lng, c.getCenterLat(), c.getCenterLon());
                if (km <= c.getRadiusKm() && km < bestKm.getOrDefault(cell, Double.MAX_VALUE)) {
                    zone.put(cell, c.getId());
                    bestKm.put(cell, km);
                }
            }
        }
        return new ZoneGrid(Map.copyOf(zone));
    }

    public boolean enabled() {
        return H3 != null && !zoneByCell.isEmpty();
    }

    /** The zone whose hexagon holds this point, if the grid knows one. Empty is not "outside": ask the circles. */
    public Optional<Long> zoneAt(double lat, double lon) {
        if (!enabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(zoneByCell.get(H3.latLngToCell(lat, lon, RESOLUTION)));
    }

    public int cells() {
        return zoneByCell.size();
    }

    /** The heatmap cell of a point; null when H3 is unavailable. */
    public static Long cellOf(double lat, double lon) {
        return H3 == null ? null : H3.latLngToCell(lat, lon, RESOLUTION);
    }

    /** A cell's centre as {lat, lon}. */
    public static double[] centreOf(long cell) {
        LatLng at = H3.cellToLatLng(cell);
        return new double[] {at.lat, at.lng};
    }

    /** A cell's six corners as [lat, lon] pairs, for drawing it. */
    public static List<double[]> boundaryOf(long cell) {
        return H3.cellToBoundary(cell).stream().map(p -> new double[] {p.lat, p.lng}).toList();
    }

    public static String address(long cell) {
        return H3.h3ToString(cell);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
