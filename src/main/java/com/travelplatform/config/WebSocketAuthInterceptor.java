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

import java.security.Principal;
import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BOOKING_TOPIC_PREFIX = "/topic/booking/";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BookingAccessGuard bookingAccessGuard;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        // 1. Authenticate the connection and attach the principal for later frames.
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.validateToken(token)) {
                    String userId = jwtUtil.extractUserId(token);
                    String role = jwtUtil.extractRole(token);
                    if (userId != null && role != null) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userId, null, List.of(new SimpleGrantedAuthority(role)));
                        accessor.setUser(auth);
                    }
                }
            }
        }

        // 2. Authorize subscriptions to a booking's location topic: only the trip's
        //    customer, its assigned driver, or an owner may listen in.
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(BOOKING_TOPIC_PREFIX)) {
                Principal user = accessor.getUser();
                String bookingId = extractBookingId(destination);
                if (!(user instanceof Authentication auth) || !bookingAccessGuard.canAccess(auth, bookingId)) {
                    throw new MessagingException("Not authorized to track this booking");
                }
            }
        }

        return message;
    }

    // "/topic/booking/{bookingId}/location" -> "{bookingId}"
    private String extractBookingId(String destination) {
        String rest = destination.substring(BOOKING_TOPIC_PREFIX.length());
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }
}
