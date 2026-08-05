package com.travelplatform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rate limiter is only as good as the identity it buckets on, so these cases pin down
 * exactly when a forwarded header is believed. The first test is the regression guard: before
 * trusted-proxy handling existed, any caller could spoof X-Forwarded-For and mint a fresh
 * bucket per request, which made the /api/auth limit — the brute-force protection on passwords
 * and 6-digit OTPs — bypassable by anyone who could set a header.
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(peer);
        if (forwardedFor != null) {
            req.addHeader("X-Forwarded-For", forwardedFor);
        }
        return req;
    }

    @Test
    @DisplayName("a spoofed X-Forwarded-For from an untrusted peer is ignored")
    void spoofedHeaderFromUntrustedPeerIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("");

        // Same attacker, a different fake address on each request.
        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "1.2.3.4")));
        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "5.6.7.8")));
        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "9.9.9.9")));
    }

    @Test
    @DisplayName("X-Real-IP from an untrusted peer is ignored too")
    void realIpHeaderFromUntrustedPeerIsIgnored() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest req = request("203.0.113.9", null);
        req.addHeader("X-Real-IP", "1.2.3.4");

        assertEquals("203.0.113.9", resolver.resolve(req));
    }

    @Test
    @DisplayName("a forwarded header from a trusted proxy is honoured")
    void forwardedHeaderFromTrustedProxyIsHonoured() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        assertEquals("198.51.100.7", resolver.resolve(request("10.1.2.3", "198.51.100.7")));
    }

    @Test
    @DisplayName("the client is the rightmost hop the attacker could not have forged")
    void rightmostUntrustedHopWins() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        // The client prepended two lies before the real chain; the proxy appended the truth.
        String spoofed = "1.1.1.1, 2.2.2.2, 198.51.100.7";
        assertEquals("198.51.100.7", resolver.resolve(request("10.1.2.3", spoofed)));
    }

    @Test
    @DisplayName("when every hop is a trusted proxy the original client is the leftmost entry")
    void allHopsTrustedFallsBackToLeftmost() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8, 192.168.0.0/16");

        assertEquals("192.168.5.5", resolver.resolve(request("10.1.2.3", "192.168.5.5, 10.9.9.9")));
    }

    @Test
    @DisplayName("wildcard trusts any peer")
    void wildcardTrustsEveryPeer() {
        ClientIpResolver resolver = new ClientIpResolver("*");

        assertEquals("198.51.100.7", resolver.resolve(request("203.0.113.9", "198.51.100.7")));
    }

    @Test
    @DisplayName("an IPv4 range never matches an IPv6 peer")
    void addressFamiliesDoNotCross() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        assertFalse(resolver.isTrustedProxy("::1"));
        assertEquals("::1", resolver.resolve(request("::1", "1.2.3.4")));
    }

    @Test
    @DisplayName("IPv6 CIDR blocks are matched bitwise")
    void ipv6RangesAreSupported() {
        ClientIpResolver resolver = new ClientIpResolver("2001:db8::/32");

        assertTrue(resolver.isTrustedProxy("2001:db8:1234::1"));
        assertFalse(resolver.isTrustedProxy("2001:db9::1"));
    }

    @Test
    @DisplayName("a malformed entry is dropped rather than widening trust")
    void malformedEntryDoesNotWidenTrust() {
        ClientIpResolver resolver = new ClientIpResolver("not-an-ip, 10.0.0.0/8, 10.0.0.0/99");

        assertTrue(resolver.isTrustedProxy("10.1.2.3"));
        assertFalse(resolver.isTrustedProxy("203.0.113.9"));
    }

    @Test
    @DisplayName("a hostname in a forwarded header never triggers a DNS lookup")
    void hostnamesAreNotResolved() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");

        assertFalse(resolver.isTrustedProxy("evil.example.com"));
    }

    @Test
    @DisplayName("an exact address with no prefix is treated as a single host")
    void exactAddressIsSingleHost() {
        ClientIpResolver resolver = new ClientIpResolver("127.0.0.1");

        assertTrue(resolver.isTrustedProxy("127.0.0.1"));
        assertFalse(resolver.isTrustedProxy("127.0.0.2"));
    }
}
