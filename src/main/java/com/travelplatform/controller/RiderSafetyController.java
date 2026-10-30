package com.travelplatform.controller;

import com.travelplatform.service.EmergencyContactService;
import com.travelplatform.service.RideSafetyService;
import com.travelplatform.service.RiderTelegramLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The passenger's side of ride safety: connecting Telegram, emergency contacts, and - on a
 * trip - answering the safety check and pressing SOS.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class RiderSafetyController {

    // Same guard as UserController: the JWT subject must be the {userId} in the path.
    private static final String SAME_USER = "hasRole('USER') and #userId.toString() == authentication.principal";

    private final RiderTelegramLinkService linkService;
    private final EmergencyContactService contactService;
    private final RideSafetyService safetyService;

    /** {@code available} is false when the server has no bot configured; the app then hides Connect. */
    @GetMapping("/{userId}/telegram")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<Map<String, Object>> telegramStatus(@PathVariable UUID userId) {
        return ResponseEntity.ok(Map.of(
                "available", linkService.available(),
                "linked", linkService.isUserLinked(userId)));
    }

    @PostMapping("/{userId}/telegram-link")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<Map<String, String>> telegramLink(@PathVariable UUID userId) {
        return ResponseEntity.ok(Map.of("linkUrl", linkService.createUserLinkUrl(userId)));
    }

    @GetMapping("/{userId}/emergency-contacts")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<List<EmergencyContactService.ContactView>> contacts(@PathVariable UUID userId) {
        return ResponseEntity.ok(contactService.list(userId));
    }

    @PostMapping("/{userId}/emergency-contacts")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<EmergencyContactService.Created> addContact(@PathVariable UUID userId,
                                                                      @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(contactService.add(userId, body.get("name"), body.get("phone")));
    }

    /** A fresh invite link for a contact who lost or never opened the first one. */
    @PostMapping("/{userId}/emergency-contacts/{contactId}/invite")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<Map<String, String>> inviteContact(@PathVariable UUID userId, @PathVariable Long contactId) {
        return ResponseEntity.ok(Map.of("linkUrl", contactService.invite(userId, contactId)));
    }

    @DeleteMapping("/{userId}/emergency-contacts/{contactId}")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<Void> removeContact(@PathVariable UUID userId, @PathVariable Long contactId) {
        contactService.remove(userId, contactId);
        return ResponseEntity.noContent().build();
    }

    /** Polled by the trip card while a trip is live: pending question, SOS state, share link. */
    @GetMapping("/{userId}/bookings/{bookingId}/safety")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<RideSafetyService.SafetyView> safety(@PathVariable UUID userId, @PathVariable UUID bookingId) {
        return ResponseEntity.ok(safetyService.view(userId, bookingId));
    }

    @PostMapping("/{userId}/bookings/{bookingId}/safety/answer")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<Map<String, Object>> answer(@PathVariable UUID userId, @PathVariable UUID bookingId,
                                                      @RequestBody Map<String, Boolean> body) {
        boolean safe = Boolean.TRUE.equals(body.get("safe"));
        RideSafetyService.Answer result = safetyService.answerInApp(userId, bookingId, safe);
        Map<String, Object> response = new HashMap<>();
        response.put("result", result.name());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/bookings/{bookingId}/sos")
    @PreAuthorize(SAME_USER)
    public ResponseEntity<RideSafetyService.SafetyView> sos(@PathVariable UUID userId, @PathVariable UUID bookingId) {
        safetyService.sos(userId, bookingId);
        return ResponseEntity.ok(safetyService.view(userId, bookingId));
    }
}
