package com.travelplatform.exception;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Overload is not a bug: an exhausted pool, a statement or lock timeout, Redis being unreachable
 * or a full bulkhead must come back as 503 + Retry-After (which clients and nginx retry), not 500
 * (which they don't, and which trips the error-rate alert).
 */
class ServiceUnavailableMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static SQLTransientConnectionException hikariTimeout() {
        return new SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 3000ms");
    }

    private static void assertBusy(ResponseEntity<Map<String, String>> r, String label) {
        assertEquals(503, r.getStatusCode().value(), label);
        assertEquals(String.valueOf(ServiceUnavailable.RETRY_AFTER_SECONDS), r.getHeaders().getFirst("Retry-After"), label);
        assertEquals(ServiceUnavailable.MESSAGE, r.getBody().get("error"), label);
    }

    @Test
    @DisplayName("each transient infrastructure failure maps to 503 with Retry-After and a friendly message")
    void transientFailuresAre503() {
        Exception[] busy = {
                new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", hikariTimeout()),
                new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                        new JDBCConnectionException("Unable to acquire JDBC Connection", hikariTimeout())),
                hikariTimeout(),
                new QueryTimeoutException("canceling statement due to statement timeout"),
                new CannotAcquireLockException("could not obtain lock (lock_timeout)"),
                new RedisConnectionFailureException("Unable to connect to Redis at 10.0.4.17:6379"),
                BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("osrm")),
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("osrm")),
        };
        for (Exception e : busy) {
            assertBusy(handler.handleServiceUnavailable(e), e.getClass().getSimpleName());
        }
    }

    @Test
    @DisplayName("wrapped failures are unwrapped: the catch-all handlers answer 503 when the cause chain is transient")
    void wrappedFailuresAre503() {
        assertBusy(handler.handleRuntimeException(new JpaSystemException(new RuntimeException("wrapped", hikariTimeout()))),
                "JpaSystemException(... Hikari timeout)");
        assertBusy(handler.handleRuntimeException(new RuntimeException("Failed to load bookings",
                new QueryTimeoutException("statement timeout"))), "RuntimeException(QueryTimeoutException)");
        // Postgres' own codes, as the driver raises them without a Spring translation.
        assertBusy(handler.handleGenericException(new Exception(new SQLException("canceling statement", "57014"))), "57014");
        assertBusy(handler.handleGenericException(new Exception(new SQLException("lock not available", "55P03"))), "55P03");
        assertBusy(handler.handleGenericException(new Exception(new SQLException("Connection refused", "08001"))), "08001");
    }

    @Test
    @DisplayName("a transaction that could not start for a non-connection reason is still a 500")
    void cannotCreateTransactionWithoutConnectionCauseIs500() {
        ResponseEntity<Map<String, String>> r = handler.handleServiceUnavailable(
                new CannotCreateTransactionException("No transaction manager", new IllegalStateException("misconfigured")));
        assertEquals(500, r.getStatusCode().value());
        assertFalse(r.getHeaders().containsKey("Retry-After"));
    }

    @Test
    @DisplayName("ordinary bugs and app messages are unaffected (500 / 4xx, no Retry-After)")
    void nonTransientUnaffected() {
        assertEquals(500, handler.handleRuntimeException(new NullPointerException("x")).getStatusCode().value());
        assertEquals(500, handler.handleGenericException(new Exception(new SQLException("syntax error", "42601"))).getStatusCode().value());
        assertEquals(404, handler.handleRuntimeException(new RuntimeException("Booking not found")).getStatusCode().value());
    }

    @Test
    @DisplayName("Spring routes each transient type to the 503 handler, not the generic RuntimeException one")
    void springPicksThe503Handler() throws Exception {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
        Method expected = GlobalExceptionHandler.class.getMethod("handleServiceUnavailable", Exception.class);
        Exception[] busy = {
                new CannotGetJdbcConnectionException("x"),
                new CannotCreateTransactionException("x"),
                hikariTimeout(),
                new QueryTimeoutException("x"),
                new CannotAcquireLockException("x"),
                new RedisConnectionFailureException("x"),
                BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("osrm")),
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("osrm")),
        };
        for (Exception e : busy) {
            assertEquals(expected, resolver.resolveMethod(e), e.getClass().getSimpleName());
        }
    }
}
