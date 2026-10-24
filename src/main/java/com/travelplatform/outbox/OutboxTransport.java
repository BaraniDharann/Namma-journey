package com.travelplatform.outbox;

/** Where a due job goes: run here, or onto a queue. Throws when it could not be handed over. */
public interface OutboxTransport {

    void deliver(OutboxJob job) throws Exception;
}
