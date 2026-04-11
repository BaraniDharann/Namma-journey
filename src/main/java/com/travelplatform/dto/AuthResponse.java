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
    
    public AuthResponse(String token, String role, Object userId) {
        this.token = token;
        this.role = role;
        this.userId = userId;
        this.message = "Authentication successful";
    }
}
