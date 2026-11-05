package com.travelplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Retention for in-app notifications. Nothing used to delete them, so every recipient's history -
 * and above all the shared "owner" bucket, which every booking, payment and driver event writes
 * to - only ever grew, and the bell read all of it.
 *
 * <p>Read notifications go after {@code read-days}; anything, read or not, after {@code max-days}.
 * Every bucket is purged the same way, the owner's included. The notification row is only the
 * bell's copy: bookings, payments and emails keep their own records.
 *
 * <p>Deletes run in batches of {@code batch-size} rows, each its own short autocommit statement, so
 * no single DELETE holds row locks or bloats one transaction for long. The batch is picked with
 * {@code FOR UPDATE SKIP LOCKED}: rows a user is marking read right now are left for the next run
 * instead of making the purge wait on them (and trip the 5 s lock_timeout). That also makes the job
 * safe on every instance at once - two instances never fight over the same rows - so no scheduler
 * lock is needed, as with the other age-based purges (RevokedTokenStore, JdbcIdempotencyStore).
 */
@Component
public class NotificationPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationPurgeJob.class);

    private static final String DELETE_READ_BATCH = """
            DELETE FROM notifications WHERE id IN (
                SELECT id FROM notifications
                WHERE created_at < ? AND read = true
                LIMIT ?
                FOR UPDATE SKIP LOCKED)
            """;

    private static final String DELETE_ANY_BATCH = """
            DELETE FROM notifications WHERE id IN (
                SELECT id FROM notifications
                WHERE created_at < ?
                LIMIT ?
                FOR UPDATE SKIP LOCKED)
            """;

    private final JdbcTemplate jdbc;
    private final int readDays;
    private final int maxDays;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public NotificationPurgeJob(JdbcTemplate jdbc,
                                @Value("${app.notifications.retention.read-days:30}") int readDays,
                                @Value("${app.notifications.retention.max-days:90}") int maxDays,
                                @Value("${app.notifications.retention.batch-size:5000}") int batchSize,
                                @Value("${app.notifications.retention.max-batches-per-run:200}") int maxBatchesPerRun) {
        this.jdbc = jdbc;
        this.readDays = readDays;
        this.maxDays = maxDays;
        this.batchSize = Math.max(1, batchSize);
        this.maxBatchesPerRun = Math.max(1, maxBatchesPerRun);
    }

    @Scheduled(fixedDelayString = "${app.notifications.retention.interval-ms:3600000}",
               initialDelayString = "${app.notifications.retention.initial-delay-ms:600000}")
    public void purgeOld() {
        try {
            purge(LocalDateTime.now());
        } catch (RuntimeException e) {
            // Retention is housekeeping: a failed run is retried on the next tick, never fatal.
            log.warn("Notification purge failed, will retry next run: {}", e.toString());
        }
    }

    /**
     * One run. {@code now} is in the same zone notifications are stamped in (the JVM default -
     * {@link com.travelplatform.entity.Notification#getCreatedAt()} is {@code LocalDateTime.now()}).
     *
     * @return rows deleted
     */
    public int purge(LocalDateTime now) {
        int removed = 0;
        // A days value of 0 or less switches that rule off.
        if (readDays > 0) {
            removed += drain(DELETE_READ_BATCH, now.minusDays(readDays));
        }
        if (maxDays > 0) {
            removed += drain(DELETE_ANY_BATCH, now.minusDays(maxDays));
        }
        if (removed > 0) {
            log.info("Purged {} old notification(s)", removed);
        }
        return removed;
    }

    /**
     * Deletes batch after batch until one comes back short. Capped per run so a first run over a
     * years-old backlog spreads across several ticks instead of one long loop.
     */
    private int drain(String sql, LocalDateTime cutoff) {
        Timestamp before = Timestamp.valueOf(cutoff);
        int total = 0;
        for (int i = 0; i < maxBatchesPerRun; i++) {
            int n = jdbc.update(sql, before, batchSize);
            total += n;
            if (n < batchSize) {
                break;
            }
        }
        return total;
    }
}
