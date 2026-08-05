package com.travelplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Telegram driver-dispatch bot ({@code app.telegram.*}).
 *
 * <p>The whole integration is opt-in. A deployment that sets nothing gets {@link #enabled}
 * false and every send path becomes a no-op, so the existing email and in-app notification
 * behaviour is unchanged and the test suite needs no bot.
 */
@Component
@ConfigurationProperties(prefix = "app.telegram")
public class TelegramProperties {

    private boolean enabled;
    private String botToken = "";
    private String botUsername = "";
    private String webhookSecret = "";
    private String apiBaseUrl = "https://api.telegram.org";

    /**
     * True only when the integration is switched on <em>and</em> actually usable.
     *
     * <p>{@code enabled=true} with a blank token is a misconfiguration that would otherwise
     * surface as a 401 from Telegram on every booking. Checking both here keeps that case
     * quiet and harmless instead.
     */
    public boolean isOperational() {
        return enabled && !botToken.isBlank();
    }

    /**
     * True when inbound webhook calls can be authenticated.
     *
     * <p>Separate from {@link #isOperational()} on purpose: without a configured secret the
     * webhook cannot tell Telegram apart from an arbitrary internet caller, so the endpoint
     * must refuse everything rather than fall back to trusting the request.
     */
    public boolean isWebhookVerifiable() {
        return !webhookSecret.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken == null ? "" : botToken.trim();
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        // Tolerate the '@' people naturally copy from Telegram; it breaks t.me/ deep links.
        String value = botUsername == null ? "" : botUsername.trim();
        this.botUsername = value.startsWith("@") ? value.substring(1) : value;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        String value = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        // Normalised so callers can concatenate without worrying about a double slash.
        this.apiBaseUrl = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
