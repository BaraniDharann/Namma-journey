package com.travelplatform.config;

import com.travelplatform.ratelimit.BucketStore;
import com.travelplatform.ratelimit.LocalBucketStore;
import com.travelplatform.ratelimit.RedisBucketStore;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Token-bucket rate limiting.
 *
 * <p>Requests are bucketed by <em>who</em> is calling rather than only by address:
 *
 * <ul>
 *   <li><b>/api/auth/**</b> — always keyed by client IP with a deliberately tight limit. These
 *       are the endpoints an attacker brute-forces (passwords, 6-digit OTPs), and the caller has
 *       no identity yet, so the address is all there is to go on.</li>
 *   <li><b>Authenticated calls</b> — keyed by the JWT subject with a much larger allowance. A
 *       single dashboard render legitimately issues a couple of dozen calls, and several users
 *       routinely share one address (NAT, corporate egress, mobile carriers), so a per-IP budget
 *       throttles real users while doing nothing an attacker can't sidestep with a new token.</li>
 *   <li><b>Anonymous calls</b> — keyed by client IP with a moderate allowance.</li>
 * </ul>
 *
 * <p>The client address comes from {@link ClientIpResolver}, which only believes forwarded
 * headers from configured proxies. Keying off a spoofable header would let a caller reset their
 * own bucket at will.
 */
@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Where the buckets live: Redis (shared by every instance) or this JVM - see RateLimitConfig.
    private final BucketStore buckets;

    private final ClientIpResolver clientIpResolver;
    private final JwtUtil jwtUtil;

    /** Anonymous, per-IP budget. */
    @Value("${app.ratelimit.general-limit:100}")
    private int generalLimit;

    /** Credential-guessing budget for /api/auth, per IP. */
    @Value("${app.ratelimit.auth-limit:10}")
    private int authLimit;

    /**
     * Payment budget, per account. Deliberately far tighter than the general signed-in limit:
     * initiating a payment or ending a trip renders a 300x300 QR code and base64-encodes the
     * PNG, which is orders of magnitude more work per request than a normal read. At the
     * signed-in allowance one account could drive continuous QR generation, and no legitimate
     * client needs more than a handful — a passenger taps pay, and re-taps if the link expires.
     */
    @Value("${app.ratelimit.payment-limit:15}")
    private int paymentLimit;

    /** Signed-in budget, per account rather than per address. */
    @Value("${app.ratelimit.authenticated-limit:300}")
    private int authenticatedLimit;

    public RateLimitingFilter(ClientIpResolver clientIpResolver, JwtUtil jwtUtil, BucketStore buckets) {
        this.clientIpResolver = clientIpResolver;
        this.jwtUtil = jwtUtil;
        this.buckets = buckets;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        if (path.startsWith("/uploads/") || path.startsWith("/driverphoto/") || path.startsWith("/actuator/")) {
            chain.doFilter(request, response);
            return;
        }

        // Every Telegram webhook delivery arrives from Telegram's own infrastructure, so all
        // of them collapse onto a single per-IP bucket. Under the general limit a busy hour of
        // dispatch would start returning 429s to Telegram, which responds by retrying and
        // backing off - delaying exactly the notifications this channel exists to make fast.
        // Safe to exempt because the endpoint authenticates every call against the shared
        // webhook secret and rejects unknown callers before doing any work.
        if (path.equals("/api/telegram/webhook")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = clientIpResolver.resolve(request);
        boolean isAuthEndpoint = path.startsWith("/api/auth");
        boolean isPaymentEndpoint = isPaymentPath(path);

        String key;
        int limit;
        Scope scope;
        if (isAuthEndpoint) {
            key = "ip:" + clientIp;
            limit = authLimit;
            scope = Scope.AUTH;
        } else {
            String accountId = resolveAccountId(request);
            if (accountId != null) {
                key = "user:" + accountId;
                // Payment calls draw on their own, much smaller bucket rather than the general
                // signed-in one, so driving QR generation in a loop cannot hide inside a normal
                // browsing allowance.
                limit = isPaymentEndpoint ? paymentLimit : authenticatedLimit;
                scope = isPaymentEndpoint ? Scope.PAYMENT : Scope.GENERAL;
            } else {
                key = "ip:" + clientIp;
                limit = generalLimit;
                scope = Scope.GENERAL;
            }
        }

        // Scope prefix keeps the three budgets apart even for the same caller.
        ConsumptionProbe probe = buckets.tryConsume(scope.name().toLowerCase() + ":" + key, limit);

        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            logger.warn("Rate limit exceeded for {} on path: {} (ip {})", key, path, clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
        }
    }

    /**
     * The account this request belongs to, or null when there is no usable token. This filter
     * runs ahead of Spring Security, so the SecurityContext is not populated yet and the bearer
     * token has to be read directly. An unusable token simply falls back to per-IP limiting;
     * rejecting it is the authentication filter's job, not this one's.
     */
    private String resolveAccountId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            if (!jwtUtil.validateToken(token)) {
                return null;
            }
            String userId = jwtUtil.extractUserId(token);
            return (userId == null || userId.isBlank()) ? null : userId;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * True for the endpoints that move money or mint a UPI QR code.
     *
     * <p>Matched on the suffix rather than a prefix because these hang off per-user and
     * per-driver paths that already carry an id segment, e.g.
     * {@code /api/user/{userId}/bookings/{bookingId}/payment} and
     * {@code /api/driver/{driverId}/bookings/{bookingId}/end-trip}.
     */
    private boolean isPaymentPath(String path) {
        return path.endsWith("/payment")
                || path.endsWith("/cash-payment")
                || path.endsWith("/end-trip")
                || path.contains("/payments/");
    }

    /** Which budget a request draws on. Part of the bucket key, so budgets never collide. */
    private enum Scope { AUTH, PAYMENT, GENERAL }

    /**
     * Frees in-memory buckets nobody has used for 10 minutes. Redis buckets expire on their own;
     * this covers the local store and the local fallback Redis mode keeps for outages.
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanUpStaleBuckets() {
        LocalBucketStore local = buckets instanceof RedisBucketStore redis ? redis.fallback()
                : buckets instanceof LocalBucketStore store ? store : null;
        int removed = local == null ? 0 : local.removeStale();
        if (removed > 0) {
            logger.info("Cleaned up {} stale rate limit bucket(s)", removed);
        }
    }
}
