package com.travelplatform.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Postgres-backed {@link IdempotencyStore}. Shared by every instance, survives restarts, and
 * needs no Redis - the guarantee rests entirely on the table's primary key.
 *
 * <p>Each call is its own auto-committed statement, deliberately outside the request's
 * transaction: the claim must be visible to a concurrent duplicate immediately, and must not be
 * rolled back together with a failing booking.
 */
@Repository
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);

    /**
     * A claim with no answer after this long belongs to a request that died mid-flight (crash,
     * redeploy). Comfortably above the slowest legitimate booking: route lookup is capped at 8s.
     */
    private static final String STALE_AFTER = "60 seconds";

    private static final String RETENTION = "24 hours";

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Row(String requestHash, Integer status, String contentType, byte[] body) {
    }

    @Override
    public Claim claim(String scope, String key, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys (scope, idem_key, request_hash)
                VALUES (?, ?, ?)
                ON CONFLICT (scope, idem_key) DO NOTHING
                """, scope, key, requestHash);
        if (inserted == 1) {
            return Claim.acquired();
        }

        // Someone holds it. If that someone died without answering, take the key over - same
        // body only, a different body is still a client bug whatever state the key is in.
        int takenOver = jdbc.update("""
                UPDATE idempotency_keys SET created_at = now()
                WHERE scope = ? AND idem_key = ? AND request_hash = ?
                  AND response_body IS NULL
                  AND created_at < now() - CAST(? AS interval)
                """, scope, key, requestHash, STALE_AFTER);
        if (takenOver == 1) {
            return Claim.acquired();
        }

        List<Row> rows = jdbc.query("""
                SELECT request_hash, response_status, content_type, response_body
                FROM idempotency_keys WHERE scope = ? AND idem_key = ?
                """,
                (rs, i) -> new Row(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3), rs.getBytes(4)),
                scope, key);
        if (rows.isEmpty()) {
            // Released between our INSERT and SELECT: the key is free again, so try once more.
            return claim(scope, key, requestHash);
        }
        Row row = rows.get(0);
        if (!row.requestHash().trim().equals(requestHash)) {
            return Claim.mismatch();
        }
        if (row.body() == null) {
            return Claim.inProgress();
        }
        return Claim.replay(new StoredResponse(row.status(), row.contentType(), row.body()));
    }

    @Override
    public void complete(String scope, String key, StoredResponse response) {
        jdbc.update("""
                UPDATE idempotency_keys
                SET response_status = ?, content_type = ?, response_body = ?, completed_at = now()
                WHERE scope = ? AND idem_key = ?
                """, response.status(), response.contentType(), response.body(), scope, key);
    }

    @Override
    public void release(String scope, String key) {
        jdbc.update("DELETE FROM idempotency_keys WHERE scope = ? AND idem_key = ? AND response_body IS NULL",
                scope, key);
    }

    /**
     * Drops keys past their 24-hour window. Safe on every instance at once: a plain DELETE by age
     * is idempotent, so no scheduler lock is needed.
     */
    @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
    public int purgeExpired() {
        int removed = jdbc.update(
                "DELETE FROM idempotency_keys WHERE created_at < now() - CAST(? AS interval)", RETENTION);
        if (removed > 0) {
            log.info("Purged {} expired idempotency key(s)", removed);
        }
        return removed;
    }
}
