package com.travelplatform.service;

import com.travelplatform.dto.DriverOfferView;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.BookingStatus;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one-row offer check an online driver's phone polls. It must answer from the driver's open
 * offer alone, and never show an offer whose time has run out: accepting that would be refused.
 */
class DriverServiceOfferTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 10, 0, 0);
    private static final Long DRIVER_ID = 7L;

    private TravelBookingRepository bookingRepository;
    private DriverService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        service = new DriverService(bookingRepository, mock(DriverRepository.class), mock(NotificationService.class),
                mock(TripDriverPhotoRepository.class), mock(BookingDriverRejectionRepository.class),
                mock(FileStorageService.class));
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW.atZone(IST).toInstant(), IST));
        when(bookingRepository.findFirstByDriverIdAndDispatchStateAndStatus(
                DRIVER_ID, DispatchState.OFFERED, BookingStatus.PENDING)).thenReturn(Optional.empty());
    }

    private TravelBooking offerExpiringAt(LocalDateTime deadline) {
        TravelBooking b = new TravelBooking();
        b.setId(UUID.randomUUID());
        b.setDriverId(DRIVER_ID);
        b.setStatus(BookingStatus.PENDING);
        b.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
        b.setDispatchState(DispatchState.OFFERED);
        b.setOfferExpiresAt(deadline);
        b.setOfferDistanceKm(0.6);
        b.setFromPlace("Chennai Central");
        b.setToPlace("Marina Beach");
        b.setFromLat(13.0827);
        b.setFromLon(80.2707);
        b.setToLat(13.05);
        b.setToLon(80.28);
        b.setTotalAmount(new BigDecimal("120.00"));
        b.setServiceMode(TravelBooking.ServiceMode.CITY);
        b.setUserName("Priya");
        b.setTravelMembers(2);
        b.setDistanceKm(4.2);
        when(bookingRepository.findFirstByDriverIdAndDispatchStateAndStatus(
                DRIVER_ID, DispatchState.OFFERED, BookingStatus.PENDING)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    @DisplayName("no open offer gives nothing")
    void noOfferGivesEmpty() {
        assertTrue(service.currentOffer(DRIVER_ID).isEmpty());
    }

    @Test
    @DisplayName("a live offer comes with the seconds left to accept it")
    void liveOfferGivesSecondsLeft() {
        TravelBooking b = offerExpiringAt(NOW.plusSeconds(9));

        DriverOfferView view = service.currentOffer(DRIVER_ID).orElseThrow();

        assertEquals(b.getId(), view.bookingId());
        assertEquals(9, view.secondsLeft());
        assertEquals("Marina Beach", view.toPlace());
        assertEquals("CITY", view.serviceMode());
        assertEquals("CAR", view.vehicleType());
        assertEquals(0.6, view.offerDistanceKm());
        assertEquals(2, view.travelMembers());
    }

    @Test
    @DisplayName("an offer past its deadline gives nothing")
    void expiredOfferGivesEmpty() {
        offerExpiringAt(NOW);
        assertTrue(service.currentOffer(DRIVER_ID).isEmpty());

        offerExpiringAt(NOW.minusSeconds(3));
        assertTrue(service.currentOffer(DRIVER_ID).isEmpty());
    }
}
