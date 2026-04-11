package com.travelplatform.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "package_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private Long packageId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String userPhone;

    @Column(nullable = false)
    private Integer numberOfPersons;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Column
    private String specialRequests;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime bookingDate;

    @Column
    private LocalDateTime confirmedAt;

    @Column
    private LocalDateTime cancelledAt;

    @Column
    private String cancellationReason;

    // Store package snapshot info at booking time
    @Column(nullable = false)
    private String packageName;

    @Column
    private String packageCategory;

    @Column
    private Integer durationDays;

    @Column
    private Integer durationNights;

    @Column
    private Double pricePerPerson;

    public enum BookingStatus {
        PENDING,
        CONFIRMED,
        CANCELLED,
        COMPLETED
    }

    @PrePersist
    public void prePersist() {
        this.bookingDate = LocalDateTime.now();
        if (this.status == null) this.status = BookingStatus.PENDING;
    }
}
