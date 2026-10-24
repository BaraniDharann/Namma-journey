package com.travelplatform.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The two HMAC checks Razorpay defines. Both are HMAC-SHA256, hex encoded.
 *
 * <ul>
 *   <li><b>Checkout</b>: {@code HMAC(key_secret, order_id + "|" + payment_id)}. The browser hands
 *       us the signature after the popup closes; only Razorpay and this server know the key
 *       secret, so a match proves Razorpay settled <em>that</em> payment against <em>that</em>
 *       order. The order's amount was fixed server-side, so the amount is proven too.</li>
 *   <li><b>Webhook</b>: {@code HMAC(webhook_secret, raw request body)}. Computed over the exact
 *       bytes received - re-serialising parsed JSON changes whitespace and key order and would
 *       never match.</li>
 * </ul>
 *
 * Comparisons are constant-time so a forged signature cannot be refined by timing responses.
 */
public final class RazorpaySignatures {

    private RazorpaySignatures() {
    }

    public static boolean isValidCheckoutSignature(String orderId, String paymentId, String signature, String keySecret) {
        if (orderId == null || paymentId == null) {
            return false;
        }
        return matches(hmacSha256Hex(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)), signature);
    }

    public static boolean isValidWebhookSignature(byte[] rawBody, String signature, String webhookSecret) {
        if (rawBody == null) {
            return false;
        }
        return matches(hmacSha256Hex(webhookSecret, rawBody), signature);
    }

    static String hmacSha256Hex(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean matches(String expected, String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
