package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Someone a passenger wants alerted when they report feeling unsafe on a trip.
 *
 * <p>Reachable on Telegram only after they open the bot from the passenger's invite link; until
 * then {@link #telegramChatId} is null and the passenger shares the trip link with them directly.
 */
@Entity
@Table(name = "emergency_contacts")
@Data
@NoArgsConstructor
public class EmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "telegram_chat_id", length = 32)
    private String telegramChatId;

    @Column(name = "telegram_linked_at")
    private LocalDateTime telegramLinkedAt;

    /** Same rules as the passenger's own link token: random, expiring, cleared once used. */
    @Column(name = "invite_token", length = 64)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
