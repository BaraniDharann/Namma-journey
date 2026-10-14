package com.travelplatform.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every request gets one id that appears on every log line it produces and in the response, so a
 * traveller's "booking failed" can be traced to the exact log lines - and through nginx, which
 * can forward the same id.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String runAndCaptureMdc(MockHttpServletRequest req, MockHttpServletResponse res) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(req, res, (r, s) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));
        return seen.get();
    }

    @Test
    @DisplayName("a request without an id gets a fresh one, in the logs and the response")
    void generatesAnId() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        String inLogs = runAndCaptureMdc(new MockHttpServletRequest("GET", "/api/x"), res);

        assertTrue(inLogs != null && inLogs.matches("[0-9a-f]{32}"), "got " + inLogs);
        assertEquals(inLogs, res.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("an id forwarded by the proxy or client is kept, so one id follows the request end to end")
    void keepsAWellFormedIncomingId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
        req.addHeader(RequestIdFilter.HEADER, "nginx-7f3a9c");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertEquals("nginx-7f3a9c", runAndCaptureMdc(req, res));
        assertEquals("nginx-7f3a9c", res.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("a hostile incoming id (log injection, oversize) is replaced, never logged")
    void replacesMalformedIds() throws Exception {
        for (String bad : new String[] {"abc\n2026-10-13 INFO forged line", "x".repeat(200), "<script>", ""}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/x");
            req.addHeader(RequestIdFilter.HEADER, bad);
            String used = runAndCaptureMdc(req, new MockHttpServletResponse());
            assertNotEquals(bad, used);
            assertTrue(used.matches("[0-9a-f]{32}"));
        }
    }

    @Test
    @DisplayName("the id is cleared afterwards so a pooled thread never leaks it into the next request")
    void clearsMdcAfterwards() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), (r, s) -> { });

        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
