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
    @DisplayName("a new owner-fleet driver with nothing said is a 4-seat AC car taking Trips only")
    void defaults() {
        Driver d = new Driver();
        DriverVehicles.apply(d, new VehicleRequest());
        assertEquals(VehicleType.CAR, d.getVehicleType());
        assertEquals(4, d.getVehicleSeats());
        assertTrue(d.isVehicleAc());
        assertFalse(d.isServesCity());
        assertTrue(d.isServesTrip());
    }

    @Test
    @DisplayName("a bike or auto takes City rides only, with its own seats and no AC")
    void bikeAndAutoAreCityOnly() {
        Driver bike = new Driver();
        bike.setFleet(Driver.Fleet.INDEPENDENT);
        DriverVehicles.apply(bike, vehicle("BIKE", true, true));
        assertEquals(VehicleType.BIKE, bike.getVehicleType());
        assertFalse(bike.isServesTrip());
        assertEquals(1, bike.getVehicleSeats());
        assertFalse(bike.isVehicleAc());

        Driver auto = new Driver();
        auto.setFleet(Driver.Fleet.INDEPENDENT);
        DriverVehicles.apply(auto, vehicle("auto", null, null));
        assertEquals(3, auto.getVehicleSeats());
        assertFalse(auto.isServesTrip());
    }

    @Test
    @DisplayName("an auto driver switching Trips on is told trips are by car")
    void autoCannotSwitchTripsOn() {
        Driver auto = new Driver();
        auto.setFleet(Driver.Fleet.INDEPENDENT);
        DriverVehicles.apply(auto, vehicle("AUTO", true, false));
        assertThrows(IllegalArgumentException.class, () -> DriverVehicles.setModes(auto, null, true));
    }

    @Test
    @DisplayName("an owner-fleet car driver takes Trips")
    void carModes() {
        Driver car = new Driver();
        car.setServesCity(false);
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

    @Test
    @DisplayName("an independent driver cannot switch Trips on, even with a car")
    void independentDriverCannotTakeTrips() {
        Driver d = new Driver();
        d.setFleet(Driver.Fleet.INDEPENDENT);
        assertThrows(IllegalArgumentException.class, () -> DriverVehicles.setModes(d, null, true));
    }

    @Test
    @DisplayName("the owner's form quietly keeps Trips off for an independent driver")
    void applyForcesTripsOffForIndependent() {
        Driver d = new Driver();
        d.setFleet(Driver.Fleet.INDEPENDENT);
        DriverVehicles.apply(d, vehicle("CAR", true, true));
        assertFalse(d.isServesTrip());
        assertTrue(d.isServesCity());
    }

    @Test
    @DisplayName("a travels owner's driver cannot switch City rides on or off themselves: their City car decides")
    void ownerFleetCityFollowsTheCar() {
        Driver off = new Driver();
        off.setServesCity(false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DriverVehicles.setModes(off, true, null));
        assertTrue(e.getMessage().contains("City car"));

        Driver paired = new Driver();
        paired.setServesCity(true);
        assertThrows(IllegalArgumentException.class, () -> DriverVehicles.setModes(paired, false, null));
        DriverVehicles.setModes(paired, true, null); // saying what already is, is fine
        assertTrue(paired.isServesCity());
    }

    @Test
    @DisplayName("the owner's driver form leaves an owner-fleet driver's City switch to their City car")
    void applyLeavesOwnerFleetCityAlone() {
        Driver fresh = new Driver();
        DriverVehicles.apply(fresh, vehicle("CAR", true, true));
        assertFalse(fresh.isServesCity()); // no City car yet

        Driver paired = new Driver();
        paired.setId(5L);
        paired.setServesCity(true);
        DriverVehicles.apply(paired, vehicle("CAR", false, true));
        assertTrue(paired.isServesCity());
    }
}
