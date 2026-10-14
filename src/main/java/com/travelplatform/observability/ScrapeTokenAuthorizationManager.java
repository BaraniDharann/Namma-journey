package com.travelplatform.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/**
 * Lets Prometheus read {@code /actuator/prometheus} with a dedicated scrape token
 * ({@code Authorization: Bearer <METRICS_SCRAPE_TOKEN>}) instead of a user's JWT, which a scraper
 * has no way to obtain or refresh.
 *
 * <p>Metrics reveal endpoint names, traffic volumes and timings, so an unset token denies
 * everyone rather than opening the endpoint. Comparison is constant-time, so response timing
 * does not leak how much of a guessed token was right.
 */
public class ScrapeTokenAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String PREFIX = "Bearer ";

    private final byte[] expected;

    public ScrapeTokenAuthorizationManager(String token) {
        this.expected = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        if (expected == null) {
            return new AuthorizationDecision(false);
        }
        HttpServletRequest request = context.getRequest();
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            return new AuthorizationDecision(false);
        }
        byte[] presented = header.substring(PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return new AuthorizationDecision(MessageDigest.isEqual(expected, presented));
    }
}
