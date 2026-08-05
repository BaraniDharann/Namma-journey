package com.travelplatform.service;

import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A rejected trip used to be dropped on the floor: unassigned, still PENDING, and going nowhere
 * until the owner read a notification and reassigned it by hand. These tests pin the replacement
 * behaviour, including the case that makes the whole thing pointless if it regresses — offering
 * the trip straight back to the driver who just refused it.
 */
class DriverServiceRejectionTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Long REJECTING_DRIVER_ID = 7L;

    private TravelBookingRepository bookingRepository;
    private DriverRepository driverRepository;
    private NotificationService notificationService;
    private BookingDriverRejectionRepository rejectionRepository;
    private DriverService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        driverRepository = mock(DriverRepository.class);
        notificationService = mock(NotificationService.class);
        rejectionRepository = mock(BookingDriverRejectionRepository.class);
        TripDriverPhotoRepository photoRepository = mock(TripDriverPhotoRepository.class);

        service = new DriverService(bookingRepository, driverRepository, notificationService,
                photoRepository, rejectionRepository);

        TravelBooking booking = booking();
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(driverRepository.findById(REJECTING_DRIVER_ID))
                .thenReturn(Optional.of(driver(REJECTING_DRIVER_ID, "Murugan")));
        when(photoRepository.findByBookingId(any())).thenReturn(Optional.empty());
        when(rejectionRepository.findDriverIdsByBookingId(BOOKING_ID))
                .thenReturn(List.of(REJECTING_DRIVER_ID));
    }

    private static TravelBooking booking() {
        TravelBooking booking = new TravelBooking();
        booking.setId(BOOKING_ID);
        booking.setUserId(UUID.randomUUID());
        booking.setDriverId(REJECTING_DRIVER_ID);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setFromPlace("Chennai");
        booking.setToPlace("Madurai");
        booking.setFromDate(LocalDate.of(2026, 8, 1));
        booking.setToDate(LocalDate.of(2026, 8, 3));
        booking.setEstimatedTimeMinutes(400L);
        booking.setTotalAmount(4500.0);
        return booking;
    }

    private static Driver driver(Long id, String name) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setName(name);
        driver.setStatus(Driver.Status.ACTIVE);
        return driver;
    }

    @Test
    @DisplayName("rejecting hands the trip to the next available driver, still awaiting their acceptance")
    void reassignsToNextAvailableDriver() {
        Driver replacement = driver(9L, "Selvi");
        when(driverRepository.findAvailableDriversExcluding(any(), any(), anyList()))
                .thenReturn(List.of(replacement));

        service.rejectBooking(REJECTING_DRIVER_ID, BOOKING_ID.toString());

        ArgumentCaptor<TravelBooking> saved = ArgumentCaptor.forClass(TravelBooking.class);
        verify(bookingRepository).save(saved.capture());
        assertEquals(9L, saved.getValue().getDriverId());
        // PENDING, not CONFIRMED: the replacement is being offered the trip, not given it.
        assertEquals(TravelBooking.BookingStatus.PENDING, saved.getValue().getStatus());

        verify(notificationService).notifyDriverAssigned(any(TravelBooking.class), eq(replacement));
        verify(notificationService).notifyTripRejected(any(), any(), eq(replacement));
    }

    @Test
    @DisplayName("the driver who just refused is excluded from the replacement search")
    void excludesTheRejectingDriver() {
        when(driverRepository.findAvailableDriversExcluding(any(), any(), anyList()))
                .thenReturn(List.of());

        service.rejectBooking(REJECTING_DRIVER_ID, BOOKING_ID.toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> excluded = ArgumentCaptor.forClass(List.class);
        verify(driverRepository).findAvailableDriversExcluding(
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 3)), excluded.capture());
        assertTrue(excluded.getValue().contains(REJECTING_DRIVER_ID),
                "the refusal is worthless if the same driver can be picked again");
    }

    @Test
    @DisplayName("the refusal is recorded before a replacement is looked for")
    void recordsTheRejection() {
        when(rejectionRepository.existsByBookingIdAndDriverId(BOOKING_ID, REJECTING_DRIVER_ID))
                .thenReturn(false);
        when(driverRepository.findAvailableDriversExcluding(any(), any(), anyList()))
                .thenReturn(List.of());

        service.rejectBooking(REJECTING_DRIVER_ID, BOOKING_ID.toString());

        ArgumentCaptor<BookingDriverRejection> rejection =
                ArgumentCaptor.forClass(BookingDriverRejection.class);
        verify(rejectionRepository).save(rejection.capture());
        assertEquals(BOOKING_ID, rejection.getValue().getBookingId());
        assertEquals(REJECTING_DRIVER_ID, rejection.getValue().getDriverId());
    }

    @Test
    @DisplayName("with nobody left to offer it to, the trip falls back to the owner unassigned")
    void fallsBackToTheOwnerWhenNobodyIsAvailable() {
        when(driverRepository.findAvailableDriversExcluding(any(), any(), anyList()))
                .thenReturn(List.of());

        service.rejectBooking(REJECTING_DRIVER_ID, BOOKING_ID.toString());

        ArgumentCaptor<TravelBooking> saved = ArgumentCaptor.forClass(TravelBooking.class);
        verify(bookingRepository).save(saved.capture());
        assertNull(saved.getValue().getDriverId());
        assertEquals(TravelBooking.BookingStatus.PENDING, saved.getValue().getStatus());

        // A null replacement is what switches the owner's notification to "assign one manually".
        verify(notificationService).notifyTripRejected(any(), any(), isNull());
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }
}
