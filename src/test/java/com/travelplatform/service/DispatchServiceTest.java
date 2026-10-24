package com.travelplatform.service;

import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.service.dispatch.DriverCardLoader;
import com.travelplatform.service.dispatch.DriverState;
import com.travelplatform.service.dispatch.DriverStateStore;
import com.travelplatform.service.dispatch.DriverStateUpdates;
import com.travelplatform.service.dispatch.LocalDriverStateStore;
import com.travelplatform.service.dispatch.LocalRideQueue;
import com.travelplatform.service.dispatch.OfferRef;
import com.travelplatform.service.dispatch.RideQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens to a City ride between matching rounds: it is queued, a driver declines or
 * accepts, the owner assigns one, the traveller cancels. Finding drivers and sending offers is
 * MatchingWorker's job and is tested there.
 */
class DispatchServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 23, 10, 0, 0);
    private static final long CITY_ID = 1L;

    private final List<BookingDriverRejection> rejections = new ArrayList<>();

    private TravelBookingRepository bookingRepository;
    private NotificationService notificationService;
    private DriverOfferPublisher offerPublisher;
    private RideQueue queue;
    private DriverStateStore states;
    private DispatchService dispatch;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        DriverRepository driverRepository = mock(DriverRepository.class);
        BookingDriverRejectionRepository rejectionRepository = mock(BookingDriverRejectionRepository.class);
        notificationService = mock(NotificationService.class);
        offerPublisher = mock(DriverOfferPublisher.class);

        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        when(bookingRepository.findBusyDriverIds(anyCollection(), any(LocalDate.class))).thenReturn(List.of());
        when(bookingRepository.findOfferedDriverIds(anyCollection())).thenReturn(List.of());
        when(driverRepository.findAllById(any())).thenAnswer(i -> {
            List<Driver> found = new ArrayList<>();
            for (Long id : i.<Iterable<Long>>getArgument(0)) {
                Driver d = new Driver();
                d.setId(id);
                found.add(d);
            }
            return found;
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

        queue = new LocalRideQueue(() -> T0.atZone(IST).toInstant().toEpochMilli());
        states = new LocalDriverStateStore(new DriverCardLoader(driverRepository, bookingRepository));
        dispatch = new DispatchService(bookingRepository, rejectionRepository, notificationService, offerPublisher,
                new AfterCommitDispatcher(Runnable::run), queue, new DriverStateUpdates(states));
        dispatch.setClock(Clock.fixed(T0.atZone(IST).toInstant(), IST));

        booking = new TravelBooking();
        booking.setId(UUID.randomUUID());
        booking.setUserId(UUID.randomUUID());
        booking.setFromPlace("Chennai Central");
        booking.setToPlace("Marina Beach");
        booking.setFromLat(13.0827);
        booking.setFromLon(80.2707);
        booking.setFromDate(LocalDate.now(IST));
        booking.setToDate(LocalDate.now(IST));
        booking.setTotalAmount(new BigDecimal("120.00"));
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
        booking.setDispatchState(DispatchState.SEARCHING);
        booking.setDispatchStartedAt(T0.minusSeconds(10));
        booking.setServiceMode(TravelBooking.ServiceMode.CITY);
        booking.setCityId(CITY_ID);
        booking.setVehicleType(VehicleType.CAR);
        booking.setTravelMembers(2);
    }

    /** The booking as a round leaves it once driver {@code driverId} has been offered it. */
    private void offeredTo(long driverId) {
        dispatch.enqueueAfterCommit(booking);
        booking.setDriverId(driverId);
        booking.setDispatchState(DispatchState.OFFERED);
        booking.setOfferExpiresAt(T0.plusSeconds(15));
        queue.markOffered(CITY_ID, booking.getId(), driverId, DispatchService.epochMs(T0.plusSeconds(15)));
        states.cards(List.of(driverId));
        states.setState(driverId, DriverState.OFFERED);
    }

    private DriverState stateOf(long driverId) {
        return states.cards(List.of(driverId)).get(driverId).state();
    }

    @Test
    @DisplayName("a new City ride joins its city's waiting queue, search start and all")
    void newRideIsQueued() {
        dispatch.enqueueAfterCommit(booking);

        assertEquals(1, queue.waiting(CITY_ID, 10).size());
        var ticket = queue.waiting(CITY_ID, 10).get(0);
        assertEquals(booking.getId(), ticket.bookingId());
        assertEquals(2, ticket.passengers());
        assertEquals(DispatchService.epochMs(T0.minusSeconds(10)), ticket.searchStartedEpochMs());
    }

    @Test
    @DisplayName("a ride with no city (booked before cities existed) is handed to the owner at once")
    void rideWithoutCityGoesToOwner() {
        booking.setCityId(null);

        dispatch.enqueueAfterCommit(booking);

        assertEquals(DispatchState.NO_DRIVER, booking.getDispatchState());
        verify(notificationService).notifyNoDriverNearby(booking);
        assertTrue(queue.isEmpty(CITY_ID));
    }

    @Test
    @DisplayName("a Trip, or anything still owing an advance, is never queued for matching")
    void tripsAndUnpaidAdvancesNeverQueued() {
        TravelBooking trip = booking;
        trip.setDispatchMode(TravelBooking.DispatchMode.SCHEDULED);
        dispatch.enqueueAfterCommit(trip);
        assertTrue(queue.isEmpty(CITY_ID));

        trip.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
        trip.setAdvanceAmount(new BigDecimal("24.00"));
        dispatch.enqueueAfterCommit(trip);
        assertTrue(queue.isEmpty(CITY_ID));
    }

    @Test
    @DisplayName("a decline frees the driver, bars them from this ride, and puts the ride back in waiting")
    void declineRequeuesWithoutTheDriver() {
        offeredTo(7L);

        dispatch.release(booking, 7L);

        assertEquals(DispatchState.SEARCHING, booking.getDispatchState());
        assertNull(booking.getDriverId());
        assertNull(booking.getOfferExpiresAt());
        assertEquals(DriverState.IDLE, stateOf(7L));
        assertEquals(Set.of(7L), queue.declined(booking.getId()));
        assertEquals(Optional.empty(), queue.currentOffer(7L));
        assertEquals(booking.getId(), queue.waiting(CITY_ID, 10).get(0).bookingId());
        assertTrue(rejections.stream().anyMatch(r -> r.getDriverId() == 7L));
    }

    @Test
    @DisplayName("backing out of an accepted ride restarts its search clock and re-seeds earlier declines")
    void backOutRestartsTheClock() {
        rejections.add(rejection(booking.getId(), 3L));
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);
        booking.setDispatchState(DispatchState.MATCHED);
        booking.setDriverId(7L);

        dispatch.release(booking, 7L);

        assertEquals(T0, booking.getDispatchStartedAt());
        assertEquals(TravelBooking.BookingStatus.PENDING, booking.getStatus());
        assertEquals(DispatchService.epochMs(T0), queue.waiting(CITY_ID, 10).get(0).searchStartedEpochMs());
        assertEquals(Set.of(3L, 7L), queue.declined(booking.getId()));
    }

    @Test
    @DisplayName("an acceptance settles the search: out of the queue, the driver busy")
    void markMatchedSettlesTheSearch() {
        offeredTo(7L);
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);

        dispatch.markMatched(booking);

        assertEquals(DispatchState.MATCHED, booking.getDispatchState());
        assertNull(booking.getOfferExpiresAt());
        assertTrue(queue.isEmpty(CITY_ID));
        assertEquals(Optional.empty(), queue.currentOffer(7L));
        assertEquals(DriverState.BUSY, stateOf(7L));
    }

    @Test
    @DisplayName("an owner assignment frees the driver who held the offer and withdraws it from them")
    void ownerAssignmentWithdrawsTheOffer() {
        offeredTo(7L);
        booking.setDriverId(9L);
        booking.setStatus(TravelBooking.BookingStatus.CONFIRMED);

        states.cards(List.of(9L));
        dispatch.ownerAssigned(booking, 7L);

        assertEquals(DriverState.IDLE, stateOf(7L));
        assertEquals(DriverState.BUSY, stateOf(9L));
        verify(offerPublisher).publish(7L, booking.getId(), DriverOfferPublisher.Event.WITHDRAWN);
        assertTrue(queue.isEmpty(CITY_ID));
    }

    @Test
    @DisplayName("a traveller's cancel takes the ride out of the queue and withdraws a held offer")
    void cancelWithdrawsTheOffer() {
        offeredTo(7L);
        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);

        dispatch.cancelled(booking);

        assertTrue(queue.isEmpty(CITY_ID));
        assertEquals(Optional.empty(), queue.currentOffer(7L));
        verify(offerPublisher).publish(7L, booking.getId(), DriverOfferPublisher.Event.WITHDRAWN);
    }

    @Test
    @DisplayName("cancelling a ride still searching tells no driver anything")
    void cancelWhileSearchingPushesNothing() {
        dispatch.enqueueAfterCommit(booking);
        booking.setStatus(TravelBooking.BookingStatus.CANCELLED);

        dispatch.cancelled(booking);

        assertTrue(queue.isEmpty(CITY_ID));
        verify(offerPublisher, never()).publish(anyLong(), any(), any());
    }

    @Test
    @DisplayName("an offer past its deadline reads as expired, so accepting it is refused")
    void expiryIsVisibleToAccept() {
        offeredTo(7L);

        assertFalse(dispatch.offerExpired(booking));
        dispatch.setClock(Clock.fixed(T0.plusSeconds(15).atZone(IST).toInstant(), IST));
        assertTrue(dispatch.offerExpired(booking));
        assertEquals(Optional.of(new OfferRef(booking.getId(), 7L)), queue.currentOffer(7L));
    }

    private static BookingDriverRejection rejection(UUID bookingId, long driverId) {
        BookingDriverRejection r = new BookingDriverRejection();
        r.setBookingId(bookingId);
        r.setDriverId(driverId);
        return r;
    }
}
