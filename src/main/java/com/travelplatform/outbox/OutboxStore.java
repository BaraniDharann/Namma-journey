package com.travelplatform.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * The {@code outbox_events} table (V18).
 *
 * <p>{@link #append} joins the caller's transaction, which is the whole point: the job is
 * committed with the booking, payment or driver change it belongs to, or not at all.
 *
 * <p>{@link #claimDue} leases rows instead of locking them for the duration of the work. Each
 * claimed row's {@code available_at} moves {@link #LEASE} into the future, so the work itself
 * (an SMTP or Razorpay call) runs with no transaction open, and a worker that dies mid-job
 * simply lets the lease lapse - another instance picks the row up then. {@code SKIP LOCKED}
 * keeps several instances from claiming the same row.
 */
@Repository
public class OutboxStore {

    /** Longer than any handler should take (SMTP and Razorpay calls are bounded well below it). */
    static final Duration LEASE = Duration.ofSeconds(120);

    private final JdbcTemplate jdbc;

    public OutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String type, String payload) {
        jdbc.update("INSERT INTO outbox_events (type, payload) VALUES (?, ?)", type, payload);
    }

    @Transactional
    public List<OutboxJob> claimDue(int limit) {
        return jdbc.query("""
                UPDATE outbox_events
                SET attempts = attempts + 1,
                    available_at = now() + make_interval(secs => ?)
                WHERE id IN (
                    SELECT id FROM outbox_events
                    WHERE status = 'PENDING' AND available_at <= now()
                    ORDER BY available_at, id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, type, payload, attempts
                """,
                (rs, i) -> new OutboxJob(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)),
                LEASE.toSeconds(), limit);
    }

    public void markDone(long id) {
        jdbc.update("UPDATE outbox_events SET status = 'DONE', processed_at = now(), last_error = NULL WHERE id = ?", id);
    }

    public void retryLater(long id, Duration delay, String error) {
        jdbc.update("""
                UPDATE outbox_events SET available_at = now() + make_interval(secs => ?), last_error = ?
                WHERE id = ?
                """, delay.toSeconds(), truncate(error), id);
    }

    public void markDead(long id, String error) {
        jdbc.update("UPDATE outbox_events SET status = 'DEAD', processed_at = now(), last_error = ? WHERE id = ?",
                truncate(error), id);
    }

    /** DONE rows are history; keep a week of it for support questions, then drop it. */
    public int purgeDoneOlderThanDays(int days) {
        return jdbc.update("""
                DELETE FROM outbox_events
                WHERE status = 'DONE' AND processed_at < now() - make_interval(days => ?)
                """, days);
    }

    public long countDead() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status = 'DEAD'", Long.class);
        return n == null ? 0 : n;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
