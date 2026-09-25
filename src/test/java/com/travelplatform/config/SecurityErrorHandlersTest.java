package com.travelplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the status code an unauthenticated caller gets back.
 *
 * <p>Spring Security's default entry point is {@code Http403ForbiddenEntryPoint}, so before these
 * handlers were wired in, a missing/expired/wrong-secret token produced an empty <b>403</b>. The SPA
 * only tears down the session and redirects to /login on a <b>401</b>, so a stale session sat on the
 * page retrying every poll behind a permanent "Access denied" toast and never offered a way back in.
 *
 * <p>401 = we don't know who you are. 403 = we do, and you still may not do this.
 */
class SecurityErrorHandlersTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SecurityErrorHandlers.RestAuthenticationEntryPoint entryPoint =
            new SecurityErrorHandlers.RestAuthenticationEntryPoint(mapper);
    private final SecurityErrorHandlers.RestAccessDeniedHandler accessDenied =
            new SecurityErrorHandlers.RestAccessDeniedHandler(mapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("no authentication is answered 401 with a JSON body, not a bare 403")
    void entryPointReturns401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("no token"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"), response.getContentType());
        assertEquals("Authentication required", mapper.readTree(response.getContentAsString()).get("error").asText());
    }

    @Test
    @DisplayName("a real user denied by role is answered 403, not logged out")
    void accessDeniedReturns403ForAuthenticatedCaller() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user-id", null, List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDenied.handle(new MockHttpServletRequest(), response, new AccessDeniedException("nope"));

        assertEquals(403, response.getStatus());
        assertEquals("Access denied", mapper.readTree(response.getContentAsString()).get("error").asText());
    }

    @Test
    @DisplayName("an anonymous caller routed to the denial handler still gets 401")
    void accessDeniedFallsBackTo401ForAnonymous() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDenied.handle(new MockHttpServletRequest(), response, new AccessDeniedException("nope"));

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("an empty security context is answered 401")
    void accessDeniedFallsBackTo401WhenNothingAuthenticated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDenied.handle(new MockHttpServletRequest(), response, new AccessDeniedException("nope"));

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("a committed response is left alone rather than throwing mid-write")
    void committedResponseIsNotTouched() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.getWriter().write("already sent");
        response.flushBuffer();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("no token"));

        assertEquals(200, response.getStatus());
    }
}
