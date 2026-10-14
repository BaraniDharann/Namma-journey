package com.travelplatform.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    // HS256 requires at least 256 bits / 32 bytes of key material.
    private static final int MIN_SECRET_BYTES = 32;

    static final String TOKEN_VERSION_CLAIM = "tv";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey signingKey;

    @PostConstruct
    void initSigningKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is not configured");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256");
        }
        if (expiration == null || expiration <= 0) {
            throw new IllegalStateException("jwt.expiration must be a positive number of milliseconds");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * @param tokenVersion the account's current token_version. JwtAuthenticationFilter refuses the
     *                     token once the account's version moves on (password reset, logout).
     */
    public String generateToken(String userId, String role, long tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put(TOKEN_VERSION_CLAIM, tokenVersion);

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Tokens issued before versioning existed carry no claim; they count as version 0. */
    public long extractTokenVersion(String token) {
        Number version = extractAllClaims(token).get(TOKEN_VERSION_CLAIM, Number.class);
        return version == null ? 0L : version.longValue();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = extractAllClaims(token);
            Date expiry = claims.getExpiration();
            return expiry != null && expiry.after(new Date());
        } catch (Exception ex) {
            // Malformed, expired, or signature mismatch — never trust the token.
            log.debug("JWT validation rejected: {}", ex.getMessage());
            return false;
        }
    }
}
