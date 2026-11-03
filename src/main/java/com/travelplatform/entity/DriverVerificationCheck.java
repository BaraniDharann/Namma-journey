package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One item of an owner's checklist for a driver they added, checked by hand on a free official
 * source: the licence on Sarathi, the RC on Vahan / mParivahan. The owner can approve the driver
 * only once both are VERIFIED.
 */
@Entity
@Table(name = "driver_verification_checks")
@IdClass(DriverVerificationCheck.Key.class)
@Data
@NoArgsConstructor
public class DriverVerificationCheck {

    public enum Type { LICENCE, RC }

    public enum Result { VERIFIED, FAILED }

    @Id
    @Column(name = "driver_id")
    private Long driverId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Result result;

    @Column(length = 500)
    private String note;

    @Column(name = "checked_by_owner_id")
    private Long checkedByOwnerId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Data
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long driverId;
        private Type type;

        public Key(Long driverId, Type type) {
            this.driverId = driverId;
            this.type = type;
        }
    }
}
