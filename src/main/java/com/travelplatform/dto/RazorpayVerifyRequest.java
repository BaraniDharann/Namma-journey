package com.travelplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** The three values Razorpay Checkout hands the browser after a successful payment. */
@Data
public class RazorpayVerifyRequest {
    @NotBlank
    @Size(max = 64)
    private String razorpayOrderId;

    @NotBlank
    @Size(max = 64)
    private String razorpayPaymentId;

    @NotBlank
    @Size(max = 128)
    private String razorpaySignature;
}
