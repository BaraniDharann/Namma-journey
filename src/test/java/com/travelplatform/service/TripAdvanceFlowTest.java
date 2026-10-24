package com.travelplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.dto.RazorpayOrderResponse;
import com.travelplatform.dto.RazorpayVerifyRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.outbox.Outbox;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A Trip held by its advance: pay it, and only then does it become a booking. */
class TripAdvanceFlowTest {

    private static final String SECRET = "key_secret";
    private static final UUID USER = UUID.randomUUID();

    private PaymentRepository payments;
    private TravelBookingRepository bookings;
    private RazorpayGateway gateway;
    private NotificationService notifications;
    private Outbox outbox;
    private RazorpayPaymentService service;
    private TravelBooking booking;
    private Driver driver;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        bookings = mock(TravelBookingRepository.class);
        gateway = mock(RazorpayGateway.class);
        notifications = mock(NotificationService.class);
        outbox = mock(Outbox.class);
        DriverRepository drivers = mock(DriverRepository.class);
        PaymentService paymentService = new PaymentService(payments, bookings, drivers, notifications,
                mock(LocationTrackingService.class));
        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId("rzp_test_key");
        props.setKeySecret(SECRET);
        service = new RazorpayPaymentService(payments, bookings, paymentService, gateway, props, new ObjectMapper());
        ReflectionTestUtils.setField(service, "notificationService", notifications);
        ReflectionTestUtils.setField(service, "driverRepository", drivers);
        ReflectionTestUtils.setField(service, "outbox", outbox);

        booking = new TravelBooking();
        booking.setId(UUID.randomUUID());
        booking.setUserId(USER);
        booking.setDriverId(9L);
        booking.setFromPlace("Chennai");
        booking.setToPlace("Ooty");
        booking.setFromDate(LocalDate.now().plusDays(5));
        booking.setServiceMode(TravelBooking.ServiceMode.TRIP);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setTotalAmount(new BigDecimal("5000.00"));
        booking.setAdvanceAmount(new BigDecimal("1000.00"));
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(20));
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookings.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(gateway.createOrder(any(Long.class), anyString(), anyMap())).thenReturn("order_adv");
        driver = new Driver();
        driver.setId(9L);
        when(drivers.findById(9L)).thenReturn(Optional.of(driver));
    }

    private Payment pending(Payment.PaymentPurpose purpose, String amount, String orderId) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(booking.getId());
        p.setUserId(USER);
        p.setPurpose(purpose);
        p.setAmount(new BigDecimal(amount));
        p.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        p.setStatus(Payment.PaymentStatus.PENDING);
        p.setRazorpayOrderId(orderId);
        when(payments.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(p));
        return p;
    }

    private RazorpayVerifyRequest checkout(String orderId) {
        RazorpayVerifyRequest r = new RazorpayVerifyRequest();
        r.setRazorpayOrderId(orderId);
        r.setRazorpayPaymentId("pay_1");
        r.setRazorpaySignature(RazorpaySignatures.hmacSha256Hex(SECRET,
                (orderId + "|pay_1").getBytes(StandardCharsets.UTF_8)));
        return r;
    }

    @Test
    @DisplayName("while the advance is due, Pay opens an order for the advance, not the fare")
    void firstOrderIsTheAdvance() {
        RazorpayOrderResponse order = service.createOrder(USER, booking.getId());

        assertEquals("ADVANCE", order.getPurpose());
        assertEquals(100000L, order.getAmountPaise());
        verify(gateway).createOrder(eq(100000L), anyString(), anyMap());
    }

    @Test
    @DisplayName("an expired hold cannot be paid")
    void expiredHoldRefused() {
        booking.setHoldExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(RuntimeException.class, () -> service.createOrder(USER, booking.getId()));
    }

    @Test
    @DisplayName("paying the advance books the trip: still PENDING, driver told now, nothing completed")
    void advanceSettles() {
        Payment advance = pending(Payment.PaymentPurpose.ADVANCE, "1000.00", "order_adv");

        service.verifyCheckout(USER, booking.getId(), checkout("order_adv"));

        assertEquals(Payment.PaymentStatus.VERIFIED, advance.getStatus());
        assertNotNull(booking.getAdvancePaidAt());
        assertNull(booking.getHoldExpiresAt());
        assertEquals(TravelBooking.BookingStatus.PENDING, booking.getStatus());
        verify(notifications).notifyAdvanceReceived(booking, new BigDecimal("1000.00"));
        verify(notifications).notifyDriverAssigned(booking, driver);
        verify(notifications, never()).notifyPaymentReceived(any());
    }

    @Test
    @DisplayName("after the advance, the fare order is for the balance")
    void fareOrderIsTheBalance() {
        booking.setAdvancePaidAt(LocalDateTime.now());
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);

        RazorpayOrderResponse order = service.createOrder(USER, booking.getId());

        assertEquals("FARE", order.getPurpose());
        assertEquals(new BigDecimal("4000.00"), order.getAmount());
        assertEquals(new BigDecimal("4000.00"), PaymentService.amountDue(booking));
    }

    @Test
    @DisplayName("an advance landing after the hold was released is refunded, not lost")
    void lateAdvanceIsRefunded() {
        Payment advance = pending(Payment.PaymentPurpose.ADVANCE, "1000.00", "order_adv");
        advance.setStatus(Payment.PaymentStatus.FAILED); // the sweeper gave up on it
        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);

        service.handleWebhook(("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_1\",\"order_id\":\"order_adv\",\"amount\":100000}}}}").getBytes(StandardCharsets.UTF_8));

        assertEquals(Payment.PaymentStatus.REFUND_PENDING, advance.getStatus());
        assertEquals("pay_1", advance.getRazorpayPaymentId());
        verify(outbox).enqueue(RefundJobHandler.TYPE, new RefundJobHandler.RefundRequest(advance.getId()));
        verify(notifications, never()).notifyAdvanceReceived(any(), any());
    }

    @Test
    @DisplayName("the hold sweeper releases an unpaid Trip and gives up on its pending advance")
    void sweeperReleasesExpiredHold() {
        Payment advance = pending(Payment.PaymentPurpose.ADVANCE, "1000.00", "order_adv");
        when(payments.findAdvance(booking.getId())).thenReturn(Optional.of(advance));
        when(bookings.findExpiredAdvanceHolds(any())).thenReturn(List.of(booking.getId()));
        AdvanceHoldSweeper sweeper = new AdvanceHoldSweeper(bookings, payments, notifications,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));

        sweeper.release(booking.getId());

        assertEquals(TravelBooking.BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Payment.PaymentStatus.FAILED, advance.getStatus());
        verify(notifications).notifyHoldReleased(booking);
    }

    @Test
    @DisplayName("the sweeper leaves a paid Trip alone")
    void sweeperSkipsPaid() {
        booking.setAdvancePaidAt(LocalDateTime.now());
        new AdvanceHoldSweeper(bookings, payments, notifications,
                new TransactionTemplate(mock(PlatformTransactionManager.class))).release(booking.getId());
        assertEquals(TravelBooking.BookingStatus.PENDING, booking.getStatus());
    }
}
