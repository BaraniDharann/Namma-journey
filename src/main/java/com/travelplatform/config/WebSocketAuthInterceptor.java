package com.travelplatform.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards the live-tracking WebSocket, which carries every passenger's real-time position.
 *
 * <p>It is an allow-list, because the simple broker is permissive by design: it accepts Ant-style
 * wildcard subscriptions ("/topic/**" receives every booking's positions) and delivers client SEND
 * frames addressed to "/topic/..." straight to subscribers, bypassing every @MessageMapping check.
 * The previous guard only looked at subscriptions starting with "/topic/booking/", so an anonymous
 * client could watch every trip or paint a fake car onto a passenger's map.
 *
 * <ul>
 *   <li>CONNECT needs a valid JWT; there is no anonymous session.</li>
 *   <li>SUBSCRIBE must be exactly /topic/booking/{uuid}/location, and BookingAccessGuard must
 *       allow this caller on that booking.</li>
 *   <li>SEND is only accepted to /app/** (handled by @MessageMapping, which checks the driver).
 *       Only the server publishes to /topic.</li>
 * </ul>
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern BOOKING_TOPIC = Pattern.compile(
            "^/topic/booking/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/location$");

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BookingAccessGuard bookingAccessGuard;

    @Autowired
    private com.travelplatform.security.TokenVersionService tokenVersionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK, NACK, BEGIN, COMMIT, ABORT carry no data to protect.
            }
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.validateToken(token)) {
                String userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);
                // Same revocation rule as REST: a token from before a reset or logout is refused.
                if (userId != null && role != null
                        && tokenVersionService.isCurrent(role, userId, jwtUtil.extractTokenVersion(token))) {
                    return new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority(role)));
                }
            }
        }
        throw new MessagingException("Authentication required for live tracking");
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Matcher m = destination == null ? null : BOOKING_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessagingException("Subscription not allowed: " + destination);
        }
        if (!(accessor.getUser() instanceof Authentication auth) || !bookingAccessGuard.canAccess(auth, m.group(1))) {
            throw new MessagingException("Not authorized to track this booking");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            throw new MessagingException("Clients may only send to /app destinations");
        }
        if (!(accessor.getUser() instanceof Authentication)) {
            throw new MessagingException("Authentication required");
        }
    }
}
