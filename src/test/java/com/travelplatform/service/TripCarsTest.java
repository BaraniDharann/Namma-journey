package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The car on a Trip is the one the owner put on it (V27), not whatever is on the driver's own profile. */
class TripCarsTest {

    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final TripCars cars = new TripCars(vehicles);

    private static Driver driver() {
        Driver d = new Driver();
        d.setName("Ravi");
        d.setVehicleNumber("OLD 1");
        d.setVehicleModel("Old car");
        return d;
    }

    private TravelBooking tripWithCar() {
        Vehicle v = new Vehicle();
        v.setId(500L);
        v.setCategory(CarCategory.SUV);
        v.setRegistrationNumber("KA05MN4321");
        v.setModel("Innova");
        v.setSeats(7);
        v.setAc(true);
        when(vehicles.findById(500L)).thenReturn(Optional.of(v));
        TravelBooking b = new TravelBooking();
        b.setVehicleId(500L);
        return b;
    }

    @Test
    @DisplayName("the passenger and driver see the Trip's car, not the driver's profile plate")
    void bookingShowsTripCar() {
        DriverDetailsResponse d = new DriverDetailsResponse().withVehicle(driver());

        cars.apply(d, tripWithCar());

        assertEquals("KA05MN4321", d.getVehicleNumber());
        assertEquals("Innova", d.getVehicleModel());
        assertEquals(7, d.getVehicleSeats());
        assertEquals("Innova KA05MN4321", cars.line(driver(), tripWithCar()));
    }

    @Test
    @DisplayName("with no car on the booking (City rides), the driver's own vehicle is shown")
    void noTripCarKeepsDriversVehicle() {
        DriverDetailsResponse d = new DriverDetailsResponse().withVehicle(driver());

        cars.apply(d, new TravelBooking());

        assertEquals("OLD 1", d.getVehicleNumber());
        assertEquals("Old car OLD 1", cars.line(driver(), new TravelBooking()));
    }
}
