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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public RazorpayPaymentService(PaymentRepository paymentRepository,
                                  TravelBookingRepository bookingRepository,
                                  PaymentService paymentService,
                                  RazorpayGateway gateway,
                                  RazorpayProperties properties,
                                  ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RazorpayOrderResponse createOrder(UUID userId, UUID bookingId) {
        if (!properties.isOperational()) {
            throw new RuntimeException("Online payment is not set up yet. Please pay by UPI or cash.");
        }
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access");
        }
        requirePayable(booking);

        BigDecimal amount = booking.getTotalAmount();
        long paise = toPaise(amount);

        Payment payment = paymentRepository.findByBookingId(bookingId).orElseGet(() -> {
            Payment p = new Payment();
            p.setBookingId(bookingId);
            p.setUserId(userId);
            p.setDriverId(booking.getDriverId());
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            throw new RuntimeException("Payment already completed");
        }

        // Reuse the order while the fare is unchanged. Minting a new one on every "Pay online"
        // tap would orphan the old one: a passenger still paying it in another tab would be
        // charged, and the webhook would find no payment holding that order id.
        boolean reusable = payment.getRazorpayOrderId() != null
                && payment.getAmount() != null
                && payment.getAmount().compareTo(amount) == 0;
        if (!reusable) {
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("booking_id", bookingId.toString());
            notes.put("user_id", userId.toString());
            payment.setRazorpayOrderId(gateway.createOrder(paise, receipt(bookingId), notes));
        }
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
        response.setDescription(describe(booking));
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
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        if (!payment.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access");
        }
        // The order must be the one this server created for this booking. Without this check a
        // signature that is genuine - but for some other, cheaper order - would settle this fare.
        if (payment.getRazorpayOrderId() == null
                || !payment.getRazorpayOrderId().equals(request.getRazorpayOrderId())) {
            throw new IllegalArgumentException("This payment does not belong to this booking");
        }
        if (!RazorpaySignatures.isValidCheckoutSignature(request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(), request.getRazorpaySignature(), properties.getKeySecret())) {
            log.warn("Rejected Razorpay checkout signature for booking {} order {}", bookingId, request.getRazorpayOrderId());
            throw new IllegalArgumentException("Payment could not be verified");
        }
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            // The webhook got here first. Same payment, nothing more to do.
            return confirmed(payment, "Payment received.");
        }
        TravelBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        return settle(payment, booking, request.getRazorpayPaymentId());
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
            case "payment.failed" -> log.info("Razorpay payment {} failed for order {}: {}",
                    entity.path("id").asText(), entity.path("order_id").asText(),
                    entity.path("error_description").asText(""));
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
        if (found.isEmpty()) {
            // Money arrived for an order we hold no record of. Loud, because someone was charged.
            log.error("Razorpay {} for unknown order {} (payment {}): reconcile manually", event, orderId, razorpayPaymentId);
            return;
        }
        Payment payment = found.get();
        if (payment.getStatus() == Payment.PaymentStatus.VERIFIED) {
            return; // the checkout callback, or an earlier delivery, already settled it
        }
        if (paidPaise != toPaise(payment.getAmount())) {
            log.error("Razorpay {} amount {} paise does not match payment {} due {}: not settling",
                    event, paidPaise, payment.getId(), payment.getAmount());
            return;
        }
        Optional<TravelBooking> booking = bookingRepository.findById(payment.getBookingId());
        if (booking.isEmpty() || !isPayable(booking.get())) {
            log.error("Razorpay {} paid order {} but booking {} is {}: refund needed",
                    event, orderId, payment.getBookingId(),
                    booking.map(b -> b.getStatus().name()).orElse("missing"));
            return;
        }
        settle(payment, booking.get(), razorpayPaymentId);
    }

    private PaymentResponse settle(Payment payment, TravelBooking booking, String razorpayPaymentId) {
        // Money came through Razorpay, whatever the row said last (the driver may have shown a
        // UPI QR since checkout was opened).
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setUpiLinkExpiresAt(null);
        return paymentService.settle(payment, booking, "razorpay:" + razorpayPaymentId);
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

    /** Razorpay caps receipts at 40 characters; "bk_" plus a dashless UUID is 35. */
    private static String receipt(UUID bookingId) {
        return "bk_" + bookingId.toString().replace("-", "");
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
