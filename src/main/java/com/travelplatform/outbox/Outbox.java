package com.travelplatform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where services hand off background work: {@code outbox.enqueue("email", message)}.
 *
 * <p>Called inside a transaction, the job commits or rolls back with it - a refund is never
 * queued for a cancellation that did not happen, and never lost for one that did. Called
 * outside one, it commits on its own.
 */
@Component
public class Outbox {

    private final OutboxStore store;
    private final ObjectMapper objectMapper;

    public Outbox(OutboxStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String type, Object payload) {
        try {
            store.append(type, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Job payload for " + type + " is not serialisable", e);
        }
    }
}
