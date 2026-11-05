package com.travelplatform.service;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.repository.VehicleRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Which car is on a booking. A Trip carries the owner's car chosen for it (V27), which can differ
 * from the plate on the driver's own profile - and the passenger checks that plate before getting
 * in. A City ride has no car on the booking: the independent driver's own vehicle is the one.
 */
@Component
public class TripCars {

    private final VehicleRepository vehicles;

    public TripCars(VehicleRepository vehicles) {
        this.vehicles = vehicles;
    }

    public Optional<Vehicle> carOf(TravelBooking booking) {
        return booking == null || booking.getVehicleId() == null ? Optional.empty() : vehicles.findById(booking.getVehicleId());
    }

    /** Puts the booking's car on a driver view that already holds the driver's own vehicle. */
    public DriverDetailsResponse apply(DriverDetailsResponse driver, TravelBooking booking) {
        carOf(booking).ifPresent(driver::withTripCar);
        return driver;
    }

    /** "Model PLATE" for messages: the booking's car, else the driver's own vehicle. */
    public String line(Driver driver, TravelBooking booking) {
        Optional<Vehicle> car = carOf(booking);
        String model = car.map(Vehicle::getModel).orElse(driver == null ? null : driver.getVehicleModel());
        String number = car.map(Vehicle::getRegistrationNumber).orElse(driver == null ? null : driver.getVehicleNumber());
        return ((model == null ? "" : model.trim()) + " " + (number == null ? "" : number.trim())).trim();
    }
}
