package com.travelplatform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request one id that shows up on every log line it causes and in the
 * {@code X-Request-Id} response header.
 *
 * <p>Before this, a traveller reporting "my booking failed at 3pm" left nothing to search for:
 * concurrent requests interleave in the log and nothing tied a line to a request. Now the id is
 * in the response (visible in the browser's network tab, easy to quote to support) and in the
 * log pattern ({@code logging.pattern.level} in application.yml).
 *
 * <p>An incoming id is kept when it is well formed, so nginx or a client can mint one and have it
 * followed end to end. Anything else - and above all anything containing a newline, which would
 * let a caller forge log lines - is replaced.
 *
 * <p>Ordered first, ahead of rate limiting and security, so their log lines carry the id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString().replace("-", "");

        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat threads are pooled: a leftover id would label the next, unrelated request.
            MDC.remove(MDC_KEY);
        }
    }
}
