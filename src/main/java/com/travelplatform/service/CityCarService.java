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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * An owner's choice of what each car does (V35): City rides, Travel trips, or neither.
 *
 * <p>A City car has one driver, the owner's own. While paired, that driver is a City driver: their
 * {@code serves_city} is on and their City-matching vehicle (CAR, seats, AC, model) is the car's,
 * so the existing Ride Now matching offers them nearby rides once they go Online. Leaving City mode
 * turns that off and takes them offline for City rides at once; their Travel trips are untouched.
 */
@Service
public class CityCarService {

    /** The driver record allows 1-20 passenger seats; a larger car still takes City rides. */
    private static final int MAX_DRIVER_SEATS = 20;

    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final TravelBookingRepository bookings;
    private final OwnerScope ownerScope = new OwnerScope();

    @Autowired(required = false)
    private DriverStateUpdates driverStates;

    @Autowired(required = false)
    private DriverPresenceService presence;

    @Autowired(required = false)
    private UnscopedLookups lookups;

    public CityCarService(VehicleRepository vehicles, DriverRepository drivers, TravelBookingRepository bookings) {
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.bookings = bookings;
    }

    void setDriverStates(DriverStateUpdates driverStates) {
        this.driverStates = driverStates;
    }

    void setPresence(DriverPresenceService presence) {
        this.presence = presence;
    }

    /**
     * Puts one of my cars in a mode. CITY needs {@code driverId}; passing CITY again with another
     * driver changes the car's driver.
     */
    @Transactional
    public Vehicle setMode(Long vehicleId, String rawMode, Long driverId) {
        Vehicle.Mode mode = Vehicle.Mode.parse(rawMode);
        if (mode == null) {
            throw new IllegalArgumentException("Choose City, Travel or Off");
        }
        Vehicle car = mine(vehicleId);
        if (mode == Vehicle.Mode.CITY) {
            return toCity(car, driverId);
        }
        release(car);
        car.setMode(mode);
        return vehicles.save(car);
    }

    /**
     * Ends this car's City pairing, if it has one: refused while its driver has a City ride open.
     * Leaves the car's mode for the caller to set (VehicleService turns a retired car OFF).
     */
    @Transactional
    public void release(Vehicle car) {
        Long driverId = car.getCityDriverId();
        if (driverId == null) {
            return;
        }
        if (openCityRides(driverId) > 0) {
            throw new ConflictException(car.getRegistrationNumber()
                    + " is on a City ride right now. Switch it after the ride is finished.");
        }
        car.setCityDriverId(null);
        car.setMode(Vehicle.Mode.OFF);
        vehicles.saveAndFlush(car); // the unique pairing index is free before anyone is re-paired
        drivers.findById(driverId).ifPresent(this::stopCity);
    }

    private Vehicle toCity(Vehicle car, Long driverId) {
        if (!car.isActive()) {
            throw new IllegalArgumentException("Turn the car back on before using it for City rides");
        }
        if (car.getCategory() == CarCategory.TEMPO_TRAVELLER) {
            throw new IllegalArgumentException("A Tempo Traveller does Travel trips only, not City rides");
        }
        if (driverId == null) {
            throw new IllegalArgumentException("Choose the driver who will drive this car on City rides");
        }
        Driver driver = drivers.findById(driverId)
                .filter(d -> Objects.equals(d.getOwnerId(), car.getOwnerId()) && d.getFleet() == Driver.Fleet.OWNER_FLEET)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
        if (driver.getVerificationStatus() == Driver.Verification.REJECTED) {
            throw new IllegalArgumentException(driver.getName() + " was not approved, so cannot take City rides");
        }
        if (car.getMode() != Vehicle.Mode.CITY && vehicles.countOpenTravelBookings(car.getId()) > 0) {
            throw new ConflictException(car.getRegistrationNumber() + " has a Travel trip that is not finished yet."
                    + " Finish it, or move it to another car on the Bookings page, first.");
        }
        Optional<Vehicle> other = vehicles.findByCityDriverId(driverId).filter(v -> !v.getId().equals(car.getId()));
        if (other.isPresent()) {
            throw new ConflictException(driver.getName() + " already drives " + other.get().getRegistrationNumber()
                    + " on City rides. Take them off that car first.");
        }
        if (driverId.equals(car.getCityDriverId())) {
            pair(car, driver); // same pairing: refresh the copied car details only
            return car;
        }
        release(car); // a different driver: the previous one stops first
        car.setMode(Vehicle.Mode.CITY);
        car.setCityDriverId(driverId);
        Vehicle saved = vehicles.save(car);
        pair(saved, driver);
        return saved;
    }

    /** The car changed (seats, AC, model): its City driver's matching details follow. */
    @Transactional
    public void carEdited(Vehicle car) {
        if (car.getMode() == Vehicle.Mode.CITY && car.getCityDriverId() != null) {
            drivers.findById(car.getCityDriverId()).ifPresent(d -> pair(car, d));
        }
    }

    private void pair(Vehicle car, Driver driver) {
        driver.setVehicleType(VehicleType.CAR);
        driver.setVehicleSeats(Math.max(1, Math.min(MAX_DRIVER_SEATS, car.getSeats())));
        driver.setVehicleAc(car.isAc());
        if (car.getModel() != null) {
            driver.setVehicleModel(car.getModel());
        }
        driver.setServesCity(true);
        drivers.save(driver);
        if (driverStates != null) {
            driverStates.profileChanged(driver);
        }
    }

    private void stopCity(Driver driver) {
        driver.setServesCity(false);
        drivers.save(driver);
        if (driverStates != null) {
            driverStates.profileChanged(driver);
        }
        if (presence != null) {
            presence.remove(driver.getId());
        }
    }

    /** City rides are the platform owner's bookings, so an owner's own connection cannot see them. */
    private long openCityRides(Long driverId) {
        return lookups != null ? lookups.openCityRides(driverId) : bookings.countOpenCityRidesByDriverId(driverId);
    }

    private Vehicle mine(Long id) {
        Long me = ownerScope.ownerId();
        return vehicles.findById(id).filter(v -> me.equals(v.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Car not found"));
    }
}
