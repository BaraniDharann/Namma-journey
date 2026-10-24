package com.travelplatform.outbox;

/**
 * One unit of background work.
 *
 * @param id       outbox row id; also the message id on SQS, so duplicates can be recognised
 * @param attempts how many times this job has been claimed, this attempt included
 */
public record OutboxJob(long id, String type, String payload, int attempts) {
}
