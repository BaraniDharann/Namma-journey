package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "drivers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String mobile;
    
    @Column(unique = true)
    private String email;
    
    @Column(nullable = false)
    private String password;

    /** Bumped on password reset and logout; tokens carrying an older value are refused. */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0;
    
    @Column(nullable = false)
    private boolean firstLogin = true;
    
    @Column(nullable = false)
    private boolean emailVerified = false;
    
    @Column(nullable = false, unique = true)
    private String licenseNumber;
    
    @Column(nullable = false, unique = true)
    private String aadhaarNumber;
    
    @Column(length = 1000)
    private String photo;
    
    @Column(length = 1000)
    private String licensePhoto;
    
    @Column(length = 1000)
    private String aadhaarPhoto;
    
    @Column(nullable = false)
    private String role = "ROLE_DRIVER";

    /**
     * Telegram chat id for dispatch pushes, or null while the driver has not opened the bot.
     *
     * <p>Telegram bots cannot initiate a conversation: until the driver taps the /start deep
     * link there is no id to send to and this channel is silently unavailable for them. Treat
     * null as "not reachable by Telegram", never as an error.
     */
    @Column(name = "telegram_chat_id", length = 32)
    private String telegramChatId;

    @Column(name = "telegram_linked_at")
    private LocalDateTime telegramLinkedAt;

    /**
     * Single-use secret that binds the next Telegram account to present it to this driver.
     *
     * <p>Whoever redeems this token gains the ability to accept and reject this driver's
     * trips, so it is a credential and is treated like one: random, expiring, and cleared on
     * redemption. It deliberately is not derived from {@link #id}, which is sequential and
     * therefore guessable.
     */
    @Column(name = "telegram_link_token", length = 64)
    private String telegramLinkToken;

    @Column(name = "telegram_link_token_expires_at")
    private LocalDateTime telegramLinkTokenExpiresAt;

    /** The one vehicle this driver drives. Bikes and autos are City only (drivers_trip_needs_car). */
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 8)
    private VehicleType vehicleType = VehicleType.CAR;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "vehicle_model", length = 60)
    private String vehicleModel;

    @Column(name = "vehicle_ac", nullable = false)
    private boolean vehicleAc = true;

    @Column(name = "vehicle_seats", nullable = false)
    private int vehicleSeats = 4;

    /** Takes City rides (offered by nearness, right now). */
    @Column(name = "serves_city", nullable = false)
    private boolean servesCity = true;

    /** Takes Trips (assigned by date). Cars only. */
    @Column(name = "serves_trip", nullable = false)
    private boolean servesTrip = true;

    /**
     * Where an independent driver is paid their earnings. Encrypted at rest (V21); never
     * serialised with the entity - only the driver and the owner's payout screen read it.
     */
    @Convert(converter = com.travelplatform.security.EncryptedStringConverter.class)
    @Column(name = "payout_upi_id", length = 512)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String payoutUpiId;

    /** Existing and owner-created drivers are OWNER_FLEET; self-signups are INDEPENDENT and never take Trips (drivers_independent_no_trips). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Fleet fleet = Fleet.OWNER_FLEET;

    /** Owner-created drivers need no check, so the default is APPROVED; self-signups start UNDER_REVIEW. */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 12)
    private Verification verificationStatus = Verification.APPROVED;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Registration certificate scan; sensitive, so served by signed link only. */
    @Column(name = "rc_photo", length = 1000)
    private String rcPhoto;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** Id of the owner who approved or rejected. */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /**
     * When this self-signup is approved automatically if the owner has not rejected or held it
     * (V24). Null means the owner decides. Cleared by every decision, so a later re-upload is
     * never approved on an old deadline.
     */
    @Column(name = "auto_approve_at")
    private LocalDateTime autoApproveAt;

    public boolean isApproved() {
        return verificationStatus == Verification.APPROVED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }

    public void setFirstLogin(boolean firstLogin) {
        this.firstLogin = firstLogin;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getAadhaarNumber() {
        return aadhaarNumber;
    }

    public void setAadhaarNumber(String aadhaarNumber) {
        this.aadhaarNumber = aadhaarNumber;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public String getLicensePhoto() {
        return licensePhoto;
    }

    public void setLicensePhoto(String licensePhoto) {
        this.licensePhoto = licensePhoto;
    }

    public String getAadhaarPhoto() {
        return aadhaarPhoto;
    }

    public void setAadhaarPhoto(String aadhaarPhoto) {
        this.aadhaarPhoto = aadhaarPhoto;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public LocalDateTime getTelegramLinkedAt() {
        return telegramLinkedAt;
    }

    public void setTelegramLinkedAt(LocalDateTime telegramLinkedAt) {
        this.telegramLinkedAt = telegramLinkedAt;
    }

    public String getTelegramLinkToken() {
        return telegramLinkToken;
    }

    public void setTelegramLinkToken(String telegramLinkToken) {
        this.telegramLinkToken = telegramLinkToken;
    }

    public LocalDateTime getTelegramLinkTokenExpiresAt() {
        return telegramLinkTokenExpiresAt;
    }

    public void setTelegramLinkTokenExpiresAt(LocalDateTime telegramLinkTokenExpiresAt) {
        this.telegramLinkTokenExpiresAt = telegramLinkTokenExpiresAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum Status {
        ACTIVE, INACTIVE
    }

    /** Whose driver this is: the owner's own fleet, or someone who signed up themselves. */
    public enum Fleet {
        OWNER_FLEET, INDEPENDENT
    }

    /** Where a driver stands with the owner's document check. */
    public enum Verification {
        UNDER_REVIEW, APPROVED, REJECTED
    }
}
