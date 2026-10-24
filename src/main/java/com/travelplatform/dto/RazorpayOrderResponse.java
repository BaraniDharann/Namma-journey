package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Everything the browser needs to open Razorpay Checkout. The key id is public by design
 * (Razorpay's checkout script requires it); the key secret never leaves the server.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayOrderResponse {
    private UUID paymentId;
    private UUID bookingId;
    private String keyId;
    private String orderId;
    /** In rupees, for display. */
    private BigDecimal amount;
    /** In paise, exactly what Razorpay will charge. */
    private long amountPaise;
    private String currency;
    private String merchantName;
    /** ADVANCE (the 20 % that holds a Trip) or FARE (the fare, or its balance). */
    private String purpose;
    private String description;
}
