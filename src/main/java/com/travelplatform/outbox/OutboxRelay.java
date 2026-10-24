package com.travelplatform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Moves due outbox rows to their transport: runs them here ({@code in-process}) or sends them
 * to SQS. Safe on every instance at once - rows are claimed with {@code SKIP LOCKED}.
 *
 * <p>In-process failures retry with exponential backoff (20s, 40s, 80s ... capped at an hour)
 * and become DEAD after {@code max-attempts}, logged at ERROR for a person to look at. With SQS,
 * a row is DONE once the queue has it; retries and the dead-letter queue are SQS's job from then.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final OutboxStore store;
    private final OutboxTransport transport;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxStore store, OutboxTransport transport, OutboxProperties properties) {
        this.store = store;
        this.transport = transport;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}",
               initialDelayString = "${app.outbox.initial-delay-ms:10000}")
    public void relay() {
        List<OutboxJob> due;
        try {
            due = store.claimDue(properties.getBatchSize());
        } catch (RuntimeException e) {
            log.warn("Outbox poll failed: {}", e.toString());
            return;
        }
        due.forEach(this::deliver);
    }

    void deliver(OutboxJob job) {
        try {
            transport.deliver(job);
            store.markDone(job.id());
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (job.attempts() >= properties.getMaxAttempts()) {
                log.error("Outbox job {} ({}) failed {} times, giving up: {}", job.id(), job.type(), job.attempts(), error);
                store.markDead(job.id(), error);
                JobRunner.count(job.type(), "dead");
            } else {
                Duration wait = backoff(job.attempts());
                log.warn("Outbox job {} ({}) attempt {} failed, retrying in {}s: {}",
                        job.id(), job.type(), job.attempts(), wait.toSeconds(), error);
                store.retryLater(job.id(), wait, error);
            }
        }
    }

    static Duration backoff(int attempts) {
        long seconds = 10L << Math.min(Math.max(attempts, 1), 10);
        return Duration.ofSeconds(Math.min(seconds, MAX_BACKOFF.toSeconds()));
    }

    @Scheduled(cron = "${app.outbox.purge-cron:0 30 3 * * *}")
    public void purge() {
        int removed = store.purgeDoneOlderThanDays(7);
        if (removed > 0) {
            log.info("Purged {} finished outbox job(s)", removed);
        }
    }
}
