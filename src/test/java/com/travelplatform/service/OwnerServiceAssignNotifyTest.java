package com.travelplatform.service;

import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * When the owner hands a trip to a driver by hand, that driver has to hear about it: the in-app
 * alert, the email and the Telegram card. Before this, only the automatic assignment at booking
 * time notified anyone, so a manually assigned driver learnt of the trip only by opening the app.
 */
class OwnerServiceAssignNotifyTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private TravelBookingRepository bookingRepository;
    private DriverRepository driverRepository;
    private NotificationService notificationService;
    private OwnerService service;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(TravelBookingRepository.class);
        driverRepository = mock(DriverRepository.class);
        notificationService = mock(NotificationService.class);
        service = new OwnerService(bookingRepository, mock(com.travelplatform.repository.OwnerRepository.class), driverRepository,
                mock(TripDriverPhotoRepository.class), notificationService);
        // The owner's own Trip, driver and usual car (V27).
        com.travelplatform.config.OwnerScope.bind(OWNER, false);
        com.travelplatform.repository.VehicleRepository vehicles = mock(com.travelplatform.repository.VehicleRepository.class);
        com.travelplatform.entity.Vehicle car = new com.travelplatform.entity.Vehicle();
        car.setId(CAR);
        car.setOwnerId(OWNER);
        car.setCategory(com.travelplatform.entity.CarCategory.SEDAN);
        car.setSeats(4);
        car.setRegistrationNumber("KA01AB1234");
        when(vehicles.findById(CAR)).thenReturn(Optional.of(car));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "vehicleRepository", vehicles);

        booking = new TravelBooking();
        booking.setId(BOOKING_ID);
        booking.setOwnerId(OWNER);
        booking.setCarCategory(com.travelplatform.entity.CarCategory.SEDAN);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setFromDate(LocalDate.of(2026, 10, 11));
        booking.setToDate(LocalDate.of(2026, 10, 11));
        booking.setEstimatedTimeMinutes(180L);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(TravelBooking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @org.junit.jupiter.api.AfterEach
    void unbind() {
        com.travelplatform.config.OwnerScope.clear();
    }

    private static final long OWNER = 1L;
    private static final long CAR = 500L;

    private Driver driver(long id) {
        Driver d = new Driver();
        d.setId(id);
        d.setOwnerId(OWNER);
        d.setDefaultVehicleId(CAR);
        d.setName("Driver " + id);
        d.setStatus(Driver.Status.ACTIVE);
        when(driverRepository.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    @DisplayName("assigning a new driver notifies that driver")
    void notifiesNewDriver() {
        booking.setDriverId(76L);
        Driver d = driver(88L);
        service.assignDriver(BOOKING_ID, 88L);
        verify(notificationService).notifyDriverAssigned(eq(booking), eq(d));
    }

    @Test
    @DisplayName("the platform cannot give a bike ride to a car driver")
    void vehicleMustMatch() {
        // City rides are the platform's, and go to independent drivers.
        com.travelplatform.config.OwnerScope.bind(OWNER, true);
        booking.setOwnerId(null);
        booking.setServiceMode(TravelBooking.ServiceMode.CITY);
        booking.setVehicleType(com.travelplatform.entity.VehicleType.BIKE);
        Driver car = driver(92L);
        car.setOwnerId(null);
        car.setFleet(Driver.Fleet.INDEPENDENT);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.assignDriver(BOOKING_ID, 92L));
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }

    @Test
    @DisplayName("re-saving the same driver does not send a duplicate notification")
    void noDuplicateForSameDriver() {
        booking.setDriverId(88L);
        driver(88L);
        service.assignDriver(BOOKING_ID, 88L);
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }

    @Test
    @DisplayName("an application still under review cannot be assigned work")
    void cannotAssignUnapprovedDriver() {
        Driver d = driver(93L);
        d.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.assignDriver(BOOKING_ID, 93L));
        org.junit.jupiter.api.Assertions.assertEquals("Driver 93 has not been approved yet.", ex.getMessage());
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }

    @Test
    @DisplayName("a Trip never goes to an independent driver")
    void tripCannotGoToIndependentDriver() {
        booking.setServiceMode(TravelBooking.ServiceMode.TRIP);
        Driver d = driver(94L);
        d.setFleet(Driver.Fleet.INDEPENDENT);
        d.setOwnerId(null);
        // Not one of my drivers, so not found - the same as any other owner's driver.
        org.junit.jupiter.api.Assertions.assertThrows(com.travelplatform.exception.ResourceNotFoundException.class,
                () -> service.assignDriver(BOOKING_ID, 94L));
        verify(notificationService, never()).notifyDriverAssigned(any(), any());
    }
}
