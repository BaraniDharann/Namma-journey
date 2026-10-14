package com.travelplatform.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Logged-out sessions. A token's unique id (jti) is recorded here at logout and refused until the
 * token would have expired anyway - ending that one session, not every device the account is
 * signed in on (that is what a password reset's token_version bump is for).
 *
 * <p>Lookups are cached per jti for {@link #CACHE_TTL}: a revocation made on this instance takes
 * effect immediately; other instances see it within the TTL.
 */
@Repository
public class RevokedTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenStore.class);
    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final JdbcTemplate jdbc;
    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(100_000)
            .build();

    public RevokedTokenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        jdbc.update("INSERT INTO revoked_tokens (jti, expires_at) VALUES (?, ?) ON CONFLICT (jti) DO NOTHING",
                jti, Timestamp.from(expiresAt));
        cache.put(jti, Boolean.TRUE);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false; // tokens issued before session ids existed
        }
        return cache.get(jti, id -> {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM revoked_tokens WHERE jti = ?", Integer.class, id);
            return n != null && n > 0;
        });
    }

    /** Safe on every instance at once: a plain DELETE by age. */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 600_000)
    public void purgeExpired() {
        int removed = jdbc.update("DELETE FROM revoked_tokens WHERE expires_at < now()");
        if (removed > 0) {
            log.info("Purged {} expired revoked-token row(s)", removed);
        }
    }
}
