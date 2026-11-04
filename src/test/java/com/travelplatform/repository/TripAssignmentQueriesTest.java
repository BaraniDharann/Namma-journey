package com.travelplatform.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Picking a driver and a car for a Trip, against real Postgres: only the booked owner's approved
 * drivers and active cars of the booked category, with enough seats and AC when asked, neither
 * busy on those dates, the driver's usual car first.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TripAssignmentQueriesTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final LocalDate DAY = LocalDate.of(2026, 12, 10);
    private static final List<Long> NOBODY = List.of(-1L);

    @Autowired
    private DriverRepository drivers;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private JdbcTemplate jdbc;

    private long owner;
    private long otherOwner;
    private long mobile = 9000000100L;

    @BeforeEach
    void owners() {
        owner = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('a@x.com', 'ROLE_OWNER', now(), 'NJO-0001', 'APPROVED') RETURNING id", Long.class);
        otherOwner = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('b@x.com', 'ROLE_OWNER', now(), 'NJO-0002', 'APPROVED') RETURNING id", Long.class);
    }

    private long vehicle(long ownerId, String category, int seats, boolean ac) {
        return jdbc.queryForObject("INSERT INTO vehicles (owner_id, category, registration_number, seats, ac) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, ownerId, category, "KA" + (mobile++), seats, ac);
    }

    private long driver(long ownerId, Long usualCar, String verification) {
        String m = String.valueOf(mobile++);
        return jdbc.queryForObject("INSERT INTO drivers (name, mobile, password, license_number, aadhaar_number, role, "
                + "status, first_login, email_verified, token_version, created_at, vehicle_type, serves_trip, serves_city, "
                + "fleet, verification_status, owner_id, default_vehicle_id) VALUES ('D', ?, 'x', ?, ?, 'ROLE_DRIVER', "
                + "'ACTIVE', true, true, 0, now(), 'CAR', true, false, 'OWNER_FLEET', ?, ?, ?) RETURNING id",
                Long.class, m, "L" + m, "1111" + m.substring(2), verification, ownerId, usualCar);
    }

    private long driver(long ownerId, Long usualCar) {
        return driver(ownerId, usualCar, "APPROVED");
    }

    private void busy(Long driverId, Long vehicleId) {
        jdbc.update("INSERT INTO travel_bookings (id, booking_date, distance_km, estimated_time_minutes, from_place, "
                + "status, to_place, travel_days, user_id, user_name, user_phone, ac_type, from_date, to_date, "
                + "travel_members, driver_id, vehicle_id, total_amount, service_mode, owner_id, car_category) "
                + "VALUES (?, now(), 10, 10, 'A', 'CONFIRMED', 'B', 1, ?, 'T', '9876543210', 'AC', ?, ?, 2, ?, ?, 100, "
                + "'TRIP', ?, 'SEDAN')", UUID.randomUUID(), UUID.randomUUID(), DAY, DAY, driverId, vehicleId, owner);
    }

    private Optional<DriverRepository.TripPair> pick(String category, int seats, boolean ac, List<Long> excluded) {
        return drivers.findFirstFreeTripPair(owner, category, seats, ac, DAY, DAY, excluded);
    }

    @Test
    @DisplayName("the driver's usual car comes first, even when a lower-numbered car is free")
    void prefersUsualCar() {
        long spare = vehicle(owner, "SEDAN", 4, true);
        long usual = vehicle(owner, "SEDAN", 4, true);
        long d = driver(owner, usual);

        DriverRepository.TripPair p = pick("SEDAN", 2, false, NOBODY).orElseThrow();

        assertEquals(d, p.getDriverId());
        assertEquals(usual, p.getVehicleId());
        assertTrue(spare < usual);
    }

    @Test
    @DisplayName("only a car of the booked category, with enough seats and AC when asked")
    void respectsCategorySeatsAndAc() {
        long suvNoAc = vehicle(owner, "SUV", 7, false);
        long small = vehicle(owner, "SEDAN", 4, true);
        driver(owner, small);

        assertTrue(pick("SUV", 6, true, NOBODY).isEmpty(), "the only SUV has no AC");
        assertEquals(suvNoAc, pick("SUV", 6, false, NOBODY).orElseThrow().getVehicleId());
        assertTrue(pick("SEDAN", 5, false, NOBODY).isEmpty(), "the Sedan seats 4");
    }

    @Test
    @DisplayName("a car already on a trip those days is skipped, and so is a busy or declining driver")
    void skipsBusyCarAndDriver() {
        long car1 = vehicle(owner, "SEDAN", 4, true);
        long car2 = vehicle(owner, "SEDAN", 4, true);
        long d1 = driver(owner, car1);
        long d2 = driver(owner, car2);
        busy(null, car1);

        // d1's usual car is out; a driver in their own usual car beats a lower driver id.
        DriverRepository.TripPair p = pick("SEDAN", 2, false, NOBODY).orElseThrow();
        assertEquals(d2, p.getDriverId());
        assertEquals(car2, p.getVehicleId());

        busy(d2, null);
        p = pick("SEDAN", 2, false, NOBODY).orElseThrow();
        assertEquals(d1, p.getDriverId(), "with d2 busy, d1 takes the free car");
        assertEquals(car2, p.getVehicleId());
        assertTrue(pick("SEDAN", 2, false, List.of(d1)).isEmpty(), "a declining driver is skipped");
    }

    @Test
    @DisplayName("another owner's drivers and cars, and unapproved drivers, are never picked")
    void ownOwnerOnly() {
        long theirs = vehicle(otherOwner, "SEDAN", 4, true);
        driver(otherOwner, theirs);
        long mine = vehicle(owner, "SEDAN", 4, true);
        driver(owner, mine, "UNDER_REVIEW");

        assertTrue(pick("SEDAN", 2, false, NOBODY).isEmpty());
    }

    @Test
    @DisplayName("the assign screen lists only my free approved drivers and my free suitable cars")
    void assignOptions() {
        long usual = vehicle(owner, "SUV", 7, true);
        long busyCar = vehicle(owner, "SUV", 7, true);
        vehicle(owner, "SEDAN", 4, true);
        vehicle(otherOwner, "SUV", 7, true);
        long free = driver(owner, usual);
        long occupied = driver(owner, null);
        driver(owner, null, "UNDER_REVIEW");
        driver(otherOwner, null);
        busy(occupied, busyCar);

        List<Long> driverIds = drivers.findFreeOwnerTripDrivers(owner, DAY, DAY, null).stream()
                .map(com.travelplatform.entity.Driver::getId).toList();
        List<Long> carIds = vehicles.findFreeVehicles(owner, com.travelplatform.entity.CarCategory.SUV, 6, false, DAY, DAY, null)
                .stream().map(com.travelplatform.entity.Vehicle::getId).toList();

        assertEquals(List.of(free), driverIds);
        assertEquals(List.of(usual), carIds);
    }
}
