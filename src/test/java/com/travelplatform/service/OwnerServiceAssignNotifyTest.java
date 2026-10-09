package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PricingConfigRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * When the owner hands a trip to a driver by hand, that driver has to hear about it: the in-app
 * alert, the email and the Telegram card. Before this, only the automatic assignment at booking
 * time notified anyone, so a manually assigned driver learnt of the trip only by opening the app.
 */
class OwnerServiceAssignNotifyTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private TravelBookingRepository bookingRepository;
    private DriverRepository driverRepository;
    private NotificationService notificationService;
    private OwnerService service;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        driverRepository = mock(DriverRepository.class);
        notificationService = mock(NotificationService.class);
        service = new OwnerService(bookingRepository, mock(PricingConfigRepository.class), driverRepository,
                mock(TripDriverPhotoRepository.class), notificationService);

        booking = new TravelBooking();
        booking.setId(BOOKING_ID);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setFromDate(LocalDate.of(2026, 10, 11));
        booking.setToDate(LocalDate.of(2026, 10, 11));
        booking.setEstimatedTimeMinutes(180L);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Driver driver(long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setStatus(Driver.Status.ACTIVE);
        when(driverRepository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    @DisplayName("assigning a new driver notifies that driver")
    void notifiesNewDriver() {
        booking.setDriverId(76L);
        Driver d = driver(88L);
        service.assignDriver(BOOKING_ID, 88L);
        verify(notificationService).notifyDriverAssigned(eq(booking), eq(d));
    }

    @Test
    @DisplayName("re-saving the same driver does not send a duplicate notification")
    void noDuplicateForSameDriver() {
        booking.setDriverId(88L);
        driver(88L);
        service.assignDriver(BOOKING_ID, 88L);
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }
}
