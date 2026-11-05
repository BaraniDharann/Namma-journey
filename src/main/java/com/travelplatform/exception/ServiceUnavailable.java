package com.travelplatform.exception;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Recognises "the database (or Redis, or a bulkhead) is overloaded right now" and answers it
 * as 503 with Retry-After.
 *
 * <p>These used to fall through to the catch-all handlers as 500. A 500 reads as a bug: clients
 * and load balancers do not retry it, it trips the error-rate alert, and the stack trace logged
 * for every request during a surge buried everything else. A 503 says "come back in a moment",
 * which is exactly what an exhausted Hikari pool or a statement/lock timeout means.
 *
 * <p>Shared by {@link GlobalExceptionHandler} and the authentication filter (filters run before
 * the controller advice exists for a request), so both answer with the same status, header and body.
 */
public final class ServiceUnavailable {

    /** Long enough for a pool spike to drain (connection-timeout is 3s), short enough to feel live. */
    public static final int RETRY_AFTER_SECONDS = 5;

    public static final String MESSAGE = "The service is busy right now. Please try again in a few seconds.";

    // Postgres SQLSTATEs that mean "busy", not "wrong": 08xxx connection failures, 53300
    // too_many_connections, 57P03 cannot_connect_now, 57014 statement_timeout, 55P03 lock_timeout.
    private static final Set<String> TRANSIENT_SQL_STATES = Set.of("53300", "57P03", "57014", "55P03");

    private ServiceUnavailable() {
    }

    /**
     * The first exception in {@code ex}'s cause chain that marks a transient infrastructure
     * failure, or null. The chain is walked because these rarely arrive bare: Hikari's timeout
     * comes wrapped in Hibernate's JDBCConnectionException inside Spring's
     * CannotCreateTransactionException, and services sometimes re-wrap in a RuntimeException.
     * CannotCreateTransactionException itself is deliberately not on the list - it is only
     * transient when its cause is a connection problem, which the walk then finds.
     */
    public static Throwable transientCause(Throwable ex) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = ex; t != null && seen.add(t); t = t.getCause()) {
            if (isTransient(t)) {
                return t;
            }
        }
        return null;
    }

    private static boolean isTransient(Throwable t) {
        if (t instanceof DataAccessResourceFailureException   // CannotGetJdbcConnectionException, RedisConnectionFailureException
                || t instanceof TransientDataAccessResourceException
                || t instanceof QueryTimeoutException
                || t instanceof PessimisticLockingFailureException // CannotAcquireLockException (lock_timeout), deadlock loser
                || t instanceof SQLTransientException               // SQLTransientConnectionException (Hikari), SQLTimeoutException
                || t instanceof org.hibernate.exception.JDBCConnectionException
                || t instanceof jakarta.persistence.QueryTimeoutException
                || t instanceof jakarta.persistence.LockTimeoutException
                || t instanceof BulkheadFullException
                || t instanceof CallNotPermittedException) {
            return true;
        }
        if (t instanceof SQLException sql && sql.getSQLState() != null) {
            String state = sql.getSQLState();
            return state.startsWith("08") || TRANSIENT_SQL_STATES.contains(state);
        }
        return false;
    }

    /** Warn with one line, not a stack trace: during an incident this fires for every request. */
    public static void log(org.slf4j.Logger log, String where, Throwable cause) {
        log.warn("Service unavailable ({}): {}: {}", where, cause.getClass().getSimpleName(), cause.getMessage());
    }

    public static ResponseEntity<Map<String, String>> response() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS))
                .body(Map.of("error", MESSAGE));
    }

    /** The same answer as {@link #response()}, written straight to a servlet response from a filter. */
    public static void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // MESSAGE is a constant with no characters that need JSON escaping.
        response.getWriter().write("{\"error\": \"" + MESSAGE + "\"}");
    }
}
