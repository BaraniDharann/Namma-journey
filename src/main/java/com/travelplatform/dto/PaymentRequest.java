package com.travelplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {
    
    @NotBlank(message = "Payment method is required")
    private String paymentMethod; // UPI or CASH
    
    private String upiTransactionId; // Optional for UPI
}
