package com.travelplatform.service;

import com.travelplatform.dto.DriverCreationResponse;
import com.travelplatform.entity.Driver;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;

@Service
@Slf4j
public class AdminDriverService {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private TravelBookingRepository travelBookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private FileStorageService fileStorageService;

    // The matching engine's copy of driver state; absent in tests that build this service by hand.
    @Autowired(required = false)
    private com.travelplatform.service.dispatch.DriverStateUpdates driverStates;

    @Autowired(required = false)
    private com.travelplatform.repository.VehicleRepository vehicleRepository;

    @Autowired(required = false)
    private UnscopedLookups lookups;

    private final com.travelplatform.config.OwnerScope ownerScope = new com.travelplatform.config.OwnerScope();

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$";

    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public DriverCreationResponse createDriver(
            String name, String mobile, String email, String licenseNumber, String aadhaarNumber,
            MultipartFile photo, MultipartFile licensePhoto, MultipartFile aadhaarPhoto,
            com.travelplatform.dto.VehicleRequest vehicle) {
        return createDriver(name, mobile, email, licenseNumber, aadhaarNumber, photo, licensePhoto, aadhaarPhoto,
                vehicle, null);
    }

    /**
     * A travels owner adds a driver to their own fleet (V27): Trips only, pending until the owner
     * checks the licence and RC and approves (OwnerDriverReviewService), which is when the login
     * is emailed. {@code defaultVehicleId} is the driver's usual car, one of the owner's own cars.
     */
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public DriverCreationResponse createDriver(
            String name, String mobile, String email, String licenseNumber, String aadhaarNumber,
            MultipartFile photo, MultipartFile licensePhoto, MultipartFile aadhaarPhoto,
            com.travelplatform.dto.VehicleRequest vehicle, Long defaultVehicleId) {
        Long ownerId = ownerScope.ownerId();
        requireUsableCar(ownerId, defaultVehicleId);

        // Checked before any file is written: a bad number plate should not leave orphaned uploads.
        Driver plateCheck = new Driver();
        DriverVehicles.apply(plateCheck, vehicle);

        // Cheap uniqueness checks first so we don't waste disk on a request that will fail. They must
        // see every owner's and independent driver's rows, which the owner's row filter hides.
        if (lookups != null) {
            String taken = lookups.driverConflict(mobile, email, licenseNumber, aadhaarNumber);
            if (taken != null) {
                throw new IllegalArgumentException(taken);
            }
        }
        if (driverRepository.existsByMobile(mobile)) {
            throw new IllegalArgumentException("Mobile number already registered");
        }
        if (driverRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (driverRepository.existsByLicenseNumber(licenseNumber)) {
            throw new IllegalArgumentException("License number already registered");
        }
        if (driverRepository.existsByAadhaarNumber(aadhaarNumber)) {
            throw new IllegalArgumentException("Aadhaar number already registered");
        }
        requirePlateFree(plateCheck.getVehicleNumber(), null);

        // File IO happens BEFORE the @Transactional block — disk hiccups must not roll back
        // a successful DB insert, and a DB rollback must not orphan written files.
        java.util.List<String> stored = new java.util.ArrayList<>();
        String generatedPassword = generatePassword();
        Driver driver;
        try {
            String photoUrl = store(photo, "photo", stored);
            String licensePhotoUrl = store(licensePhoto, "license", stored);
            String aadhaarPhotoUrl = store(aadhaarPhoto, "aadhaar", stored);
            driver = persistDriver(
                    name, mobile, email, licenseNumber, aadhaarNumber,
                    photoUrl, licensePhotoUrl, aadhaarPhotoUrl, generatedPassword, vehicle, ownerId, defaultVehicleId);
        } catch (RuntimeException e) {
            // Identity scans must not outlive a driver that was never saved.
            for (String path : stored) {
                try {
                    fileStorageService.delete(path);
                } catch (RuntimeException ignored) {
                    // Best-effort; must not hide the original failure.
                }
            }
            throw e;
        }
        if (driverStates != null) {
            driverStates.profileChanged(driver);
        }

        // Email is @Async on EmailService, but dispatching it AFTER the DB commit means
        // a slow SMTP handshake never lengthens the API response.
        log.info("Driver {} added to owner {}'s fleet, pending the owner's checks", driver.getId(), ownerId);

        return new DriverCreationResponse(
                driver.getId(),
                driver.getName(),
                driver.getEmail(),
                driver.getMobile(),
                "Driver added. Check their licence and RC, then approve them to email their login."
        );
    }

    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public void deleteDriver(Long driverId) {
        Driver driver = actingOn(driverId);

        // Block deletion while the driver is tied to a live trip — removing them would orphan
        // an active booking (a passenger expecting this driver) with no way to reassign.
        long activeBookings = travelBookingRepository.countActiveBookingsByDriverId(driverId);
        if (activeBookings > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete driver with " + activeBookings + " active booking(s). "
                            + "Complete or cancel those trips first.");
        }

        driverRepository.delete(driver);
        log.info("Driver deleted with ID: {}", driverId);
    }

