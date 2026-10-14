package com.travelplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.travelplatform.security.TokenVersionService tokenVersionService;

    @Autowired
    private com.travelplatform.security.RevokedTokenStore revokedTokens;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                String userId = null;
                String role = null;
                boolean current = false;
                if (jwtUtil.validateToken(token)) {
                    userId = jwtUtil.extractUserId(token);
                    role = jwtUtil.extractRole(token);
                    // A correctly signed token is not enough: the account must still exist and the
                    // token must not predate a password reset or logout (token_version).
                    current = tokenVersionService.isCurrent(role, userId, jwtUtil.extractTokenVersion(token))
                            && !revokedTokens.isRevoked(jwtUtil.extractJti(token)); // logged out on this device
                }
                if (current) {

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.singletonList(new SimpleGrantedAuthority(role))
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                logger.error("JWT validation failed: " + e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
