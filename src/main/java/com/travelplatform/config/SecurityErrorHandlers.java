package com.travelplatform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Turns security rejections into the JSON envelope the rest of the API uses ({@code {"error": ...}})
 * and — more importantly — into the <em>right</em> status code.
 *
 * <p>Without an explicit entry point Spring Security falls back to {@code Http403ForbiddenEntryPoint},
 * so a request with no token, an expired token, or a token signed with a rotated secret came back as
 * an empty 403. The browser client only clears the session and redirects to /login on a 401, so a
 * stale session produced an endless "Access denied" toast on every poll instead of a re-login.
 *
 * <p>The split is the standard one: <b>401</b> means "we don't know who you are, authenticate",
 * <b>403</b> means "we know who you are and you may not do this".
 */
public final class SecurityErrorHandlers {

    private SecurityErrorHandlers() {}

    private static void write(HttpServletResponse response, HttpStatus status, String message,
                              ObjectMapper mapper) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), Map.of("error", message));
    }

    /** No usable authentication on the request → 401 so the client re-authenticates. */
    @Component
    public static class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

        private final ObjectMapper mapper;

        public RestAuthenticationEntryPoint(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            write(response, HttpStatus.UNAUTHORIZED, "Authentication required", mapper);
        }
    }

    /**
     * Authenticated but not permitted → 403. ExceptionTranslationFilter normally routes anonymous
     * rejections to the entry point above, but a denial raised outside that path (a method-security
     * check, say) can land here with nothing authenticated; answer those 401 too so the client
     * recovers by logging in instead of getting stuck on a message it can't act on.
     */
    @Component
    public static class RestAccessDeniedHandler implements AccessDeniedHandler {

        private final ObjectMapper mapper;
        private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

        public RestAccessDeniedHandler(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || trustResolver.isAnonymous(auth)) {
                write(response, HttpStatus.UNAUTHORIZED, "Authentication required", mapper);
                return;
            }
            write(response, HttpStatus.FORBIDDEN, "Access denied", mapper);
        }
    }
}
