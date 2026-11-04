package com.travelplatform.service.bus;

import com.travelplatform.entity.BusPayment;
import com.travelplatform.entity.BusRefund;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.outbox.Outbox;
import com.travelplatform.repository.BusPaymentRepository;
import com.travelplatform.repository.BusRefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Money going back to bus customers. A refund is a {@code bus_refunds} row (PENDING) plus an outbox
 * job written in the same transaction as the cancellation that caused it; {@link BusRefundJobHandler}
 * asks Razorpay for it, and the refund.processed / refund.failed webhooks close it.
 */
@Component
public class BusRefunds {

    private static final Logger log = LoggerFactory.getLogger(BusRefunds.class);

    private final BusRefundRepository refunds;
    private final BusPaymentRepository payments;
    private final Outbox outbox;

    public BusRefunds(BusRefundRepository refunds, BusPaymentRepository payments, Outbox outbox) {
        this.refunds = refunds;
        this.payments = payments;
        this.outbox = outbox;
    }

    /** The payment that paid for the ticket: the first one captured. Null when nothing was paid. */
    public BusPayment paidWith(BusTicket ticket) {
        return payments.findByTicketIdOrderByCreatedAtDesc(ticket.getId()).stream()
                .filter(p -> p.getStatus() == BusPayment.Status.CAPTURED && p.getRazorpayPaymentId() != null)
                .min(Comparator.comparing(BusPayment::getCapturedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(p -> p.getId().toString()))
                .orElse(null);
    }

    /** Queues a refund of {@code amount} on the payment; nothing for a zero amount. */
    @Transactional
    public BusRefund queue(BusTicket ticket, BusPayment payment, BigDecimal amount, String reason) {
        if (payment == null || amount == null || amount.signum() <= 0) {
            return null;
        }
        BigDecimal already = refunds.findByPaymentId(payment.getId()).stream()
                .filter(r -> r.getStatus() != BusRefund.Status.FAILED)
                .map(BusRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal left = payment.getAmount().subtract(already);
        BigDecimal value = BusFares.money(amount.min(left));
        if (value.signum() <= 0) {
            log.warn("Payment {} is already fully refunded; not refunding {} again", payment.getId(), amount);
            return null;
        }
        BusRefund r = new BusRefund();
        r.setTicketId(ticket.getId());
        r.setPaymentId(payment.getId());
        r.setOwnerId(ticket.getOwnerId());
        r.setAmount(value);
        r.setReason(reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason);
        r.setStatus(BusRefund.Status.PENDING);
        r = refunds.saveAndFlush(r);
        outbox.enqueue(BusRefundJobHandler.TYPE, new BusRefundJobHandler.RefundRequest(r.getId()));
        log.info("Refund {} of {} queued for bus ticket {} ({})", r.getId(), value, ticket.getTicketNo(), reason);
        return r;
    }

    /** True when the Razorpay payment is a bus payment (so the webhook's refund events are ours). */
    public boolean isBusPayment(String razorpayPaymentId) {
        return razorpayPaymentId != null && payments.findByRazorpayPaymentId(razorpayPaymentId).isPresent();
    }

    /** refund.processed: the money is back with the customer. Repeats are no-ops. */
    @Transactional
    public void processed(String razorpayPaymentId, String razorpayRefundId, long amountPaise) {
        find(razorpayPaymentId, razorpayRefundId, amountPaise).ifPresentOrElse(r -> {
            if (r.getStatus() == BusRefund.Status.PROCESSED) {
                return;
            }
            r.setStatus(BusRefund.Status.PROCESSED);
            r.setRazorpayRefundId(razorpayRefundId);
            r.setProcessedAt(LocalDateTime.now());
            refunds.save(r);
            log.info("Bus refund {} processed ({})", r.getId(), razorpayRefundId);
        }, () -> log.warn("Razorpay refund {} on bus payment {} matches no refund of ours (made in the dashboard?)",
                razorpayRefundId, razorpayPaymentId));
    }

    /** refund.failed: someone has to refund it by hand. */
    @Transactional
    public void failed(String razorpayPaymentId, String razorpayRefundId, long amountPaise) {
        find(razorpayPaymentId, razorpayRefundId, amountPaise).ifPresent(r -> {
            r.setStatus(BusRefund.Status.FAILED);
            r.setRazorpayRefundId(razorpayRefundId);
            refunds.save(r);
        });
        log.error("Razorpay refund {} for bus payment {} FAILED: refund it by hand from the dashboard",
                razorpayRefundId, razorpayPaymentId);
    }

    /** By refund id; before the job saved that id, the oldest unsent refund of that amount on the payment. */
    private Optional<BusRefund> find(String razorpayPaymentId, String razorpayRefundId, long amountPaise) {
        if (razorpayRefundId != null) {
            Optional<BusRefund> byId = refunds.findByRazorpayRefundId(razorpayRefundId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        Optional<BusPayment> payment = razorpayPaymentId == null ? Optional.empty()
                : payments.findByRazorpayPaymentId(razorpayPaymentId);
        if (payment.isEmpty()) {
            return Optional.empty();
        }
        List<BusRefund> open = refunds.findByPaymentId(payment.get().getId()).stream()
                .filter(r -> r.getStatus() == BusRefund.Status.PENDING && r.getRazorpayRefundId() == null)
                .filter(r -> r.getAmount().movePointRight(2).longValue() == amountPaise)
                .sorted(Comparator.comparing(BusRefund::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return open.stream().findFirst();
    }
}
