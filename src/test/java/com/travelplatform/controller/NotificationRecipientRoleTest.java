package com.travelplatform.controller;

import com.travelplatform.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Admins, owners on a setup token and drivers all have numeric ids from different tables, so
 * "same id" is not "same person": the role asked for must be the caller's own.
 */
class NotificationRecipientRoleTest {

    private final NotificationController controller = new NotificationController(mock(NotificationService.class));

    private static void signIn(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("admin 7 cannot read driver 7's notifications")
    void adminCannotReadDriverWithSameId() {
        signIn("7", "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> controller.getNotifications("7", "ROLE_DRIVER"));
        assertThrows(AccessDeniedException.class, () -> controller.getUnreadCount("7", "ROLE_DRIVER"));
    }

    @Test
    @DisplayName("an owner on a setup token cannot read driver 7's notifications")
    void setupOwnerCannotReadDriverWithSameId() {
        signIn("7", "ROLE_OWNER_SETUP");
        assertThrows(AccessDeniedException.class, () -> controller.getNotifications("7", "ROLE_DRIVER"));
    }

    @Test
    @DisplayName("a driver still reads their own")
    void driverReadsOwn() {
        signIn("7", "ROLE_DRIVER");
        assertDoesNotThrow(() -> controller.getNotifications("7", "ROLE_DRIVER"));
    }
}
