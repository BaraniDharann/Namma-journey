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
    /** True for a travels owner still on the emailed one-time password; the token only allows changing it. */
    private Boolean mustChangePassword;
    /** A travels owner's Owner ID, e.g. NJO-0002. */
    private String ownerCode;
    
    public AuthResponse(String token, String role, Object userId) {
        this.token = token;
        this.role = role;
        this.userId = userId;
        this.message = "Authentication successful";
    }
}
