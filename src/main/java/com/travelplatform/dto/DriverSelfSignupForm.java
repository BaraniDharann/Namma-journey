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
    /** Where they drive (V38): a listed town's id, or a town found on the map with its coordinates. */
    private Long homePlaceId;
    private String homeState;
    private String homeDistrict;
    private String homeTown;
    private Double homeLat;
    private Double homeLon;
}
