package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * One item of the admin's checklist for an owner application, checked by hand on a free official
 * source (Income Tax "Verify Your PAN", UIDAI Secure QR, GST/Udyam, RBI IFSC). An owner can be
 * approved only once all four are VERIFIED.
 */
@Entity
@Table(name = "owner_verification_checks")
@IdClass(OwnerVerificationCheck.Key.class)
@Data
@NoArgsConstructor
public class OwnerVerificationCheck {

    public enum Type { PAN, AADHAAR, REGISTRATION_DOC, BANK }

    public enum Result { VERIFIED, FAILED }

    @Id
    @Column(name = "owner_id")
    private Long ownerId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", length = 30)
    private Type checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Result result;

    @Column(length = 500)
    private String note;

    @Column(name = "checked_by_admin_id", nullable = false)
    private Long checkedByAdminId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Data
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long ownerId;
        private Type checkType;

        public Key(Long ownerId, Type checkType) {
            this.ownerId = ownerId;
            this.checkType = checkType;
        }
    }
}
