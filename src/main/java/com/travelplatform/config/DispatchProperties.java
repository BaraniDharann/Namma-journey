package com.travelplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tuning for City ride matching ({@code app.dispatch.*}). The defaults are the agreed behaviour:
 * every 2 s each city's waiting rides and free drivers are paired in one batch; a ride searches
 * 1 km at first, 3 km after 20 s and 5 km after 40 s; a driver has 15 seconds to accept; and the
 * booking goes to the owner after two minutes with nobody found.
 */
@Component
@ConfigurationProperties(prefix = "app.dispatch")
public class DispatchProperties {

    /** Search radii around the pickup, smallest first, paired with {@link #radiusAfterSeconds}. */
    private List<Double> radiiKm = List.of(1.0, 3.0, 5.0);

    /** Seconds of waiting after which each radius in {@link #radiiKm} applies. */
    private List<Integer> radiusAfterSeconds = List.of(0, 20, 40);

    /** How long a driver has to accept before the offer moves on. */
    private int offerSeconds = 15;

    /** How long a search keeps retrying with nobody nearby before the owner takes over. */
    private int searchTimeoutSeconds = 120;

    /** A driver whose phone has not reported a position for this long is treated as gone. */
    private int presenceTtlSeconds = 60;

    /** Pause between one city's matching rounds. */
    private int roundIntervalMs = 2000;

    /** Drivers per ride handed to the solver (K): the nearest that may take it. */
    private int candidatesPerRide = 10;

    /** Nearest drivers read from the location set per ride, before filtering down to K. */
    private int searchCount = 30;

    /** A driver further than this from the pickup, in seconds, is not offered the ride. */
    private int maxEtaSeconds = 900;

    /** Seconds of ETA one second of waiting is worth: a ride that waited longer wins a contested driver. */
    private int waitWeight = 2;

    /** Waiting rides read per city per round; anything beyond waits for the next round. */
    private int maxRidesPerRound = 2000;

    /**
     * Average city driving speed, for the straight-line ETA used until road ETAs (OSRM) exist.
     * Only the ranking of drivers matters to the solver, so a rough figure is enough.
     */
    private int citySpeedKmh = 20;

    public List<Integer> getRadiusAfterSeconds() {
        return radiusAfterSeconds;
    }

    public void setRadiusAfterSeconds(List<Integer> radiusAfterSeconds) {
        this.radiusAfterSeconds = radiusAfterSeconds;
    }

    public int getRoundIntervalMs() {
        return roundIntervalMs;
    }

    public void setRoundIntervalMs(int roundIntervalMs) {
        this.roundIntervalMs = roundIntervalMs;
    }

    public int getCandidatesPerRide() {
        return candidatesPerRide;
    }

    public void setCandidatesPerRide(int candidatesPerRide) {
        this.candidatesPerRide = candidatesPerRide;
    }

    public int getSearchCount() {
        return searchCount;
    }

    public void setSearchCount(int searchCount) {
        this.searchCount = searchCount;
    }

    public int getMaxEtaSeconds() {
        return maxEtaSeconds;
    }

    public void setMaxEtaSeconds(int maxEtaSeconds) {
        this.maxEtaSeconds = maxEtaSeconds;
    }

    public int getWaitWeight() {
        return waitWeight;
    }

    public void setWaitWeight(int waitWeight) {
        this.waitWeight = waitWeight;
    }

    public int getMaxRidesPerRound() {
        return maxRidesPerRound;
    }

    public void setMaxRidesPerRound(int maxRidesPerRound) {
        this.maxRidesPerRound = maxRidesPerRound;
    }

    public int getCitySpeedKmh() {
        return citySpeedKmh;
    }

    public void setCitySpeedKmh(int citySpeedKmh) {
        this.citySpeedKmh = citySpeedKmh;
    }

    public List<Double> getRadiiKm() {
        return radiiKm;
    }

    /**
     * Search radii for a TOWN zone (V38). Drivers are thinner on the ground in a town than a
     * metro, so the search starts and ends wider; the steps use radius-after-seconds as well.
     */
    private List<Double> townRadiiKm = List.of(2.0, 5.0, 8.0);

    /** Also search the zones whose edge is within the current radius, so a border never hides the nearest driver. */
    private boolean searchNeighbourZones = true;

    /**
     * Driver quality in the matching cost (Phase 5), in seconds of ETA: a driver who declines half
     * their offers is scored as if half of accept-penalty-seconds further away; each star below
     * rating-pivot costs rating-penalty-seconds. Drivers with fewer than quality-min-offers recent
     * offers are scored on ETA alone, so a new driver is not punished for having no history.
     */
    private int acceptPenaltySeconds = 120;
    private int ratingPenaltySeconds = 60;
    private double ratingPivot = 4.5;
    private int qualityMinOffers = 5;

    /** The radii for this kind of zone. */
    public List<Double> radiiFor(boolean town) {
        return town ? townRadiiKm : radiiKm;
    }

    public List<Double> getTownRadiiKm() {
        return townRadiiKm;
    }

    public void setTownRadiiKm(List<Double> townRadiiKm) {
        this.townRadiiKm = townRadiiKm;
    }

    public boolean isSearchNeighbourZones() {
        return searchNeighbourZones;
    }

    public void setSearchNeighbourZones(boolean searchNeighbourZones) {
        this.searchNeighbourZones = searchNeighbourZones;
    }

    public int getAcceptPenaltySeconds() {
        return acceptPenaltySeconds;
    }

    public void setAcceptPenaltySeconds(int acceptPenaltySeconds) {
        this.acceptPenaltySeconds = acceptPenaltySeconds;
    }

    public int getRatingPenaltySeconds() {
        return ratingPenaltySeconds;
    }

    public void setRatingPenaltySeconds(int ratingPenaltySeconds) {
        this.ratingPenaltySeconds = ratingPenaltySeconds;
    }

    public double getRatingPivot() {
        return ratingPivot;
    }

    public void setRatingPivot(double ratingPivot) {
        this.ratingPivot = ratingPivot;
    }

    public int getQualityMinOffers() {
        return qualityMinOffers;
    }

    public void setQualityMinOffers(int qualityMinOffers) {
        this.qualityMinOffers = qualityMinOffers;
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
