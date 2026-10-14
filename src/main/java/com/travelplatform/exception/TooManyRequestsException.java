package com.travelplatform.exception;

/** Answered as 429 with its message - e.g. asking for a new OTP within the resend cooldown. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
