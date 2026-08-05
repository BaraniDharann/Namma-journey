package com.travelplatform.controller;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.service.TelegramWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives Telegram bot updates.
 *
 * <p>This endpoint is necessarily public and unauthenticated in the ordinary sense: Telegram's
 * servers call it and cannot present a JWT. Its only defence is the shared secret configured
 * on the webhook, which Telegram echoes in {@code X-Telegram-Bot-Api-Secret-Token} on every
 * request. Without that check anyone who learned the URL could POST a synthetic
 * {@code callback_query} and accept or reject trips on a driver's behalf.
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramWebhookService webhookService;
    private final TelegramProperties properties;

    public TelegramWebhookController(TelegramWebhookService webhookService,
                                     TelegramProperties properties) {
        this.webhookService = webhookService;
        this.properties = properties;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveUpdate(
            @RequestHeader(value = SECRET_HEADER, required = false) String presentedSecret,
            @RequestBody Map<String, Object> update) {

        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // Fail closed. An unconfigured secret means every caller is indistinguishable from
        // Telegram, so the endpoint refuses rather than accepting updates it cannot vouch for.
        if (!properties.isWebhookVerifiable()) {
            log.error("Telegram webhook called but app.telegram.webhook-secret is not set; refusing");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!secretMatches(presentedSecret)) {
            log.warn("Telegram webhook called with a missing or incorrect secret token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        webhookService.handleUpdate(update);

        // Always 200 once the update is ours. Telegram re-delivers anything non-2xx, so
        // reporting a processing failure here would turn one bad update into a retry loop;
        // handleUpdate already logs and contains its own errors.
        return ResponseEntity.ok().build();
    }

    /** Constant-time comparison so a wrong guess cannot be refined by timing the response. */
    private boolean secretMatches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8));
    }
}
