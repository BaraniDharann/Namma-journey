package com.travelplatform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    /**
     * How long one batch may run. Under the claim lease, so a job is never handed to a second
     * instance while this one is still working on it.
     */
    static final Duration BATCH_DEADLINE = OutboxStore.LEASE.minusSeconds(30);

    private final ExecutorService workers;

    public OutboxRelay(OutboxStore store, OutboxTransport transport, OutboxProperties properties) {
        this.store = store;
        this.transport = transport;
        this.properties = properties;
        this.workers = Executors.newFixedThreadPool(Math.max(1, properties.getWorkers()), r -> {
            Thread t = new Thread(r, "outbox-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Fixed delay and waits for the whole batch, so batches never overlap. Jobs of a batch run in
     * parallel: they used to run one after another, and one hung SMTP connection stalled every
     * refund queued behind it.
     */
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
        if (due.isEmpty()) {
            return;
        }
        List<Callable<Void>> deliveries = new ArrayList<>(due.size());
        for (OutboxJob job : due) {
            deliveries.add(() -> {
                deliver(job);
                return null;
            });
        }
        try {
            // Anything still running at the deadline is interrupted; its lease lapses and the job
            // is claimed again - delivery is at-least-once either way, as with SQS.
            workers.invokeAll(deliveries, BATCH_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void stop() {
        workers.shutdownNow();
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
