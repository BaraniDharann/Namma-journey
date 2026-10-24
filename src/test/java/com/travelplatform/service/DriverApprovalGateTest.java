package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Only APPROVED drivers go online or report location. (Assignment rules: OwnerServiceAssignNotifyTest.) */
class DriverApprovalGateTest {

    private DriverRepository driverRepository;
    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverRepository = mock(DriverRepository.class);
        driverService = new DriverService(mock(TravelBookingRepository.class), driverRepository,
                mock(NotificationService.class), mock(TripDriverPhotoRepository.class),
                mock(BookingDriverRejectionRepository.class), mock(FileStorageService.class));
    }

    private Driver driver(long id, Driver.Verification v) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("D" + id);
        d.setStatus(Driver.Status.ACTIVE);
        d.setVerificationStatus(v);
        d.setFleet(Driver.Fleet.INDEPENDENT);
        when(driverRepository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void unapprovedCannotGoOnline() {
        driver(1L, Driver.Verification.UNDER_REVIEW);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> driverService.updateAvailability(1L, "ACTIVE"));
        assertEquals("Your account is still being verified.", e.getMessage());
    }

    @Test
    void unapprovedCanGoOffline() {
        driver(1L, Driver.Verification.REJECTED);
        assertDoesNotThrow(() -> driverService.updateAvailability(1L, "INACTIVE"));
    }

    @Test
    void approvedCanGoOnline() {
        driver(2L, Driver.Verification.APPROVED);
        assertDoesNotThrow(() -> driverService.updateAvailability(2L, "ACTIVE"));
    }

    @Test
    void unapprovedPresenceReportRefused() {
        driver(1L, Driver.Verification.UNDER_REVIEW);
        assertThrows(AccessDeniedException.class, () -> driverService.requireApproved(1L));
    }

    @Test
    void approvalIsCachedAndEvictable() {
        driver(3L, Driver.Verification.APPROVED);
        driverService.requireApproved(3L);
        driverService.requireApproved(3L);
        verify(driverRepository, times(1)).findById(3L);
        driverService.evictApproval(3L);
        driverService.requireApproved(3L);
        verify(driverRepository, times(2)).findById(3L);
    }

    @Test
    void unapprovedIsNeverCached() {
        Driver d = driver(4L, Driver.Verification.UNDER_REVIEW);
        assertThrows(AccessDeniedException.class, () -> driverService.requireApproved(4L));
        d.setVerificationStatus(Driver.Verification.APPROVED);
        assertDoesNotThrow(() -> driverService.requireApproved(4L));
    }

    private static String jpql(String method) {
        return Arrays.stream(DriverRepository.class.getMethods())
                .filter(m -> m.getName().equals(method)).map(Method::getAnnotations).flatMap(Arrays::stream)
                .filter(Query.class::isInstance).map(a -> ((Query) a).value()).findFirst().orElseThrow();
    }

    @Test
    void queriesFilterOnApprovalAndFleet() {
        // Native SQL, so the enums appear as their stored names.
        assertTrue(jpql("findFirstFreeTripDriver").contains("d.verification_status = 'APPROVED'"));
        assertTrue(jpql("findFirstFreeTripDriver").contains("d.fleet = 'OWNER_FLEET'"));
        assertTrue(jpql("findDispatchableAmong").contains("verificationStatus = com.travelplatform.entity.Driver.Verification.APPROVED"));
    }
}
