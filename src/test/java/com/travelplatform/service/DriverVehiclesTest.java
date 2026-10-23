package com.travelplatform.service;

import com.travelplatform.dto.VehicleRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverVehiclesTest {

    private static VehicleRequest vehicle(String type, Boolean city, Boolean trip) {
        return new VehicleRequest(type, null, null, null, null, city, trip);
    }

    @Test
    @DisplayName("a new driver with nothing said is a 4-seat AC car taking both modes")
    void defaults() {
        Driver d = new Driver();
        DriverVehicles.apply(d, new VehicleRequest());
        assertEquals(VehicleType.CAR, d.getVehicleType());
        assertEquals(4, d.getVehicleSeats());
        assertTrue(d.isVehicleAc());
        assertTrue(d.isServesCity());
        assertTrue(d.isServesTrip());
    }

    @Test
    @DisplayName("a bike or auto takes City rides only, with its own seats and no AC")
    void bikeAndAutoAreCityOnly() {
        Driver bike = new Driver();
        DriverVehicles.apply(bike, vehicle("BIKE", true, true));
        assertEquals(VehicleType.BIKE, bike.getVehicleType());
        assertFalse(bike.isServesTrip());
        assertEquals(1, bike.getVehicleSeats());
        assertFalse(bike.isVehicleAc());

        Driver auto = new Driver();
        DriverVehicles.apply(auto, vehicle("auto", null, null));
        assertEquals(3, auto.getVehicleSeats());
        assertFalse(auto.isServesTrip());
    }

    @Test
    @DisplayName("an auto driver switching Trips on is told trips are by car")
    void autoCannotSwitchTripsOn() {
        Driver auto = new Driver();
        DriverVehicles.apply(auto, vehicle("AUTO", true, false));
        assertThrows(IllegalArgumentException.class, () -> DriverVehicles.setModes(auto, null, true));
    }

    @Test
    @DisplayName("a car driver can take City only, Trips only, or both")
    void carModes() {
        Driver car = new Driver();
        DriverVehicles.setModes(car, false, true);
        assertFalse(car.isServesCity());
        assertTrue(car.isServesTrip());
    }

    @Test
    @DisplayName("number plates are tidied, and nonsense is refused")
    void numberPlate() {
        Driver d = new Driver();
        DriverVehicles.apply(d, new VehicleRequest(null, "  ka 01  ab 1234 ", null, null, 7, null, null));
        assertEquals("KA 01 AB 1234", d.getVehicleNumber());
        assertEquals(7, d.getVehicleSeats());
        assertThrows(IllegalArgumentException.class,
                () -> DriverVehicles.apply(d, new VehicleRequest(null, "<script>", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> DriverVehicles.apply(d, new VehicleRequest(null, null, null, null, 40, null, null)));
    }
}
