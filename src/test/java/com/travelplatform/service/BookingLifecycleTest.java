package com.travelplatform.service;

import com.travelplatform.controller.UserController;
import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Payment;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PaymentRepository;
import com.travelplatform.repository.PricingConfigRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may move a booking, and from which state. Passengers could hard-delete trips in any state
 * (a driver lost the record mid-ride, revenue vanished), edit a paid booking's route so the fare
 * changed but the collected amount did not, and confirm their own booking over the driver's head.
 * Drivers could start a December trip in October; owners could reassign a trip already under way.
 */
class BookingLifecycleTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private TravelBookingRepository bookings;
    private PaymentRepository payments;
    private NotificationService notifications;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        bookings = mock(TravelBookingRepository.class);
        payments = mock(PaymentRepository.class);
        notifications = mock(NotificationService.class);
        booking = new TravelBooking();
        booking.setId(bookingId);
        booking.setUserId(userId);
        booking.setDriverId(7L);
        booking.setFromDate(TODAY.plusDays(5));
        booking.setToDate(TODAY.plusDays(5));
        booking.setEstimatedTimeMinutes(60L);
        booking.setTotalAmount(new BigDecimal("900.00"));
        booking.setBookingType(TravelBooking.BookingType.DISTANCE_BASED);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookings.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(payments.findByBookingId(bookingId)).thenReturn(Optional.empty());
    }

    private UserService userService() {
        // A working route, so an edit can only be refused by the status rules - not by a null route.
        RoutingService routing = mock(RoutingService.class);
        when(routing.calculateRoute(any(String.class), any(String.class)))
                .thenReturn(new com.travelplatform.dto.RouteInfo(40.0, 60L, "route"));
        OwnerService pricing = mock(OwnerService.class);
        when(pricing.getCurrentPricePerKm()).thenReturn(new BigDecimal("14.00"));
        UserService s = new UserService(bookings, routing, mock(DriverRepository.class),
                pricing, mock(UserRepository.class), mock(OtpService.class), mock(PasswordEncoder.class),
                notifications, mock(TripDriverPhotoRepository.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        ReflectionTestUtils.setField(s, "paymentRepository", payments);
        return s;
    }

    private DriverService driverService(FileStorageService storage) {
        DriverService s = new DriverService(bookings, mock(DriverRepository.class), notifications,
                mock(TripDriverPhotoRepository.class), mock(BookingDriverRejectionRepository.class), storage);
        ReflectionTestUtils.setField(s, "clock", Clock.fixed(TODAY.atTime(9, 0).atZone(IST).toInstant(), IST));
        return s;
    }

    private Payment payment(Payment.PaymentStatus status) {
        Payment p = new Payment();
        p.setBookingId(bookingId);
        p.setStatus(status);
        p.setPaymentMethod(Payment.PaymentMethod.UPI);
        when(payments.findByBookingId(bookingId)).thenReturn(Optional.of(p));
        return p;
    }

    // ---- passenger cancel (the "delete" endpoint) ----

    @Test
    @DisplayName("cancelling keeps the booking as CANCELLED instead of deleting it")
    void cancelIsSoft() {
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        userService().deleteBooking(userId, bookingId);
        assertEquals(TravelBooking.BookingStatus.CANCELLED, booking.getStatus());
        verify(bookings, never()).delete(any());
        verify(notifications).notifyBookingCancelled(booking);
    }

    @Test
    @DisplayName("a trip under way, finished, or already paid for cannot be cancelled by the passenger")
    void cancelRefusedOnceUnderWayOrPaid() {
        for (TravelBooking.BookingStatus s : new TravelBooking.BookingStatus[] {
                TravelBooking.BookingStatus.STARTED, TravelBooking.BookingStatus.COMPLETED, TravelBooking.BookingStatus.CANCELLED}) {
            booking.setStatus(s);
            assertThrows(RuntimeException.class, () -> userService().deleteBooking(userId, bookingId), s.name());
        }
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        payment(Payment.PaymentStatus.VERIFIED);
        assertThrows(RuntimeException.class, () -> userService().deleteBooking(userId, bookingId));
        verify(bookings, never()).delete(any());
    }

    @Test
    @DisplayName("cancelling a confirmed booking voids its unpaid payment")
    void cancelVoidsPendingPayment() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        Payment p = payment(Payment.PaymentStatus.PENDING);
        userService().deleteBooking(userId, bookingId);
        assertEquals(Payment.PaymentStatus.FAILED, p.getStatus());
    }

    // ---- passenger edit ----

    @Test
    @DisplayName("a booking can only be edited while pending and unpaid")
    void editOnlyWhilePendingAndUnpaid() {
        TravelBookingRequest req = new TravelBookingRequest();
        req.setFromDate(TODAY.plusDays(6));
        req.setToDate(TODAY.plusDays(6));
        req.setFromPlace("Chennai");
        req.setToPlace("Vellore");
        req.setTravelMembers(2);
        req.setAcType("AC");

        // Control: a pending, unpaid booking IS editable, so the refusals below are the rules at work.
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        assertDoesNotThrow(() -> userService().updateBooking(userId, bookingId, req));
        org.mockito.Mockito.clearInvocations(bookings);


        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        assertThrows(RuntimeException.class, () -> userService().updateBooking(userId, bookingId, req));

        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        payment(Payment.PaymentStatus.PENDING);
        assertThrows(RuntimeException.class, () -> userService().updateBooking(userId, bookingId, req));
        verify(bookings, never()).save(any());
    }

    @Test
    @DisplayName("there is no way for a passenger to confirm their own booking")
    void noSelfConfirm() {
        assertFalse(Arrays.stream(UserService.class.getMethods()).anyMatch(m -> m.getName().equals("confirmBooking")));
        assertFalse(Arrays.stream(UserController.class.getMethods()).anyMatch(m -> m.getName().equals("confirmBooking")));
    }

    // ---- driver ----

    @Test
    @DisplayName("a trip cannot be started before its date")
    void noEarlyStart() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        DriverService service = driverService(mock(FileStorageService.class));
        assertThrows(RuntimeException.class, () -> service.startTrip(7L, bookingId.toString()));

        booking.setFromDate(TODAY);
        assertDoesNotThrow(() -> service.startTrip(7L, bookingId.toString()));
        assertEquals(TravelBooking.BookingStatus.STARTED, booking.getStatus());
    }

    @Test
    @DisplayName("an operator-configured early-start window lets a trip start ahead of its date (default: none)")
    void earlyStartWindowIsConfigurable() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        DriverService service = driverService(mock(FileStorageService.class));
        ReflectionTestUtils.setField(service, "earlyStartDays", 5L);
        assertDoesNotThrow(() -> service.startTrip(7L, bookingId.toString()));
    }

    @Test
    @DisplayName("the end-of-trip photo needs a started trip")
    void photoNeedsAStartedTrip() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        FileStorageService storage = mock(FileStorageService.class);
        DriverService service = driverService(storage);
        MockMultipartFile photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[] {1});
        assertThrows(RuntimeException.class, () -> service.uploadEndTripPhoto(7L, bookingId, photo));
        verify(storage, never()).storeImage(any(), any(), any());
    }

    // ---- owner ----

    @Test
    @DisplayName("the owner cannot reassign a trip that is already under way")
    void noReassignMidTrip() {
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        DriverRepository drivers = mock(DriverRepository.class);
        Driver d = new Driver();
        d.setId(9L);
        d.setStatus(Driver.Status.ACTIVE);
        when(drivers.findById(9L)).thenReturn(Optional.of(d));
        OwnerService owner = new OwnerService(bookings, mock(PricingConfigRepository.class), drivers,
                mock(TripDriverPhotoRepository.class), notifications);

        assertThrows(RuntimeException.class, () -> owner.assignDriver(bookingId, 9L));
        assertEquals(7L, booking.getDriverId());
    }
}
