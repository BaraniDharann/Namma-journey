package com.travelplatform.controller;

import com.travelplatform.config.BookingAccessGuard;
import com.travelplatform.dto.DriverLocationDTO;
import com.travelplatform.service.DriverPresenceService;
import com.travelplatform.service.DriverService;
import com.travelplatform.service.LocationTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Location reports from a driver who is not approved are refused before anything is stored or pushed. */
class DriverApprovalLocationGateTest {

    private static final long ID = 7L;

    private DriverService driverService;
    private DriverPresenceService presence;
    private LocationTrackingService tracking;
    private DriverController driverController;
    private LocationWebSocketController wsController;

    @BeforeEach
    void setUp() {
        driverService = mock(DriverService.class);
        presence = mock(DriverPresenceService.class);
        tracking = mock(LocationTrackingService.class);
        BookingAccessGuard guard = mock(BookingAccessGuard.class);
        when(guard.canReportLocation(any(), any())).thenReturn(true);
        doThrow(new AccessDeniedException("Your account is still being verified.")).when(driverService).requireApproved(ID);

        driverController = new DriverController();
        ReflectionTestUtils.setField(driverController, "driverService", driverService);
        ReflectionTestUtils.setField(driverController, "driverPresenceService", presence);
        ReflectionTestUtils.setField(driverController, "locationTrackingService", tracking);
        ReflectionTestUtils.setField(driverController, "bookingAccessGuard", guard);

        wsController = new LocationWebSocketController();
        ReflectionTestUtils.setField(wsController, "driverService", driverService);
        ReflectionTestUtils.setField(wsController, "driverPresenceService", presence);
        ReflectionTestUtils.setField(wsController, "locationTrackingService", tracking);
        ReflectionTestUtils.setField(wsController, "bookingAccessGuard", guard);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static DriverLocationDTO dto(long driverId) {
        return new DriverLocationDTO(driverId, "b1", 12.9, 77.6, 0, 0);
    }

    private static void signInAs(long driverId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(driverId), "n/a"));
    }

    @Test
    void presenceEndpointRefusesUnapproved() {
        assertThrows(AccessDeniedException.class,
                () -> driverController.reportPresence(ID, Map.of("latitude", 12.9, "longitude", 77.6)));
        verify(presence, never()).report(anyLong(), anyDouble(), anyDouble());
    }

    @Test
    void restLocationEndpointRefusesUnapproved() {
        signInAs(ID);
        assertThrows(AccessDeniedException.class, () -> driverController.updateLocationRest(dto(ID)));
        verify(presence, never()).report(anyLong(), anyDouble(), anyDouble());
        verify(tracking, never()).updateLocation(any());
    }

    @Test
    void stompHandlerRefusesUnapproved() {
        Principal p = () -> String.valueOf(ID);
        assertThrows(AccessDeniedException.class, () -> wsController.updateLocation(dto(ID), p));
        verify(presence, never()).report(anyLong(), anyDouble(), anyDouble());
        verify(tracking, never()).updateLocation(any());
    }

    @Test
    void approvedDriverStillReports() {
        signInAs(8L);
        driverController.updateLocationRest(dto(8L));
        verify(presence).report(8L, 12.9, 77.6);
    }
}
