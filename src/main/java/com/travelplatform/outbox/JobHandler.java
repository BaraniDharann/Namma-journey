package com.travelplatform.outbox;

/**
 * Does the work for one job type. Delivery is at-least-once - a job can run twice if a worker
 * dies after the work but before recording it - so handlers must be safe to repeat.
 *
 * <p>Throw to ask for a retry; return normally when the job is done, including when it turns
 * out there is nothing left to do.
 */
public interface JobHandler {

    /** Matches {@link OutboxJob#type()}, e.g. {@code "email"}. */
    String type();

    void handle(String payload) throws Exception;
}
