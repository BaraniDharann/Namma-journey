package com.travelplatform.dto;

import com.travelplatform.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {
    
    @NotNull(message = "Login type is required")
    private User.LoginType loginType;

    public User.LoginType getLoginType() {
        return loginType;
    }

    public void setLoginType(User.LoginType loginType) {
        this.loginType = loginType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    private String email;
    
    private String password;
    
    private String token;
    
    private String otp;
    
    private String name;
}
