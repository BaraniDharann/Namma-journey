package com.travelplatform.service;

import com.travelplatform.dto.CashPaymentRequest;
import com.travelplatform.dto.PaymentRequest;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.entity.Driver;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Money only moves at the right point of a trip. End-trip, cash and UPI verification used to work
 * from any state, so a confirmed trip that never happened could be "completed" and booked into
 * revenue, and a prepaid trip was marked complete before the car had even arrived.
 *
 * <p>Lifecycle: CONFIRMED (driver accepted) -> STARTED (driver picked up) -> COMPLETED (trip ended
 * and paid). A passenger may prepay while CONFIRMED; that settles the payment, not the trip.
 */
class PaymentLifecycleTest {

    private static final UUID BOOKING = UUID.randomUUID();
    private static final Long DRIVER = 7L;

    private PaymentRepository payments;
    private TravelBookingRepository bookings;
    private NotificationService notifications;
    private PaymentService service;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        bookings = mock(TravelBookingRepository.class);
        notifications = mock(NotificationService.class);
        DriverRepository drivers = mock(DriverRepository.class);
        service = new PaymentService(payments, bookings, drivers, notifications, mock(LocationTrackingService.class));
        ReflectionTestUtils.setField(service, "ownerUpiId", "owner@bank");
        ReflectionTestUtils.setField(service, "ownerName", "Namma Journey");

        booking = new TravelBooking();
        booking.setId(BOOKING);
        booking.setUserId(UUID.randomUUID());
        booking.setDriverId(DRIVER);
        booking.setTotalAmount(new BigDecimal("2500.00"));
        when(bookings.findById(BOOKING)).thenReturn(Optional.of(booking));
        when(bookings.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        Driver d = new Driver();
        d.setId(DRIVER);
        when(drivers.findById(DRIVER)).thenReturn(Optional.of(d));
    }

    private Payment payment(Payment.PaymentMethod method, Payment.PaymentStatus status) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setBookingId(BOOKING);
        p.setUserId(booking.getUserId());
        p.setDriverId(DRIVER);
        p.setAmount(new BigDecimal("2500.00"));
        p.setPaymentMethod(method);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.now());
        when(payments.findByBookingId(BOOKING)).thenReturn(Optional.of(p));
        when(payments.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private static CashPaymentRequest cash(String amount) {
        CashPaymentRequest r = new CashPaymentRequest();
        r.setAmountReceived(new BigDecimal(amount));
        return r;
    }

    @Test
    @DisplayName("a trip that never started cannot be ended")
    void endTripNeedsAStartedTrip() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        assertThrows(RuntimeException.class, () -> service.generateTripEndQr(DRIVER, BOOKING));
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        assertThrows(RuntimeException.class, () -> service.generateTripEndQr(DRIVER, BOOKING));
        verify(payments, never()).save(any());
        verify(notifications, never()).notifyTripEnded(any(), any());
    }

    @Test
    @DisplayName("ending a started trip produces the payment QR")
    void endingAStartedTripGivesTheQr() {
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        when(payments.findByBookingId(BOOKING)).thenReturn(Optional.empty());

        PaymentResponse r = service.generateTripEndQr(DRIVER, BOOKING);

        assertEquals("PENDING", r.getStatus());
        assertEquals(TravelBooking.BookingStatus.STARTED, booking.getStatus());
    }

    @Test
    @DisplayName("ending a prepaid trip completes it instead of failing with 'already paid'")
    void endingAPrepaidTripCompletesIt() {
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.VERIFIED);

        PaymentResponse r = service.generateTripEndQr(DRIVER, BOOKING);

        assertEquals(TravelBooking.BookingStatus.COMPLETED, booking.getStatus());
        assertEquals("VERIFIED", r.getStatus());
        assertNull(r.getUpiQrCode(), "nothing left to pay - no QR");
    }

    @Test
    @DisplayName("cash can only be collected on a started trip")
    void cashNeedsAStartedTrip() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        payment(Payment.PaymentMethod.CASH, Payment.PaymentStatus.PENDING);
        assertThrows(RuntimeException.class, () -> service.markCashReceived(DRIVER, BOOKING, cash("2500.00")));
        assertEquals(TravelBooking.BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    @DisplayName("verifying UPI on a started trip completes it")
    void verifyCompletesAStartedTrip() {
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        Payment p = payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.PENDING);

        service.verifyPayment(p.getId(), "1");

        assertEquals(Payment.PaymentStatus.VERIFIED, p.getStatus());
        assertEquals(TravelBooking.BookingStatus.COMPLETED, booking.getStatus());
    }

    @Test
    @DisplayName("verifying a prepayment settles the money but leaves the trip to happen")
    void verifyingAPrepaymentDoesNotCompleteTheTrip() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        Payment p = payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.PENDING);

        service.verifyPayment(p.getId(), "1");

        assertEquals(Payment.PaymentStatus.VERIFIED, p.getStatus());
        assertEquals(TravelBooking.BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    @DisplayName("a cancelled or pending booking's payment cannot be verified")
    void verifyRefusedForDeadBookings() {
        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);
        Payment p = payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.PENDING);
        assertThrows(RuntimeException.class, () -> service.verifyPayment(p.getId(), "1"));
        assertEquals(Payment.PaymentStatus.PENDING, p.getStatus());
    }

    @Test
    @DisplayName("a UPI reference the passenger supplies is kept, so the owner can match it when verifying")
    void upiReferenceIsStored() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        Payment p = payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.PENDING);
        PaymentRequest req = new PaymentRequest();
        req.setPaymentMethod("UPI");
        req.setUpiTransactionId(" 412345678901 ");

        service.initiatePayment(booking.getUserId(), BOOKING, req);

        assertEquals("412345678901", p.getUpiTransactionId());
    }

    @Test
    @DisplayName("choosing UPI after picking cash switches the pending payment to UPI")
    void methodChangeIsHonoured() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        Payment p = payment(Payment.PaymentMethod.CASH, Payment.PaymentStatus.PENDING);
        PaymentRequest req = new PaymentRequest();
        req.setPaymentMethod("UPI");

        PaymentResponse r = service.initiatePayment(booking.getUserId(), BOOKING, req);

        assertEquals(Payment.PaymentMethod.UPI, p.getPaymentMethod());
        assertEquals("UPI", r.getPaymentMethod());
    }
}
