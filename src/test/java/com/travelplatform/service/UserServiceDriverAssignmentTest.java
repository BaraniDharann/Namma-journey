package com.travelplatform.service;

import com.travelplatform.dto.RouteInfo;
import com.travelplatform.dto.TravelBookingRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.User;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two travellers booking the same dates at the same moment used to both be handed the first free
 * driver: availability was read, then written, with nothing in between to stop the second
 * request. The database now refuses the second insert (constraint no_driver_overlap); these tests
 * pin what the service does when that happens - try the next free driver, and only when nobody is
 * left fall back to an unassigned booking for the owner, never fail the traveller's booking.
 */
class UserServiceDriverAssignmentTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444");

    private TravelBookingRepository bookingRepository;
    private DriverRepository driverRepository;
    private RoutingService routingService;
    private NotificationService notificationService;
    private PlatformTransactionManager txManager;
    private UserService service;

    private final List<Long> driverIdsAtSave = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        driverRepository = mock(DriverRepository.class);
        routingService = mock(RoutingService.class);
        notificationService = mock(NotificationService.class);
        OwnerService ownerService = mock(OwnerService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TripDriverPhotoRepository photoRepository = mock(TripDriverPhotoRepository.class);
        txManager = mock(PlatformTransactionManager.class);

        service = new UserService(bookingRepository, routingService, driverRepository, ownerService,
                userRepository, mock(OtpService.class), mock(PasswordEncoder.class), notificationService,
                photoRepository, new TransactionTemplate(txManager));

        User user = new User();
        user.setId(USER_ID);
        user.setPhone("9876543210");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(routingService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteInfo(460.0, 480L, "NH38"));
        when(ownerService.getCurrentPricePerKm()).thenReturn(14.0);
        when(photoRepository.findByBookingId(any())).thenReturn(Optional.empty());
        when(driverRepository.findById(any())).thenReturn(Optional.empty());
    }

    private static TravelBookingRequest request() {
        TravelBookingRequest r = new TravelBookingRequest();
        r.setUserName("Kavya");
        r.setUserPhone("9876543210");
        r.setFromPlace("Chennai");
        r.setToPlace("Madurai");
        r.setFromLat(13.08);
        r.setFromLon(80.27);
        r.setToLat(9.93);
        r.setToLon(78.12);
        r.setFromDate(LocalDate.of(2026, 11, 1));
        r.setToDate(LocalDate.of(2026, 11, 2));
        r.setTravelMembers(4);
        r.setAcType("AC");
        return r;
    }

    private static Driver driver(long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setStatus(Driver.Status.ACTIVE);
        return d;
    }

    /** What Postgres raises when an insert breaks the EXCLUDE constraint. */
    private static DataIntegrityViolationException overlapViolation() {
        SQLException sql = new SQLException(
                "ERROR: conflicting key value violates exclusion constraint \"no_driver_overlap\"", "23P01");
        return new DataIntegrityViolationException("could not execute statement", sql);
    }

    /** Records which driver each save attempt carried, then behaves as scripted. */
    private void savesFailingForDrivers(Long... conflictingDriverIds) {
        List<Long> conflicting = List.of(conflictingDriverIds);
        when(bookingRepository.saveAndFlush(any(TravelBooking.class))).thenAnswer(inv -> {
            TravelBooking b = inv.getArgument(0);
            driverIdsAtSave.add(b.getDriverId());
            if (b.getDriverId() != null && conflicting.contains(b.getDriverId())) {
                throw overlapViolation();
            }
            b.setId(UUID.randomUUID());
            return b;
        });
    }

    @Test
    @DisplayName("losing the race for a driver moves on to the next free one")
    void retriesWithNextDriverWhenTheFirstIsTaken() {
        when(driverRepository.findAvailableDrivers(any(), any())).thenReturn(List.of(driver(1)));
        when(driverRepository.findAvailableDriversExcluding(any(), any(), eq(List.of(1L))))
                .thenReturn(List.of(driver(2)));
        savesFailingForDrivers(1L);

        service.createBooking(USER_ID, request());

        assertEquals(List.of(1L, 2L), driverIdsAtSave);
        verify(notificationService).notifyDriverAssigned(any(), eq(driver(2)));
        verify(notificationService, never()).notifyDriverAssigned(any(), eq(driver(1)));
    }

    @Test
    @DisplayName("a deadlock while racing for a driver is treated as losing the race, not as an error")
    void retriesWhenPostgresBreaksADeadlock() {
        when(driverRepository.findAvailableDrivers(any(), any())).thenReturn(List.of(driver(1)));
        when(driverRepository.findAvailableDriversExcluding(any(), any(), eq(List.of(1L))))
                .thenReturn(List.of(driver(2)));
        when(bookingRepository.saveAndFlush(any(TravelBooking.class))).thenAnswer(inv -> {
            TravelBooking b = inv.getArgument(0);
            driverIdsAtSave.add(b.getDriverId());
            if (Long.valueOf(1L).equals(b.getDriverId())) {
                // What Spring translates Postgres SQLSTATE 40P01 into.
                throw new DeadlockLoserDataAccessException("deadlock detected", new SQLException("deadlock", "40P01"));
            }
            b.setId(UUID.randomUUID());
            return b;
        });

        service.createBooking(USER_ID, request());

        assertEquals(List.of(1L, 2L), driverIdsAtSave);
        verify(notificationService).notifyDriverAssigned(any(), eq(driver(2)));
    }

    @Test
    @DisplayName("when every candidate is taken the booking still goes through, unassigned for the owner")
    void fallsBackToUnassignedWhenEveryDriverIsTaken() {
        when(driverRepository.findAvailableDrivers(any(), any())).thenReturn(List.of(driver(1)));
        when(driverRepository.findAvailableDriversExcluding(any(), any(), anyList()))
                .thenAnswer(inv -> {
                    List<Long> excluded = inv.getArgument(2);
                    return List.of(driver(excluded.size() + 1L));
                });
        savesFailingForDrivers(1L, 2L, 3L, 4L, 5L);

        service.createBooking(USER_ID, request());

        ArgumentCaptor<TravelBooking> saved = ArgumentCaptor.forClass(TravelBooking.class);
        verify(notificationService).notifyBookingCreated(saved.capture());
        assertNull(saved.getValue().getDriverId());
        assertNull(driverIdsAtSave.get(driverIdsAtSave.size() - 1));
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }

    @Test
    @DisplayName("an unrelated constraint failure is not mistaken for a driver clash")
    void unrelatedIntegrityErrorsPropagate() {
        when(driverRepository.findAvailableDrivers(any(), any())).thenReturn(List.of(driver(1)));
        when(bookingRepository.saveAndFlush(any(TravelBooking.class)))
                .thenThrow(new DataIntegrityViolationException("null value in column \"user_name\""));

        assertThrows(DataIntegrityViolationException.class, () -> service.createBooking(USER_ID, request()));
    }

    @Test
    @DisplayName("the route is fetched before the transaction opens, so a slow map API holds no DB connection")
    void routingHappensOutsideTheTransaction() {
        when(driverRepository.findAvailableDrivers(any(), any())).thenReturn(List.of());
        savesFailingForDrivers();

        service.createBooking(USER_ID, request());

        InOrder order = inOrder(routingService, txManager);
        order.verify(routingService).calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
        order.verify(txManager).getTransaction(any());
    }
}
