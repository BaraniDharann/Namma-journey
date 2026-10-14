package com.travelplatform.idempotency;

import io.micrometer.core.instrument.Metrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Makes the POST endpoints that create bookings and payments safe to repeat.
 *
 * <p>A double-tapped "Book" button, or a phone that resends a request because the response was
 * lost on a weak signal, used to create a second booking. The client now sends an
 * {@code Idempotency-Key} header (a fresh UUID per user action, reused on retries of that same
 * action). The first request with a key runs and its answer is stored; any repeat gets that same
 * answer back, marked {@code Idempotent-Replayed: true}, without running again. This is the
 * pattern payment APIs (Stripe, Razorpay) use.
 *
 * <ul>
 *   <li>Keys are scoped to the signed-in account, method and path, so two users can never collide.</li>
 *   <li>A repeat while the first is still running gets 409 + Retry-After instead of running twice.</li>
 *   <li>Reusing a key for a different body is a client bug and gets 422.</li>
 *   <li>Only successful (2xx) answers are kept. A failure frees the key, so a traveller who fixes
 *       a validation error can resubmit the same action.</li>
 *   <li>The header is optional: requests without it behave exactly as before.</li>
 * </ul>
 *
 * <p>Runs after Spring Security (default servlet-filter order), so the account is known and
 * unauthenticated calls never reach the store.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    /** Long enough for any UUID or ULID, short enough that nobody stores essays in the table. */
    private static final int MAX_KEY_LENGTH = 255;

    /** Endpoints where running twice creates a second booking or payment. */
    private static final List<Pattern> PROTECTED = List.of(
            Pattern.compile("^/api/user/[^/]+/bookings$"),
            Pattern.compile("^/api/user/[^/]+/bookings/[^/]+/payment$"),
            Pattern.compile("^/api/user/[^/]+/package-bookings$"));

    private final IdempotencyStore store;

    public IdempotencyFilter(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || request.getHeader(HEADER) == null) {
            return true;
        }
        String path = request.getRequestURI();
        return PROTECTED.stream().noneMatch(p -> p.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER).trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Idempotency-Key must be 1-" + MAX_KEY_LENGTH + " characters");
            return;
        }

        // Read the body once up front: the hash needs it before the controller runs, and
        // BodyReplayingRequest hands the controller the same bytes again afterwards.
        byte[] body = request.getInputStream().readAllBytes();
        String scope = principal() + "|POST|" + request.getRequestURI();

        IdempotencyStore.Claim claim = store.claim(scope, key, sha256(body));
        // namma_idempotency_requests_total{outcome}: "replay" is a double tap or retry that would
        // otherwise have become a second booking.
        Metrics.counter("namma.idempotency.requests", "outcome", claim.kind().name().toLowerCase()).increment();
        switch (claim.kind()) {
            case REPLAY -> {
                replay(response, claim.response());
                return;
            }
            case IN_PROGRESS -> {
                response.setHeader("Retry-After", "1");
                writeError(response, HttpServletResponse.SC_CONFLICT,
                        "This request is already being processed. Please wait a moment.");
                return;
            }
            case MISMATCH -> {
                writeError(response, 422, "This Idempotency-Key was already used for a different request.");
                return;
            }
            case ACQUIRED -> {
                // fall through and run the request
            }
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(new BodyReplayingRequest(request, body), cachedResponse);
            int status = cachedResponse.getStatus();
            if (status >= 200 && status < 300) {
                store.complete(scope, key, new IdempotencyStore.StoredResponse(
                        status, cachedResponse.getContentType(), cachedResponse.getContentAsByteArray()));
                completed = true;
            }
        } finally {
            if (!completed) {
                try {
                    store.release(scope, key);
                } catch (RuntimeException e) {
                    // The key then stays claimed until it goes stale; never mask the real outcome.
                    log.warn("Could not release Idempotency-Key after a failed request: {}", e.toString());
                }
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    private static String principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "anonymous" : auth.getName();
    }

    private static void replay(HttpServletResponse response, IdempotencyStore.StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        response.getOutputStream().write(stored.body());
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }

    static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
