package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Accept and reject only make sense on a trip that is still waiting for its driver. Without a
 * status check, a stale Telegram button or a replayed request could drag a cancelled or finished
 * trip back to CONFIRMED, or bounce a trip already under way back to PENDING with a new driver.
 */
class DriverServiceStatusGuardTest {

    private static final UUID BOOKING_ID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final Long DRIVER_ID = 7L;

    private TravelBookingRepository bookingRepository;
    private NotificationService notificationService;
    private DriverService service;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        DriverRepository driverRepository = mock(DriverRepository.class);
        notificationService = mock(NotificationService.class);
        TripDriverPhotoRepository photoRepository = mock(TripDriverPhotoRepository.class);
        BookingDriverRejectionRepository rejectionRepository = mock(BookingDriverRejectionRepository.class);

        service = new DriverService(bookingRepository, driverRepository, notificationService,
                photoRepository, rejectionRepository);

        booking = new TravelBooking();
        booking.setId(BOOKING_ID);
        booking.setUserId(UUID.randomUUID());
        booking.setDriverId(DRIVER_ID);
        booking.setFromPlace("Chennai");
        booking.setToPlace("Madurai");
        booking.setFromDate(LocalDate.of(2026, 8, 1));
        booking.setToDate(LocalDate.of(2026, 8, 3));
        booking.setEstimatedTimeMinutes(400L);
        booking.setTotalAmount(4500.0);

        Driver driver = new Driver();
        driver.setId(DRIVER_ID);
        driver.setName("Murugan");
        driver.setStatus(Driver.Status.ACTIVE);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));
        when(photoRepository.findByBookingId(any())).thenReturn(Optional.empty());
    }

    @ParameterizedTest(name = "accepting a {0} trip is refused")
    @EnumSource(value = TravelBooking.BookingStatus.class, names = {"STARTED", "COMPLETED", "CANCELLED"})
    void acceptRefusedOutsidePending(TravelBooking.BookingStatus status) {
        booking.setStatus(status);

        assertThrows(RuntimeException.class,
                () -> service.acceptBooking(DRIVER_ID, BOOKING_ID.toString()));

        assertEquals(status, booking.getStatus(), "a refused accept must not change the trip");
        verify(bookingRepository, never()).save(any());
        verify(notificationService, never()).notifyTripAccepted(any(), any());
    }

    @Test
    @DisplayName("accepting a trip this driver already accepted is a quiet no-op, not an error")
    void repeatedAcceptIsIdempotent() {
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);

        var response = service.acceptBooking(DRIVER_ID, BOOKING_ID.toString());

        assertEquals("CONFIRMED", response.getStatus());
        verify(bookingRepository, never()).save(any());
        verify(notificationService, never()).notifyTripAccepted(any(), any());
    }

    @ParameterizedTest(name = "rejecting a {0} trip is refused")
    @EnumSource(value = TravelBooking.BookingStatus.class, names = {"CONFIRMED", "STARTED", "COMPLETED", "CANCELLED"})
    void rejectRefusedOutsidePending(TravelBooking.BookingStatus status) {
        booking.setStatus(status);

        assertThrows(RuntimeException.class,
                () -> service.rejectBooking(DRIVER_ID, BOOKING_ID.toString()));

        assertEquals(status, booking.getStatus());
        assertEquals(DRIVER_ID, booking.getDriverId(), "a refused reject must not unassign the driver");
        verify(bookingRepository, never()).save(any());
    }
}
