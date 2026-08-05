package com.travelplatform.controller;

import com.travelplatform.config.TelegramProperties;
import com.travelplatform.service.TelegramWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The webhook is the one endpoint in the application that is publicly reachable and cannot
 * authenticate its caller the normal way — Telegram's servers have no JWT to present. The
 * shared secret header is therefore the entire boundary: anything that gets past it can post
 * a synthetic {@code callback_query} and accept or reject trips as somebody else's driver.
 *
 * <p>These tests pin that boundary, including the fail-closed case where the secret has not
 * been configured at all, which is the state a fresh deployment is in.
 */
class TelegramWebhookControllerTest {

    private static final String SECRET = "s3cret-token-value";
    private static final Map<String, Object> UPDATE = Map.of("update_id", 1);

    private static TelegramProperties properties(boolean enabled, String webhookSecret) {
        TelegramProperties props = new TelegramProperties();
        props.setEnabled(enabled);
        props.setBotToken("123:abc");
        props.setWebhookSecret(webhookSecret);
        return props;
    }

    @Test
    @DisplayName("a request carrying the configured secret is processed")
    void correctSecretIsAccepted() {
        TelegramWebhookService service = mock(TelegramWebhookService.class);
        TelegramWebhookController controller =
                new TelegramWebhookController(service, properties(true, SECRET));

        ResponseEntity<Void> response = controller.receiveUpdate(SECRET, UPDATE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).handleUpdate(UPDATE);
    }

    @Test
    @DisplayName("a request with no secret header is refused and never reaches the handler")
    void missingSecretIsRefused() {
        TelegramWebhookService service = mock(TelegramWebhookService.class);
        TelegramWebhookController controller =
                new TelegramWebhookController(service, properties(true, SECRET));

        ResponseEntity<Void> response = controller.receiveUpdate(null, UPDATE);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("a request with the wrong secret is refused")
    void wrongSecretIsRefused() {
        TelegramWebhookService service = mock(TelegramWebhookService.class);
        TelegramWebhookController controller =
                new TelegramWebhookController(service, properties(true, SECRET));

        ResponseEntity<Void> response = controller.receiveUpdate("not-the-secret", UPDATE);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).handleUpdate(UPDATE);
    }

    @Test
    @DisplayName("a secret that is a prefix of the real one is refused")
    void prefixOfSecretIsRefused() {
        TelegramWebhookService service = mock(TelegramWebhookService.class);
        TelegramWebhookController controller =
                new TelegramWebhookController(service, properties(true, SECRET));

        ResponseEntity<Void> response = controller.receiveUpdate(SECRET.substring(0, 5), UPDATE);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("with no secret configured the endpoint refuses everything rather than trusting the caller")
    void unconfiguredSecretFailsClosed() {
        TelegramWebhookService service = mock(TelegramWebhookService.class);
        TelegramWebhookController controller =
                new TelegramWebhookController(service, properties(true, ""));

        // Including a caller that presents no header at all - the shape a drive-by scan takes.
        assertEquals(HttpStatus.FORBIDDEN, controller.receiveUpdate(null, UPDATE).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.receiveUpdate("anything", UPDATE).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("the endpoint is invisible while the integration is switched off")
    void disabledIntegrationReturnsNotFound() {
        TelegramWebhookService service = mock(TelegramWebhookService.class);
        TelegramWebhookController controller =
                new TelegramWebhookController(service, properties(false, SECRET));

        ResponseEntity<Void> response = controller.receiveUpdate(SECRET, UPDATE);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(service);
    }
}
