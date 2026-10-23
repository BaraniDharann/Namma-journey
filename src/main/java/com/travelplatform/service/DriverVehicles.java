package com.travelplatform.service;

import com.travelplatform.dto.VehicleRequest;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.VehicleType;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The rules for a driver's vehicle and City/Trip switches, in one place for the owner's create
 * and edit forms and the driver's own switches. The database enforces the same "trips are by
 * car" rule (V14), so this is about a clear message, not the last line of defence.
 */
final class DriverVehicles {

    /** Indian registration numbers, written with or without spaces: KA 01 AB 1234, 22 BH 1234 AA. */
    private static final Pattern NUMBER = Pattern.compile("^[A-Z0-9][A-Z0-9 -]{3,18}[A-Z0-9]$");

    private DriverVehicles() {
    }

    /** Applies the owner's vehicle form; anything left blank keeps the driver's current value or the type's default. */
    static void apply(Driver driver, VehicleRequest r) {
        if (r == null) {
            return;
        }
        VehicleType type = VehicleType.parse(r.getVehicleType());
        if (type != null && type != driver.getVehicleType()) {
            driver.setVehicleType(type);
            // A new kind of vehicle starts from that kind's defaults, not the old vehicle's.
            driver.setVehicleSeats(type.defaultSeats());
            driver.setVehicleAc(type == VehicleType.CAR);
        }
        type = driver.getVehicleType();

        if (r.getVehicleNumber() != null) {
            String number = r.getVehicleNumber().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (number.isEmpty()) {
                driver.setVehicleNumber(null);
            } else if (!NUMBER.matcher(number).matches()) {
                throw new IllegalArgumentException("Vehicle number looks wrong. Use the number plate, e.g. KA 01 AB 1234");
            } else {
                driver.setVehicleNumber(number);
            }
        }
        if (r.getVehicleModel() != null) {
            String model = r.getVehicleModel().trim();
            if (model.length() > 60) {
                throw new IllegalArgumentException("Vehicle model is too long");
            }
            driver.setVehicleModel(model.isEmpty() ? null : model);
        }
        if (type == VehicleType.CAR) {
            if (r.getVehicleAc() != null) {
                driver.setVehicleAc(r.getVehicleAc());
            }
            if (r.getVehicleSeats() != null) {
                if (r.getVehicleSeats() < 1 || r.getVehicleSeats() > 20) {
                    throw new IllegalArgumentException("A car has between 1 and 20 passenger seats");
                }
                driver.setVehicleSeats(r.getVehicleSeats());
            }
        } else {
            // A bike carries one passenger and an auto three; neither has AC.
            driver.setVehicleAc(false);
            driver.setVehicleSeats(type.maxPassengers());
        }
        // On the owner's form a bike, auto or independent driver simply takes no trips, rather than being an error.
        setModes(driver, r.getServesCity(), type == VehicleType.CAR && !independent(driver) ? r.getServesTrip() : Boolean.FALSE);
    }

    private static boolean independent(Driver driver) {
        return driver.getFleet() == Driver.Fleet.INDEPENDENT;
    }

    /** Turns City rides and Trips on or off. Null leaves a switch as it is. */
    static void setModes(Driver driver, Boolean servesCity, Boolean servesTrip) {
        if (servesCity != null) {
            driver.setServesCity(servesCity);
        }
        if (servesTrip != null) {
            if (servesTrip && driver.getVehicleType() != VehicleType.CAR) {
                throw new IllegalArgumentException("Trips are by car only. A "
                        + driver.getVehicleType().name().toLowerCase(Locale.ROOT) + " driver takes City rides.");
            }
            if (servesTrip && independent(driver)) {
                throw new IllegalArgumentException("Trips are assigned by the owner to their own fleet. Independent drivers take City rides.");
            }
            driver.setServesTrip(servesTrip);
        }
        // Switching to a bike or auto ends trips; not a choice the owner has to remember to make.
        if (driver.getVehicleType() != VehicleType.CAR || independent(driver)) {
            driver.setServesTrip(false);
        }
    }
}