    // Spring Data JPA's repository.save() is already transactional; a single insert needs no outer @Transactional.
    private Driver persistDriver(String name, String mobile, String email, String licenseNumber,
                                   String aadhaarNumber, String photoUrl, String licensePhotoUrl,
                                   String aadhaarPhotoUrl, String generatedPassword,
                                   com.travelplatform.dto.VehicleRequest vehicle, Long ownerId, Long defaultVehicleId) {
        Driver driver = new Driver();
        driver.setName(name);
        driver.setMobile(mobile);
        driver.setEmail(email);
        driver.setPassword(passwordEncoder.encode(generatedPassword));
        driver.setLicenseNumber(licenseNumber);
        driver.setAadhaarNumber(aadhaarNumber);
        driver.setPhoto(photoUrl);
        driver.setLicensePhoto(licensePhotoUrl);
        driver.setAadhaarPhoto(aadhaarPhotoUrl);
        driver.setRole("ROLE_DRIVER");
        driver.setStatus(Driver.Status.ACTIVE);
        driver.setFirstLogin(true);
        driver.setEmailVerified(false);
        driver.setFleet(Driver.Fleet.OWNER_FLEET);
        driver.setOwnerId(ownerId);
        driver.setDefaultVehicleId(defaultVehicleId);
        driver.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
        driver.setSubmittedAt(java.time.LocalDateTime.now());
        DriverVehicles.apply(driver, vehicle);
        // A new owner-fleet driver takes Trips; City rides start only when the owner pairs them with a City car (V35).
        driver.setServesCity(false);
        driver.setServesTrip(true);
        return driverRepository.save(driver);
    }

    /** A usual car must be one of the owner's own cars in use; none is fine. */
    private void requireUsableCar(Long ownerId, Long vehicleId) {
        if (vehicleId == null) {
            return;
        }
        com.travelplatform.entity.Vehicle car = vehicleRepository.findById(vehicleId)
                .filter(v -> ownerId.equals(v.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Car not found"));
        if (!car.isActive()) {
            throw new IllegalArgumentException(car.getRegistrationNumber() + " is not in use. Choose another car.");
        }
    }

    /** Sets a driver's usual car (one of the owner's own cars in use). */
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public Driver setDefaultVehicle(Long driverId, Long vehicleId) {
        Long ownerId = ownerScope.ownerId();
        Driver driver = ownFleetDriver(driverId, ownerId);
        requireUsableCar(ownerId, vehicleId);
        driver.setDefaultVehicleId(vehicleId);
        return driverRepository.save(driver);
    }

    /** A driver the caller may manage: one of their own, or - for the platform owner - an independent one. */
    private Driver actingOn(Long driverId) {
        Long me = ownerScope.ownerId();
        return driverRepository.findById(driverId)
                .filter(d -> me.equals(d.getOwnerId())
                        || (d.getFleet() == Driver.Fleet.INDEPENDENT && ownerScope.isPlatform()))
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + driverId));
    }

    /** One of this owner's fleet drivers, or "not found". */
    private Driver ownFleetDriver(Long driverId, Long ownerId) {
        return driverRepository.findById(driverId).filter(d -> ownerId.equals(d.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found"));
    }

    private String store(MultipartFile file, String kind, java.util.List<String> stored) {
        String path = fileStorageService.storeFile(file, kind);
        stored.add(path);
        return path;
    }

    /** One plate, one driver, ignoring case and spacing (same key as the V16 unique index). */
    private void requirePlateFree(String plate, Long ownDriverId) {
        if (plate == null || plate.isBlank()) {
            return;
        }
        String key = plate.replaceAll("\s+", "").toUpperCase(java.util.Locale.ROOT);
        (lookups != null ? lookups.driverPlateHolder(key) : driverRepository.findByVehicleKey(key).map(Driver::getId))
                .filter(otherId -> !otherId.equals(ownDriverId))
                .ifPresent(otherId -> {
                    throw new IllegalArgumentException("Vehicle number already registered");
                });
    }

    /** The owner sets a driver's vehicle and City/Trip switches - e.g. for drivers added before V14. */
    @Caching(evict = {
            @CacheEvict(value = "allDrivers", allEntries = true),
            @CacheEvict(value = "driverById", allEntries = true)
    })
    public Driver updateVehicle(Long driverId, com.travelplatform.dto.VehicleRequest vehicle) {
        Driver driver = actingOn(driverId);
        DriverVehicles.apply(driver, vehicle);
        requirePlateFree(driver.getVehicleNumber(), driverId);
        Driver saved = driverRepository.save(driver);
        if (driverStates != null) {
            // A car switched to a bike must stop being offered car rides from the next round.
            driverStates.profileChanged(saved);
        }
        return saved;
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return password.toString();
    }
}
