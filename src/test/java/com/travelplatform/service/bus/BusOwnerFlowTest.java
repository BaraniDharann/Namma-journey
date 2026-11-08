package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.config.OwnerScopeDataSourcePostProcessor;
import com.travelplatform.dto.bus.BusForms.BusForm;
import com.travelplatform.dto.bus.BusForms.FareForm;
import com.travelplatform.dto.bus.BusForms.PointForm;
import com.travelplatform.dto.bus.BusForms.RouteForm;
import com.travelplatform.dto.bus.BusForms.SeatForm;
import com.travelplatform.dto.bus.BusForms.StopForm;
import com.travelplatform.dto.bus.BusViews.BusView;
import com.travelplatform.dto.bus.BusViews.RouteView;
import com.travelplatform.dto.bus.BusViews.TripDetail;
import com.travelplatform.dto.bus.BusViews.TripView;
import com.travelplatform.entity.BusDocument;
import com.travelplatform.entity.BusPhoto;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.repository.BusDocumentRepository;
import com.travelplatform.repository.BusPhotoRepository;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRouteFareRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusSeatAllocationRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.OwnerRepository;
import com.travelplatform.service.FileStorageService;
import com.travelplatform.service.NotificationService;
import com.travelplatform.storage.SignedUploadUrls;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The owner side end to end against Postgres, on the owner's row-level-security scope: a bus is
 * added and approved, a route drafted, edited (points keep their ids) and published, which creates
 * its trips; then a seat is blocked and the trip cancelled.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(OwnerScopeDataSourcePostProcessor.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BusOwnerFlowTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private BusRepository buses;
    @Autowired private BusSeatRepository seats;
    @Autowired private BusDocumentRepository documents;
    @Autowired private BusPhotoRepository photos;
    @Autowired private BusTicketRepository tickets;
    @Autowired private BusTicketPassengerRepository passengers;
    @Autowired private BusPlaceRepository places;
    @Autowired private BusRouteRepository routes;
    @Autowired private BusRouteStopRepository stops;
    @Autowired private BusRoutePointRepository points;
    @Autowired private BusRouteFareRepository fares;
    @Autowired private BusTripRepository trips;
    @Autowired private BusSeatAllocationRepository allocations;
    @Autowired private OwnerRepository owners;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private BusService busService;
    private BusRouteService routeService;
    private BusTripService tripService;
    private BusAdminService adminService;
    private long owner;

    @BeforeEach
    void setUp() {
        OwnerScope.clear();
        tx = new TransactionTemplate(txManager);
        BusViewMapper mapper = new BusViewMapper(documents, photos, seats, tickets,
                new SignedUploadUrls("k", "jwt", Duration.ofMinutes(30)));
        busService = new BusService(buses, seats, documents, photos, tickets, places, mock(FileStorageService.class), mapper);
        BusTripGenerator generator = new BusTripGenerator(routes, buses, documents, stops, points, trips,
                mock(NotificationService.class), tx);
        routeService = new BusRouteService(routes, stops, points, fares, places, buses, tickets, busService, generator);
        tripService = new BusTripService(trips, routes, buses, seats, stops, points, places, allocations, tickets, passengers);
        adminService = new BusAdminService(buses, owners, mapper);

        jdbc.update("TRUNCATE bus_seat_allocations, bus_trips, bus_route_fares, bus_route_points, bus_route_stops, "
                + "bus_routes, bus_photos, bus_documents, bus_seats, buses RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM bus_places WHERE created_by_owner_id IS NOT NULL");
        jdbc.update("DELETE FROM owners");
        owner = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('a@x.com', 'ROLE_OWNER', now(), 'NJO-0002', 'APPROVED') RETURNING id", Long.class);
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    /** Runs as the owner's request would: on their scope, in a transaction. */
    private <T> T asOwner(Supplier<T> work) {
        OwnerScope.bind(owner, false);
        try {
            return tx.execute(s -> work.get());
        } finally {
            OwnerScope.clear();
        }
    }

    private long chennai() {
        return places.findByNameKey("Chennai").orElseThrow().getId();
    }

    private long madurai() {
        return places.findByNameKey("Madurai").orElseThrow().getId();
    }

    private RouteForm route(long busId, String boardingName, List<PointForm> extraBoarding) {
        List<PointForm> first = new ArrayList<>();
        first.add(new PointForm(null, "BOARDING", boardingName, "Bus stand", null, null, null, 0, "23:30"));
        first.addAll(extraBoarding);
        return new RouteForm(busId, "Chennai - Madurai", List.of(1, 2, 3, 4, 5, 6, 7), LocalDate.now(ZoneId.of("Asia/Kolkata")),
                null, 4, null,
                List.of(new StopForm(0, chennai(), first),
                        new StopForm(1, madurai(), List.of(new PointForm(null, "DROPPING", "Mattuthavani", null, null,
                                null, null, 1, "07:00")))),
                List.of(new FareForm(0, 1, "SEATER", new BigDecimal("650"))));
    }

    @Test
    @DisplayName("bus → approval → route → publish creates trips → block a seat → cancel the trip")
    void ownerFlow() {
        BusView bus = asOwner(() -> busService.create(new BusForm("TN 01 AB 1234", "Night Rider", true,
                "STAGE_CARRIAGE", List.of("Water bottle"))));
        long busId = bus.id();
        BusView laidOut = asOwner(() -> busService.replaceSeats(busId, List.of(
                new SeatForm("1", 0, 0, 0, "SEATER", null, "2", false),
                new SeatForm("2", 0, 0, 1, "SEATER", null, "1", true),
                new SeatForm("3", 0, 0, 3, "SEATER", null, null, false))));
        assertEquals(3, laidOut.seats().size());
        assertEquals("A/C Seater (2+1)", laidOut.typeLabel());
        // Replacing the layout again reuses the labels.
        asOwner(() -> busService.replaceSeats(busId, List.of(
                new SeatForm("1", 0, 0, 0, "SEATER", null, null, false),
                new SeatForm("2", 0, 0, 1, "SEATER", null, null, false))));

        RouteView draft = asOwner(() -> routeService.create(route(busId, "Koyambedu", List.of())));
        long routeId = draft.id();
        assertEquals(List.of(48, 24, 12, 0), draft.cancellationPolicy().stream().map(s -> s.minHours()).toList());
        assertThrows(ConflictException.class, () -> asOwner(() -> routeService.publish(routeId)),
                "an unapproved bus cannot run a published route");

        // Papers and a photo straight into the tables (uploads are FileStorageService's business), then approval.
        asOwner(() -> {
            for (BusDocument.Type t : BusDocument.Type.values()) {
                BusDocument d = new BusDocument();
                d.setBusId(busId);
                d.setOwnerId(owner);
                d.setType(t);
                d.setPath("/uploads/buses/" + busId + "/doc_" + t + ".jpg");
                d.setExpiresOn(LocalDate.now().plusYears(1));
                documents.save(d);
            }
            BusPhoto p = new BusPhoto();
            p.setBusId(busId);
            p.setOwnerId(owner);
            p.setPath("/uploads/buses/" + busId + "/photo.jpg");
            return photos.save(p);
        });
        assertEquals("APPROVED", asOwner(() -> busService.submit(busId)).status()); // live at once: no admin review of buses

        // Editing keeps the existing point's id and adds the new one.
        long koyambedu = draft.stops().get(0).points().get(0).id();
        RouteView edited = asOwner(() -> routeService.update(routeId, route(busId, "koyambedu", List.of(
                new PointForm(null, "BOARDING", "Guindy", null, null, null, null, 0, "23:55")))));
        assertEquals(koyambedu, edited.stops().get(0).points().get(0).id());
        assertEquals(2, edited.stops().get(0).points().size());
        assertEquals("23:30", edited.stops().get(0).points().get(0).time());

        RouteView published = asOwner(() -> routeService.publish(routeId));
        assertEquals("PUBLISHED", published.status());
        List<TripView> list = asOwner(() -> tripService.list(LocalDate.now(), LocalDate.now().plusDays(60)));
        assertTrue(list.size() >= 59, "about 60 days of trips, got " + list.size());
        assertEquals(2, list.get(0).seatsTotal());
        // Publishing again creates nothing new.
        asOwner(() -> routeService.publish(routeId));
        assertEquals(list.size(), trips.count());

        // Stops are fixed once published.
        assertThrows(ConflictException.class, () -> asOwner(() -> routeService.update(routeId,
                new RouteForm(busId, "X", List.of(1), LocalDate.now(), null, null, null,
                        List.of(new StopForm(0, madurai(), List.of(new PointForm(null, "BOARDING", "A", null, null, null, null, 0, "10:00"))),
                                new StopForm(1, chennai(), List.of(new PointForm(null, "DROPPING", "B", null, null, null, null, 0, "18:00")))),
                        List.of()))));

        long tripId = list.get(list.size() - 1).id();
        TripDetail blocked = asOwner(() -> tripService.block(tripId, List.of("2")));
        assertEquals("BLOCKED", blocked.seats().get(1).allocations().get(0).status());
        assertThrows(ConflictException.class, () -> asOwner(() -> tripService.block(tripId, List.of("2"))));
        assertTrue(asOwner(() -> tripService.manifestCsv(tripId)).startsWith("Bus,TN01AB1234"));

        TripDetail cancelled = asOwner(() -> tripService.cancel(tripId, "Bus under repair"));
        assertEquals("CANCELLED", cancelled.status());
        assertTrue(cancelled.seats().stream().allMatch(s -> s.allocations().isEmpty()));

        // Another owner sees none of it.
        long other = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status) "
                + "VALUES ('b@x.com', 'ROLE_OWNER', now(), 'NJO-0003', 'APPROVED') RETURNING id", Long.class);
        OwnerScope.bind(other, false);
        try {
            assertEquals(0, tx.execute(s -> busService.list()).size());
            assertEquals(0, tx.execute(s -> tripService.list(LocalDate.now(), LocalDate.now().plusDays(60))).size());
        } finally {
            OwnerScope.clear();
        }
    }
}
