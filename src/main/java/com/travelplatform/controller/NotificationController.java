package com.travelplatform.controller;

import com.travelplatform.entity.Notification;
import com.travelplatform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Same JSON array as before, but only the latest {@code limit} (default 50, at most 100).
     * Older ones: pass the last row's createdAt as {@code before} (ISO date-time).
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam String recipientId,
            @RequestParam String role,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before) {
        assertOwnRecipient(recipientId, role);
        return ResponseEntity.ok(notificationService.getNotifications(recipientId, role, limit, before));
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
        notificationService.markAsRead(notificationId, currentPrincipal(), currentIsOwner());
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
     * Without this check any logged-in user could read another user's notifications by passing
     * their recipientId as a query param. Everyone - each travels owner included, since every owner
     * has their own feed (V27) - reads only the feed with their own id and their own role.
     */
    private void assertOwnRecipient(String recipientId, String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        if (!auth.getName().equals(recipientId)) {
            throw new AccessDeniedException("Cannot read another user's notifications");
        }
        // Ids come from different tables (users, drivers, owners), so the same id is not the same
        // person: the feed's role must be the caller's own.
        boolean ownRole = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equalsIgnoreCase(role));
        if (!ownRole) {
            throw new AccessDeniedException("Cannot read another user's notifications");
        }
    }

    private String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private boolean currentIsOwner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
    }
}
