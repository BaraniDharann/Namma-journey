package com.travelplatform.exception;

/**
 * The request is fine but the record is in the wrong state for it - approving an application
 * that was already decided, say. Answered 409 with the message, which is written for people.
 */
public class ConflictException extends IllegalStateException {
    public ConflictException(String message) {
        super(message);
    }
}
