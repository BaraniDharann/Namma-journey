package com.travelplatform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prometheus has to read /actuator/prometheus without a user's JWT. It presents its own scrape
 * token instead. Metrics expose endpoint names and traffic, so no token configured means no access.
 */
class ScrapeTokenAuthorizationManagerTest {

    private static boolean allowed(ScrapeTokenAuthorizationManager manager, String authorization) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/prometheus");
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        return manager.check(() -> null, new RequestAuthorizationContext(req)).isGranted();
    }

    @Test
    @DisplayName("the configured token is let in")
    void correctTokenIsGranted() {
        assertTrue(allowed(new ScrapeTokenAuthorizationManager("s3cret-scrape-token"), "Bearer s3cret-scrape-token"));
    }

    @Test
    @DisplayName("a wrong, missing or non-bearer token is refused")
    void wrongTokensAreDenied() {
        ScrapeTokenAuthorizationManager manager = new ScrapeTokenAuthorizationManager("s3cret-scrape-token");
        assertFalse(allowed(manager, "Bearer nope"));
        assertFalse(allowed(manager, null));
        assertFalse(allowed(manager, "Basic s3cret-scrape-token"));
        assertFalse(allowed(manager, "Bearer s3cret-scrape-token-but-longer"));
    }

    @Test
    @DisplayName("with no token configured, nobody gets in - not even with an empty bearer")
    void unconfiguredDeniesEverything() {
        assertFalse(allowed(new ScrapeTokenAuthorizationManager(""), "Bearer "));
        assertFalse(allowed(new ScrapeTokenAuthorizationManager(null), "Bearer null"));
    }
}
