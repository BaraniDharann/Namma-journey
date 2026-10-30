package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The safety monitor's state for one started trip, from Start Trip until the driver ends it.
 *
 * <p>{@code @DynamicUpdate} because two writers touch it at once: the scheduled monitor (next
 * check, SOS) and the location feed (last point, live-map edits). They write different columns,
 * so updating only the changed ones keeps one from overwriting the other's with a stale copy.
 */
@Entity
@Table(name = "ride_safety_sessions")
@DynamicUpdate
@Data
@NoArgsConstructor
public class RideSafetySession {

    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Credential behind the public share-trip link; random so the link cannot be guessed. */
    @Column(name = "share_token", nullable = false, length = 64)
    private String shareToken;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "next_check_at", nullable = false)
    private LocalDateTime nextCheckAt;

    @Column(name = "last_check_at")
    private LocalDateTime lastCheckAt;

    /** When a point was last written to the trail; throttles the trail to one row per interval. */
    @Column(name = "last_point_at")
    private LocalDateTime lastPointAt;

    @Column(name = "user_live_message_id")
    private Long userLiveMessageId;

    @Column(name = "ops_live_message_id")
    private Long opsLiveMessageId;

    @Column(name = "sos_at")
    private LocalDateTime sosAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public boolean isActive() {
        return endedAt == null;
    }
}
