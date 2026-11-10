package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A car in a travels owner's fleet. Trips are booked by {@link CarCategory}; the owner (or the
 * automatic assignment) puts one of their drivers and one of these cars on each Trip. A plate the
 * data move (V27) could not find is stored as {@code TEMP-<driver id>} until the owner fixes it.
 */
@Entity
@Table(name = "vehicles")
@Data
@NoArgsConstructor
public class Vehicle {

    public static final String PLACEHOLDER_PREFIX = "TEMP-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarCategory category;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(length = 60)
    private String model;

    @Column(nullable = false)
    private int seats;

    @Column(nullable = false)
    private boolean ac = true;

    /** Registration certificate scan; sensitive, so served by signed link only. */
    @Column(name = "rc_photo", length = 1000)
    private String rcPhoto;

    @Column(nullable = false)
    private boolean active = true;

    /** What the owner uses this car for (V35). A new car does Travel trips. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Mode mode = Mode.TRAVEL;

    /** The owner's driver who drives this car on City rides; set exactly when {@link #mode} is CITY. */
    @Column(name = "city_driver_id")
    private Long cityDriverId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isRegistrationMissing() {
        return registrationNumber != null && registrationNumber.startsWith(PLACEHOLDER_PREFIX);
    }

    /**
     * CITY: offered nearby City rides through its paired driver. TRAVEL: picked for Travel
     * (outstation) trips. OFF: neither. The owner can still put any free car on a Travel booking by hand.
     */
    public enum Mode {
        CITY, TRAVEL, OFF;

        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Mode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
