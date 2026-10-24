package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.service.AfterCommitDispatcher;
import com.travelplatform.service.CityService;
import com.travelplatform.service.DispatchService;
import com.travelplatform.service.DriverOfferPublisher;
import com.travelplatform.service.DriverPresenceService;
import com.travelplatform.service.NoOpTransactionManager;
import com.travelplatform.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch matching rounds against the in-memory twins of every store, a fixed clock and the
 * straight-line ETA: the whole engine except Redis and Postgres. The booking "table" behaves like
 * one - each read is a fresh copy, so a write only counts once it is saved.
 */
class MatchingWorkerTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 24, 10, 0, 0);
    private static final long CITY = 1L;
    // Chennai Central. One degree of latitude is 111.195 km on the sphere TripRules uses.
    private static final double LAT = 13.0827;
    private static final double LON = 80.2707;
    private static final double KM = 1 / 111.195;

    private final Map<UUID, TravelBooking> table = new HashMap<>();
    private final Map<Long, Driver> drivers = new HashMap<>();
    private final List<BookingDriverRejection> rejections = new ArrayList<>();
    private final Set<Long> overlapOnFlush = new HashSet<>();
    private final AtomicReference<Clock> clock = new AtomicReference<>();

    private TravelBookingRepository bookingRepository;
    private DriverRepository driverRepository;
    private NotificationService notificationService;
    private DriverOfferPublisher publisher;
    private CityService cityService;
    private DriverPresenceService presence;
    private DriverStateStore states;
    private RideQueue queue;
    private LocalCityLock.Registry locks;
    private DispatchService dispatch;
    private DispatchProperties properties;
    private MatchingWorker worker;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        driverRepository = mock(DriverRepository.class);
        BookingDriverRejectionRepository rejectionRepository = mock(BookingDriverRejectionRepository.class);
        notificationService = mock(NotificationService.class);
        publisher = mock(DriverOfferPublisher.class);

        when(bookingRepository.findById(any(UUID.class)))
                .thenAnswer(i -> Optional.ofNullable(table.get(i.<UUID>getArgument(0))).map(MatchingWorkerTest::copy));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(i -> store(i.getArgument(0)));
        when(bookingRepository.saveAndFlush(any(TravelBooking.class))).thenAnswer(i -> {
            TravelBooking b = i.getArgument(0);
            if (b.getDriverId() != null && overlapOnFlush.remove(b.getDriverId())) {
                throw new DataIntegrityViolationException("violates exclusion constraint \"no_driver_overlap\"");
            }
            return store(b);
        });
        when(bookingRepository.findBusyDriverIds(anyCollection(), any(LocalDate.class))).thenReturn(List.of());
        when(bookingRepository.findOfferedDriverIds(anyCollection())).thenReturn(List.of());
        when(bookingRepository.findOpenDispatchInCity(anyLong())).thenReturn(List.of());

        when(driverRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(drivers.get(i.<Long>getArgument(0))));
        when(driverRepository.findAllById(any())).thenAnswer(i -> {
            List<Driver> found = new ArrayList<>();
            for (Long id : i.<Iterable<Long>>getArgument(0)) {
                if (drivers.containsKey(id)) {
                    found.add(drivers.get(id));
                }
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

        properties = new DispatchProperties();
        cityService = mock(CityService.class);
        City chennai = new City();
        chennai.setId(CITY);
        when(cityService.cityFor(anyDouble(), anyDouble())).thenReturn(Optional.of(chennai));
        when(cityService.activeIds()).thenReturn(List.of(CITY));

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        presence = new DriverPresenceService(noRedis, "caffeine", properties);
        ReflectionTestUtils.setField(presence, "cityService", cityService);

        states = new LocalDriverStateStore(new DriverCardLoader(driverRepository, bookingRepository));
        queue = new LocalRideQueue(() -> clock.get().millis());
        locks = new LocalCityLock.Registry();
        AfterCommitDispatcher inline = new AfterCommitDispatcher(Runnable::run);
        dispatch = new DispatchService(bookingRepository, rejectionRepository, notificationService, publisher, inline,
                queue, new DriverStateUpdates(states));
        at(T0);
        worker = newWorker("a", publisher);
    }

    private MatchingWorker newWorker(String instance, DriverOfferPublisher offers) {
        AfterCommitDispatcher inline = new AfterCommitDispatcher(Runnable::run);
        return new MatchingWorker(cityService, presence, states, queue,
                new LocalCityLock(locks, instance, () -> clock.get().millis(), 5_000),
                new HaversineEtaService(properties), new AssignmentSolver(), dispatch,
                bookingRepository, driverRepository, notificationService, offers, inline,
                new TransactionTemplate(new NoOpTransactionManager()), properties);
    }

    private void at(LocalDateTime time) {
        Clock fixed = Clock.fixed(time.atZone(IST).toInstant(), IST);
        clock.set(fixed);
        ReflectionTestUtils.setField(dispatch, "clock", fixed);
    }

    // ---- fixtures ----

    private Driver driver(long id, double kmNorth, VehicleType vehicle, int seats) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setVehicleType(vehicle);
        d.setVehicleSeats(seats);
        d.setServesCity(true);
        d.setStatus(Driver.Status.ACTIVE);
        d.setVerificationStatus(Driver.Verification.APPROVED);
        drivers.put(id, d);
        presence.report(id, LAT + kmNorth * KM, LON);
        return d;
    }

    private Driver car(long id, double kmNorth) {
        return driver(id, kmNorth, VehicleType.CAR, 4);
    }

    /** A City ride with its pickup {@code kmNorth} of the base point, searching for {@code waited} seconds. */
    private TravelBooking ride(double kmNorth, long waited, VehicleType vehicle, int passengers) {
        TravelBooking b = new TravelBooking();
        b.setId(UUID.randomUUID());
        b.setUserId(UUID.randomUUID());
        b.setUserName("Passenger");
        b.setFromPlace("Pickup");
        b.setToPlace("Drop");
        b.setFromLat(LAT + kmNorth * KM);
        b.setFromLon(LON);
        b.setFromDate(T0.toLocalDate());
        b.setToDate(T0.toLocalDate());
        b.setTotalAmount(new BigDecimal("120.00"));
        b.setStatus(TravelBooking.BookingStatus.PENDING);
        b.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
        b.setDispatchState(DispatchState.SEARCHING);
        b.setDispatchStartedAt(currentTime().minusSeconds(waited));
        b.setServiceMode(TravelBooking.ServiceMode.CITY);
        b.setCityId(CITY);
        b.setVehicleType(vehicle);
        b.setTravelMembers(passengers);
        store(b);
        dispatch.enqueueAfterCommit(b);
        return b;
    }

    private TravelBooking ride(double kmNorth, long waited) {
        return ride(kmNorth, waited, VehicleType.CAR, 1);
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock.get());
    }

    private TravelBooking row(TravelBooking b) {
        return table.get(b.getId());
    }

    private DriverState stateOf(long driverId) {
        return states.cards(List.of(driverId)).get(driverId).state();
    }

    private void round() {
        worker.runRound(CITY);
    }

    // ---- tests ----

    @Test
    @DisplayName("a waiting ride is offered to the nearest free driver, for 15 seconds")
    void rideOfferedToNearestFreeDriver() {
        car(1L, 0.8);
        car(2L, 0.5);
        TravelBooking r = ride(0, 0);

        round();

        assertEquals(DispatchState.OFFERED, row(r).getDispatchState());
        assertEquals(2L, row(r).getDriverId());
        assertEquals(T0.plusSeconds(15), row(r).getOfferExpiresAt());
        assertEquals(0.5, row(r).getOfferDistanceKm(), 0.02);
        assertEquals(DriverState.OFFERED, stateOf(2L));
        assertEquals(Optional.of(new OfferRef(r.getId(), 2L)), queue.currentOffer(2L));
        assertTrue(queue.waiting(CITY, 10).isEmpty());
        verify(publisher).publish(2L, r.getId(), DriverOfferPublisher.Event.OFFER);
        verify(notificationService).notifyRideOffered(any(), any());
    }

    @Test
    @DisplayName("two rides want one driver: the one that has waited longer gets them")
    void contestedDriverGoesToLongerWaitingRide() {
        car(1L, 0.9);   // X: under 1 km from both pickups
        car(2L, -4.0);  // Y: 4 km away, beyond either ride's radius
        TravelBooking a = ride(0, 30);
        TravelBooking b = ride(0, 0);

        round();

        assertEquals(1L, row(a).getDriverId());
        assertNull(row(b).getDriverId());
        assertEquals(DispatchState.SEARCHING, row(b).getDispatchState());
    }

    @Test
    @DisplayName("the round minimises the total, where nearest-first would strand a ride")
    void globalMinimumBeatsGreedy() {
        TravelBooking r1 = ride(0, 0);
        TravelBooking r2 = ride(1.1, 0);
        car(1L, 0.3);   // D1: nearest to R1, and the only driver R2 can reach
        car(2L, -0.6);  // D2: a little further from R1, out of R2's 1 km

        round();

        assertEquals(2L, row(r1).getDriverId());
        assertEquals(1L, row(r2).getDriverId());
    }

    @Test
    @DisplayName("the search radius widens with waiting: 1 km at first, 3 km after 20 s")
    void radiusWidensWithWaiting() {
        car(1L, 2.5);
        TravelBooking r = ride(0, 10);

        round();
        assertEquals(DispatchState.SEARCHING, row(r).getDispatchState());

        at(T0.plusSeconds(15)); // waited 25 s now
        round();
        assertEquals(1L, row(r).getDriverId());
    }

    @Test
    @DisplayName("a driver who declined is never offered that ride again, even as the only one near")
    void declinedDriverNeverReOffered() {
        car(1L, 0.3);
        TravelBooking r = ride(0, 0);
        round();
        assertEquals(1L, row(r).getDriverId());

        dispatch.release(copy(row(r)), 1L);
        assertEquals(DriverState.IDLE, stateOf(1L));
        at(T0.plusSeconds(2));
        round();
        at(T0.plusSeconds(4));
        round();

        assertNull(row(r).getDriverId());
        assertEquals(DispatchState.SEARCHING, row(r).getDispatchState());
        verify(publisher, times(1)).publish(eq(1L), eq(r.getId()), eq(DriverOfferPublisher.Event.OFFER));
        assertTrue(queue.waiting(CITY, 10).stream().anyMatch(t -> t.bookingId().equals(r.getId())));
    }

    @Test
    @DisplayName("an offer past its deadline returns the ride to waiting and frees the driver")
    void expiredOfferReturnsRideAndFreesDriver() {
        car(1L, 0.3);
        TravelBooking r = ride(0, 0);
        round();

        at(T0.plusSeconds(15).plusNanos(1_000_000)); // deadline + 1 ms
        round();

        assertEquals(DispatchState.SEARCHING, row(r).getDispatchState());
        assertNull(row(r).getDriverId());
        assertNull(row(r).getOfferExpiresAt());
        assertEquals(DriverState.IDLE, stateOf(1L));
        assertTrue(rejections.stream().anyMatch(x -> x.getDriverId() == 1L), "silence counts as a decline");
        assertTrue(queue.waiting(CITY, 10).stream().anyMatch(t -> t.bookingId().equals(r.getId())));
        assertEquals(Optional.empty(), queue.currentOffer(1L));
        verify(publisher).publish(1L, r.getId(), DriverOfferPublisher.Event.WITHDRAWN);
    }

    @Test
    @DisplayName("one driver is never given two rides, in one round or the next")
    void oneDriverNeverGetsTwoRidesInARound() {
        car(1L, 0.2);
        TravelBooking a = ride(0, 0);
        TravelBooking b = ride(0.1, 0);

        round();
        at(T0.plusSeconds(2));
        round();

        long offered = List.of(row(a), row(b)).stream().filter(x -> x.getDispatchState() == DispatchState.OFFERED).count();
        assertEquals(1, offered);
        verify(publisher, times(1)).publish(eq(1L), any(), eq(DriverOfferPublisher.Event.OFFER));
    }

    @Test
    @DisplayName("a closer driver in the wrong vehicle, or with too few seats, is never offered the ride")
    void wrongVehicleOrTooFewSeatsNeverOffered() {
        driver(1L, 0.2, VehicleType.CAR, 4);
        driver(2L, 0.3, VehicleType.AUTO, 3);
        TravelBooking bike = ride(0, 0, VehicleType.BIKE, 1);
        driver(3L, 20.2, VehicleType.CAR, 4);
        TravelBooking six = ride(20, 0, VehicleType.CAR, 6);

        round();

        assertNull(row(bike).getDriverId());
        assertNull(row(six).getDriverId());
        verify(publisher, never()).publish(anyLong(), any(), any());

        driver(4L, 0.9, VehicleType.BIKE, 1);
        driver(5L, 20.7, VehicleType.CAR, 7);
        at(T0.plusSeconds(2));
        round();

        assertEquals(4L, row(bike).getDriverId());
        assertEquals(5L, row(six).getDriverId());
    }

    @Test
    @DisplayName("after the search time with nobody near, the ride goes to the owner")
    void searchTimeoutHandsToOwner() {
        TravelBooking r = ride(0, 121);

        round();

        assertEquals(DispatchState.NO_DRIVER, row(r).getDispatchState());
        verify(notificationService).notifyNoDriverNearby(any());
        assertTrue(queue.isEmpty(CITY));
    }

    @Test
    @DisplayName("a driver the database refuses (no_driver_overlap) is passed over and the ride stays waiting")
    void overlapRefusalLeavesRideWaiting() {
        car(1L, 0.2);
        car(2L, 0.6);
        overlapOnFlush.add(1L);
        TravelBooking r = ride(0, 0);

        round();
        assertEquals(DispatchState.SEARCHING, row(r).getDispatchState());
        assertEquals(DriverState.BUSY, stateOf(1L), "the database knows something the store did not");

        at(T0.plusSeconds(2));
        round();
        assertEquals(2L, row(r).getDriverId());
    }

    @Test
    @DisplayName("Redis lost mid-search: the lock taker rebuilds waiting rides and open offers from Postgres")
    void rebuildsQueuesFromDatabaseAfterRedisLoss() {
        TravelBooking searching = new TravelBooking();
        searching.setId(UUID.randomUUID());
        searching.setFromLat(LAT);
        searching.setFromLon(LON);
        searching.setFromDate(T0.toLocalDate());
        searching.setToDate(T0.toLocalDate());
        searching.setStatus(TravelBooking.BookingStatus.PENDING);
        searching.setDispatchMode(TravelBooking.DispatchMode.RIDE_NOW);
        searching.setDispatchState(DispatchState.SEARCHING);
        searching.setDispatchStartedAt(T0.minusSeconds(5));
        searching.setCityId(CITY);
        searching.setVehicleType(VehicleType.CAR);
        searching.setTravelMembers(1);
        store(searching);
        TravelBooking offered = copy(searching);
        offered.setId(UUID.randomUUID());
        offered.setDispatchState(DispatchState.OFFERED);
        offered.setDriverId(5L);
        offered.setOfferExpiresAt(T0.plusSeconds(10));
        store(offered);
        car(5L, 0.4);
        when(bookingRepository.findOpenDispatchInCity(CITY)).thenReturn(List.of(copy(searching), copy(offered)));
        assertTrue(queue.isEmpty(CITY));

        worker.tick();

        assertTrue(queue.waiting(CITY, 10).stream().anyMatch(t -> t.bookingId().equals(searching.getId())));
        assertEquals(Optional.of(new OfferRef(offered.getId(), 5L)), queue.currentOffer(5L));
        assertEquals(DriverState.OFFERED, stateOf(5L), "the offered driver is not handed a second ride");
        assertEquals(List.of(new OfferRef(offered.getId(), 5L)),
                queue.expiredOffers(CITY, clock.get().millis() + 10_001), "the open offer still times out");
    }

    @Test
    @DisplayName("two servers: only the lock holder runs the city's rounds")
    void onlyLockHolderRunsTheCity() {
        DriverOfferPublisher otherPublisher = mock(DriverOfferPublisher.class);
        MatchingWorker other = newWorker("b", otherPublisher);
        car(1L, 0.3);
        car(2L, 0.4);
        ride(0, 0);
        ride(0.1, 0);

        worker.tick();
        other.tick();
        at(T0.plusSeconds(2));
        other.tick();
        worker.tick();

        verify(publisher, times(2)).publish(anyLong(), any(), eq(DriverOfferPublisher.Event.OFFER));
        verify(otherPublisher, never()).publish(anyLong(), any(), any());
        assertEquals(Set.of(CITY), worker.heldCities());
        assertTrue(other.heldCities().isEmpty());
    }

    // ---- the booking table ----

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
        c.setCityId(b.getCityId());
        c.setVehicleType(b.getVehicleType());
        c.setTravelMembers(b.getTravelMembers());
        c.setAdvanceAmount(b.getAdvanceAmount());
        c.setAdvancePaidAt(b.getAdvancePaidAt());
        return c;
    }
}
