package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LoginType getLoginType() {
        return loginType;
    }

    public void setLoginType(LoginType loginType) {
        this.loginType = loginType;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(unique = true)
    private String phone;
    
    @Column
    private String password;

    /** Bumped on password reset and logout; tokens carrying an older value are refused. */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginType loginType;
    
    @Column(nullable = false)
    private String role = "ROLE_USER";

    /**
     * Telegram chat for ride updates and safety checks, or null until the passenger opens the
     * bot from their personal link. Optional for passengers (unlike drivers): null only means
     * they get in-app alerts alone. See V23.
     */
    @Column(name = "telegram_chat_id", length = 32)
    private String telegramChatId;

    @Column(name = "telegram_linked_at")
    private LocalDateTime telegramLinkedAt;

    /** Single-use, expiring credential behind the passenger's connect link; cleared on redemption. */
    @Column(name = "telegram_link_token", length = 64)
    private String telegramLinkToken;

    @Column(name = "telegram_link_token_expires_at")
    private LocalDateTime telegramLinkTokenExpiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum LoginType {
        GOOGLE, EMAIL
    }
}
