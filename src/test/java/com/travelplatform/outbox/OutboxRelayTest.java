package com.travelplatform.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private OutboxStore store;
    private OutboxTransport transport;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        store = mock(OutboxStore.class);
        transport = mock(OutboxTransport.class);
        OutboxProperties props = new OutboxProperties();
        props.setMaxAttempts(3);
        relay = new OutboxRelay(store, transport, props);
    }

    @Test
    @DisplayName("a delivered job is marked done")
    void deliveredIsDone() throws Exception {
        OutboxJob job = new OutboxJob(1, "email", "{}", 1);
        when(store.claimDue(anyInt())).thenReturn(List.of(job));

        relay.relay();

        verify(transport).deliver(job);
        verify(store).markDone(1);
    }

    @Test
    @DisplayName("a failed job is retried later with backoff, and the next job still runs")
    void failureRetries() throws Exception {
        OutboxJob bad = new OutboxJob(1, "email", "{}", 1);
        OutboxJob good = new OutboxJob(2, "email", "{}", 1);
        when(store.claimDue(anyInt())).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("smtp down")).when(transport).deliver(bad);

        relay.relay();

        verify(store).retryLater(eq(1L), eq(Duration.ofSeconds(20)), anyString());
        verify(store, never()).markDone(1);
        verify(store).markDone(2);
    }

    @Test
    @DisplayName("after max attempts a job is dead, not retried forever")
    void exhaustedIsDead() throws Exception {
        OutboxJob job = new OutboxJob(1, "razorpay-refund", "{}", 3);
        when(store.claimDue(anyInt())).thenReturn(List.of(job));
        doThrow(new IllegalStateException("razorpay 500")).when(transport).deliver(any());

        relay.relay();

        verify(store).markDead(eq(1L), anyString());
        verify(store, never()).retryLater(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("backoff doubles from 20s and stops at an hour")
    void backoffGrows() {
        assertEquals(Duration.ofSeconds(20), OutboxRelay.backoff(1));
        assertEquals(Duration.ofSeconds(40), OutboxRelay.backoff(2));
        assertEquals(Duration.ofSeconds(160), OutboxRelay.backoff(4));
        assertEquals(Duration.ofHours(1), OutboxRelay.backoff(30));
    }

    @Test
    @DisplayName("a database hiccup while polling is survived, not thrown at the scheduler")
    void pollFailureSurvives() {
        when(store.claimDue(anyInt())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));
        relay.relay();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
