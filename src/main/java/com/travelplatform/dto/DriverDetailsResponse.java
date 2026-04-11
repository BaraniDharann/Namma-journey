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
    private java.time.LocalDateTime createdAt;
}
