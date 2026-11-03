package com.travelplatform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.travelplatform.security.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A travels owner. Many of them since V26: each signs up with their business and KYC details,
 * waits as PENDING_REVIEW until a NammaJourney admin approves them, and only then gets an
 * Owner ID ({@link #ownerCode}, e.g. NJO-0002) and a password.
 *
 * <p>PAN, bank account and UPI are encrypted at rest; only the last 4 digits of the Aadhaar
 * number are kept.
 */
@Entity
@Table(name = "owners")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Null until the admin approves the application and a password is generated. */
    @JsonIgnore
    private String password;

    /** Bumped on password reset and logout; tokens carrying an older value are refused. */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0;

    @Column(nullable = false)
    private String role = "ROLE_OWNER";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "owner_code", length = 20, unique = true)
    private String ownerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OwnerStatus status = OwnerStatus.PENDING_REVIEW;

    /** How this owner's Trips get a driver (V27): automatically, or by the owner from the Bookings screen. */
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_mode", nullable = false, length = 8)
    private AssignmentMode assignmentMode = AssignmentMode.MANUAL;

    public enum AssignmentMode { AUTO, MANUAL }

    @Column(name = "travels_name")
    private String travelsName;

    @Column(name = "contact_name")
    private String contactName;

    @Column(length = 20)
    private String phone;

    @ManyToMany
    @JoinTable(name = "owner_cities",
            joinColumns = @JoinColumn(name = "owner_id"),
            inverseJoinColumns = @JoinColumn(name = "city_id"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<City> cities = new HashSet<>();

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pan_number")
    @JsonIgnore
    private String panNumber;

    @Column(name = "pan_hash", length = 64)
    @JsonIgnore
    private String panHash;

    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;

    @Column(name = "aadhaar_consent_at")
    private LocalDateTime aadhaarConsentAt;

    @Column(name = "registration_doc_path", length = 512)
    @JsonIgnore
    private String registrationDocPath;

    @Column(name = "pan_doc_path", length = 512)
    @JsonIgnore
    private String panDocPath;

    @Column(name = "aadhaar_doc_path", length = 512)
    @JsonIgnore
    private String aadhaarDocPath;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bank_account_number")
    @JsonIgnore
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 11)
    private String bankIfsc;

    @Column(name = "bank_name")
    private String bankName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "upi_id")
    @JsonIgnore
    private String upiId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    /** Set when the 36-hour reminder went to the admin, so it goes once. */
    @Column(name = "admin_reminded_at")
    private LocalDateTime adminRemindedAt;

    /** True from approval (a generated password) until the owner chooses their own. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "resubmit_token_hash", length = 64)
    @JsonIgnore
    private String resubmitTokenHash;

    @Column(name = "resubmit_token_expires_at")
    @JsonIgnore
    private LocalDateTime resubmitTokenExpiresAt;

    /** Two admins approving at once: the second save fails instead of minting a second Owner ID. */
    @Version
    @Column(nullable = false)
    private long version;
}
