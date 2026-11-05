package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.entity.CarCategory;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A travels owner's cars (V27). Every call acts for the owner of the current request: another
 * owner's car is "not found". Plates are stored in capitals without spaces, unique across owners.
 */
@Service
public class VehicleService {

    /** Indian registration numbers once spaces are gone: KA01AB1234, 22BH1234AA. */
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9-]{4,20}$");

    /** What the owner's form sends; blank fields on an edit keep their value. */
    public record VehicleForm(String category, String registrationNumber, String model, Integer seats, Boolean ac) {
    }

    private final VehicleRepository vehicles;
    private final FileStorageService files;
    private final OwnerScope ownerScope = new OwnerScope();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UnscopedLookups lookups;

    public VehicleService(VehicleRepository vehicles, FileStorageService files) {
        this.vehicles = vehicles;
        this.files = files;
    }

    public List<Vehicle> list() {
        return vehicles.findByOwnerIdOrderByIdAsc(ownerScope.ownerId());
    }

    /** Adds a car; the RC photo is stored only once everything else is valid. */
    @Transactional
    public Vehicle create(VehicleForm form, MultipartFile rcPhoto) {
        Vehicle v = new Vehicle();
        v.setOwnerId(ownerScope.ownerId());
        apply(v, form, true);
        if (rcPhoto != null && !rcPhoto.isEmpty()) {
            v.setRcPhoto(files.storeFile(rcPhoto, "rc"));
        }
        return vehicles.save(v);
    }

    @Transactional
    public Vehicle update(Long id, VehicleForm form) {
        Vehicle v = mine(id);
        apply(v, form, false);
        return vehicles.save(v);
    }

    /** Takes a car out of use; refused while it is on an unfinished trip. */
    @Transactional
    public Vehicle deactivate(Long id) {
        Vehicle v = mine(id);
        if (vehicles.countActiveBookings(id) > 0) {
            throw new ConflictException(v.getRegistrationNumber() + " is on a trip that has not finished yet.");
        }
        v.setActive(false);
        return vehicles.save(v);
    }

    @Transactional
    public Vehicle activate(Long id) {
        Vehicle v = mine(id);
        v.setActive(true);
        return vehicles.save(v);
    }

    /** One of my cars, or "not found". */
    public Vehicle mine(Long id) {
        Long me = ownerScope.ownerId();
        return vehicles.findById(id).filter(v -> me.equals(v.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Car not found"));
    }

    private void apply(Vehicle v, VehicleForm f, boolean creating) {
        if (f == null) {
            throw new IllegalArgumentException("Give the car's details");
        }
        if (creating || f.category() != null) {
            CarCategory c = CarCategory.parse(f.category());
            if (c == null) {
                throw new IllegalArgumentException("Choose a car type");
            }
            v.setCategory(c);
        }
        if (creating || f.registrationNumber() != null) {
            String plate = f.registrationNumber() == null ? ""
                    : f.registrationNumber().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!PLATE.matcher(plate).matches()) {
                throw new IllegalArgumentException("Registration number looks wrong. Use the number plate, e.g. KA 01 AB 1234");
            }
            // Through UnscopedLookups: the row filter hides other owners' plates from this owner.
            (lookups != null ? lookups.vehiclePlateHolder(plate) : vehicles.findByRegistrationKey(plate).map(Vehicle::getId))
                    .filter(otherId -> v.getId() == null || !v.getId().equals(otherId))
                    .ifPresent(otherId -> {
                        throw new IllegalArgumentException("Registration number already added");
                    });
            v.setRegistrationNumber(plate);
        }
        if (f.model() != null) {
            String model = f.model().trim();
            if (model.length() > 60) {
                throw new IllegalArgumentException("Car model is too long");
            }
            v.setModel(model.isEmpty() ? null : model);
        }
        if (creating || f.seats() != null) {
            if (f.seats() == null || f.seats() < 1 || f.seats() > 30) {
                throw new IllegalArgumentException("A car has between 1 and 30 passenger seats");
            }
            v.setSeats(f.seats());
        }
        if (f.ac() != null) {
            v.setAc(f.ac());
        }
    }
}
