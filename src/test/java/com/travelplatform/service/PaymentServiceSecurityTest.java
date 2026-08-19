package com.travelplatform.service;

import com.travelplatform.dto.CashPaymentRequest;
import com.travelplatform.dto.PaymentRequest;
import com.travelplatform.dto.PaymentResponse;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards on the money path.
 *
 * <p>Each test corresponds to a way the payment flow could previously be driven into recognising
 * revenue that never arrived, or into emitting a UPI intent that pays the wrong amount. They are
 * deliberately about the refusals rather than the happy path: a regression in any of these is
 * silent, because it books money rather than throwing.
 */
class PaymentServiceSecurityTest {

    private static final UUID BOOKING_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID PAYMENT_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final Long DRIVER_ID = 7L;

    private PaymentRepository paymentRepository;
    private TravelBookingRepository bookingRepository;
    private NotificationService notificationService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        bookingRepository = mock(TravelBookingRepository.class);
        notificationService = mock(NotificationService.class);
        LocationTrackingService locationTrackingService = mock(LocationTrackingService.class);
        DriverRepository driverRepository = mock(DriverRepository.class);

        service = new PaymentService(paymentRepository, bookingRepository, driverRepository,
                notificationService, locationTrackingService);
        ReflectionTestUtils.setField(service, "ownerUpiId", "owner@examplebank");
        ReflectionTestUtils.setField(service, "ownerName", "Namma Journey");

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private TravelBooking booking(TravelBooking.BookingStatus status) {
        TravelBooking b = new TravelBooking();
        b.setId(BOOKING_ID);
        b.setUserId(USER_ID);
        b.setDriverId(DRIVER_ID);
        b.setTotalAmount(2500.00);
        b.setStatus(status);
        return b;
    }

    private Payment payment(Payment.PaymentMethod method, Payment.PaymentStatus status) {
        Payment p = new Payment();
        p.setId(PAYMENT_ID);
        p.setBookingId(BOOKING_ID);
        p.setUserId(USER_ID);
        p.setDriverId(DRIVER_ID);
        p.setAmount(2500.00);
        p.setPaymentMethod(method);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    @Test
    @DisplayName("a driver cannot settle the same cash fare twice")
    void cashPaymentIsIdempotent() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));
        when(paymentRepository.findByBookingId(BOOKING_ID))
                .thenReturn(Optional.of(payment(Payment.PaymentMethod.CASH, Payment.PaymentStatus.VERIFIED)));

        CashPaymentRequest request = new CashPaymentRequest();
        request.setAmountReceived(2500.00);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.markCashReceived(DRIVER_ID, BOOKING_ID, request));

        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("already"),
                "message must contain 'already' for the handler to map it to 409, got: " + ex.getMessage());
        verify(bookingRepository, never()).save(any(TravelBooking.class));
        verify(notificationService, never()).notifyPaymentReceived(any());
    }

    @Test
    @DisplayName("a driver cannot report a shortfall and have the fare settle in full")
    void cashPaymentRejectsShortfall() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));
        when(paymentRepository.findByBookingId(BOOKING_ID))
                .thenReturn(Optional.of(payment(Payment.PaymentMethod.CASH, Payment.PaymentStatus.PENDING)));

        CashPaymentRequest request = new CashPaymentRequest();
        request.setAmountReceived(1.00);

        assertThrows(IllegalArgumentException.class,
                () -> service.markCashReceived(DRIVER_ID, BOOKING_ID, request));

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(notificationService, never()).notifyPaymentReceived(any());
    }

    @Test
    @DisplayName("a cash fare matching the amount due settles and records the driver")
    void cashPaymentSettlesWhenAmountMatches() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));
        when(paymentRepository.findByBookingId(BOOKING_ID))
                .thenReturn(Optional.of(payment(Payment.PaymentMethod.CASH, Payment.PaymentStatus.PENDING)));

        CashPaymentRequest request = new CashPaymentRequest();
        request.setAmountReceived(2500.00);

        PaymentResponse response = service.markCashReceived(DRIVER_ID, BOOKING_ID, request);

        assertEquals(Payment.PaymentStatus.VERIFIED.name(), response.getStatus());
        verify(notificationService).notifyPaymentReceived(any());
    }

    @Test
    @DisplayName("an owner cannot verify the same payment twice")
    void verifyIsIdempotent() {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.VERIFIED)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.verifyPayment(PAYMENT_ID, "owner-1"));

        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("already"));
        verify(bookingRepository, never()).save(any(TravelBooking.class));
        verify(notificationService, never()).notifyPaymentReceived(any());
    }

    @Test
    @DisplayName("the owner verify endpoint refuses cash, which only the assigned driver settles")
    void verifyRejectsCashPayments() {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment(Payment.PaymentMethod.CASH, Payment.PaymentStatus.PENDING)));

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyPayment(PAYMENT_ID, "owner-1"));

        verify(bookingRepository, never()).save(any(TravelBooking.class));
    }

    @Test
    @DisplayName("verifying a UPI payment records which owner did it")
    void verifyRecordsTheActingOwner() {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment(Payment.PaymentMethod.UPI, Payment.PaymentStatus.PENDING)));
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));

        service.verifyPayment(PAYMENT_ID, "owner-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals("owner:owner-1", captor.getValue().getVerifiedBy());
        assertNotNull(captor.getValue().getVerifiedDate());
    }

    @Test
    @DisplayName("an unknown or missing payment method is a 400, not a 500 from inside the transaction")
    void unknownPaymentMethodIsRejected() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("BITCOIN");
        assertThrows(IllegalArgumentException.class,
                () -> service.initiatePayment(USER_ID, BOOKING_ID, request));

        request.setPaymentMethod(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.initiatePayment(USER_ID, BOOKING_ID, request));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("a booking belonging to another account cannot be paid, so its QR cannot be minted")
    void cannotInitiatePaymentForAnotherAccountsBooking() {
        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("UPI");

        UUID intruder = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.initiatePayment(intruder, BOOKING_ID, request));

        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("unauthorized"));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("the UPI intent carries a dot decimal whatever locale the host runs in")
    void upiLinkAmountIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            // Germany renders 2500.00 as "2500,00". Before Locale.ROOT was pinned, that comma
            // went straight into the am= parameter of the UPI intent.
            Locale.setDefault(Locale.GERMANY);

            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            PaymentRequest request = new PaymentRequest();
            request.setPaymentMethod("UPI");

            PaymentResponse response = service.initiatePayment(USER_ID, BOOKING_ID, request);

            assertTrue(response.getUpiDeepLink().contains("am=2500.00"),
                    "expected a dot decimal separator, got: " + response.getUpiDeepLink());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("a payee address containing a separator cannot inject extra intent parameters")
    void upiLinkEscapesThePayeeAddress() {
        ReflectionTestUtils.setField(service, "ownerUpiId", "evil@bank&am=1");

        when(bookingRepository.findById(BOOKING_ID))
                .thenReturn(Optional.of(booking(TravelBooking.BookingStatus.CONFIRMED)));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

        PaymentRequest request = new PaymentRequest();
        request.setPaymentMethod("UPI");

        String link = service.initiatePayment(USER_ID, BOOKING_ID, request).getUpiDeepLink();

        // The injected "&am=1" must arrive percent-encoded inside pa=, not as its own parameter.
        assertTrue(link.contains("evil%40bank%26am%3D1"), "payee not escaped: " + link);
        assertTrue(link.contains("am=2500.00"), "real amount missing: " + link);
    }
}
