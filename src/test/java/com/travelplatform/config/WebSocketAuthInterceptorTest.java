package com.travelplatform.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Live tracking carries every passenger's real-time position. The broker accepts wildcard
 * subscriptions and lets clients SEND straight to /topic, so the guard has to be an allow-list:
 * authenticated connections only, exact per-booking subscriptions only, client sends to /app only.
 */
class WebSocketAuthInterceptorTest {

    private static final String BOOKING = "3f1c2a4e-1111-4222-8333-444455556666";
    private static final String TOPIC = "/topic/booking/" + BOOKING + "/location";

    private final MessageChannel channel = mock(MessageChannel.class);
    private BookingAccessGuard guard;
    private WebSocketAuthInterceptor interceptor;
    private final Authentication passenger = new UsernamePasswordAuthenticationToken(
            "u-1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

    @BeforeEach
    void setUp() {
        JwtUtil jwt = mock(JwtUtil.class);
        when(jwt.validateToken("good")).thenReturn(true);
        when(jwt.extractUserId("good")).thenReturn("u-1");
        when(jwt.extractRole("good")).thenReturn("ROLE_USER");
        guard = mock(BookingAccessGuard.class);
        interceptor = new WebSocketAuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtUtil", jwt);
        ReflectionTestUtils.setField(interceptor, "bookingAccessGuard", guard);
    }

    private static Message<byte[]> frame(StompCommand command, String destination, Authentication user, String bearer) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        if (bearer != null) {
            accessor.setNativeHeader("Authorization", "Bearer " + bearer);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("a connection without a valid token is refused")
    void anonymousConnectIsRefused() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel));
        assertThrows(MessagingException.class, () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, "forged"), channel));
        assertNotNull(interceptor.preSend(frame(StompCommand.CONNECT, null, null, "good"), channel));
    }

    @Test
    @DisplayName("wildcard and non-booking subscriptions are refused - even for an owner")
    void wildcardSubscriptionsAreRefused() {
        Authentication owner = new UsernamePasswordAuthenticationToken("1", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(guard.canAccess(any(Authentication.class), any())).thenReturn(true);
        for (String dest : new String[] {"/topic/**", "/topic/*/*/location", "/topic/booking/**",
                "/topic/booking/" + BOOKING + "/**", "/topic/booking/" + BOOKING, "/topic/other", "/user/queue/x"}) {
            assertThrows(MessagingException.class,
                    () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, dest, owner, null), channel), dest);
        }
    }

    @Test
    @DisplayName("an exact booking subscription is allowed only to someone the guard lets in")
    void exactSubscriptionFollowsTheGuard() {
        when(guard.canAccess(eq(passenger), eq(BOOKING))).thenReturn(true);
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, TOPIC, passenger, null), channel));

        when(guard.canAccess(eq(passenger), eq(BOOKING))).thenReturn(false);
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, TOPIC, passenger, null), channel));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, TOPIC, null, null), channel));
    }

    @Test
    @DisplayName("clients may only SEND to /app - never straight onto a broker topic")
    void clientsCannotPublishToTopics() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, TOPIC, passenger, null), channel));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/topic/anything", passenger, null), channel));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, "/app/location.update", null, null), channel));
        assertDoesNotThrow(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/location.update", passenger, null), channel));
    }
}
