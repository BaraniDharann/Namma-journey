package com.travelplatform.controller;

import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.service.RazorpayPaymentService;
import com.travelplatform.service.RazorpaySignatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Razorpay's server-to-server payment events.
 *
 * <p>Public for the same reason as the Telegram webhook: Razorpay cannot present a JWT. Every
 * call is authenticated instead by {@code X-Razorpay-Signature}, an HMAC of the raw body under
 * the webhook secret set in the Razorpay dashboard. The body is taken as bytes because the HMAC
 * is over exactly what was sent; parsing first would change it.
 */
@RestController
@RequestMapping("/api/payments/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

    private final RazorpayPaymentService razorpayPaymentService;
    private final RazorpayProperties properties;

    public RazorpayWebhookController(RazorpayPaymentService razorpayPaymentService,
                                     RazorpayProperties properties) {
        this.razorpayPaymentService = razorpayPaymentService;
        this.properties = properties;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        // Fail closed: without the secret nobody can be told apart from Razorpay.
        if (!properties.isWebhookVerifiable()) {
            log.error("Razorpay webhook called but razorpay.webhook-secret is not set; refusing");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!RazorpaySignatures.isValidWebhookSignature(body, signature, properties.getWebhookSecret())) {
            log.warn("Razorpay webhook with a missing or incorrect signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // A failure here (database down, lock conflict) propagates as a 5xx on purpose: Razorpay
        // then re-delivers, and the next attempt settles the payment.
        razorpayPaymentService.handleWebhook(body);
        return ResponseEntity.ok().build();
    }
}
