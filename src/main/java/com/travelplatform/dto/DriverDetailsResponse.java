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
}
