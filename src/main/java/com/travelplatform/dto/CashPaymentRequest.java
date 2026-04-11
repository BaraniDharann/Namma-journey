package com.travelplatform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CashPaymentRequest {
    
    @NotNull(message = "Amount received is required")
    @Positive(message = "Amount must be positive")
    private Double amountReceived;
}
