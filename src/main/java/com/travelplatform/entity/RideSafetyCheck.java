package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One "are you safe?" question put to a passenger mid-trip, and their answer. */
@Entity
@Table(name = "ride_safety_checks")
@Data
@NoArgsConstructor
public class RideSafetyCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Reason reason;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Response response;

    /** The Telegram message carrying the buttons, so it can be rewritten once answered. */
    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    public boolean isOpen() {
        return response == null;
    }

    public enum Reason {
        /** The regular check-in while the trip is running. */
        SCHEDULED,
        /** The car has not moved for several minutes away from the drop. */
        STOPPED,
        /** The driver's phone stopped sending positions. */
        GPS_SILENT,
        /** The trip is taking much longer than its estimate. */
        OVERDUE
    }

    public enum Response {
        SAFE,
        UNSAFE,
        /** Asked twice, never answered; escalated to the owner. */
        NO_RESPONSE
    }
}
