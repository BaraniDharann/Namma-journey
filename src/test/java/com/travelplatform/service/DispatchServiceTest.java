package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ride Now dispatch against a real in-memory presence store and a booking "table" that behaves
 * like one: every read is a fresh copy and a failed attempt leaves nothing behind, so a retry sees
 * what a new transaction would.
 */
class DispatchServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 23, 10, 0, 0);

    // Chennai Central; one degree of latitude is about 111.2 km.
    private static final double PICKUP_LAT = 13.0827;
    private static final double PICKUP_LON = 80.2707;

    private final Map<UUID, TravelBooking> table = new HashMap<>();
    private final List<BookingDriverRejection> rejections = new ArrayList<>();
    private final Set<Long> busyDrivers = new HashSet<>();
    private final Set<Long> overlapOnFlush = new HashSet<>();
    private final Map<Long, VehicleType> vehicles = new HashMap<>();
    private final Map<Long, Integer> seatsOf = new HashMap<>();

    private TravelBookingRepository bookingRepository;
    private NotificationService notificationService;
    private DriverOfferPublisher offerPublisher;
    private DriverPresenceService presence;
    private DispatchService dispatch;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        DriverRepository driverRepository = mock(DriverRepository.class);
        BookingDriverRejectionRepository rejectionRepository = mock(BookingDriverRejectionRepository.class);
        notificationService = mock(NotificationService.class);
        offerPublisher = mock(DriverOfferPublisher.class);

        when(bookingRepository.findById(any(UUID.class)))
                .thenAnswer(i -> Optional.ofNullable(table.get(i.<UUID>getArgument(0))).map(DispatchServiceTest::copy));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(i -> store(i.getArgument(0)));
        when(bookingRepository.saveAndFlush(any(TravelBooking.class))).thenAnswer(i -> {
            TravelBooking b = i.getArgument(0);
            if (b.getDriverId() != null && overlapOnFlush.remove(b.getDriverId())) {
                throw new DataIntegrityViolationException("violates exclusion constraint \"no_driver_overlap\"");
            }
            return store(b);
        });

        when(driverRepository.findById(anyLong())).thenAnswer(i -> {
            Driver d = new Driver();
            d.setId(i.getArgument(0));
            d.setName("Driver " + d.getId());
            return Optional.of(d);
        });
        // Mirrors the query: free today, the right vehicle, enough seats. Unlisted drivers are 4-seat cars.
        when(driverRepository.findDispatchableAmong(anyCollection(), any(LocalDate.class), any(VehicleType.class), anyInt()))
                .thenAnswer(i -> {
                    Collection<Long> ids = i.getArgument(0);
                    VehicleType wanted = i.getArgument(2);
                    int seats = i.getArgument(3);
                    return ids.stream()
                            .filter(id -> !busyDrivers.contains(id))
                            .filter(id -> vehicles.getOrDefault(id, VehicleType.CAR) == wanted)
                            .filter(id -> seatsOf.getOrDefault(id, 4) >= seats)
                            .toList();
                });

        when(rejectionRepository.findDriverIdsByBookingId(any()))
                .thenAnswer(i -> rejections.stream().filter(r -> r.getBookingId().equals(i.getArgument(0)))
                        .map(BookingDriverRejection::getDriverId).toList());
        when(rejectionRepository.existsByBookingIdAndDriverId(any(), any()))
                .thenAnswer(i -> rejections.stream().anyMatch(r -> r.getBookingId().equals(i.getArgument(0))
                        && r.getDriverId().equals(i.getArgument(1))));
        when(rejectionRepository.save(any(BookingDriverRejection.class))).thenAnswer(i -> {
            rejections.add(i.getArgument(0));
            return i.getArgument(0);
        });

        DispatchProperties properties = new DispatchProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        presence = new DriverPresenceService(noRedis, "caffeine", properties);

        dispatch = new DispatchService(bookingRepository, driverRepository, rejectionRepository, presence,
                notificationService, offerPublisher, new AfterCommitDispatcher(Runnable::run),
                new TransactionTemplate(new NoOpTransactionManager()), properties);
        at(T0);

        TravelBooking booking = new TravelBooking();
        booking.setId(UUID.randomUUID());
        booking.setUserId(UUID.randomUUID());
        booking.setUserName("Priya");
        booking.setFromPlace("Chennai Central");
        booking.setToPlace("Marina Beach");
        booking.setFromLat(PICKUP_LAT);
        booking.setFromLon(PICKUP_LON);
        booking.setFromDate(T0.toLocalDate());
        booking.setToDate(T0.toLocalDate());
        booking.setTotalAmount(new BigDecimal("120.00"));
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
        booking.setDispatchState(DispatchState.SEARCHING);
        booking.setDispatchStartedAt(T0);
        booking.setServiceMode(TravelBooking.ServiceMode.CITY);
        booking.setVehicleType(VehicleType.CAR);
        booking.setTravelMembers(1);
        bookingId = booking.getId();
        table.put(bookingId, booking);
    }

    private void at(LocalDateTime time) {
        dispatch.setClock(Clock.fixed(time.atZone(IST).toInstant(), IST));
    }

    /** A driver online {@code km} north of the pickup. */
    private void driverAt(long driverId, double km) {
        presence.report(driverId, PICKUP_LAT + km / 111.2, PICKUP_LON);
    }

    private TravelBooking booking() {
        return table.get(bookingId);
    }

    @Test
    @DisplayName("the nearest driver in the first ring gets the offer, for 30 seconds")
    void offersNearestDriver() {
        driverAt(1L, 0.8);
        driverAt(2L, 0.5);
        driverAt(3L, 2.0);

        dispatch.dispatch(bookingId);

        assertEquals(DispatchState.OFFERED, booking().getDispatchState());
        assertEquals(2L, booking().getDriverId());
        assertEquals(T0.plusSeconds(30), booking().getOfferExpiresAt());
        assertEquals(0.5, booking().getOfferDistanceKm(), 0.02);
        verify(offerPublisher).publish(2L, bookingId, DriverOfferPublisher.Event.OFFER);
        verify(notificationService).notifyRideOffered(any(), any());
    }

    @Test
    @DisplayName("with nobody within 1 km the search widens to 3 km, then 5 km")
    void widensTheRing() {
        driverAt(4L, 4.2);
        driverAt(5L, 2.6);

        dispatch.dispatch(bookingId);

        assertEquals(5L, booking().getDriverId());
    }

    @Test
    @DisplayName("a driver beyond 5 km is never offered the ride")
    void ignoresDriversBeyondTheLastRing() {
        driverAt(6L, 6.0);

        dispatch.dispatch(bookingId);

        assertEquals(DispatchState.SEARCHING, booking().getDispatchState());
        assertNull(booking().getDriverId());
    }

    @Test
    @DisplayName("an offer still inside its 30 seconds is left with its driver")
    void leavesALiveOfferAlone() {
        driverAt(1L, 0.3);
        driverAt(2L, 0.6);
        dispatch.dispatch(bookingId);

        at(T0.plusSeconds(20));
        dispatch.sweep();

        assertEquals(1L, booking().getDriverId());
        assertEquals(DispatchState.OFFERED, booking().getDispatchState());
    }

    @Test
    @DisplayName("silence for 30 seconds counts as a decline and the next nearest driver is offered")
    void expiredOfferMovesOn() {
        driverAt(1L, 0.3);
        driverAt(2L, 0.6);
        dispatch.dispatch(bookingId);

        at(T0.plusSeconds(31));
        when(bookingRepository.findDispatchWork(any())).thenReturn(List.of(bookingId));
        dispatch.sweep();

        assertEquals(2L, booking().getDriverId());
        assertEquals(T0.plusSeconds(61), booking().getOfferExpiresAt());
        assertTrue(rejections.stream().anyMatch(r -> r.getDriverId() == 1L));
        verify(offerPublisher).publish(1L, bookingId, DriverOfferPublisher.Event.WITHDRAWN);
    }

    @Test
    @DisplayName("a driver who declined is not offered the same ride again")
    void declinedDriverIsSkipped() {
        driverAt(1L, 0.3);
        driverAt(2L, 0.6);
        dispatch.dispatch(bookingId);

        dispatch.release(copy(booking()), 1L);

        assertEquals(2L, booking().getDriverId());
        assertEquals(DispatchState.OFFERED, booking().getDispatchState());
    }

    @Test
    @DisplayName("a driver already busy today, or holding another offer, is passed over")
    void busyDriverIsSkipped() {
        driverAt(1L, 0.3);
        driverAt(2L, 0.9);
        busyDrivers.add(1L);

        dispatch.dispatch(bookingId);

        assertEquals(2L, booking().getDriverId());
    }

    @Test
    @DisplayName("a driver claimed by another booking in the same instant is skipped for the next")
    void raceLostToAnotherBooking() {
        driverAt(1L, 0.3);
        driverAt(2L, 0.6);
        overlapOnFlush.add(1L);

        dispatch.dispatch(bookingId);

        assertEquals(2L, booking().getDriverId());
    }

    @Test
    @DisplayName("a bike ride goes to the nearest bike, never to a closer car or auto")
    void bikeRideOnlyReachesBikes() {
        TravelBooking b = booking();
        b.setVehicleType(VehicleType.BIKE);
        store(b);
        vehicles.put(1L, VehicleType.CAR);
        vehicles.put(2L, VehicleType.AUTO);
        vehicles.put(3L, VehicleType.BIKE);
        driverAt(1L, 0.2);
        driverAt(2L, 0.3);
        driverAt(3L, 0.9);

        dispatch.dispatch(bookingId);

        assertEquals(3L, booking().getDriverId());
    }

    @Test
    @DisplayName("a car ride for six skips the nearer four-seater")
    void carRideNeedsEnoughSeats() {
        TravelBooking b = booking();
        b.setTravelMembers(6);
        store(b);
        seatsOf.put(1L, 4);
        seatsOf.put(2L, 7);
        driverAt(1L, 0.2);
        driverAt(2L, 0.7);

        dispatch.dispatch(bookingId);

        assertEquals(2L, booking().getDriverId());
    }

    @Test
    @DisplayName("after the search time with nobody near, the owner takes over")
    void givesUpAfterSearchTimeout() {
        dispatch.dispatch(bookingId);
        assertEquals(DispatchState.SEARCHING, booking().getDispatchState());
        verify(notificationService, never()).notifyNoDriverNearby(any());

        at(T0.plusSeconds(121));
        dispatch.dispatch(bookingId);

        assertEquals(DispatchState.NO_DRIVER, booking().getDispatchState());
        verify(notificationService).notifyNoDriverNearby(any());
    }

    @Test
    @DisplayName("a driver who comes online during the search is found by the next sweep")
    void driverComingOnlineIsPickedUp() {
        dispatch.dispatch(bookingId);
        assertNull(booking().getDriverId());

        driverAt(9L, 1.5);
        at(T0.plusSeconds(5));
        dispatch.dispatch(bookingId);

        assertEquals(9L, booking().getDriverId());
    }

    @Test
    @DisplayName("an offer past its deadline reads as expired, so accepting it is refused")
    void expiryIsVisibleToAccept() {
        driverAt(1L, 0.3);
        dispatch.dispatch(bookingId);

        assertFalse(dispatch.offerExpired(booking()));
        at(T0.plusSeconds(30));
        assertTrue(dispatch.offerExpired(booking()));
    }

    @Test
    @DisplayName("scheduled bookings and matched rides are never touched")
    void leavesOtherBookingsAlone() {
        driverAt(1L, 0.3);
        TravelBooking b = booking();
        b.setDispatchState(DispatchState.MATCHED);
        b.setDriverId(7L);
        b.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        store(b);

        dispatch.dispatch(bookingId);

        assertEquals(7L, booking().getDriverId());
        verify(offerPublisher, never()).publish(anyLong(), any(), any());
    }

    @Test
    @DisplayName("an owner assignment or acceptance settles the search")
    void markMatchedClearsTheOffer() {
        driverAt(1L, 0.3);
        dispatch.dispatch(bookingId);

        TravelBooking b = booking();
        DispatchService.markMatched(b);

        assertEquals(DispatchState.MATCHED, b.getDispatchState());
        assertNull(b.getOfferExpiresAt());
    }

    private final AtomicInteger version = new AtomicInteger();

    private TravelBooking store(TravelBooking b) {
        TravelBooking saved = copy(b);
        saved.setVersion((long) version.incrementAndGet());
        table.put(saved.getId(), saved);
        return copy(saved);
    }

    private static TravelBooking copy(TravelBooking b) {
        TravelBooking c = new TravelBooking();
        c.setId(b.getId());
        c.setUserId(b.getUserId());
        c.setUserName(b.getUserName());
        c.setFromPlace(b.getFromPlace());
        c.setToPlace(b.getToPlace());
        c.setFromLat(b.getFromLat());
        c.setFromLon(b.getFromLon());
        c.setFromDate(b.getFromDate());
        c.setToDate(b.getToDate());
        c.setTotalAmount(b.getTotalAmount());
        c.setStatus(b.getStatus());
        c.setDriverId(b.getDriverId());
        c.setDispatchMode(b.getDispatchMode());
        c.setDispatchState(b.getDispatchState());
        c.setDispatchStartedAt(b.getDispatchStartedAt());
        c.setOfferExpiresAt(b.getOfferExpiresAt());
        c.setOfferDistanceKm(b.getOfferDistanceKm());
        c.setVersion(b.getVersion());
        c.setServiceMode(b.getServiceMode());
        c.setVehicleType(b.getVehicleType());
        c.setTravelMembers(b.getTravelMembers());
        return c;
    }
}
