package com.travelplatform.config;

import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Location updates only checked that the driverId in the body was the caller, so any driver
 * could post positions onto any passenger's trip. Now the booking must be assigned to that
 * driver and actually be live.
 */
class BookingAccessGuardLocationTest {

    private final TravelBookingRepository repo = mock(TravelBookingRepository.class);
    private final BookingAccessGuard guard = new BookingAccessGuard(repo);
    private final UUID id = UUID.randomUUID();

    private void booking(Long driverId, TravelBooking.BookingStatus status) {
        TravelBooking b = new TravelBooking();
        b.setId(id);
        b.setDriverId(driverId);
        b.setStatus(status);
        when(repo.findById(id)).thenReturn(Optional.of(b));
    }

    @Test
    @DisplayName("the assigned driver may report positions on a confirmed or started trip")
    void assignedDriverOnLiveTrip() {
        booking(7L, TravelBooking.BookingStatus.STARTED);
        assertTrue(guard.canReportLocation(7L, id.toString()));
        booking(7L, TravelBooking.BookingStatus.CONFIRMED);
        assertTrue(guard.canReportLocation(7L, id.toString()));
    }

    @Test
    @DisplayName("another driver, a finished trip, or an unknown booking is refused")
    void everythingElseIsRefused() {
        booking(7L, TravelBooking.BookingStatus.STARTED);
        assertFalse(guard.canReportLocation(9L, id.toString()));
        booking(7L, TravelBooking.BookingStatus.COMPLETED);
        assertFalse(guard.canReportLocation(7L, id.toString()));
        booking(7L, TravelBooking.BookingStatus.CANCELLED);
        assertFalse(guard.canReportLocation(7L, id.toString()));
        assertFalse(guard.canReportLocation(7L, "not-a-uuid"));
        assertFalse(guard.canReportLocation(7L, UUID.randomUUID().toString()));
        assertFalse(guard.canReportLocation(null, id.toString()));
    }
}
