package com.travelplatform.controller;

import com.travelplatform.entity.Notification;
import com.travelplatform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.ok(notificationService.getNotifications(recipientId, role));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestParam String recipientId,
            @RequestParam String role) {
        long count = notificationService.getUnreadCount(recipientId, role);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable UUID notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @RequestParam String recipientId,
            @RequestParam String role) {
        notificationService.markAllAsRead(recipientId, role);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
