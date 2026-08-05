package com.travelplatform.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out the real client IP for rate limiting.
 *
 * <p>{@code X-Forwarded-For} and {@code X-Real-IP} are ordinary request headers: anything on
 * the internet can send them with any value. Trusting them unconditionally lets a caller mint
 * a brand-new identity per request and walk straight through any per-IP limit, which makes the
 * auth limiter — the only brute-force protection on login and OTP — decorative.
 *
 * <p>So forwarded headers are honoured <em>only</em> when the immediate peer
 * ({@code getRemoteAddr()}) is a proxy we configured as trusted via
 * {@code app.ratelimit.trusted-proxies}. With no trusted proxies configured (the default) the
 * peer address is always used and the headers are ignored entirely.
 *
 * <p>When the headers are honoured, the client is the <strong>rightmost</strong> entry that is
 * not itself a trusted proxy. Taking the leftmost entry — the common mistake — is exactly what
 * makes spoofing work, because the attacker controls everything they prepend to the list.
 *
 * <p>Configuration accepts a comma-separated list of literal addresses and CIDR blocks, or
 * {@code *} to trust any peer (only correct when the app is unreachable except through a proxy
 * that overwrites the header itself):
 *
 * <pre>
 *   app.ratelimit.trusted-proxies: 10.0.0.0/8, 172.16.0.0/12, 127.0.0.1
 * </pre>
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final List<CidrBlock> trustedProxies = new ArrayList<>();
    private final boolean trustAllPeers;

    public ClientIpResolver(@Value("${app.ratelimit.trusted-proxies:}") String configured) {
        String value = configured == null ? "" : configured.trim();
        this.trustAllPeers = "*".equals(value);

        if (!trustAllPeers && !value.isEmpty()) {
            for (String entry : value.split(",")) {
                String candidate = entry.trim();
                if (candidate.isEmpty()) {
                    continue;
                }
                try {
                    trustedProxies.add(CidrBlock.parse(candidate));
                } catch (IllegalArgumentException ex) {
                    // A malformed entry must not silently widen trust — drop it and say so.
                    log.error("Ignoring malformed app.ratelimit.trusted-proxies entry '{}': {}",
                            candidate, ex.getMessage());
                }
            }
        }

        if (trustAllPeers) {
            log.warn("app.ratelimit.trusted-proxies=* — forwarded headers are trusted from ANY peer. "
                    + "Only safe when this app is reachable exclusively through a proxy that overwrites them.");
        } else if (trustedProxies.isEmpty()) {
            log.info("No trusted proxies configured — X-Forwarded-For/X-Real-IP are ignored and the "
                    + "socket peer address is used for rate limiting.");
        } else {
            log.info("Trusting forwarded headers from {} proxy range(s).", trustedProxies.size());
        }
    }

    /** The address to attribute this request to for rate-limiting purposes. */
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();

        if (!isTrustedProxy(peer)) {
            return peer;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            // Rightmost hop that is not itself a trusted proxy: everything further left was
            // supplied by the client and cannot be believed.
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!hop.isEmpty() && !isTrustedProxy(hop)) {
                    return hop;
                }
            }
            // Every hop is a proxy we trust; the original client is the leftmost entry.
            String leftmost = hops[0].trim();
            if (!leftmost.isEmpty()) {
                return leftmost;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return peer;
    }

    /** Whether forwarded headers arriving from this address may be believed. */
    public boolean isTrustedProxy(String address) {
        if (trustAllPeers) {
            return true;
        }
        if (address == null || address.isBlank() || trustedProxies.isEmpty()) {
            return false;
        }
        byte[] candidate = toBytes(address);
        if (candidate == null) {
            return false;
        }
        for (CidrBlock block : trustedProxies) {
            if (block.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] toBytes(String address) {
        try {
            // Literal addresses only — never let a hostname trigger a DNS lookup here.
            if (!address.matches("[0-9a-fA-F:.\\[\\]%]+")) {
                return null;
            }
            String cleaned = address.startsWith("[") && address.endsWith("]")
                    ? address.substring(1, address.length() - 1)
                    : address;
            int scope = cleaned.indexOf('%'); // strip IPv6 zone id, e.g. fe80::1%eth0
            if (scope >= 0) {
                cleaned = cleaned.substring(0, scope);
            }
            return InetAddress.getByName(cleaned).getAddress();
        } catch (UnknownHostException | SecurityException ex) {
            return null;
        }
    }

    /** An address range, held as raw bytes so IPv4 and IPv6 are handled the same way. */
    private record CidrBlock(byte[] network, int prefixBits) {

        static CidrBlock parse(String spec) {
            String[] parts = spec.split("/", 2);
            byte[] address = toBytes(parts[0].trim());
            if (address == null) {
                throw new IllegalArgumentException("not a literal IP address");
            }

            int bits = address.length * 8;
            if (parts.length == 2) {
                try {
                    bits = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("prefix length is not a number");
                }
                if (bits < 0 || bits > address.length * 8) {
                    throw new IllegalArgumentException("prefix length out of range for this address family");
                }
            }
            return new CidrBlock(address, bits);
        }

        boolean contains(byte[] candidate) {
            // An IPv4 range never matches an IPv6 address, and vice versa.
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
