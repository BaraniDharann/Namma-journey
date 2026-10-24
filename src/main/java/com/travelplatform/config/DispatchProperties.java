package com.travelplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tuning for Ride Now dispatch ({@code app.dispatch.*}). The defaults are the agreed behaviour:
 * search 1 km, then 3 km, then 5 km around the pickup, give each driver 30 seconds, and hand the
 * booking to the owner after two minutes with nobody found.
 */
@Component
@ConfigurationProperties(prefix = "app.dispatch")
public class DispatchProperties {

    /** Search rings around the pickup, smallest first. The last one is the furthest a driver is fetched from. */
    private List<Double> radiiKm = List.of(1.0, 3.0, 5.0);

    /** How long a driver has to accept before the offer moves on. */
    private int offerSeconds = 30;

    /** How long a search keeps retrying with nobody nearby before the owner takes over. */
    private int searchTimeoutSeconds = 120;

    /** A driver whose phone has not reported a position for this long is treated as gone. */
    private int presenceTtlSeconds = 60;

    /**
     * Average city driving speed, for the straight-line ETA used until road ETAs (OSRM) exist.
     * Only the ranking of drivers matters to the solver, so a rough figure is enough.
     */
    private int citySpeedKmh = 20;

    public int getCitySpeedKmh() {
        return citySpeedKmh;
    }

    public void setCitySpeedKmh(int citySpeedKmh) {
        this.citySpeedKmh = citySpeedKmh;
    }

    public List<Double> getRadiiKm() {
        return radiiKm;
    }

    public void setRadiiKm(List<Double> radiiKm) {
        this.radiiKm = radiiKm;
    }

    public int getOfferSeconds() {
        return offerSeconds;
    }

    public void setOfferSeconds(int offerSeconds) {
        this.offerSeconds = offerSeconds;
    }

    public int getSearchTimeoutSeconds() {
        return searchTimeoutSeconds;
    }

    public void setSearchTimeoutSeconds(int searchTimeoutSeconds) {
        this.searchTimeoutSeconds = searchTimeoutSeconds;
    }

    public int getPresenceTtlSeconds() {
        return presenceTtlSeconds;
    }

    public void setPresenceTtlSeconds(int presenceTtlSeconds) {
        this.presenceTtlSeconds = presenceTtlSeconds;
    }
}
