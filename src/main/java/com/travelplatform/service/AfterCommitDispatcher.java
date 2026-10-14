package com.travelplatform.service;

import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends things to the outside world (email, Telegram) only once the database work they describe
 * has actually committed, and off the request thread.
 *
 * <p>Notifications used to be sent from inside the caller's transaction. Two consequences: a
 * driver could be told about a trip whose insert was then rolled back (e.g. the driver-overlap
 * constraint refusing it), and a slow Telegram API - up to 5s per call - kept a pooled DB
 * connection checked out the whole time.
 *
 * <p>Delivery is best-effort, exactly as before: a failed or rejected send is logged and the
 * request still succeeds, because the in-app notification row (written inside the transaction)
 * remains the durable record.
 */
@Component
public class AfterCommitDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitDispatcher.class);

    private final Executor executor;

    public AfterCommitDispatcher(@Qualifier("dispatchExecutor") Executor executor) {
        this.executor = executor;
    }

    /**
     * Runs {@code send} after the current transaction commits, or right away if there is none.
     * Dropped silently if the transaction rolls back - that is the point.
     *
     * @param channel short label for logs, e.g. "telegram" or "email"
     */
    public void dispatch(String channel, Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(channel, send);
                }
            });
        } else {
            submit(channel, send);
        }
    }

    private void submit(String channel, Runnable send) {
        try {
            executor.execute(() -> {
                try {
                    send.run();
                    count(channel, "sent");
                } catch (RuntimeException e) {
                    log.warn("{} dispatch failed: {}", channel, e.toString());
                    count(channel, "failed");
                }
            });
        } catch (RejectedExecutionException e) {
            // Queue full: shedding one notification beats failing a request that already committed.
            log.warn("{} dispatch dropped, executor saturated: {}", channel, e.toString());
            count(channel, "dropped");
        }
    }

    /** namma_notifications_dispatch_total{channel,outcome} - the "failed" rate is alerted on. */
    private static void count(String channel, String outcome) {
        Metrics.counter("namma.notifications.dispatch", "channel", channel, "outcome", outcome).increment();
    }
}
