package com.travelplatform.service;

import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A driver's trip list looked up the end-trip photo once per trip, and the driver - who is the
 * same person on every row - once per trip as well. Now: one query for the trips, one for all
 * their photos, one for the driver.
 */
class DriverServiceBookingListTest {

    @Test
    @DisplayName("a driver's trip list costs three queries whatever its length")
    void tripListIsBatched() {
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        DriverRepository drivers = mock(DriverRepository.class);
        TripDriverPhotoRepository photos = mock(TripDriverPhotoRepository.class);
        DriverService service = new DriverService(bookings, drivers, mock(NotificationService.class), photos,
                mock(BookingDriverRejectionRepository.class), mock(FileStorageService.class));

        TravelBooking a = trip();
        TravelBooking b = trip();
        TravelBooking c = trip();
        when(bookings.findByDriverId(7L)).thenReturn(List.of(a, b, c));
        Driver driver = new Driver();
        driver.setId(7L);
        driver.setName("Murugan");
        when(drivers.findById(7L)).thenReturn(Optional.of(driver));
        TripDriverPhoto photo = new TripDriverPhoto();
        photo.setBookingId(b.getId());
        photo.setPhotoPath("/uploads/trips/b/end.jpg");
        when(photos.findByBookingIdIn(anyCollection())).thenReturn(List.of(photo));

        List<TravelBookingResponse> list = service.getAssignedBookings(7L);

        verify(photos, times(1)).findByBookingIdIn(anyCollection());
        verify(photos, never()).findByBookingId(any());
        verify(drivers, times(1)).findById(7L);
        assertEquals(3, list.size());
        assertNull(list.get(0).getDriverEndTripPhoto());
        assertEquals("/uploads/trips/b/end.jpg", list.get(1).getDriverEndTripPhoto());
        assertEquals("Murugan", list.get(2).getDriver().getName());
    }

    private static TravelBooking trip() {
        TravelBooking t = new TravelBooking();
        t.setId(UUID.randomUUID());
        t.setUserId(UUID.randomUUID());
        t.setDriverId(7L);
        t.setFromPlace("Chennai");
        t.setToPlace("Madurai");
        t.setFromDate(LocalDate.of(2026, 11, 1));
        t.setToDate(LocalDate.of(2026, 11, 1));
        t.setEstimatedTimeMinutes(60L);
        t.setTotalAmount(new BigDecimal("100.00"));
        t.setBookingDate(LocalDateTime.now());
        t.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        t.setBookingType(TravelBooking.BookingType.DISTANCE_BASED);
        return t;
    }
}
