package com.travelplatform.config;

import com.travelplatform.repository.OwnerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds {@link OwnerScope} for a ROLE_OWNER request, after {@link JwtAuthenticationFilter} has
 * authenticated it, and always clears it afterwards.
 *
 * <p>Deliberately not a @Component: Spring Boot would also register it in the plain servlet chain,
 * where it runs before authentication and then, being once-per-request, is skipped inside the
 * security chain. SecurityConfig creates it.
 */
public class OwnerScopeFilter extends OncePerRequestFilter {

    private final OwnerRepository owners;
    private final String platformOwnerCode;
    /** Owner codes never change once assigned, so whether an id is the platform owner is cached. */
    private final Map<Long, Boolean> platformById = new ConcurrentHashMap<>();

    public OwnerScopeFilter(OwnerRepository owners, String platformOwnerCode) {
        this.owners = owners;
        this.platformOwnerCode = platformOwnerCode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long ownerId = ownerId(SecurityContextHolder.getContext().getAuthentication());
        if (ownerId == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            OwnerScope.bind(ownerId, platformById.computeIfAbsent(ownerId, id -> owners.findById(id)
                    .map(o -> platformOwnerCode.equalsIgnoreCase(String.valueOf(o.getOwnerCode())))
                    .orElse(false)));
            chain.doFilter(request, response);
        } finally {
            OwnerScope.clear();
        }
    }

    private static Long ownerId(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_OWNER".equals(a.getAuthority()))) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
