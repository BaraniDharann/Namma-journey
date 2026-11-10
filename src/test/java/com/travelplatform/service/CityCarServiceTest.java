package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.VehicleRepository;
import com.travelplatform.service.dispatch.DriverStateUpdates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An owner putting cars on City rides or Travel trips, and what that does to the car's driver. */
class CityCarServiceTest {

    private static final long ME = 7L;

    private VehicleRepository vehicles;
    private DriverRepository drivers;
    private TravelBookingRepository bookings;
    private DriverStateUpdates states;
    private DriverPresenceService presence;
    private CityCarService service;

    @BeforeEach
    void setUp() {
        vehicles = mock(VehicleRepository.class);
        drivers = mock(DriverRepository.class);
        bookings = mock(TravelBookingRepository.class);
        states = mock(DriverStateUpdates.class);
        presence = mock(DriverPresenceService.class);
        when(vehicles.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));
        when(vehicles.saveAndFlush(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));
        when(vehicles.findByCityDriverId(anyLong())).thenReturn(Optional.empty());
        when(drivers.save(any(Driver.class))).thenAnswer(i -> i.getArgument(0));
        service = new CityCarService(vehicles, drivers, bookings);
        service.setDriverStates(states);
        service.setPresence(presence);
        OwnerScope.bind(ME, false);
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private Vehicle car(long id, long owner, CarCategory category) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setOwnerId(owner);
        v.setCategory(category);
        v.setRegistrationNumber("KA01AB" + id);
        v.setModel("Dzire");
        v.setSeats(4);
        v.setAc(true);
        when(vehicles.findById(id)).thenReturn(Optional.of(v));
        return v;
    }

    private Driver driver(long id, long owner) {
        Driver d = new Driver();
        d.setId(id);
        d.setName("Driver " + id);
        d.setFleet(Driver.Fleet.OWNER_FLEET);
        d.setOwnerId(owner);
        d.setServesCity(false);
        d.setVehicleType(VehicleType.CAR);
        d.setVehicleSeats(7);
        d.setVehicleAc(false);
        when(drivers.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    @DisplayName("a car put on City rides takes its driver with it: City switch on, the car's seats and AC")
    void toCityPairsTheDriver() {
        Vehicle v = car(1, ME, CarCategory.SEDAN);
        Driver d = driver(11, ME);

        Vehicle out = service.setMode(1L, "city", 11L);

        assertEquals(Vehicle.Mode.CITY, out.getMode());
        assertEquals(11L, out.getCityDriverId());
        assertTrue(d.isServesCity());
        assertEquals(VehicleType.CAR, d.getVehicleType());
        assertEquals(4, d.getVehicleSeats());
        assertTrue(d.isVehicleAc());
        verify(states).profileChanged(d);
    }

    @Test
    @DisplayName("a City car needs a driver, and the driver must be one of mine")
    void cityNeedsMyDriver() {
        car(1, ME, CarCategory.SEDAN);
        driver(12, 99L);

        assertThrows(IllegalArgumentException.class, () -> service.setMode(1L, "CITY", null));
        assertThrows(ResourceNotFoundException.class, () -> service.setMode(1L, "CITY", 12L));
    }

    @Test
    @DisplayName("a Tempo Traveller stays on Travel trips")
    void tempoIsTravelOnly() {
        car(1, ME, CarCategory.TEMPO_TRAVELLER);
        driver(11, ME);

        assertThrows(IllegalArgumentException.class, () -> service.setMode(1L, "CITY", 11L));
    }

    @Test
    @DisplayName("a car with a Travel trip still to finish cannot move to City rides")
    void openTravelTripBlocksCity() {
        car(1, ME, CarCategory.SUV);
        driver(11, ME);
        when(vehicles.countOpenTravelBookings(1L)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.setMode(1L, "CITY", 11L));
    }

    @Test
    @DisplayName("a driver already on another City car is refused")
    void oneCityCarPerDriver() {
        car(1, ME, CarCategory.SEDAN);
        Vehicle other = car(2, ME, CarCategory.SEDAN);
        other.setMode(Vehicle.Mode.CITY);
        other.setCityDriverId(11L);
        driver(11, ME);
        when(vehicles.findByCityDriverId(11L)).thenReturn(Optional.of(other));

        assertThrows(ConflictException.class, () -> service.setMode(1L, "CITY", 11L));
    }

    @Test
    @DisplayName("moving a City car to Travel ends the pairing and takes the driver offline for City rides")
    void toTravelReleasesTheDriver() {
        Vehicle v = car(1, ME, CarCategory.SEDAN);
        v.setMode(Vehicle.Mode.CITY);
        v.setCityDriverId(11L);
        Driver d = driver(11, ME);
        d.setServesCity(true);

        Vehicle out = service.setMode(1L, "TRAVEL", null);

        assertEquals(Vehicle.Mode.TRAVEL, out.getMode());
        assertNull(out.getCityDriverId());
        assertFalse(d.isServesCity());
        verify(presence).remove(11L);
    }

    @Test
    @DisplayName("a City car cannot leave City rides while its driver is on one")
    void openCityRideBlocksTheSwitch() {
        Vehicle v = car(1, ME, CarCategory.SEDAN);
        v.setMode(Vehicle.Mode.CITY);
        v.setCityDriverId(11L);
        driver(11, ME).setServesCity(true);
        when(bookings.countOpenCityRidesByDriverId(11L)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.setMode(1L, "OFF", null));
        verify(presence, never()).remove(anyLong());
    }

    @Test
    @DisplayName("giving a City car a different driver stops the old one first")
    void changeDriver() {
        Vehicle v = car(1, ME, CarCategory.SEDAN);
        v.setMode(Vehicle.Mode.CITY);
        v.setCityDriverId(11L);
        Driver old = driver(11, ME);
        old.setServesCity(true);
        Driver next = driver(12, ME);

        Vehicle out = service.setMode(1L, "CITY", 12L);

        assertEquals(12L, out.getCityDriverId());
        assertFalse(old.isServesCity());
        assertTrue(next.isServesCity());
        verify(presence).remove(11L);
    }

    @Test
    @DisplayName("another owner's car is not found")
    void notMyCar() {
        car(1, 99L, CarCategory.SEDAN);
        assertThrows(ResourceNotFoundException.class, () -> service.setMode(1L, "OFF", null));
    }

    @Test
    @DisplayName("an unknown mode is refused")
    void badMode() {
        car(1, ME, CarCategory.SEDAN);
        assertThrows(IllegalArgumentException.class, () -> service.setMode(1L, "BOAT", null));
    }
}
