package com.travelplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.dto.RazorpayOrderResponse;
import com.travelplatform.dto.RazorpayVerifyRequest;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Online payment through Razorpay Checkout.
 *
 * <ol>
 *   <li>{@link #createOrder}: the server creates a Razorpay order for the fare <em>as the booking
 *       records it</em> - the browser never names an amount - and remembers the order id.</li>
 *   <li>The browser opens Checkout with that order; the passenger pays by card, UPI or netbanking.</li>
 *   <li>{@link #verifyCheckout}: the browser posts back the payment id and signature, and a valid
 *       signature settles the fare at once.</li>
 *   <li>{@link #handleWebhook}: Razorpay's own server-to-server confirmation. It is what settles
 *       the fare when the passenger closes the tab before step 3, so a paid fare is never lost.</li>
 * </ol>
 *
 * Steps 3 and 4 both arrive for a normal payment. Whichever lands second finds the payment
 * VERIFIED and does nothing, so a fare is booked into revenue exactly once.
 */
@Service
public class RazorpayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentService.class);

    private final PaymentRepository paymentRepository;
    private final TravelBookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final RazorpayGateway gateway;
    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;
    /** createOrder's two short transactions either side of the Razorpay call. */
    private final TransactionTemplate tx;

    public RazorpayPaymentService(PaymentRepository paymentRepository,
                                  TravelBookingRepository bookingRepository,
                                  PaymentService paymentService,
                                  RazorpayGateway gateway,
                                  RazorpayProperties properties,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate tx) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    // Field-injected so the tests that build this service by hand are unaffected.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.repository.DriverRepository driverRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.outbox.Outbox outbox;
    // The bus module's payments live in their own tables (bus_payments, bus_refunds); the one
    // Razorpay webhook passes it the orders and payments this class does not know.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.bus.BusBookingService busBooking;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.travelplatform.service.bus.BusRefunds busRefunds;

    void setBusModule(com.travelplatform.service.bus.BusBookingService busBooking,
                      com.travelplatform.service.bus.BusRefunds busRefunds) {
        this.busBooking = busBooking;
        this.busRefunds = busRefunds;
    }

    /**
     * Opens checkout for whatever the passenger owes next. While a Trip's advance is due that is
     * the advance (and the booking is still PENDING); after it, the fare balance once the trip is
     * confirmed. The browser never chooses: one "Pay" endpoint, the server decides.
     *
     * <p>Deliberately not {@code @Transactional}. Creating an order is an HTTP call to Razorpay
     * (200-600 ms normally, up to the 5 s read timeout when it is slow), and a transaction would
     * hold one of the 20 pooled connections for all of it: a slow Razorpay could then exhaust the
     * pool and fail every login and booking. So it runs in three steps: a short transaction that
     * checks the booking and reuses an open order when there is one; the Razorpay call with no
     * transaction open; and a second short transaction that re-checks everything and records the
     * new order. Nothing is written before Razorpay answers, so a failed call leaves no trace.
     */
    public RazorpayOrderResponse createOrder(UUID userId, UUID bookingId) {
        if (!properties.isOperational()) {
            throw new RuntimeException("Online payment is not set up yet. Please pay by UPI or cash.");
        }
        Checkout open = tx.execute(s -> reuseOpenOrder(userId, bookingId));
        if (open.response() != null) {
            return open.response();
        }

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("booking_id", bookingId.toString());
        notes.put("user_id", userId.toString());
        notes.put("purpose", open.purpose().name());
        String orderId = gateway.createOrder(toPaise(open.amount()), receipt(bookingId, open.purpose()), notes);

        try {
            return tx.execute(s -> recordOrder(userId, bookingId, open, orderId));
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException raced) {
            // Another tap (or the driver's UPI QR) wrote this payment while we were at Razorpay.
            // Once more on fresh state: it either finds that tap's order and reuses it, or stores ours.
            log.info("Payment for booking {} changed while creating Razorpay order {}; retrying", bookingId, orderId);
            return tx.execute(s -> recordOrder(userId, bookingId, open, orderId));
        }
    }

    /** What step 1 decided: an answer when an open order could be reused, else what to order. */
    private record Checkout(RazorpayOrderResponse response, Payment.PaymentPurpose purpose, BigDecimal amount) {
    }

    private Checkout reuseOpenOrder(UUID userId, UUID bookingId) {
        TravelBooking booking = payableBooking(userId, bookingId);
        Payment.PaymentPurpose purpose = purposeDue(booking);
        BigDecimal amount = amountDue(booking, purpose);
        toPaise(amount); // refuse a non-positive amount before ever calling Razorpay

        Payment payment = existingPayment(booking, purpose);
        // Reuse the order while the amount is unchanged. Minting a new one on every "Pay online"
        // tap would orphan the old one: a passenger still paying it in another tab would be
        // charged, and the webhook would find no payment holding that order id.
        if (holdsOrderFor(payment, amount)) {
            return new Checkout(store(payment, booking, purpose, amount, payment.getRazorpayOrderId()), purpose, amount);
        }
        return new Checkout(null, purpose, amount);
    }

    /**
     * Step 3: Razorpay has answered, but the booking may have moved on in the meantime - been
     * cancelled, its hold lapsed, its fare changed, or the fare paid in cash. Every check from
     * step 1 runs again on fresh state. When one fails, the new order is simply never handed to
     * the browser; an order nobody pays costs nothing and Razorpay expires it.
     */
    private RazorpayOrderResponse recordOrder(UUID userId, UUID bookingId, Checkout open, String orderId) {
        TravelBooking booking = payableBooking(userId, bookingId);
        Payment.PaymentPurpose purpose = purposeDue(booking);
        BigDecimal amount = amountDue(booking, purpose);
        if (purpose != open.purpose() || amount.compareTo(open.amount()) != 0) {
            log.info("Fare for booking {} changed while creating Razorpay order {}; not using it", bookingId, orderId);
            throw new RuntimeException("The amount due changed while opening payment. Please try again.");
        }
        Payment payment = existingPayment(booking, purpose);
        if (holdsOrderFor(payment, amount)) {
            // A second tap stored its order first. Hand out that one, so the passenger only ever
            // has one order to pay; ours goes unused.
            log.info("Booking {} already has Razorpay order {}; dropping {}", bookingId, payment.getRazorpayOrderId(), orderId);
            return store(payment, booking, purpose, amount, payment.getRazorpayOrderId());
        }
        return store(payment, booking, purpose, amount, orderId);
    }

    private TravelBooking payableBooking(UUID userId, UUID bookingId) {
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access");
        }
        if (booking.isAdvanceDue()) {
            if (booking.getStatus() != TravelBooking.BookingStatus.PENDING) {
                throw new RuntimeException("This booking can no longer be paid");
            }
            if (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("This booking's hold has expired. Please book again.");
            }
        } else {
            requirePayable(booking);
        }
        return booking;
    }

    private static Payment.PaymentPurpose purposeDue(TravelBooking booking) {
        return booking.isAdvanceDue() ? Payment.PaymentPurpose.ADVANCE : Payment.PaymentPurpose.FARE;
    }

    private static BigDecimal amountDue(TravelBooking booking, Payment.PaymentPurpose purpose) {
        return purpose == Payment.PaymentPurpose.ADVANCE ? booking.getAdvanceAmount() : PaymentService.amountDue(booking);
    }

    private Payment existingPayment(TravelBooking booking, Payment.PaymentPurpose purpose) {
        Payment payment = paymentRepository.findByBookingIdAndPurpose(booking.getId(), purpose).orElseGet(() -> {
            Payment p = new Payment();
            p.setBookingId(booking.getId());
            p.setUserId(booking.getUserId());
            p.setDriverId(booking.getDriverId());
            p.setCreatedAt(LocalDateTime.now());
            p.setPurpose(purpose);
            return p;
        });
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            throw new RuntimeException("Payment already completed");
        }
        return payment;
    }

    private static boolean holdsOrderFor(Payment payment, BigDecimal amount) {
        return payment.getRazorpayOrderId() != null
                && payment.getAmount() != null
                && payment.getAmount().compareTo(amount) == 0;
    }

    private RazorpayOrderResponse store(Payment payment, TravelBooking booking, Payment.PaymentPurpose purpose,
                                        BigDecimal amount, String orderId) {
        UUID bookingId = booking.getId();
        long paise = toPaise(amount);
        payment.setRazorpayOrderId(orderId);
        payment.setAmount(amount);
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setUpiLinkExpiresAt(null);
        Payment saved = paymentRepository.save(payment);

        RazorpayOrderResponse response = new RazorpayOrderResponse();
        response.setPaymentId(saved.getId());
        response.setBookingId(bookingId);
        response.setKeyId(properties.getKeyId());
        response.setOrderId(saved.getRazorpayOrderId());
        response.setAmount(amount);
        response.setAmountPaise(paise);
        response.setCurrency("INR");
        response.setMerchantName(properties.getMerchantName());
        response.setPurpose(purpose.name());
        response.setDescription((purpose == Payment.PaymentPurpose.ADVANCE ? "Advance: " : "") + describe(booking));
        return response;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public PaymentResponse verifyCheckout(UUID userId, UUID bookingId, RazorpayVerifyRequest request) {
        if (!properties.isOperational()) {
            throw new RuntimeException("Online payment is not set up yet. Please pay by UPI or cash.");
        }
        // The order must be one this server created for this booking. Without this check a
        // signature that is genuine - but for some other, cheaper order - would settle this one.
        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .filter(p -> p.getBookingId().equals(bookingId))
                .orElseThrow(() -> new IllegalArgumentException("This payment does not belong to this booking"));
        if (!payment.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access");
        }
        if (!RazorpaySignatures.isValidCheckoutSignature(request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(), request.getRazorpaySignature(), properties.getKeySecret())) {
            log.warn("Rejected Razorpay checkout signature for booking {} order {}", bookingId, request.getRazorpayOrderId());
            throw new IllegalArgumentException("Payment could not be verified");
        }
        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            // The webhook got here first: settled it (VERIFIED) or already sent it back.
            return confirmed(payment, payment.getStatus() == Payment.PaymentStatus.VERIFIED
                    ? "Payment received." : "This booking was cancelled; your payment is being refunded.");
        }
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        return apply(payment, booking, request.getRazorpayPaymentId());
    }

    /**
     * Applies a webhook that has already passed its signature check.
     *
     * <p>Never throws for an event it understands but cannot apply: Razorpay retries anything
     * non-2xx for a day, so a permanent problem (an unknown order, an amount mismatch) is logged
     * for a person to look at rather than turned into a retry storm.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "dailyRevenue",   allEntries = true),
            @CacheEvict(value = "monthlyRevenue", allEntries = true),
            @CacheEvict(value = "yearlyRevenue",  allEntries = true),
            @CacheEvict(value = "monthlyRevenueSeries", allEntries = true)
    })
    public void handleWebhook(byte[] rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (IOException e) {
            log.warn("Razorpay webhook body is not JSON; ignoring");
            return;
        }
        String event = root.path("event").asText("");
        JsonNode entity = root.path("payload").path("payment").path("entity");

        switch (event) {
            case "payment.captured", "order.paid" -> settleFromWebhook(event, entity);
            case "refund.processed" -> {
                JsonNode refund = root.path("payload").path("refund").path("entity");
                if (isBusPayment(refund)) {
                    busRefunds.processed(refund.path("payment_id").asText(), refund.path("id").asText(null),
                            refund.path("amount").asLong(-1));
                } else {
                    refundProcessed(refund);
                }
            }
            case "refund.failed" -> {
                JsonNode refund = root.path("payload").path("refund").path("entity");
                if (isBusPayment(refund)) {
                    busRefunds.failed(refund.path("payment_id").asText(), refund.path("id").asText(null),
                            refund.path("amount").asLong(-1));
                } else {
                    log.error("Razorpay refund {} for payment {} FAILED: refund it by hand from the dashboard",
                            refund.path("id").asText(), refund.path("payment_id").asText());
                }
            }
            case "payment.failed" -> {
                log.info("Razorpay payment {} failed for order {}: {}",
                        entity.path("id").asText(), entity.path("order_id").asText(),
                        entity.path("error_description").asText(""));
                String orderId = entity.path("order_id").asText(null);
                if (busBooking != null && orderId != null && paymentRepository.findByRazorpayOrderId(orderId).isEmpty()) {
                    // A bus checkout that failed: its seats go back on sale now rather than when the hold runs out.
                    busBooking.onPaymentFailed(orderId);
                }
            }
            default -> log.debug("Ignoring Razorpay webhook event {}", event);
        }
    }

    private void settleFromWebhook(String event, JsonNode entity) {
        String orderId = entity.path("order_id").asText(null);
        String razorpayPaymentId = entity.path("id").asText(null);
        long paidPaise = entity.path("amount").asLong(-1);
        if (orderId == null || razorpayPaymentId == null) {
            log.warn("Razorpay {} webhook without order or payment id; ignoring", event);
            return;
        }

        Optional<Payment> found = paymentRepository.findByRazorpayOrderId(orderId);
        if (found.isEmpty() && busBooking != null && busBooking.onPaymentCaptured(orderId, razorpayPaymentId, paidPaise)) {
            return; // a bus ticket's order: settled (or refunded) by the bus module
        }
        if (found.isEmpty()) {
            // Money arrived for an order we hold no record of. Loud, because someone was charged.
            log.error("Razorpay {} for unknown order {} (payment {}): reconcile manually", event, orderId, razorpayPaymentId);
            return;
        }
        Payment payment = found.get();
        // PENDING, or FAILED when the hold sweeper gave up on it a moment before the money landed.
        if (payment.getStatus() != Payment.PaymentStatus.PENDING && payment.getStatus() != Payment.PaymentStatus.FAILED) {
            return; // the checkout callback, or an earlier delivery, already dealt with it
        }
        if (paidPaise != toPaise(payment.getAmount())) {
            log.error("Razorpay {} amount {} paise does not match payment {} due {}: not settling",
                    event, paidPaise, payment.getId(), payment.getAmount());
            return;
        }
        Optional<TravelBooking> booking = bookingRepository.findById(payment.getBookingId());
        if (booking.isEmpty()) {
            log.error("Razorpay {} paid order {} for missing booking {}: refund needed", event, orderId, payment.getBookingId());
            return;
        }
        apply(payment, booking.get(), razorpayPaymentId);
    }

    /** A refund event for a payment of the bus module (not in the car {@code payments} table). */
    private boolean isBusPayment(JsonNode refund) {
        String razorpayPaymentId = refund.path("payment_id").asText(null);
        return busRefunds != null && razorpayPaymentId != null
                && paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).isEmpty()
                && busRefunds.isBusPayment(razorpayPaymentId);
    }

    /** The money is back with the passenger: close the payment out. Repeats are no-ops. */
    private void refundProcessed(JsonNode refund) {
        String razorpayPaymentId = refund.path("payment_id").asText(null);
        if (razorpayPaymentId == null) {
            return;
        }
        Optional<Payment> found = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId);
        if (found.isEmpty()) {
            // Refunded from the dashboard for a payment we never settled - nothing to update.
            log.warn("Razorpay refund {} for unknown payment {}", refund.path("id").asText(), razorpayPaymentId);
            return;
        }
        Payment payment = found.get();
        if (payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
            return;
        }
        long refundedPaise = refund.path("amount").asLong(-1);
        if (refundedPaise != toPaise(payment.getAmount())) {
            // A partial refund from the dashboard: the fare is not fully returned, so keep the
            // payment's status and leave the difference to whoever issued it.
            log.warn("Partial refund {} ({} paise) on payment {}; status unchanged",
                    refund.path("id").asText(), refundedPaise, payment.getId());
            return;
        }
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            // Refunded by hand in the dashboard without cancelling here: revenue must drop anyway.
            log.warn("Payment {} refunded outside the app; marking it REFUNDED", payment.getId());
        }
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment.setRazorpayRefundId(refund.path("id").asText(payment.getRazorpayRefundId()));
        payment.setRefundedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        log.info("Refund {} processed for payment {}", payment.getRazorpayRefundId(), payment.getId());
    }

    /**
     * Money has arrived for this payment. Settle it if the booking can still take it; otherwise -
     * the hold lapsed and the booking was released while the passenger was paying, or it was
     * cancelled - send it straight back rather than leave a person to notice.
     */
    private PaymentResponse apply(Payment payment, TravelBooking booking, String razorpayPaymentId) {
        // Money came through Razorpay, whatever the row said last (the driver may have shown a
        // UPI QR since checkout was opened).
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setUpiLinkExpiresAt(null);

        boolean advance = payment.getPurpose() == Payment.PaymentPurpose.ADVANCE;
        boolean takesIt = advance
                ? booking.getStatus() == TravelBooking.BookingStatus.PENDING
                        || booking.getStatus() == TravelBooking.BookingStatus.CONFIRMED
                : isPayable(booking);
        if (!takesIt) {
            return refundUnwanted(payment, booking);
        }
        return advance ? settleAdvance(payment, booking, razorpayPaymentId)
                : paymentService.settle(payment, booking, "razorpay:" + razorpayPaymentId);
    }

    /** The advance is in: the hold becomes a booking, and only now is the driver told about it. */
    private PaymentResponse settleAdvance(Payment payment, TravelBooking booking, String razorpayPaymentId) {
        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        payment.setPaymentDate(now);
        payment.setVerifiedDate(now);
        payment.setVerifiedBy("razorpay:" + razorpayPaymentId);
        paymentRepository.save(payment);

        booking.setAdvancePaidAt(now);
        booking.setHoldExpiresAt(null);
        bookingRepository.save(booking);

        if (notificationService != null) {
            notificationService.notifyAdvanceReceived(booking, payment.getAmount());
            if (booking.getDriverId() != null && driverRepository != null) {
                driverRepository.findById(booking.getDriverId())
                        .ifPresent(driver -> notificationService.notifyDriverAssigned(booking, driver));
            }
        }
        return confirmed(payment, "Advance received. Your trip is booked; the balance is paid at the end.");
    }

    private PaymentResponse refundUnwanted(Payment payment, TravelBooking booking) {
        log.warn("Payment {} arrived for booking {} which is {}: refunding it", payment.getId(), booking.getId(), booking.getStatus());
        if (outbox == null) {
            log.error("No outbox to queue the refund of payment {}: refund it by hand", payment.getId());
            paymentRepository.save(payment);
            return confirmed(payment, "This booking can no longer be paid. Please contact us for a refund.");
        }
        payment.setStatus(Payment.PaymentStatus.REFUND_PENDING);
        paymentRepository.save(payment);
        outbox.enqueue(RefundJobHandler.TYPE, new RefundJobHandler.RefundRequest(payment.getId()));
        return confirmed(payment, "This booking was released before your payment arrived, so it is being refunded in full.");
    }

    private static void requirePayable(TravelBooking booking) {
        if (!isPayable(booking)) {
            throw new RuntimeException("Booking must be confirmed before payment");
        }
    }

    /** Same window as the owner's UPI verification: prepaid while CONFIRMED, or at the trip's end. */
    private static boolean isPayable(TravelBooking booking) {
        return booking.getStatus() == TravelBooking.BookingStatus.CONFIRMED
                || booking.getStatus() == TravelBooking.BookingStatus.STARTED;
    }

    static long toPaise(BigDecimal rupees) {
        if (rupees == null || rupees.signum() <= 0) {
            throw new IllegalStateException("Refusing to charge a non-positive amount");
        }
        // Money is NUMERIC(12,2) (V8), so this is exact; longValueExact refuses anything that is not.
        return rupees.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
    }

    /** Razorpay caps receipts at 40 characters; a 3-letter prefix plus a dashless UUID is 35. */
    private static String receipt(UUID bookingId, Payment.PaymentPurpose purpose) {
        return (purpose == Payment.PaymentPurpose.ADVANCE ? "ad_" : "bk_") + bookingId.toString().replace("-", "");
    }

    private static String describe(TravelBooking booking) {
        String text = "Trip " + shortPlace(booking.getFromPlace()) + " to " + shortPlace(booking.getToPlace());
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private static String shortPlace(String place) {
        if (place == null || place.isBlank()) {
            return "?";
        }
        int comma = place.indexOf(',');
        return (comma > 0 ? place.substring(0, comma) : place).trim();
    }

    private static PaymentResponse confirmed(Payment payment, String message) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getId());
        response.setBookingId(payment.getBookingId());
        response.setAmount(payment.getAmount());
        response.setPaymentMethod(payment.getPaymentMethod().name());
        response.setStatus(payment.getStatus().name());
        response.setMessage(message);
        return response;
    }
}
