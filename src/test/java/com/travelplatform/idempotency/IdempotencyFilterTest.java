package com.travelplatform.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A traveller double-tapping "Book", or a phone retrying a request whose response was lost on a
 * flaky network, used to create two bookings. With an Idempotency-Key the second request gets the
 * first one's answer instead of running again.
 */
class IdempotencyFilterTest {

    private static final String BOOKINGS = "/api/user/u-1/bookings";

    private InMemoryIdempotencyStore store;
    private IdempotencyFilter filter;
    private AtomicInteger handlerRuns;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
        filter = new IdempotencyFilter(store);
        handlerRuns = new AtomicInteger();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u-1", null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest post(String path, String key, String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setContentType("application/json");
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (key != null) {
            req.addHeader("Idempotency-Key", key);
        }
        return req;
    }

    /** The controller: creates a booking and answers 200 with its id. */
    private FilterChain bookingHandler(int status) {
        return (request, response) -> {
            int n = handlerRuns.incrementAndGet();
            HttpServletResponse res = (HttpServletResponse) response;
            res.setStatus(status);
            res.setContentType("application/json");
            res.getWriter().write("{\"bookingId\":\"b-" + n + "\"}");
        };
    }

    private MockHttpServletResponse run(MockHttpServletRequest req, FilterChain chain) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test
    @DisplayName("the same key twice creates one booking and replays the first answer")
    void repeatIsReplayedNotRerun() throws Exception {
        MockHttpServletResponse first = run(post(BOOKINGS, "k-1", "{\"to\":\"Madurai\"}"), bookingHandler(200));
        MockHttpServletResponse second = run(post(BOOKINGS, "k-1", "{\"to\":\"Madurai\"}"), bookingHandler(200));

        assertEquals(1, handlerRuns.get(), "the booking must be created exactly once");
        assertEquals(200, second.getStatus());
        assertEquals(first.getContentAsString(), second.getContentAsString());
        assertEquals("true", second.getHeader("Idempotent-Replayed"));
        assertNull(first.getHeader("Idempotent-Replayed"));
    }

    @Test
    @DisplayName("a different key is a different booking")
    void differentKeysRunSeparately() throws Exception {
        run(post(BOOKINGS, "k-1", "{}"), bookingHandler(200));
        run(post(BOOKINGS, "k-2", "{}"), bookingHandler(200));

        assertEquals(2, handlerRuns.get());
    }

    @Test
    @DisplayName("reusing a key for a different request is refused with 422")
    void keyReusedWithDifferentBodyIsRefused() throws Exception {
        run(post(BOOKINGS, "k-1", "{\"to\":\"Madurai\"}"), bookingHandler(200));
        MockHttpServletResponse res = run(post(BOOKINGS, "k-1", "{\"to\":\"Ooty\"}"), bookingHandler(200));

        assertEquals(422, res.getStatus());
        assertEquals(1, handlerRuns.get());
    }

    @Test
    @DisplayName("while the first request is still running, a duplicate gets 409 and is not run")
    void duplicateDuringFlightIsRejected() throws Exception {
        store.claim("u-1|POST|" + BOOKINGS, "k-1", IdempotencyFilter.sha256("{}".getBytes(StandardCharsets.UTF_8)));

        MockHttpServletResponse res = run(post(BOOKINGS, "k-1", "{}"), bookingHandler(200));

        assertEquals(409, res.getStatus());
        assertEquals("1", res.getHeader("Retry-After"));
        assertEquals(0, handlerRuns.get());
    }

    @Test
    @DisplayName("a failed attempt frees the key so the traveller can fix the input and retry")
    void failureReleasesTheKey() throws Exception {
        run(post(BOOKINGS, "k-1", "{}"), bookingHandler(400));
        MockHttpServletResponse retry = run(post(BOOKINGS, "k-1", "{}"), bookingHandler(200));

        assertEquals(2, handlerRuns.get());
        assertEquals(200, retry.getStatus());
    }

    @Test
    @DisplayName("the same key from two different users never collides")
    void keysAreScopedPerUser() throws Exception {
        run(post(BOOKINGS, "k-1", "{}"), bookingHandler(200));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u-2", null, List.of()));
        run(post(BOOKINGS, "k-1", "{}"), bookingHandler(200));

        assertEquals(2, handlerRuns.get());
    }

    @Test
    @DisplayName("requests without a key, and unprotected paths, pass straight through")
    void passThrough() throws Exception {
        run(post(BOOKINGS, null, "{}"), bookingHandler(200));
        run(post(BOOKINGS, null, "{}"), bookingHandler(200));
        run(post("/api/user/u-1/bookings/b-1/reviews", "k-9", "{}"), bookingHandler(200));
        run(post("/api/user/u-1/bookings/b-1/reviews", "k-9", "{}"), bookingHandler(200));

        assertEquals(4, handlerRuns.get());
    }

    @Test
    @DisplayName("payment initiation and package bookings are protected too")
    void otherMoneyPathsAreProtected() throws Exception {
        String payment = "/api/user/u-1/bookings/b-1/payment";
        String pkg = "/api/user/u-1/package-bookings";
        run(post(payment, "p-1", "{}"), bookingHandler(200));
        run(post(payment, "p-1", "{}"), bookingHandler(200));
        run(post(pkg, "p-2", "{}"), bookingHandler(200));
        run(post(pkg, "p-2", "{}"), bookingHandler(200));

        assertEquals(2, handlerRuns.get());
    }

    @Test
    @DisplayName("an absurdly long key is rejected rather than stored")
    void oversizedKeyIsRejected() throws Exception {
        MockHttpServletResponse res = run(post(BOOKINGS, "x".repeat(300), "{}"), bookingHandler(200));

        assertEquals(400, res.getStatus());
        assertEquals(0, handlerRuns.get());
    }
}
