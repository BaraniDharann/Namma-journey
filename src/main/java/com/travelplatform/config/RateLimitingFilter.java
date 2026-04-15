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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final int GENERAL_LIMIT = 50;
    private static final int AUTH_LIMIT = 10;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);
    private static final long STALE_THRESHOLD_MS = Duration.ofMinutes(10).toMillis();

    private final Map<String, BucketEntry> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, BucketEntry> authBuckets = new ConcurrentHashMap<>();

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

        String clientIp = resolveClientIp(request);
        boolean isAuthEndpoint = path.startsWith("/api/auth");

        Bucket bucket = resolveBucket(clientIp, isAuthEndpoint);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
        }
    }

    private Bucket resolveBucket(String clientIp, boolean isAuthEndpoint) {
        Map<String, BucketEntry> buckets = isAuthEndpoint ? authBuckets : generalBuckets;
        int limit = isAuthEndpoint ? AUTH_LIMIT : GENERAL_LIMIT;

        BucketEntry entry = buckets.computeIfAbsent(clientIp, key -> {
            Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, REFILL_DURATION));
            Bucket bucket = Bucket.builder().addLimit(bandwidth).build();
            return new BucketEntry(bucket);
        });

        entry.updateLastAccessTime();
        return entry.getBucket();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
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
