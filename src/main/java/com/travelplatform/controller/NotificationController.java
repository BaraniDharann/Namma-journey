package com.travelplatform.controller;

import com.travelplatform.entity.Notification;
import com.travelplatform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam String recipientId,
            @RequestParam String role) {
        assertOwnRecipient(recipientId, role);
        return ResponseEntity.ok(notificationService.getNotifications(recipientId, role));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestParam String recipientId,
            @RequestParam String role) {
        assertOwnRecipient(recipientId, role);
        long count = notificationService.getUnreadCount(recipientId, role);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable UUID notificationId) {
        // Ownership is checked inside the service via the JWT principal so a user can't
        // mark someone else's notification as read.
        notificationService.markAsRead(notificationId, currentPrincipal());
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestParam String recipientId,
            @RequestParam String role) {
        assertOwnRecipient(recipientId, role);
        notificationService.markAllAsRead(recipientId, role);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    /**
     * Without this check any logged-in user could read another user's notifications
     * by passing their recipientId as a query param. Owner notifications use the
     * literal "owner" bucket and require the OWNER role.
     */
    private void assertOwnRecipient(String recipientId, String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        boolean isOwner = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
        if ("owner".equalsIgnoreCase(recipientId) || "ROLE_OWNER".equalsIgnoreCase(role)) {
            if (!isOwner) {
                throw new AccessDeniedException("Owner role required");
            }
            return;
        }
        // Owners can also read any user's bucket (admin view); regular users/drivers must match.
        if (!isOwner && !auth.getName().equals(recipientId)) {
            throw new AccessDeniedException("Cannot read another user's notifications");
        }
    }

    private String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
