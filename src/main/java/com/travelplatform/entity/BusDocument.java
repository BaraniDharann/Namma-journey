package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One of a bus's four papers (V30): registration certificate, permit, insurance, fitness
 * certificate. One of each per bus; uploading again replaces it. A bus runs a trip only on dates
 * all four are still valid.
 */
@Entity
@Table(name = "bus_documents")
@IdClass(BusDocument.Key.class)
@Data
@NoArgsConstructor
public class BusDocument {

    public enum Type { RC, PERMIT, INSURANCE, FITNESS }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long busId;
        private Type type;
    }

    @Id
    @Column(name = "bus_id")
    private Long busId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Type type;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Served by signed link only (SignedUploadUrls). */
    @Column(nullable = false, length = 1000)
    private String path;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
