package com.travelplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID paymentId;
    private UUID bookingId;
    private Double amount;
    private String paymentMethod;
    private String status;
    private String upiQrCode;       // base64 encoded PNG QR code image
    private String upiDeepLink;     // fallback deep link
    private String upiTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime upiLinkExpiresAt;
    private boolean upiLinkExpired;
    private String message;
}
