package com.travelplatform.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);
    private static final long STALE_THRESHOLD_MS = Duration.ofMinutes(10).toMillis();

    private final Map<String, BucketEntry> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> authBuckets = new ConcurrentHashMap<>();

    private final ClientIpResolver clientIpResolver;
    private final JwtUtil jwtUtil;

    /** Anonymous, per-IP budget. */
    @Value("${app.ratelimit.general-limit:100}")
    private int generalLimit;

    /** Credential-guessing budget for /api/auth, per IP. */
    @Value("${app.ratelimit.auth-limit:10}")
    private int authLimit;

    /** Signed-in budget, per account rather than per address. */
    @Value("${app.ratelimit.authenticated-limit:300}")
    private int authenticatedLimit;

    public RateLimitingFilter(ClientIpResolver clientIpResolver, JwtUtil jwtUtil) {
        this.clientIpResolver = clientIpResolver;
        this.jwtUtil = jwtUtil;
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

        String key;
        int limit;
        if (isAuthEndpoint) {
            key = "ip:" + clientIp;
            limit = authLimit;
        } else {
            String accountId = resolveAccountId(request);
            if (accountId != null) {
                key = "user:" + accountId;
                limit = authenticatedLimit;
            } else {
                key = "ip:" + clientIp;
                limit = generalLimit;
            }
        }

        Bucket bucket = resolveBucket(key, limit, isAuthEndpoint);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

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

    private Bucket resolveBucket(String key, int limit, boolean isAuthEndpoint) {
        Map<String, BucketEntry> buckets = isAuthEndpoint ? authBuckets : generalBuckets;

        BucketEntry entry = buckets.computeIfAbsent(key, ignored -> {
            Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, REFILL_DURATION));
            Bucket bucket = Bucket.builder().addLimit(bandwidth).build();
            return new BucketEntry(bucket);
        });

        entry.updateLastAccessTime();
        return entry.getBucket();
    }

    @Scheduled(fixedRate = 600_000)
    public void cleanUpStaleBuckets() {
        long now = System.currentTimeMillis();
        int removed = 0;
        removed += removeStaleEntries(generalBuckets, now);
        removed += removeStaleEntries(authBuckets, now);
        if (removed > 0) {
            logger.info("Cleaned up {} stale rate limit bucket(s)", removed);
        }
    }

    private int removeStaleEntries(Map<String, BucketEntry> buckets, long now) {
        int count = 0;
        var iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().getLastAccessTime() > STALE_THRESHOLD_MS) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }

    private static class BucketEntry {
        private final Bucket bucket;
        private volatile long lastAccessTime;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessTime = System.currentTimeMillis();
        }

        Bucket getBucket() {
            return bucket;
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }

        void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
