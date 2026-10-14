package com.travelplatform.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Error responses used to echo any RuntimeException's message: Redis host names, SQL fragments,
 * file-system paths, Java class names. The app's own messages ("Booking not found") are written
 * for people and still shown; anything thrown by a library becomes a generic message and the
 * detail stays in the server log.
 */
class ErrorLeakageTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static String body(ResponseEntity<Map<String, String>> r) {
        return r.getBody() == null ? "" : String.valueOf(r.getBody().get("error"));
    }

    @Test
    @DisplayName("the app's own messages are still shown, with the right status")
    void ownMessagesAreShown() {
        ResponseEntity<Map<String, String>> r = handler.handleRuntimeException(new RuntimeException("Booking not found"));
        assertEquals(404, r.getStatusCode().value());
        assertEquals("Booking not found", body(r));
    }

    @Test
    @DisplayName("infrastructure failures never reach the client verbatim")
    void libraryExceptionsAreGeneric() {
        RuntimeException[] internals = {
                new RedisConnectionFailureException("Unable to connect to Redis at 10.0.4.17:6379"),
                new QueryTimeoutException("ERROR: canceling statement due to statement timeout; SQL [select * from payments]"),
                new UncheckedIOException("Failed to store uploads/drivers/aadhaar_1.jpg", new IOException("disk full")),
                new NullPointerException("Cannot invoke \"com.travelplatform.entity.Driver.getId()\""),
                new IllegalStateException("jwt.secret is not configured"),
        };
        for (RuntimeException e : internals) {
            ResponseEntity<Map<String, String>> r = handler.handleRuntimeException(e);
            assertEquals(500, r.getStatusCode().value(), e.getClass().getSimpleName());
            assertFalse(body(r).contains("10.0.4.17") || body(r).contains("SQL") || body(r).contains("uploads/")
                    || body(r).contains("com.travelplatform") || body(r).contains("jwt"), "leaked: " + body(r));
        }
    }

    @Test
    @DisplayName("enum parsing errors do not expose internal class names")
    void enumErrorsAreSanitised() {
        ResponseEntity<Map<String, String>> r = handler.handleIllegalArgument(
                new IllegalArgumentException("No enum constant com.travelplatform.entity.Payment.PaymentMethod.BITCOIN"));
        assertEquals(400, r.getStatusCode().value());
        assertFalse(body(r).contains("com.travelplatform"), body(r));
    }

    @Test
    @DisplayName("too many requests is a 429 with a readable message")
    void tooManyRequestsIs429() {
        ResponseEntity<Map<String, String>> r = handler.handleTooManyRequests(
                new TooManyRequestsException("A code was just sent. Please wait 30 seconds."));
        assertEquals(429, r.getStatusCode().value());
        assertEquals("A code was just sent. Please wait 30 seconds.", body(r));
    }
}
