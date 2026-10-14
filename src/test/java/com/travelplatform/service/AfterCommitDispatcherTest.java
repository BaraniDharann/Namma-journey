package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Emails and Telegram cards about a booking must only go out once that booking is really saved.
 * Sending them from inside the transaction told drivers about trips that were then rolled back,
 * and held a DB connection for as long as Telegram took to answer.
 */
class AfterCommitDispatcherTest {

    private final List<Runnable> queued = new ArrayList<>();
    /** Captures what reaches the executor, so the test controls when it "runs". */
    private final AfterCommitDispatcher dispatcher = new AfterCommitDispatcher(queued::add);
    private final TransactionTemplate tx = new TransactionTemplate(new NoOpTransactionManager());

    @Test
    @DisplayName("inside a transaction, nothing is sent until it commits")
    void waitsForCommit() {
        AtomicBoolean sent = new AtomicBoolean();

        tx.executeWithoutResult(status -> {
            dispatcher.dispatch("telegram", () -> sent.set(true));
            assertTrue(queued.isEmpty(), "must not even be queued before commit");
        });

        assertEquals(1, queued.size());
        queued.get(0).run();
        assertTrue(sent.get());
    }

    @Test
    @DisplayName("a rolled-back transaction sends nothing")
    void rollbackSendsNothing() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            dispatcher.dispatch("telegram", () -> { });
            throw new IllegalStateException("booking insert failed");
        }));

        assertTrue(queued.isEmpty());
    }

    @Test
    @DisplayName("outside any transaction it is handed to the executor straight away")
    void noTransactionDispatchesImmediately() {
        dispatcher.dispatch("email", () -> { });

        assertEquals(1, queued.size());
    }

    @Test
    @DisplayName("a failing send is contained: logged, never thrown at the caller or the executor")
    void failuresAreContained() {
        dispatcher.dispatch("telegram", () -> {
            throw new RuntimeException("Telegram is down");
        });

        assertDoesNotThrow(() -> queued.get(0).run());
    }

    @Test
    @DisplayName("an executor that refuses work (queue full) does not fail the committed request")
    void rejectedExecutionIsContained() {
        AfterCommitDispatcher saturated = new AfterCommitDispatcher(task -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        });
        AtomicBoolean reached = new AtomicBoolean();

        assertDoesNotThrow(() -> tx.executeWithoutResult(status -> {
            saturated.dispatch("telegram", () -> { });
            reached.set(true);
        }));
        assertTrue(reached.get());
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
