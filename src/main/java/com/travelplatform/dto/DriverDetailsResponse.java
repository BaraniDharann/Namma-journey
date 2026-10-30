package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverDetailsResponse {
    private Long driverId;
    private String name;
    private String mobile;
    private String email;
    private String photo;
    private String licenseNumber;
    private String licensePhoto;
    private String aadhaarNumber;
    private String aadhaarPhoto;
    private String status;
    private String role;
    private boolean emailVerified;
    private boolean firstLogin;
    /**
     * Whether this driver can receive dispatch pushes on Telegram.
     *
     * <p>Surfaced to the owner because an unlinked driver fails silently: bookings are still
     * assigned to them, they simply never get the alert. Without this flag on the drivers
     * list there is nothing to reveal that until a trip is missed.
     */
    private boolean telegramLinked;
    private java.time.LocalDateTime createdAt;
    private String vehicleType;
    private String vehicleNumber;
    private String vehicleModel;
    private Boolean vehicleAc;
    private Integer vehicleSeats;
    private Boolean servesCity;
    private Boolean servesTrip;
    /** OWNER_FLEET or INDEPENDENT, and where the owner's document check stands. */
    private String fleet;
    private String verificationStatus;
    private String rejectionReason;
    private String rcPhoto;
    private java.time.LocalDateTime submittedAt;
    /** Minutes until automatic approval (V24); null when the owner decides this one. */
    private Long autoApproveInMinutes;

    /** Copies the vehicle and City/Trip switches; shared by every mapper that shows a driver. */
    public DriverDetailsResponse withVehicle(com.travelplatform.entity.Driver driver) {
        this.vehicleType = driver.getVehicleType() == null ? null : driver.getVehicleType().name();
        this.vehicleNumber = driver.getVehicleNumber();
        this.vehicleModel = driver.getVehicleModel();
        this.vehicleAc = driver.isVehicleAc();
        this.vehicleSeats = driver.getVehicleSeats();
        this.servesCity = driver.isServesCity();
        this.servesTrip = driver.isServesTrip();
        return this;
    }

    /**
     * Copies fleet and verification. The RC scan is an identity document, so the caller passes the
     * signing it uses for the licence and Aadhaar links rather than this DTO knowing about storage.
     */
    public DriverDetailsResponse withVerification(com.travelplatform.entity.Driver driver,
                                                  java.util.function.UnaryOperator<String> sign) {
        this.fleet = driver.getFleet() == null ? null : driver.getFleet().name();
        this.verificationStatus = driver.getVerificationStatus() == null ? null : driver.getVerificationStatus().name();
        this.rejectionReason = driver.getRejectionReason();
        this.rcPhoto = sign.apply(driver.getRcPhoto());
        this.submittedAt = driver.getSubmittedAt();
        // Minutes, not a timestamp: the server clock's zone is not the owner's browser's.
        this.autoApproveInMinutes = driver.getAutoApproveAt() == null ? null
                : Math.max(0, java.time.Duration.between(java.time.LocalDateTime.now(), driver.getAutoApproveAt()).toMinutes());
        return this;
    }
}
