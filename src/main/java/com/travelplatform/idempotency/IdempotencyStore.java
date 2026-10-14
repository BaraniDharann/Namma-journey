package com.travelplatform.idempotency;

/**
 * Remembers which Idempotency-Keys have been used and what they answered.
 *
 * <p>{@link #claim} must be atomic across every instance of the application: of two requests
 * presenting the same key at the same moment, exactly one may get {@link Claim.Kind#ACQUIRED}.
 */
public interface IdempotencyStore {

    /** What the store knows about a key at the moment a request presents it. */
    record Claim(Kind kind, StoredResponse response) {
        public enum Kind {
            /** First sighting: this request owns the key and should run. */
            ACQUIRED,
            /** Another request with this key is still running. */
            IN_PROGRESS,
            /** The key was already used for a different request body. */
            MISMATCH,
            /** Already answered: send {@link #response()} again. */
            REPLAY
        }

        public static Claim acquired() {
            return new Claim(Kind.ACQUIRED, null);
        }

        public static Claim inProgress() {
            return new Claim(Kind.IN_PROGRESS, null);
        }

        public static Claim mismatch() {
            return new Claim(Kind.MISMATCH, null);
        }

        public static Claim replay(StoredResponse response) {
            return new Claim(Kind.REPLAY, response);
        }
    }

    /** The answer a completed request gave, kept so a repeat can be sent the same thing. */
    record StoredResponse(int status, String contentType, byte[] body) {
    }

    Claim claim(String scope, String key, String requestHash);

    /** Records the successful answer for a key this request acquired. */
    void complete(String scope, String key, StoredResponse response);

    /** Forgets a key whose request failed, so the client may retry with the same key. */
    void release(String scope, String key);
}
