package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private String message;
    private Object userId;
    private String name;
    private String email;
    private String mobile;
    /** False for a Google-only traveller, so the app can offer to save a password. */
    private Boolean hasPassword;
    
    public AuthResponse(String token, String role, Object userId) {
        this.token = token;
        this.role = role;
        this.userId = userId;
        this.message = "Authentication successful";
    }
}
