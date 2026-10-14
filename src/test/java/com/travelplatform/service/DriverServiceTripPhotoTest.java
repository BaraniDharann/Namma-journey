package com.travelplatform.service;

import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-of-trip photos were written to ./driverphoto - outside the Docker uploads volume, so every
 * redeploy deleted them, and invisible to any second backend instance. They now go through
 * FileStorageService into the shared FileStore like every other upload.
 */
class DriverServiceTripPhotoTest {

    @Test
    @DisplayName("the end-of-trip photo is stored through the shared file store and its path recorded")
    void tripPhotoGoesThroughTheFileStore() {
        UUID bookingId = UUID.randomUUID();
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        TripDriverPhotoRepository photos = mock(TripDriverPhotoRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        DriverService service = new DriverService(bookings, mock(DriverRepository.class),
                mock(NotificationService.class), photos, mock(BookingDriverRejectionRepository.class), storage);

        TravelBooking booking = new TravelBooking();
        booking.setId(bookingId);
        booking.setDriverId(7L);
        booking.setStatus(TravelBooking.BookingStatus.STARTED);
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(photos.findByBookingIdAndDriverId(bookingId, 7L)).thenReturn(Optional.empty());
        when(photos.save(any(TripDriverPhoto.class))).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile photo = new MockMultipartFile("photo", "end.jpg", "image/jpeg", new byte[] {1});
        String stored = "/uploads/trips/trip_" + bookingId + "/driver_7_x.jpg";
        when(storage.storeImage(photo, "trips/trip_" + bookingId, "driver_7")).thenReturn(stored);

        TripDriverPhoto saved = service.uploadEndTripPhoto(7L, bookingId, photo);

        verify(storage).storeImage(photo, "trips/trip_" + bookingId, "driver_7");
        assertEquals(stored, saved.getPhotoPath());
    }
}
