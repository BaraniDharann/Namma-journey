package com.travelplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.repository.UserRepository;
import com.travelplatform.security.RevokedTokenStore;
import com.travelplatform.security.TokenVersionService;
import jakarta.servlet.FilterChain;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A busy database must not log everyone out.
 *
 * <p>The filter checks token_version and revoked_tokens in the database. It used to swallow any
 * failure there and carry on anonymously, so a pool timeout ended as 401 - and the SPA answers a
 * 401 by clearing the session and redirecting to /login. Now "could not check" is a 503 the client
 * retries, while "checked, and the token is no good" stays a 401.
 */
class JwtAuthenticationFilterOutageTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private JwtUtil jwt;
    private UserRepository users;
    private RevokedTokenStore revoked;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "test-only-secret-0123456789abcdef0123456789");
        ReflectionTestUtils.setField(jwt, "expiration", 3_600_000L);
        ReflectionTestUtils.invokeMethod(jwt, "initSigningKey");

        users = mock(UserRepository.class);
        revoked = mock(RevokedTokenStore.class);
        TokenVersionService versions = new TokenVersionService(
                users, mock(DriverRepository.class), mock(OwnerRepository.class));

        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwt);
        ReflectionTestUtils.setField(filter, "tokenVersionService", versions);
        ReflectionTestUtils.setField(filter, "revokedTokens", revoked);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(UUID userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/" + userId + "/bookings");
        request.addHeader("Authorization", "Bearer " + jwt.generateToken(userId.toString(), "ROLE_USER", 0L));
        return request;
    }

    @Test
    @DisplayName("Hikari pool timeout while checking the token answers 503 + Retry-After, not an anonymous request")
    void poolTimeoutIs503() throws Exception {
        UUID userId = UUID.randomUUID();
        // What Spring Data actually throws when the pool is exhausted: Hikari's timeout, wrapped by
        // Hibernate, wrapped by the transaction manager failing to begin.
        when(users.findById(userId)).thenThrow(new CannotCreateTransactionException(
                "Could not open JPA EntityManager for transaction",
                new JDBCConnectionException("Unable to acquire JDBC Connection",
                        new SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 3000ms"))));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(userId), response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        assertNotNull(mapper.readTree(response.getContentAsString()).get("error").asText());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("revoked-token lookup failing to get a JDBC connection answers 503, not 401")
    void revokedTokenLookupOutageIs503() throws Exception {
        UUID userId = UUID.randomUUID();
        com.travelplatform.entity.User user = new com.travelplatform.entity.User();
        user.setTokenVersion(0L);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(revoked.isRevoked(anyString())).thenThrow(new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(userId), response, chain);

        assertEquals(503, response.getStatus());
        assertEquals("5", response.getHeader("Retry-After"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("an account that no longer exists still continues unauthenticated (ends as 401)")
    void unknownAccountStaysAnonymous() throws Exception {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(userId), response, chain);

        verify(chain).doFilter(any(), any());
        assertEquals(200, response.getStatus()); // untouched: the entry point turns this into 401
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("a non-infrastructure failure is still swallowed as before (anonymous, not 503)")
    void otherFailuresStayAnonymous() throws Exception {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenThrow(new IllegalStateException("bug"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestWithToken(userId), response, chain);

        verify(chain).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
