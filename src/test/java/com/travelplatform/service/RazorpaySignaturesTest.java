package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expected values computed independently with {@code openssl dgst -sha256 -hmac}. */
class RazorpaySignaturesTest {

    @Test
    @DisplayName("checkout signature is HMAC-SHA256(secret, order_id|payment_id)")
    void checkoutSignatureMatchesOpenssl() {
        assertTrue(RazorpaySignatures.isValidCheckoutSignature("order_ABC123", "pay_XYZ789",
                "85cbc6036124891c4d0280fbb7cd83804f87a66f2eb485a89af574086f592cbc", "test_secret"));
    }

    @Test
    @DisplayName("checkout signature fails for a different payment, secret, or a blank signature")
    void checkoutSignatureRejectsTampering() {
        String sig = "85cbc6036124891c4d0280fbb7cd83804f87a66f2eb485a89af574086f592cbc";
        assertFalse(RazorpaySignatures.isValidCheckoutSignature("order_ABC123", "pay_OTHER", sig, "test_secret"));
        assertFalse(RazorpaySignatures.isValidCheckoutSignature("order_ABC123", "pay_XYZ789", sig, "wrong_secret"));
        assertFalse(RazorpaySignatures.isValidCheckoutSignature("order_ABC123", "pay_XYZ789", "", "test_secret"));
        assertFalse(RazorpaySignatures.isValidCheckoutSignature("order_ABC123", "pay_XYZ789", null, "test_secret"));
        assertFalse(RazorpaySignatures.isValidCheckoutSignature(null, "pay_XYZ789", sig, "test_secret"));
    }

    @Test
    @DisplayName("webhook signature is HMAC-SHA256 over the exact raw body")
    void webhookSignatureMatchesOpenssl() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String sig = "4673dd707ef4c41b987cb7fefe1583142dc702388c93145b7814b9ad3d3c183e";
        assertTrue(RazorpaySignatures.isValidWebhookSignature(body, sig, "whsec"));
        // One extra space - what re-serialising the JSON would do - must not verify.
        byte[] reformatted = "{\"event\": \"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(RazorpaySignatures.isValidWebhookSignature(reformatted, sig, "whsec"));
        assertFalse(RazorpaySignatures.isValidWebhookSignature(null, sig, "whsec"));
    }
}
