package com.travelplatform.config;

import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusPlace;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTripRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bus tables (V30) through the app's own repositories: the entities match the schema
 * (ddl-auto=validate), an owner's request sees and writes only their own buses, and creating a
 * route's trip for a date twice leaves one trip.
 *
 * <p>Not transactional: each repository call takes its own pooled connection, as a request does.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(OwnerScopeDataSourcePostProcessor.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BusRowLevelSecurityTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private BusRepository buses;
    @Autowired
    private BusSeatRepository seats;
    @Autowired
    private BusRouteRepository routes;
    @Autowired
    private BusRouteStopRepository stops;
    @Autowired
    private BusRoutePointRepository points;
    @Autowired
    private BusPlaceRepository places;
    @Autowired
    private BusTripRepository trips;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager txManager;

    private long ownerA;
    private long ownerB;
    private long busA;
    private long busB;

    @BeforeEach
    void fixtures() {
        OwnerScope.clear();
        jdbc.update("TRUNCATE bus_trips, bus_route_fares, bus_route_points, bus_route_stops, bus_routes, bus_seats, "
                + "buses RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM bus_places WHERE created_by_owner_id IS NOT NULL");
        jdbc.update("DELETE FROM owners");
        ownerA = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('a@x.com', 'ROLE_OWNER', now(), 'NJO-0001', 'APPROVED') RETURNING id", Long.class);
        ownerB = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('b@x.com', 'ROLE_OWNER', now(), 'NJO-0002', 'APPROVED') RETURNING id", Long.class);
        busA = buses.save(bus(ownerA, "TN01AB1234")).getId();
        busB = buses.save(bus(ownerB, "TN02CD5678")).getId();
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private static Bus bus(long owner, String plate) {
        Bus b = new Bus();
        b.setOwnerId(owner);
        b.setRegistrationNumber(plate);
        b.setName("Express " + plate);
        b.setPermitType(Bus.PermitType.STAGE_CARRIAGE);
        return b;
    }

    @Test
    @DisplayName("an owner sees only their own buses, by list and by id")
    void ownerSeesOnlyOwnBuses() {
        OwnerScope.bind(ownerB, false);

        assertEquals(List.of(busB), buses.findAll().stream().map(Bus::getId).toList());
        assertTrue(buses.findById(busA).isEmpty());
        // The platform owner gets no extra bus rows: every bus has an owner.
        OwnerScope.bind(ownerA, true);
        assertEquals(List.of(busA), buses.findAll().stream().map(Bus::getId).toList());
    }

    @Test
    @DisplayName("an owner cannot add a seat to another owner's bus")
    void ownerCannotWriteForAnotherOwner() {
        OwnerScope.bind(ownerB, false);
        BusSeat s = new BusSeat();
        s.setBusId(busA);
        s.setOwnerId(ownerA);
        s.setLabel("L1");
        s.setKind(BusSeat.Kind.SEATER);

        assertThrows(RuntimeException.class, () -> seats.save(s));
        OwnerScope.clear();
        assertEquals(0, seats.count());
    }

    @Test
    @DisplayName("creating a route's trip for a date twice leaves one trip; owners can add towns")
    void tripCreationIsIdempotent() {
        OwnerScope.bind(ownerA, false);
        BusPlace chennai = places.findByNameKey("chennai").orElseThrow();
        BusPlace added = new BusPlace();
        added.setName("Ulundurpet");
        added.setCreatedByOwnerId(ownerA);
        added = places.save(added);

        BusRoute r = new BusRoute();
        r.setOwnerId(ownerA);
        r.setBusId(busA);
        r.setName("Chennai - Ulundurpet");
        r.setRunDays((short) 127);
        r.setActiveFrom(LocalDate.now());
        r.setCancellationPolicy("[{\"minHours\":0,\"percent\":100}]");
        r = routes.save(r);
        BusRouteStop first = stop(r.getId(), 0, chennai.getId());
        stop(r.getId(), 1, added.getId());
        BusRoutePoint p = new BusRoutePoint();
        p.setStopId(first.getId());
        p.setOwnerId(ownerA);
        p.setKind(BusRoutePoint.Kind.BOARDING);
        p.setName("Koyambedu");
        p.setTime(LocalTime.of(21, 30));
        points.save(p);

        long routeId = r.getId();
        LocalDate date = LocalDate.now().plusDays(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Integer firstRun = tx.execute(s -> trips.insertIfAbsent(routeId, busA, ownerA, date,
                date.atTime(21, 30), date.plusDays(1).atTime(4, 0)));
        Integer secondRun = tx.execute(s -> trips.insertIfAbsent(routeId, busA, ownerA, date,
                date.atTime(21, 30), date.plusDays(1).atTime(4, 0)));

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(1, trips.findByOwnerIdAndTravelDateBetweenOrderByDepartureAtAscIdAsc(ownerA, date, date).size());
        assertTrue(routes.findById(routeId).orElseThrow().getCancellationPolicy().contains("\"minHours\": 0"));
        assertEquals(LocalTime.of(21, 30), points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(List.of(first.getId()))
                .get(0).getTime());

        OwnerScope.bind(ownerB, false);
        assertFalse(trips.findAll().iterator().hasNext());
    }

    private BusRouteStop stop(long routeId, int seq, long placeId) {
        BusRouteStop s = new BusRouteStop();
        s.setRouteId(routeId);
        s.setOwnerId(ownerA);
        s.setSeq(seq);
        s.setPlaceId(placeId);
        return stops.save(s);
    }
}
