package com.travelplatform.service;

import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.outbox.Outbox;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cancelling a prepaid trip before it starts: online payments refund themselves. */
class UserServiceCancelRefundTest {

    private static final UUID USER = UUID.randomUUID();

    private PaymentRepository payments;
    private Outbox outbox;
    private EmailService email;
    private UserService service;
    private TravelBooking booking;
    private Payment payment;

    @BeforeEach
    void setUp() {
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        UserRepository users = mock(UserRepository.class);
        payments = mock(PaymentRepository.class);
        outbox = mock(Outbox.class);
        email = mock(EmailService.class);
        service = new UserService(bookings, mock(RoutingService.class), mock(DriverRepository.class),
                mock(OwnerService.class), users, mock(OtpService.class), mock(PasswordEncoder.class),
                mock(NotificationService.class), mock(TripDriverPhotoRepository.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        ReflectionTestUtils.setField(service, "paymentRepository", payments);
        ReflectionTestUtils.setField(service, "outbox", outbox);
        ReflectionTestUtils.setField(service, "emailService", email);

        booking = new TravelBooking();
        booking.setId(UUID.randomUUID());
        booking.setUserId(USER);
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        when(bookings.findById(booking.getId())).thenReturn(Optional.of(booking));

        payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setBookingId(booking.getId());
        payment.setAmount(new BigDecimal("900.00"));
        payment.setStatus(Payment.PaymentStatus.VERIFIED);
        when(payments.findByBookingId(booking.getId())).thenReturn(Optional.of(payment));

        User user = new User();
        user.setEmail("kavya@example.com");
        user.setName("Kavya");
        when(users.findById(USER)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("paid online: cancelled, refund queued in the same transaction, passenger told")
    void onlinePaymentIsRefunded() {
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);

        service.deleteBooking(USER, booking.getId());

        assertEquals(TravelBooking.BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Payment.PaymentStatus.REFUND_PENDING, payment.getStatus());
        verify(outbox).enqueue(RefundJobHandler.TYPE, new RefundJobHandler.RefundRequest(payment.getId()));
        verify(email).sendRefundStartedEmail(eq("kavya@example.com"), eq("Kavya"), eq(booking), eq(new BigDecimal("900.00")));
    }

    @Test
    @DisplayName("paid by UPI transfer: still refused - there is nothing to refund against")
    void upiPaymentStillNeedsOwner() {
        payment.setPaymentMethod(Payment.PaymentMethod.UPI);

        assertThrows(RuntimeException.class, () -> service.deleteBooking(USER, booking.getId()));

        assertEquals(TravelBooking.BookingStatus.CONFIRMED, booking.getStatus());
        verify(outbox, never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("a started trip cannot be cancelled for a refund")
    void startedTripRefused() {
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        booking.setStatus(TravelBooking.BookingStatus.STARTED);

        assertThrows(RuntimeException.class, () -> service.deleteBooking(USER, booking.getId()));
        verify(outbox, never()).enqueue(any(), any());
    }

    @Test
    @DisplayName("unpaid booking: cancelled with no refund")
    void unpaidCancelsWithoutRefund() {
        payment.setPaymentMethod(Payment.PaymentMethod.RAZORPAY);
        payment.setStatus(Payment.PaymentStatus.PENDING);

        service.deleteBooking(USER, booking.getId());

        assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
        verify(outbox, never()).enqueue(any(), any());
    }
}
