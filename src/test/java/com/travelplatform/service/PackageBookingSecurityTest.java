package com.travelplatform.service;

import com.travelplatform.controller.TravelPackageController;
import com.travelplatform.dto.PackageBookingRequest;
import com.travelplatform.entity.PackageBooking;
import com.travelplatform.repository.NotificationRepository;
import com.travelplatform.repository.PackageBookingRepository;
import com.travelplatform.repository.TravelPackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Package bookings had no ownership checks at all: any signed-in passenger could list another
 * passenger's bookings (name, email, phone), cancel anyone's confirmed or completed trip, or book
 * under someone else's id. States had no rules either - a completed booking could be cancelled.
 */
class PackageBookingSecurityTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID intruder = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private PackageBookingRepository repo;
    private PackageBookingService service;
    private PackageBooking booking;

    @BeforeEach
    void setUp() {
        repo = mock(PackageBookingRepository.class);
        service = new PackageBookingService(repo, mock(TravelPackageRepository.class), mock(NotificationRepository.class));
        booking = new PackageBooking();
        booking.setId(bookingId);
        booking.setUserId(owner);
        booking.setStatus(PackageBooking.BookingStatus.CONFIRMED);
        when(repo.findById(bookingId)).thenReturn(Optional.of(booking));
        when(repo.save(any(PackageBooking.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("every passenger package endpoint is limited to the passenger in the URL")
    void controllerChecksTheUser() throws Exception {
        for (String name : new String[] {"bookPackage", "getUserPackageBookings", "cancelUserPackageBooking"}) {
            Method m = java.util.Arrays.stream(TravelPackageController.class.getMethods())
                    .filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
            String rule = m.getAnnotation(PreAuthorize.class).value();
            assertTrue(rule.contains("#userId") && rule.contains("authentication.principal"), name + ": " + rule);
        }
    }

    @Test
    @DisplayName("a passenger cannot cancel someone else's package booking")
    void cannotCancelOthers() {
        assertThrows(AccessDeniedException.class, () -> service.cancelByUser(intruder, bookingId, "x"));
        assertEquals(PackageBooking.BookingStatus.CONFIRMED, booking.getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("the passenger can cancel their own upcoming booking")
    void canCancelOwn() {
        service.cancelByUser(owner, bookingId, "change of plans");
        assertEquals(PackageBooking.BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    @DisplayName("completed or cancelled bookings cannot be cancelled, confirmed or completed again")
    void statesOnlyMoveForward() {
        booking.setStatus(PackageBooking.BookingStatus.COMPLETED);
        assertThrows(RuntimeException.class, () -> service.cancelByUser(owner, bookingId, "x"));
        assertThrows(RuntimeException.class, () -> service.cancelByOwner(bookingId, "x"));

        booking.setStatus(PackageBooking.BookingStatus.CANCELLED);
        assertThrows(RuntimeException.class, () -> service.confirmBooking(bookingId));
        assertThrows(RuntimeException.class, () -> service.completeBooking(bookingId));

        booking.setStatus(PackageBooking.BookingStatus.PENDING);
        assertThrows(RuntimeException.class, () -> service.completeBooking(bookingId), "must be confirmed first");
    }

    @Test
    @DisplayName("a package cannot be booked for a date in the past")
    void noPastTravelDate() {
        PackageBookingRequest r = new PackageBookingRequest();
        r.setPackageId(1L);
        r.setNumberOfPersons(2);
        r.setTravelDate(LocalDate.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> service.createBooking(owner, r));
    }
}
