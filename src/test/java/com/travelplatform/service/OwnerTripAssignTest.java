package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.AssignOptions;
import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import com.travelplatform.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An owner puts one of their own drivers and one of their own cars on one of their own Trips. The
 * car must be active, of the booked category, big enough, AC when asked, and free those days; left
 * out, the driver's usual car is used.
 */
class OwnerTripAssignTest {

    private static final long ME = 7L;
    private static final long OTHER = 8L;
    private static final UUID TRIP = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private TravelBookingRepository bookings;
    private DriverRepository drivers;
    private VehicleRepository vehicles;
    private NotificationService notifications;
    private OwnerService service;
    private TravelBooking booking;

    @BeforeEach
    void setUp() {
        bookings = mock(TravelBookingRepository.class);
        drivers = mock(DriverRepository.class);
        vehicles = mock(VehicleRepository.class);
        notifications = mock(NotificationService.class);
        service = new OwnerService(bookings, mock(com.travelplatform.repository.OwnerRepository.class), drivers,
                mock(TripDriverPhotoRepository.class), notifications);
        ReflectionTestUtils.setField(service, "vehicleRepository", vehicles);
        OwnerScope.bind(ME, false);

        booking = new TravelBooking();
        booking.setId(TRIP);
        booking.setOwnerId(ME);
        booking.setServiceMode(TravelBooking.ServiceMode.TRIP);
        booking.setCarCategory(CarCategory.SUV);
        booking.setTravelMembers(5);
        booking.setAcType("AC");
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setFromDate(LocalDate.of(2026, 12, 10));
        booking.setToDate(LocalDate.of(2026, 12, 11));
        booking.setEstimatedTimeMinutes(180L);
        when(bookings.findById(TRIP)).thenReturn(Optional.of(booking));
        when(bookings.save(any(TravelBooking.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private Driver driver(long id, long owner, Long usualCar) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setOwnerId(owner);
        d.setDefaultVehicleId(usualCar);
        when(drivers.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private Vehicle vehicle(long id, long owner, CarCategory category, int seats, boolean ac) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setOwnerId(owner);
        v.setCategory(category);
        v.setSeats(seats);
        v.setAc(ac);
        v.setRegistrationNumber("KA01AB" + id);
        when(vehicles.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    @DisplayName("left out, the car is the driver's usual one")
    void usesUsualCar() {
        vehicle(500, ME, CarCategory.SUV, 7, true);
        Driver d = driver(1, ME, 500L);

        service.assignDriver(TRIP, 1L, null);

        assertEquals(1L, booking.getDriverId());
        assertEquals(500L, booking.getVehicleId());
        assertEquals(TravelBooking.BookingStatus.CONFIRMED, booking.getStatus());
        verify(notifications).notifyDriverAssigned(eq(booking), eq(d));
    }

    @Test
    @DisplayName("a car of another category, too small, without AC or inactive is refused")
    void carMustSuit() {
        driver(1, ME, null);
        vehicle(501, ME, CarCategory.SEDAN, 4, true);
        vehicle(502, ME, CarCategory.SUV, 4, true);
        vehicle(503, ME, CarCategory.SUV, 7, false);
        vehicle(504, ME, CarCategory.SUV, 7, true).setActive(false);

        assertThrows(IllegalArgumentException.class, () -> service.assignDriver(TRIP, 1L, 501L));
        assertThrows(IllegalArgumentException.class, () -> service.assignDriver(TRIP, 1L, 502L));
        assertThrows(IllegalArgumentException.class, () -> service.assignDriver(TRIP, 1L, 503L));
        assertThrows(IllegalArgumentException.class, () -> service.assignDriver(TRIP, 1L, 504L));
        verify(bookings, never()).save(any());
    }

    @Test
    @DisplayName("no car given and no usual car: the owner is asked to choose one")
    void needsACar() {
        driver(1, ME, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.assignDriver(TRIP, 1L, null));
        assertEquals("Choose a car for this trip", e.getMessage());
    }

    @Test
    @DisplayName("another owner's driver, car or Trip is not found")
    void otherOwnersRecordsAreNotFound() {
        driver(2, OTHER, null);
        vehicle(600, OTHER, CarCategory.SUV, 7, true);
        driver(1, ME, null);

        assertThrows(ResourceNotFoundException.class, () -> service.assignDriver(TRIP, 2L, 600L));
        assertThrows(ResourceNotFoundException.class, () -> service.assignDriver(TRIP, 1L, 600L));
        booking.setOwnerId(OTHER);
        vehicle(500, ME, CarCategory.SUV, 7, true);
        assertThrows(ResourceNotFoundException.class, () -> service.assignDriver(TRIP, 1L, 500L));
    }

    @Test
    @DisplayName("a car already on a trip those days is a conflict")
    void busyCarIsConflict() {
        driver(1, ME, null);
        vehicle(500, ME, CarCategory.SUV, 7, true);
        when(vehicles.countOverlappingActiveBookings(eq(500L), any(), any(), eq(TRIP))).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.assignDriver(TRIP, 1L, 500L));
    }

    @Test
    @DisplayName("a busy driver is a conflict")
    void busyDriverIsConflict() {
        driver(1, ME, null);
        vehicle(500, ME, CarCategory.SUV, 7, true);
        when(drivers.countOverlappingActiveBookings(eq(1L), any(), any(), eq(TRIP))).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.assignDriver(TRIP, 1L, 500L));
    }

    @Test
    @DisplayName("City rides are assigned only from the platform account, and only to independent drivers")
    void cityRideIsPlatformOnly() {
        booking.setServiceMode(TravelBooking.ServiceMode.CITY);
        booking.setOwnerId(null);
        booking.setCarCategory(null);
        booking.setVehicleType(com.travelplatform.entity.VehicleType.CAR);
        when(bookings.findById(TRIP)).thenReturn(Optional.of(booking));
        Driver fleet = driver(1, ME, null);

        assertThrows(ResourceNotFoundException.class, () -> service.assignDriver(TRIP, 1L, null),
                "a non-platform owner does not see platform rides");

        OwnerScope.bind(ME, true);
        assertThrows(IllegalArgumentException.class, () -> service.assignDriver(TRIP, 1L, null),
                "a travels owner's driver never takes a City ride");
        fleet.setOwnerId(null);
        fleet.setFleet(Driver.Fleet.INDEPENDENT);
        service.assignDriver(TRIP, 1L, null);
        assertEquals(1L, booking.getDriverId());
    }

    @Test
    @DisplayName("the assign screen offers my free drivers and suitable cars, marking each driver's usual car")
    void assignOptions() {
        Driver d = driver(1, ME, 500L);
        Vehicle v = vehicle(500, ME, CarCategory.SUV, 7, true);
        when(drivers.findFreeOwnerTripDrivers(ME, booking.getFromDate(), booking.getToDate(), TRIP)).thenReturn(List.of(d));
        when(vehicles.findFreeVehicles(ME, CarCategory.SUV, 5, true, booking.getFromDate(), booking.getToDate(), TRIP))
                .thenReturn(List.of(v));

        AssignOptions options = service.assignOptions(TRIP);

        assertEquals(List.of(new AssignOptions.DriverOption(1L, "Driver 1", 500L)), options.drivers());
        assertEquals(List.of(new AssignOptions.VehicleOption(500L, "KA01AB500", null, 7, true)), options.vehicles());
    }

    @Test
    @DisplayName("another owner's Trip has no assign options")
    void assignOptionsOtherOwner() {
        booking.setOwnerId(OTHER);
        assertThrows(ResourceNotFoundException.class, () -> service.assignOptions(TRIP));
        verify(drivers, never()).findFreeOwnerTripDrivers(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("outside an owner's request there is no one to assign as")
    void needsOwner() {
        OwnerScope.clear();
        assertThrows(AccessDeniedException.class, () -> service.assignDriver(TRIP, 1L, null));
    }
}
