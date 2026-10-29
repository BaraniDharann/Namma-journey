package com.travelplatform.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Builds {@link RestTemplate}s for calls that leave this process.
 *
 * <p>A bare {@code new RestTemplate()} has <strong>no connect or read timeout at all</strong>.
 * Every outbound call here is to a third party (routing, geocoding, Razorpay, Telegram), so an
 * unresponsive provider does not fail — it hangs, holding a Tomcat request thread for as long as
 * the socket stays open. With a 200-thread pool and one slow dependency, that is how a degraded
 * upstream turns into a full outage of an otherwise healthy application.
 *
 * <p>Timeouts alone still make every caller wait them out while a provider is down. So each
 * client is also guarded, per named dependency, by:
 *
 * <ul>
 *   <li>a <b>circuit breaker</b>: once half of the last 20 calls failed (I/O error, 5xx, 429) or
 *       80% took over 3s, calls fail at once for 30s instead of each waiting 5s, then 3 trial
 *       calls decide whether it closes again. 4xx answers are the caller's problem, not the
 *       provider's, and never trip it (Telegram's 403 for a blocked bot, Razorpay's "already
 *       refunded").</li>
 *   <li>a <b>bulkhead</b>: at most 20 calls to one dependency in flight per instance, so even a
 *       slow-but-not-failing provider can hold only 20 request threads, never the whole pool.</li>
 * </ul>
 *
 * <p>Both refuse by throwing {@link ResourceAccessException}, the same exception a timeout
 * produces, so every caller's existing fallback (straight-line ETA, "pay by UPI or cash", skipped
 * Telegram push, outbox retry for refunds) applies unchanged. Their state is exported to
 * Prometheus as {@code resilience4j_circuitbreaker_state} and {@code resilience4j_bulkhead_*}.
 */
public final class ExternalHttpClients {

    private static final Logger log = LoggerFactory.getLogger(ExternalHttpClients.class);

    /** Long enough for a healthy provider on a slow link, short enough to fail before the caller does. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    static final int MAX_CONCURRENT_CALLS = 20;

    private static final CircuitBreakerRegistry BREAKERS = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            // Two failures right after boot must not open it: judge only once there is a sample.
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .slowCallRateThreshold(80)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build());

    private static final BulkheadRegistry BULKHEADS = BulkheadRegistry.of(BulkheadConfig.custom()
            .maxConcurrentCalls(MAX_CONCURRENT_CALLS)
            // Never queue: a full bulkhead means the provider is slow, and waiting is the problem.
            .maxWaitDuration(Duration.ZERO)
            .build());

    static {
        // Spring Boot adds its Prometheus registry to the global one, so these reach /actuator/prometheus.
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(BREAKERS).bindTo(Metrics.globalRegistry);
        TaggedBulkheadMetrics.ofBulkheadRegistry(BULKHEADS).bindTo(Metrics.globalRegistry);
        BREAKERS.getEventPublisher().onEntryAdded(added -> added.getAddedEntry().getEventPublisher()
                .onStateTransition(e -> log.warn("Circuit breaker '{}' {}", e.getCircuitBreakerName(),
                        e.getStateTransition())));
    }

    private ExternalHttpClients() {
    }

    /**
     * @param dependency the provider this client talks to (e.g. "razorpay", "telegram"); clients
     *                   built with the same name share one breaker and one bulkhead
     */
    public static RestTemplate forThirdPartyApis(String dependency) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(
                new Guard(dependency, BREAKERS.circuitBreaker(dependency), BULKHEADS.bulkhead(dependency)));
        return restTemplate;
    }

    static CircuitBreaker breaker(String dependency) {
        return BREAKERS.circuitBreaker(dependency);
    }

    /**
     * Runs one request under the dependency's bulkhead and breaker.
     *
     * <p>Messages name only the dependency, never the URL: Telegram's carries the bot token.
     */
    private record Guard(String dependency, CircuitBreaker breaker, Bulkhead bulkhead)
            implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            try {
                bulkhead.acquirePermission();
            } catch (BulkheadFullException e) {
                throw new ResourceAccessException(dependency + " has " + MAX_CONCURRENT_CALLS
                        + " calls in flight; failing fast");
            }
            try {
                try {
                    breaker.acquirePermission();
                } catch (CallNotPermittedException e) {
                    throw new ResourceAccessException(dependency + " is failing (circuit open); failing fast");
                }
                long started = System.nanoTime();
                try {
                    ClientHttpResponse response = execution.execute(request, body);
                    long took = System.nanoTime() - started;
                    int status = response.getStatusCode().value();
                    if (status >= 500 || status == 429) {
                        breaker.onError(took, TimeUnit.NANOSECONDS, new IOException(dependency + " answered " + status));
                    } else {
                        breaker.onSuccess(took, TimeUnit.NANOSECONDS);
                    }
                    return response;
                } catch (IOException | RuntimeException e) {
                    breaker.onError(System.nanoTime() - started, TimeUnit.NANOSECONDS, e);
                    throw e;
                }
            } finally {
                bulkhead.onComplete();
            }
        }
    }
}
