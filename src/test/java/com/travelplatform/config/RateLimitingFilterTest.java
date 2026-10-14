package com.travelplatform.config;

import com.travelplatform.ratelimit.BucketStore;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which budget each request draws on, and what a refusal looks like. The buckets themselves now
 * live behind BucketStore (Redis-shared or in-memory); this pins that the filter keeps asking for
 * the right bucket with the right limit.
 */
class RateLimitingFilterTest {

    private record Call(String key, int limit) {
    }

    private final List<Call> calls = new ArrayList<>();
    private boolean allow = true;

    private RateLimitingFilter filter;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        BucketStore store = (key, limit) -> {
            calls.add(new Call(key, limit));
            return allow
                    ? ConsumptionProbe.consumed(limit - 1, 0)
                    : ConsumptionProbe.rejected(0, 30_000_000_000L, 30_000_000_000L);
        };
        jwtUtil = mock(JwtUtil.class);
        filter = new RateLimitingFilter(new ClientIpResolver(""), jwtUtil, store);
        ReflectionTestUtils.setField(filter, "generalLimit", 100);
        ReflectionTestUtils.setField(filter, "authLimit", 10);
        ReflectionTestUtils.setField(filter, "paymentLimit", 15);
        ReflectionTestUtils.setField(filter, "authenticatedLimit", 300);
    }

    private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private static MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setRemoteAddr("203.0.113.9");
        return r;
    }

    private MockHttpServletRequest signedIn(String path) {
        MockHttpServletRequest r = req("POST", path);
        r.addHeader("Authorization", "Bearer good-token");
        when(jwtUtil.validateToken("good-token")).thenReturn(true);
        when(jwtUtil.extractUserId("good-token")).thenReturn("u-42");
        return r;
    }

    @Test
    @DisplayName("login and OTP draw on the tight per-IP auth budget")
    void authIsPerIp() throws Exception {
        run(req("POST", "/api/auth/user/login"));
        assertEquals(new Call("auth:ip:203.0.113.9", 10), calls.get(0));
    }

    @Test
    @DisplayName("signed-in calls are budgeted per account; payments on their own smaller bucket")
    void signedInIsPerAccount() throws Exception {
        run(signedIn("/api/user/u-42/bookings"));
        run(signedIn("/api/user/u-42/bookings/b-1/payment"));

        assertEquals(new Call("general:user:u-42", 300), calls.get(0));
        assertEquals(new Call("payment:user:u-42", 15), calls.get(1));
    }

    @Test
    @DisplayName("anonymous calls are per IP on the general budget")
    void anonymousIsPerIp() throws Exception {
        run(req("GET", "/api/public/packages"));
        assertEquals(new Call("general:ip:203.0.113.9", 100), calls.get(0));
    }

    @Test
    @DisplayName("an exhausted bucket answers 429 with Retry-After, and the request goes no further")
    void refusalIs429() throws Exception {
        allow = false;
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req("POST", "/api/auth/user/login"), res, chain);

        assertEquals(429, res.getStatus());
        assertEquals("30", res.getHeader("Retry-After"));
        assertTrue(res.getContentAsString().contains("Too many requests"));
        assertNull(chain.getRequest(), "a refused request must not reach the controller");
    }

    @Test
    @DisplayName("uploads, actuator and the Telegram webhook bypass the limiter")
    void exemptPaths() throws Exception {
        run(req("GET", "/uploads/drivers/a.jpg"));
        run(req("GET", "/actuator/health"));
        run(req("POST", "/api/telegram/webhook"));
        assertTrue(calls.isEmpty());
        assertNotNull(filter);
    }
}
