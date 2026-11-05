package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A bus in a travels owner's fleet (V30). The owner fills it in as a DRAFT (details, seat layout,
 * documents, photos) and submits it; an admin approves or rejects it. Only an APPROVED bus runs
 * trips. Plates are stored in capitals without spaces, unique across owners.
 */
@Entity
@Table(name = "buses")
@Data
@NoArgsConstructor
public class Bus {

    public enum Status { DRAFT, PENDING, APPROVED, REJECTED }

    /** Stage carriage may sell seat by seat; contract carriage and AITP permits are a legal risk in TN. */
    public enum PermitType { STAGE_CARRIAGE, CONTRACT_CARRIAGE, ALL_INDIA_TOURIST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean ac = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "permit_type", nullable = false, length = 20)
    private PermitType permitType;

    /** Comma-separated list, e.g. "Water bottle,Charging point". */
    @Column(length = 500)
    private String amenities;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
