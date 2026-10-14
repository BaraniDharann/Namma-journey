package com.travelplatform.service;

import com.travelplatform.dto.TravelBookingResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TripDriverPhoto;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "My bookings" ran two extra queries per booking (trip photo, driver). With ~30 bookings a user
 * that was ~60 queries per page load, and the load test showed it: at ~100 req/s the 20-connection
 * pool was exhausted with 53 requests queued and p95 above 11s. Photos and drivers are now loaded
 * for the whole list in one query each.
 */
class UserServiceBookingListTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("a list of bookings costs three queries in total, not two more per booking")
    void listUsesBatchedLookups() {
        TravelBookingRepository bookings = mock(TravelBookingRepository.class);
        DriverRepository drivers = mock(DriverRepository.class);
        TripDriverPhotoRepository photos = mock(TripDriverPhotoRepository.class);
        UserService service = new UserService(bookings, mock(RoutingService.class), drivers, mock(OwnerService.class),
                mock(UserRepository.class), mock(OtpService.class), mock(PasswordEncoder.class),
                mock(NotificationService.class), photos, new TransactionTemplate(mock(PlatformTransactionManager.class)));

        TravelBooking withDriverAndPhoto = booking(7L);
        TravelBooking sameDriver = booking(7L);
        TravelBooking unassigned = booking(null);
        when(bookings.findByUserId(USER)).thenReturn(List.of(withDriverAndPhoto, sameDriver, unassigned));

        Driver murugan = new Driver();
        murugan.setId(7L);
        murugan.setName("Murugan");
        murugan.setMobile("9876500000");
        when(drivers.findAllById(anyIterable())).thenReturn(List.of(murugan));

        TripDriverPhoto photo = new TripDriverPhoto();
        photo.setBookingId(withDriverAndPhoto.getId());
        photo.setPhotoPath("/uploads/trips/x/end.jpg");
        when(photos.findByBookingIdIn(anyCollection())).thenReturn(List.of(photo));

        List<TravelBookingResponse> list = service.getAllBookings(USER);

        verify(photos, times(1)).findByBookingIdIn(anyCollection());
        verify(drivers, times(1)).findAllById(anyIterable());
        verify(photos, never()).findByBookingId(any());
        verify(drivers, never()).findById(any());

        assertEquals(3, list.size());
        assertEquals("/uploads/trips/x/end.jpg", list.get(0).getDriverEndTripPhoto());
        assertEquals("Murugan", list.get(0).getDriver().getName());
        assertEquals("Murugan", list.get(1).getDriver().getName());
        assertNull(list.get(1).getDriverEndTripPhoto());
        assertNull(list.get(2).getDriver());
    }

    private static TravelBooking booking(Long driverId) {
        TravelBooking b = new TravelBooking();
        b.setId(UUID.randomUUID());
        b.setUserId(USER);
        b.setDriverId(driverId);
        b.setFromPlace("Chennai");
        b.setToPlace("Madurai");
        b.setFromDate(LocalDate.of(2026, 11, 1));
        b.setToDate(LocalDate.of(2026, 11, 1));
        b.setEstimatedTimeMinutes(60L);
        b.setTotalAmount(new BigDecimal("100.00"));
        b.setBookingDate(LocalDateTime.now());
        b.setStatus(TravelBooking.BookingStatus.PENDING);
        b.setBookingType(TravelBooking.BookingType.DISTANCE_BASED);
        return b;
    }
}
