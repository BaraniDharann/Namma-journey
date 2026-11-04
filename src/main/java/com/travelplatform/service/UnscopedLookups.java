package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Duplicate checks that must see every owner's rows. Row-level security (V28) hides other owners'
 * plates, mobiles and licences from an owner's own connection, so their uniqueness checks passed
 * and the database's unique index answered with a generic conflict instead of a clear message.
 *
 * <p>Each check runs on a fresh connection (a new transaction, so not the owner's already-scoped
 * one) with the owner scope lifted for just that call, and returns only an id or a message - never
 * another owner's data.
 */
@Service
public class UnscopedLookups {

    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final TransactionTemplate fresh;

    public UnscopedLookups(VehicleRepository vehicles, DriverRepository drivers, PlatformTransactionManager transactions) {
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.fresh = new TransactionTemplate(transactions);
        this.fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.fresh.setReadOnly(true);
    }

    public boolean vehiclePlateTaken(String key) {
        return vehiclePlateHolder(key).isPresent();
    }

    /** The id of the car holding this plate (spaceless, upper case), whoever owns it. */
    public Optional<Long> vehiclePlateHolder(String key) {
        return unscoped(() -> vehicles.findByRegistrationKey(key).map(v -> v.getId()));
    }

    /** The id of the driver whose own profile holds this plate, whoever they belong to. */
    public Optional<Long> driverPlateHolder(String key) {
        return unscoped(() -> drivers.findByVehicleKey(key).map(d -> d.getId()));
    }

    /** Why a new driver's details are already taken by someone, anyone; null when they are free. */
    public String driverConflict(String mobile, String email, String licenseNumber, String aadhaarNumber) {
        return unscoped(() -> {
            if (drivers.existsByMobile(mobile)) {
                return "Mobile number already registered";
            }
            if (drivers.existsByEmail(email)) {
                return "Email already registered";
            }
            if (drivers.existsByLicenseNumber(licenseNumber)) {
                return "License number already registered";
            }
            if (drivers.existsByAadhaarNumber(aadhaarNumber)) {
                return "Aadhaar number already registered";
            }
            return null;
        });
    }

    private <T> T unscoped(Supplier<T> work) {
        Optional<OwnerScope.Bound> bound = OwnerScope.current();
        OwnerScope.clear();
        try {
            return fresh.execute(status -> work.get());
        } finally {
            bound.ifPresent(b -> OwnerScope.bind(b.ownerId(), b.platform()));
        }
    }
}
