package com.travelplatform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * What the UIDAI Secure QR on an owner's Aadhaar upload said, and whether UIDAI's signature held
 * ({@code AadhaarSecureQr.Status}). The admin compares this - not the printed card - with the
 * application.
 */
@Entity
@Table(name = "owner_aadhaar_qr")
@Data
@NoArgsConstructor
public class OwnerAadhaarQr {

    @Id
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(nullable = false, length = 30)
    private String status;

    private String name;

    @Column(length = 20)
    private String dob;

    @Column(length = 10)
    private String gender;

    @Column(length = 100)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(length = 4)
    private String last4;

    /** The QR's photo, converted from JPEG 2000 to JPEG so a browser can show it. */
    @Column(name = "photo_jpeg")
    @JsonIgnore
    @ToString.Exclude
    private byte[] photoJpeg;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
