package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverCreationResponse {
    private Long id;
    private String name;
    private String email;
    private String mobile;
    private String message;
}
