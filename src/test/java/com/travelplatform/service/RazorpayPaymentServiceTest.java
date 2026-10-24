package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.dto.RazorpayOrderResponse;
import com.travelplatform.dto.RazorpayVerifyRequest;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayPaymentServiceTest {

    private static final String SECRET = "key_secret";
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private PaymentRepository payments;
    private RazorpayGateway gateway;
    private NotificationService notifications;
    private RazorpayPaymentService service;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        gateway = mock(RazorpayGateway.class);
        notifications = mock(NotificationService.class);
        PaymentService paymentService = new PaymentService(payments, bookings, mock(DriverRepository.class),
                notifications, mock(LocationTrackingService.class));
        ReflectionTestUtils.setField(paymentService, "ownerUpiId", "owner@bank");
        ReflectionTestUtils.setField(paymentService, "ownerName", "Namma Journey");

        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId("rzp_test_key");
        props.setKeySecret(SECRET);
        props.setWebhookSecret("whsec");
        service = new RazorpayPaymentService(payments, bookings, paymentService, gateway, props, new ObjectMapper());

        booking = new TravelBooking();
        booking.setId(BOOKING);
        booking.setUserId(USER);
        booking.setDriverId(7L);
        booking.setFromPlace("Chennai, Tamil Nadu");
        booking.setToPlace("Vellore, Tamil Nadu");
        booking.setTotalAmount(new BigDecimal("2500.50"));
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        when(bookings.findById(BOOKING)).thenReturn(Optional.of(booking));
        when(bookings.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(gateway.createOrder(anyLong(), anyString(), anyMap())).thenReturn("order_1");
    }

    private Payment pendingOnline() {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(BOOKING);
        p.setUserId(USER);
        p.setAmount(new BigDecimal("2500.50"));
        p.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        p.setStatus(Payment.PaymentStatus.PENDING);
        p.setRazorpayOrderId("order_1");
        p.setCreatedAt(LocalDateTime.now());
        when(payments.findByBookingId(BOOKING)).thenReturn(Optional.of(p));
        when(payments.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(p));
        return p;
    }

    private RazorpayVerifyRequest checkout(String orderId, String paymentId, String secret) {
        RazorpayVerifyRequest r = new RazorpayVerifyRequest();
        r.setRazorpayOrderId(orderId);
        r.setRazorpayPaymentId(paymentId);
        r.setRazorpaySignature(RazorpaySignatures.hmacSha256Hex(secret,
                (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
        return r;
    }

    private static byte[] capturedEvent(String orderId, long paise) {
        return ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"" + orderId + "\",\"amount\":" + paise + "}}}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the order is for the booking's fare in paise, not anything the browser says")
    void createsOrderForBookingFare() {
        when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty());

        RazorpayOrderResponse order = service.createOrder(USER, BOOKING);

        verify(gateway).createOrder(eq(250050L), anyString(), anyMap());
        assertEquals("order_1", order.getOrderId());
        assertEquals(250050L, order.getAmountPaise());
        assertEquals("rzp_test_key", order.getKeyId());
        assertEquals("Trip Chennai to Vellore", order.getDescription());
    }

    @Test
    @DisplayName("a second Pay tap reuses the open order instead of orphaning it")
    void reusesOpenOrder() {
        pendingOnline();
        service.createOrder(USER, BOOKING);
        verify(gateway, never()).createOrder(anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("a changed fare gets a fresh order")
    void newOrderWhenFareChanged() {
        pendingOnline();
        booking.setTotalAmount(new BigDecimal("3000.00"));
        service.createOrder(USER, BOOKING);
        verify(gateway).createOrder(eq(300000L), anyString(), anyMap());
    }

    @Test
    @DisplayName("another user cannot open checkout for this booking")
    void rejectsOtherUser() {
        assertThrows(RuntimeException.class, () -> service.createOrder(UUID.randomUUID(), BOOKING));
    }

    @Test
    @DisplayName("a cancelled booking cannot be paid")
    void rejectsUnpayableBooking() {
        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);
        assertThrows(RuntimeException.class, () -> service.createOrder(USER, BOOKING));
    }

    @Test
    @DisplayName("a valid checkout signature settles a prepaid fare without completing the trip")
    void verifiedCheckoutSettles() {
        Payment p = pendingOnline();

        PaymentResponse res = service.verifyCheckout(USER, BOOKING, checkout("order_1", "pay_1", SECRET));

        assertEquals("VERIFIED", res.getStatus());
        assertEquals(Payment.PaymentStatus.VERIFIED, p.getStatus());
        assertEquals("pay_1", p.getRazorpayPaymentId());
        assertEquals("razorpay:pay_1", p.getVerifiedBy());
        assertEquals(TravelBooking.BookingStatus.CONFIRMED, booking.getStatus());
        verify(notifications).notifyPaymentReceived(booking);
    }

    @Test
    @DisplayName("paying at the trip's end completes it")
    void paymentAtTripEndCompletes() {
        pendingOnline();
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        service.verifyCheckout(USER, BOOKING, checkout("order_1", "pay_1", SECRET));
        assertEquals(TravelBooking.BookingStatus.COMPLETED, booking.getStatus());
    }

    @Test
    @DisplayName("a forged signature settles nothing")
    void forgedSignatureRejected() {
        Payment p = pendingOnline();
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCheckout(USER, BOOKING, checkout("order_1", "pay_1", "not_the_secret")));
        assertEquals(Payment.PaymentStatus.PENDING, p.getStatus());
    }

    @Test
    @DisplayName("a genuine signature for some other order cannot settle this fare")
    void otherOrderRejected() {
        Payment p = pendingOnline();
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCheckout(USER, BOOKING, checkout("order_cheap", "pay_1", SECRET)));
        assertEquals(Payment.PaymentStatus.PENDING, p.getStatus());
    }

    @Test
    @DisplayName("webhook settles a payment whose tab was closed before the callback")
    void webhookSettles() {
        Payment p = pendingOnline();
        service.handleWebhook(capturedEvent("order_1", 250050L));
        assertEquals(Payment.PaymentStatus.VERIFIED, p.getStatus());
        verify(notifications).notifyPaymentReceived(booking);
    }

    @Test
    @DisplayName("callback then webhook books the fare once")
    void callbackAndWebhookSettleOnce() {
        pendingOnline();
        service.verifyCheckout(USER, BOOKING, checkout("order_1", "pay_1", SECRET));
        service.handleWebhook(capturedEvent("order_1", 250050L));
        service.verifyCheckout(USER, BOOKING, checkout("order_1", "pay_1", SECRET));
        verify(notifications, times(1)).notifyPaymentReceived(booking);
    }

    @Test
    @DisplayName("webhook with the wrong amount does not settle")
    void webhookAmountMismatch() {
        Payment p = pendingOnline();
        service.handleWebhook(capturedEvent("order_1", 100L));
        assertEquals(Payment.PaymentStatus.PENDING, p.getStatus());
    }

    @Test
    @DisplayName("webhook for an unknown order or junk body is ignored, not thrown")
    void webhookUnknownOrderIgnored() {
        when(payments.findByRazorpayOrderId("order_x")).thenReturn(Optional.empty());
        service.handleWebhook(capturedEvent("order_x", 250050L));
        service.handleWebhook("not json".getBytes(StandardCharsets.UTF_8));
        verify(notifications, never()).notifyPaymentReceived(any());
    }

    private static byte[] refundEvent(String paymentId, long paise) {
        return ("{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":"
                + "{\"id\":\"rfnd_1\",\"payment_id\":\"" + paymentId + "\",\"amount\":" + paise + "}}}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("refund.processed closes a pending refund")
    void refundProcessed() {
        Payment p = pendingOnline();
        p.setStatus(Payment.PaymentStatus.REFUND_PENDING);
        p.setRazorpayPaymentId("pay_1");
        when(payments.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.of(p));

        service.handleWebhook(refundEvent("pay_1", 250050L));

        assertEquals(Payment.PaymentStatus.REFUNDED, p.getStatus());
        assertEquals("rfnd_1", p.getRazorpayRefundId());
    }

    @Test
    @DisplayName("a partial refund from the dashboard leaves the payment as it was")
    void partialRefundIgnored() {
        Payment p = pendingOnline();
        p.setStatus(Payment.PaymentStatus.VERIFIED);
        p.setRazorpayPaymentId("pay_1");
        when(payments.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.of(p));

        service.handleWebhook(refundEvent("pay_1", 100L));

        assertEquals(Payment.PaymentStatus.VERIFIED, p.getStatus());
    }

    @Test
    @DisplayName("an unconfigured deployment refuses online payment")
    void refusesWithoutKeys() {
        RazorpayPaymentService unconfigured = new RazorpayPaymentService(payments, mock(TravelBookingRepository.class),
                mock(PaymentService.class), gateway, new RazorpayProperties(), new ObjectMapper());
        assertThrows(RuntimeException.class, () -> unconfigured.createOrder(USER, BOOKING));
    }
}
