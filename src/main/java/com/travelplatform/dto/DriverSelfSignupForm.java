package com.travelplatform.dto;

import lombok.Data;

/** The text fields of the public driver sign-up form; the four document images arrive as file parts. */
@Data
public class DriverSelfSignupForm {
    private String name;
    private String mobile;
    private String email;
    private String password;
    private String licenseNumber;
    private String aadhaarNumber;
    private String vehicleType;
    private String vehicleNumber;
    private String vehicleModel;
    private Boolean vehicleAc;
    private Integer vehicleSeats;
}
