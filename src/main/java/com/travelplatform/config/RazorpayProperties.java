package com.travelplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Razorpay online checkout ({@code razorpay.*}).
 *
 * <p>Opt-in like Telegram: a deployment that sets no keys keeps UPI QR and cash exactly as
 * before, and the online-payment endpoints refuse with a readable message instead of calling
 * Razorpay with blank credentials. Test keys ({@code rzp_test_...}) and live keys go through the
 * same code; switching to live is only a change of environment variables.
 */
@Component
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {

    private String keyId = "";
    private String keySecret = "";
    private String webhookSecret = "";
    private String apiBaseUrl = "https://api.razorpay.com";
    /** Shown as the merchant name in the checkout popup. */
    private String merchantName = "Namma Journey";

    /** True when orders can be created: both halves of the API key are present. */
    public boolean isOperational() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    /**
     * True when webhook calls can be authenticated. Without the secret the endpoint cannot tell
     * Razorpay apart from anyone else on the internet, so it refuses everything.
     */
    public boolean isWebhookVerifiable() {
        return !webhookSecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId == null ? "" : keyId.trim();
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret == null ? "" : keySecret.trim();
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
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }
}
