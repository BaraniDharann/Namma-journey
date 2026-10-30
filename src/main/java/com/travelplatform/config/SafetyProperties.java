package com.travelplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ride safety settings ({@code app.safety.*}): when the passenger is asked "are you safe?",
 * when silence turns into an alert, and where alerts go.
 *
 * <p>Everything degrades rather than breaks. With Telegram off, checks and SOS still work in the
 * app and land in the owner's notifications; with no {@link #opsChatId}, the owner just gets no
 * Telegram copy of the alert.
 */
@Component
@ConfigurationProperties(prefix = "app.safety")
public class SafetyProperties {

    private boolean enabled = true;

    /** Where the public share-trip page lives; the link is {@code <this>/trip/<token>}. */
    private String publicBaseUrl = "https://nammajourneys.site";

    /**
     * Telegram chat (usually a group the owner and staff are in, with the bot added) that
     * receives SOS alerts. Group ids are negative numbers, e.g. -1001234567890. Blank disables it.
     */
    private String opsChatId = "";

    /** First check this long after the trip starts, then every {@link #checkInterval}. */
    private Duration firstCheckAfter = Duration.ofMinutes(10);
    private Duration checkInterval = Duration.ofMinutes(25);

    /** Unanswered for this long: ask again. */
    private Duration reminderAfter = Duration.ofMinutes(2);
    /** Still unanswered this long after the first ask: alert the owner. */
    private Duration escalateAfter = Duration.ofMinutes(4);

    /** No movement beyond {@link #stoppedRadiusMeters} for this long (away from the drop) triggers a check. */
    private Duration stoppedFor = Duration.ofMinutes(5);
    private int stoppedRadiusMeters = 150;
    /** A stop this close to the drop is the trip ending, not a concern. */
    private int nearDropMeters = 500;

    /** No position from the driver's phone for this long triggers a check. */
    private Duration gpsSilentFor = Duration.ofMinutes(3);

    /** A trip running past its estimate times this factor triggers a check. */
    private double overdueFactor = 1.5;

    /** Gap between anomaly checks, so one long traffic jam does not ask every 30 seconds. */
    private Duration anomalyCooldown = Duration.ofMinutes(10);

    /** One trail point, and one live-map move, per this interval per trip. */
    private Duration trailInterval = Duration.ofSeconds(20);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        String value = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        this.publicBaseUrl = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public String getOpsChatId() {
        return opsChatId;
    }

    public void setOpsChatId(String opsChatId) {
        this.opsChatId = opsChatId == null ? "" : opsChatId.trim();
    }

    public boolean hasOpsChat() {
        return !opsChatId.isBlank();
    }

    public Duration getFirstCheckAfter() {
        return firstCheckAfter;
    }

    public void setFirstCheckAfter(Duration firstCheckAfter) {
        this.firstCheckAfter = firstCheckAfter;
    }

    public Duration getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(Duration checkInterval) {
        this.checkInterval = checkInterval;
    }

    public Duration getReminderAfter() {
        return reminderAfter;
    }

    public void setReminderAfter(Duration reminderAfter) {
        this.reminderAfter = reminderAfter;
    }

    public Duration getEscalateAfter() {
        return escalateAfter;
    }

    public void setEscalateAfter(Duration escalateAfter) {
        this.escalateAfter = escalateAfter;
    }

    public Duration getStoppedFor() {
        return stoppedFor;
    }

    public void setStoppedFor(Duration stoppedFor) {
        this.stoppedFor = stoppedFor;
    }

    public int getStoppedRadiusMeters() {
        return stoppedRadiusMeters;
    }

    public void setStoppedRadiusMeters(int stoppedRadiusMeters) {
        this.stoppedRadiusMeters = stoppedRadiusMeters;
    }

    public int getNearDropMeters() {
        return nearDropMeters;
    }

    public void setNearDropMeters(int nearDropMeters) {
        this.nearDropMeters = nearDropMeters;
    }

    public Duration getGpsSilentFor() {
        return gpsSilentFor;
    }

    public void setGpsSilentFor(Duration gpsSilentFor) {
        this.gpsSilentFor = gpsSilentFor;
    }

    public double getOverdueFactor() {
        return overdueFactor;
    }

    public void setOverdueFactor(double overdueFactor) {
        this.overdueFactor = overdueFactor;
    }

    public Duration getAnomalyCooldown() {
        return anomalyCooldown;
    }

    public void setAnomalyCooldown(Duration anomalyCooldown) {
        this.anomalyCooldown = anomalyCooldown;
    }

    public Duration getTrailInterval() {
        return trailInterval;
    }

    public void setTrailInterval(Duration trailInterval) {
        this.trailInterval = trailInterval;
    }
}
