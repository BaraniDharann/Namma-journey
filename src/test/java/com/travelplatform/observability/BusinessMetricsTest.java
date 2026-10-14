package com.travelplatform.observability;

import com.travelplatform.idempotency.IdempotencyFilter;
import com.travelplatform.idempotency.IdempotencyStore;
import com.travelplatform.service.AfterCommitDispatcher;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The numbers the dashboard and alerts are built on. A counter that silently stops counting
 * leaves an alert that can never fire, so each one is pinned here.
 */
class BusinessMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void attach() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void detach() {
        Metrics.removeRegistry(registry);
    }

    private double count(String name, String... tags) {
        var counter = registry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("notification dispatch counts sent, failed and dropped per channel")
    void dispatchOutcomes() {
        new AfterCommitDispatcher(Runnable::run).dispatch("telegram", () -> { });
        new AfterCommitDispatcher(Runnable::run).dispatch("telegram", () -> {
            throw new RuntimeException("down");
        });
        new AfterCommitDispatcher(task -> {
            throw new RejectedExecutionException("full");
        }).dispatch("email", () -> { });

        assertEquals(1, count("namma.notifications.dispatch", "channel", "telegram", "outcome", "sent"));
        assertEquals(1, count("namma.notifications.dispatch", "channel", "telegram", "outcome", "failed"));
        assertEquals(1, count("namma.notifications.dispatch", "channel", "email", "outcome", "dropped"));
    }

    @Test
    @DisplayName("idempotency counts every outcome, so replayed double taps are visible")
    void idempotencyOutcomes() throws Exception {
        IdempotencyStore replaying = new IdempotencyStore() {
            @Override
            public Claim claim(String scope, String key, String requestHash) {
                return Claim.replay(new StoredResponse(200, "application/json", "{}".getBytes()));
            }

            @Override
            public void complete(String scope, String key, StoredResponse response) {
            }

            @Override
            public void release(String scope, String key) {
            }
        };
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/user/u/bookings");
        req.addHeader(IdempotencyFilter.HEADER, "k");
        req.setContent("{}".getBytes());

        new IdempotencyFilter(replaying).doFilter(req, new MockHttpServletResponse(), (r, s) -> { });

        assertEquals(1, count("namma.idempotency.requests", "outcome", "replay"));
    }
}
