package com.travelplatform.service.bus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.entity.BusPayment;
import com.travelplatform.entity.BusRefund;
import com.travelplatform.outbox.JobHandler;
import com.travelplatform.repository.BusPaymentRepository;
import com.travelplatform.repository.BusRefundRepository;
import com.travelplatform.service.RazorpayGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Asks Razorpay for one bus refund (full or partial) on the captured payment, like
 * RefundJobHandler does for car trips. Queued with the cancellation in its transaction, retried by
 * the outbox while Razorpay is unreachable.
 *
 * <p>Safe to repeat: a refund that already has a Razorpay refund id, or is no longer PENDING, is
 * skipped. "Already fully refunded" from Razorpay counts as done.
 */
@Component
public class BusRefundJobHandler implements JobHandler {

    public static final String TYPE = "bus-refund";

    private static final Logger log = LoggerFactory.getLogger(BusRefundJobHandler.class);

    /** The job payload. */
    public record RefundRequest(UUID refundId) {
    }

    private final BusRefundRepository refunds;
    private final BusPaymentRepository payments;
    private final RazorpayGateway gateway;
    private final ObjectMapper objectMapper;

    public BusRefundJobHandler(BusRefundRepository refunds, BusPaymentRepository payments, RazorpayGateway gateway,
                               ObjectMapper objectMapper) {
        this.refunds = refunds;
        this.payments = payments;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(String payload) throws Exception {
        UUID id = objectMapper.readValue(payload, RefundRequest.class).refundId();
        BusRefund refund = refunds.findById(id).orElse(null);
        if (refund == null || refund.getStatus() != BusRefund.Status.PENDING || refund.getRazorpayRefundId() != null) {
            return;
        }
        BusPayment payment = payments.findById(refund.getPaymentId()).orElse(null);
        if (payment == null || payment.getRazorpayPaymentId() == null) {
            log.error("Bus refund {} has no captured Razorpay payment: refund it by hand", id);
            return;
        }
        long paise = refund.getAmount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        String refundId = gateway.refund(payment.getRazorpayPaymentId(), paise,
                Map.of("bus_refund_id", id.toString(), "ticket_id", refund.getTicketId().toString(),
                        "reason", refund.getReason() == null ? "bus ticket" : refund.getReason()));
        if (refundId == null) {
            // Razorpay says nothing is left to refund on this payment: the money is already back.
            refund.setStatus(BusRefund.Status.PROCESSED);
            refund.setProcessedAt(LocalDateTime.now());
        } else {
            refund.setRazorpayRefundId(refundId);
        }
        refunds.save(refund);
        log.info("Bus refund {} requested from Razorpay ({})", id, refundId);
    }
}
