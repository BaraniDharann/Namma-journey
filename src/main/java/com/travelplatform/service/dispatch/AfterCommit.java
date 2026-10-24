package com.travelplatform.service.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a write to the matching stores once the database change it mirrors has committed, on the
 * same thread. A rolled-back accept must not leave the driver BUSY in Redis, and a store that is
 * down must not fail a request whose database work already succeeded - reconcile heals the gap.
 *
 * <p>Not {@code AfterCommitDispatcher}: that one is for the outside world, asynchronous and
 * best-effort. These writes are our own state, cheap, and the next line of the caller (a round,
 * a test) may read them back at once.
 */
final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    static void run(String what, Runnable write) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(what, write);
                }
            });
        } else {
            safely(what, write);
        }
    }

    private static void safely(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.warn("Matching store update '{}' failed; reconcile will correct it: {}", what, e.toString());
        }
    }
}
