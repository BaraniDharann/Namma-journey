package com.travelplatform.exception;

/**
 * The right owner and password, but the application is not approved (still under review, or
 * rejected). Answered 403 with the message. Thrown only after the password matched, so it says
 * nothing to someone who does not hold the account.
 */
public class OwnerNotApprovedException extends RuntimeException {
    public OwnerNotApprovedException(String message) {
        super(message);
    }
}
