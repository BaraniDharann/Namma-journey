package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PricingConfigRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only APPROVED drivers go online, report location or get work; Trips stay with the owner's fleet. */
class DriverApprovalGateTest {

    private DriverRepository driverRepository;
    private DriverService driverService;
    private OwnerService ownerService;
    private TravelBookingRepository bookingRepository;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        bookingRepository = mock(TravelBookingRepository.class);
        driverService = new DriverService(bookingRepository, driverRepository, mock(NotificationService.class),
                mock(TripDriverPhotoRepository.class), mock(BookingDriverRejectionRepository.class),
                mock(FileStorageService.class));
        ownerService = new OwnerService(bookingRepository, mock(PricingConfigRepository.class), driverRepository,
                mock(TripDriverPhotoRepository.class), mock(NotificationService.class));
    }

    private Driver driver(long id, Driver.Verification v, Driver.Fleet f) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("D" + id);
        d.setStatus(Driver.Status.ACTIVE);
        d.setVerificationStatus(v);
        d.setFleet(f);
        when(driverRepository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void unapprovedCannotGoOnline() {
        driver(1L, Driver.Verification.UNDER_REVIEW, Driver.Fleet.INDEPENDENT);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> driverService.updateAvailability(1L, "ACTIVE"));
        assertEquals("Your account is still being verified.", e.getMessage());
    }

    @Test
    void unapprovedCanGoOffline() {
        driver(1L, Driver.Verification.REJECTED, Driver.Fleet.INDEPENDENT);
        assertDoesNotThrow(() -> driverService.updateAvailability(1L, "INACTIVE"));
    }

    @Test
    void approvedCanGoOnline() {
        driver(2L, Driver.Verification.APPROVED, Driver.Fleet.INDEPENDENT);
        assertDoesNotThrow(() -> driverService.updateAvailability(2L, "ACTIVE"));
    }

    @Test
    void unapprovedPresenceReportRefused() {
        driver(1L, Driver.Verification.UNDER_REVIEW, Driver.Fleet.INDEPENDENT);
        assertThrows(AccessDeniedException.class, () -> driverService.requireApproved(1L));
    }

    @Test
    void approvalIsCachedAndEvictable() {
        driver(3L, Driver.Verification.APPROVED, Driver.Fleet.INDEPENDENT);
        driverService.requireApproved(3L);
        driverService.requireApproved(3L);
        verify(driverRepository, times(1)).findById(3L);
        driverService.evictApproval(3L);
        driverService.requireApproved(3L);
        verify(driverRepository, times(2)).findById(3L);
    }

    @Test
    void rejectedDriverIsNotCachedAsApproved() {
        Driver d = driver(4L, Driver.Verification.UNDER_REVIEW, Driver.Fleet.INDEPENDENT);
        assertThrows(AccessDeniedException.class, () -> driverService.requireApproved(4L));
        d.setVerificationStatus(Driver.Verification.APPROVED);
        assertDoesNotThrow(() -> driverService.requireApproved(4L));
    }

    private TravelBooking booking(TravelBooking.ServiceMode mode) {
        TravelBooking b = new TravelBooking();
        b.setId(UUID.randomUUID());
        b.setStatus(TravelBooking.BookingStatus.PENDING);
        b.setServiceMode(mode);
        when(bookingRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
        return b;
    }

    @Test
    void tripCannotGoToIndependentDriver() {
        driver(5L, Driver.Verification.APPROVED, Driver.Fleet.INDEPENDENT);
        TravelBooking b = booking(TravelBooking.ServiceMode.TRIP);
        assertThrows(IllegalArgumentException.class, () -> ownerService.assignDriver(b.getId(), 5L));
    }

    @Test
    void cannotAssignUnapprovedDriver() {
        driver(6L, Driver.Verification.UNDER_REVIEW, Driver.Fleet.OWNER_FLEET);
        TravelBooking b = booking(TravelBooking.ServiceMode.TRIP);
        assertThrows(IllegalArgumentException.class, () -> ownerService.assignDriver(b.getId(), 6L));
    }

    private static String jpql(String method) {
        return Arrays.stream(DriverRepository.class.getMethods())
                .filter(m -> m.getName().equals(method)).map(Method::getAnnotations).flatMap(Arrays::stream)
                .filter(Query.class::isInstance).map(a -> ((Query) a).value()).findFirst().orElseThrow();
    }

    @Test
    void queriesFilterOnApprovalAndFleet() {
        for (String m : new String[]{"findAvailableDrivers", "findAvailableDriversExcluding"}) {
            assertTrue(jpql(m).contains("verificationStatus = com.travelplatform.entity.Driver.Verification.APPROVED"), m);
            assertTrue(jpql(m).contains("fleet = com.travelplatform.entity.Driver.Fleet.OWNER_FLEET"), m);
        }
        assertTrue(jpql("findDispatchableAmong").contains("verificationStatus = com.travelplatform.entity.Driver.Verification.APPROVED"));
    }
}
