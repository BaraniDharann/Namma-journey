package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.entity.Payment;
import com.travelplatform.outbox.JobHandler;
import com.travelplatform.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Asks Razorpay to refund a cancelled prepaid trip. Queued by {@code UserService.deleteBooking}
 * in the cancellation's own transaction, so a refund is requested exactly when a cancellation
 * really happened, and keeps retrying if Razorpay is unreachable at that moment.
 *
 * <p>Safe to repeat: a payment that already has a refund id, or is no longer REFUND_PENDING, is
 * skipped, and a second request for the same payment comes back "already fully refunded",
 * which the gateway treats as done. The money is confirmed back by the refund.processed webhook.
 */
@Component
public class RefundJobHandler implements JobHandler {

    public static final String TYPE = "razorpay-refund";

    private static final Logger log = LoggerFactory.getLogger(RefundJobHandler.class);

    /** The job payload. */
    public record RefundRequest(UUID paymentId) {
    }

    private final PaymentRepository paymentRepository;
    private final RazorpayGateway gateway;
    private final ObjectMapper objectMapper;

    public RefundJobHandler(PaymentRepository paymentRepository, RazorpayGateway gateway, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(String payload) throws Exception {
        UUID paymentId = objectMapper.readValue(payload, RefundRequest.class).paymentId();
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != Payment.PaymentStatus.REFUND_PENDING
                || payment.getRazorpayRefundId() != null) {
            return;
        }
        if (payment.getRazorpayPaymentId() == null) {
            log.error("Payment {} is REFUND_PENDING but has no Razorpay payment id: refund it by hand", paymentId);
            return;
        }

        String refundId = gateway.refund(payment.getRazorpayPaymentId(),
                RazorpayPaymentService.toPaise(payment.getAmount()),
                Map.of("booking_id", payment.getBookingId().toString(), "reason", "cancelled before trip"));
        if (refundId != null) {
            payment.setRazorpayRefundId(refundId);
            paymentRepository.save(payment);
            log.info("Refund {} requested for payment {} (booking {})", refundId, paymentId, payment.getBookingId());
        }
    }
}
