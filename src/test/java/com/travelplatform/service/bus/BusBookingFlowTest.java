package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.config.OwnerScopeDataSourcePostProcessor;
import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.dto.bus.BusBookingForms.HoldForm;
import com.travelplatform.dto.bus.BusBookingForms.PassengerForm;
import com.travelplatform.dto.bus.BusBookingForms.ReviewForm;
import com.travelplatform.dto.bus.BusBookingForms.VerifyForm;
import com.travelplatform.dto.bus.BusBookingViews.CancelQuote;
import com.travelplatform.dto.bus.BusBookingViews.MapSeat;
import com.travelplatform.dto.bus.BusBookingViews.PayView;
import com.travelplatform.dto.bus.BusBookingViews.PayoutView;
import com.travelplatform.dto.bus.BusBookingViews.SearchResult;
import com.travelplatform.dto.bus.BusBookingViews.SeatMap;
import com.travelplatform.dto.bus.BusBookingViews.TicketView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusOwnerPayout;
import com.travelplatform.entity.BusPayment;
import com.travelplatform.entity.BusRefund;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRouteFare;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusSeatClass;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.outbox.Outbox;
import com.travelplatform.repository.BusOwnerPayoutRepository;
import com.travelplatform.repository.BusPaymentRepository;
import com.travelplatform.repository.BusPhotoRepository;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRefundRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusReviewRepository;
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
import com.travelplatform.repository.UserRepository;
import com.travelplatform.service.EmailService;
import com.travelplatform.service.RazorpayGateway;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Customer booking and after-booking against Postgres: search, the seat map, holds (limits, the
 * gender rule, idempotency, 20 customers racing for one seat), payment (on time, late, taken,
 * duplicate callback + webhook), the sweeper, cancelling seats, the owner cancelling the trip,
 * the status job, reviews and payouts.
 *
 * <p>Route: Chennai (stop 0, boards 21:00) → Trichy (stop 1, next day 01:00) → Madurai (stop 2,
 * next day 05:00). Seats: U1+U2 an upper single-berth pair, U3 an upper single berth on its own,
 * L1 a ladies' seater, L2 a seater. Sleeper fares for every pair; seaters only Chennai → Madurai.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(OwnerScopeDataSourcePostProcessor.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BusBookingFlowTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String SECRET = "test_secret";

    @Autowired private BusRepository buses;
    @Autowired private BusSeatRepository seats;
    @Autowired private BusPhotoRepository photos;
    @Autowired private BusPlaceRepository places;
    @Autowired private BusRouteRepository routes;
    @Autowired private BusRouteStopRepository stops;
    @Autowired private BusRoutePointRepository points;
    @Autowired private BusRouteFareRepository fares;
    @Autowired private BusTripRepository trips;
    @Autowired private BusSeatAllocationRepository allocations;
    @Autowired private BusTicketRepository tickets;
    @Autowired private BusTicketPassengerRepository passengers;
    @Autowired private BusPaymentRepository payments;
    @Autowired private BusRefundRepository refunds;
    @Autowired private BusReviewRepository reviews;
    @Autowired private BusOwnerPayoutRepository payouts;
    @Autowired private OwnerRepository owners;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private EmailService email;
    private Outbox outbox;
    private BusSearchService search;
    private BusBookingService booking;
    private BusTicketService ticketService;
    private BusPayoutService payoutService;
    private BusTripService tripService;
    private BusTicketViews views;

    private long owner;
    private final List<UUID> customers = new ArrayList<>();
    private long chennai;
    private long trichy;
    private long madurai;
    private long routeId;
    private LocalDate day;
    private long tripId;
    private long koyambedu;
    private long trichyStand;
    private long mattuthavani;
    private final AtomicInteger orders = new AtomicInteger();

    @BeforeEach
    void setUp() {
        OwnerScope.clear();
        tx = new TransactionTemplate(txManager);
        email = mock(EmailService.class);
        outbox = mock(Outbox.class);
        RazorpayGateway gateway = mock(RazorpayGateway.class);
        when(gateway.createOrder(anyLong(), anyString(), anyMap())).thenAnswer(i -> "order_" + orders.incrementAndGet());
        RazorpayProperties razorpay = new RazorpayProperties();
        razorpay.setKeyId("rzp_test_key");
        razorpay.setKeySecret(SECRET);
        BusSettings settings = new BusSettings();
        views = new BusTicketViews(trips, buses, seats, stops, points, places, owners, passengers, refunds, reviews);
        BusNotifier notifier = new BusNotifier(email, outbox, users, trips, buses, stops, points, places, passengers);
        BusRefunds busRefunds = new BusRefunds(refunds, payments, outbox);
        search = new BusSearchService(trips, routes, buses, seats, stops, points, places, fares, allocations, photos,
                reviews, owners, settings);
        booking = new BusBookingService(trips, routes, seats, stops, points, places, fares, allocations, tickets,
                passengers, payments, refunds, owners, gateway, razorpay, busRefunds, notifier, views, settings, txManager);
        ticketService = new BusTicketService(tickets, passengers, allocations, trips, points, payments, reviews,
                busRefunds, notifier, views, txManager);
        payoutService = new BusPayoutService(payouts, trips, routes, buses, tickets, passengers, owners, txManager);
        tripService = new BusTripService(trips, routes, buses, seats, stops, points, places, allocations, tickets, passengers);
        tripService.setTicketService(ticketService);

        jdbc.update("TRUNCATE bus_reviews, bus_owner_payouts, bus_refunds, bus_payments, bus_ticket_passengers, "
                + "bus_seat_allocations, bus_tickets, bus_trips, bus_route_fares, bus_route_points, bus_route_stops, "
                + "bus_routes, bus_photos, bus_documents, bus_seats, buses RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM owners");
        owner = jdbc.queryForObject("INSERT INTO owners (email, role, created_at, owner_code, status, travels_name, "
                + "upi_id, bank_name) VALUES ('o@x.com', 'ROLE_OWNER', now(), 'NJO-0002', 'APPROVED', 'KPN Travels', "
                + "NULL, 'SBI') RETURNING id", Long.class);
        customers.clear();
        for (int i = 0; i < 25; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, created_at, login_type, name, role, email) VALUES (?, now(), 'EMAIL', ?, "
                    + "'ROLE_USER', ?)", id, "Customer " + i, "c" + i + "@x.com");
            customers.add(id);
        }
        chennai = places.findByNameKey("Chennai").orElseThrow().getId();
        trichy = places.findByNameKey("Trichy").orElseThrow().getId();
        madurai = places.findByNameKey("Madurai").orElseThrow().getId();
        day = LocalDate.now(IST).plusDays(5);
        build();
        at(LocalDateTime.now(IST));
    }

    @AfterEach
    void unbind() {
        OwnerScope.clear();
    }

    private void build() {
        Bus bus = new Bus();
        bus.setOwnerId(owner);
        bus.setRegistrationNumber("TN01AB1234");
        bus.setName("Night Rider");
        bus.setAc(true);
        bus.setPermitType(Bus.PermitType.STAGE_CARRIAGE);
        bus.setAmenities("Water bottle,Blanket");
        bus.setStatus(Bus.Status.APPROVED);
        long busId = buses.save(bus).getId();
        seats.saveAll(List.of(seat(busId, "U1", 1, 0, 0, BusSeat.Kind.SLEEPER, "U2", false),
                seat(busId, "U2", 1, 0, 1, BusSeat.Kind.SLEEPER, "U1", false),
                seat(busId, "U3", 1, 0, 3, BusSeat.Kind.SLEEPER, null, false),
                seat(busId, "L1", 0, 0, 0, BusSeat.Kind.SEATER, null, true),
                seat(busId, "L2", 0, 0, 3, BusSeat.Kind.SEATER, null, false)));

        BusRoute r = new BusRoute();
        r.setOwnerId(owner);
        r.setBusId(busId);
        r.setName("Chennai - Madurai");
        r.setRunDays((short) 127);
        r.setActiveFrom(LocalDate.now(IST));
        r.setMaxSeatsPerBooking(4);
        r.setCancellationPolicy(BusCancellationPolicy.toJson(BusCancellationPolicy.DEFAULT));
        r.setStatus(BusRoute.Status.PUBLISHED);
        routeId = routes.save(r).getId();
        long s0 = stop(0, chennai);
        long s1 = stop(1, trichy);
        long s2 = stop(2, madurai);
        koyambedu = point(s0, BusRoutePoint.Kind.BOARDING, "Koyambedu", 0, LocalTime.of(21, 0));
        trichyStand = point(s1, BusRoutePoint.Kind.BOTH, "Trichy CBS", 1, LocalTime.of(1, 0));
        mattuthavani = point(s2, BusRoutePoint.Kind.DROPPING, "Mattuthavani", 1, LocalTime.of(5, 0));
        fare(0, 1, BusSeatClass.UPPER_SINGLE, "400");
        fare(1, 2, BusSeatClass.UPPER_SINGLE, "300");
        fare(0, 2, BusSeatClass.UPPER_SINGLE, "650");
        fare(0, 2, BusSeatClass.SEATER, "500");
        tripId = trip(busId, day);
    }

    private BusSeat seat(long busId, String label, int deck, int row, int col, BusSeat.Kind kind, String pair, boolean ladies) {
        BusSeat s = new BusSeat();
        s.setBusId(busId);
        s.setOwnerId(owner);
        s.setLabel(label);
        s.setDeck(deck);
        s.setRowNo(row);
        s.setColNo(col);
        s.setKind(kind);
        s.setBerth(kind == BusSeat.Kind.SLEEPER ? BusSeat.Berth.SINGLE : null);
        s.setPairLabel(pair);
        s.setLadiesOnly(ladies);
        return s;
    }

    private long stop(int seq, long place) {
        BusRouteStop s = new BusRouteStop();
        s.setRouteId(routeId);
        s.setOwnerId(owner);
        s.setSeq(seq);
        s.setPlaceId(place);
        return stops.save(s).getId();
    }

    private long point(long stopId, BusRoutePoint.Kind kind, String name, int dayOffset, LocalTime time) {
        BusRoutePoint p = new BusRoutePoint();
        p.setStopId(stopId);
        p.setOwnerId(owner);
        p.setKind(kind);
        p.setName(name);
        p.setAddress(name + " bus stand");
        p.setDayOffset(dayOffset);
        p.setTime(time);
        return points.save(p).getId();
    }

    private void fare(int from, int to, BusSeatClass cls, String amount) {
        BusRouteFare f = new BusRouteFare();
        f.setRouteId(routeId);
        f.setOwnerId(owner);
        f.setFromSeq(from);
        f.setToSeq(to);
        f.setSeatClass(cls);
        f.setFare(new BigDecimal(amount));
        fares.save(f);
    }

    private long trip(long busId, LocalDate date) {
        BusTrip t = new BusTrip();
        t.setRouteId(routeId);
        t.setBusId(busId);
        t.setOwnerId(owner);
        t.setTravelDate(date);
        t.setDepartureAt(date.atTime(21, 0));
        t.setArrivalAt(date.plusDays(1).atTime(5, 0));
        return trips.save(t).getId();
    }

    /** Every service's clock at this moment. */
    private void at(LocalDateTime when) {
        Clock c = Clock.fixed(when.atZone(IST).toInstant(), IST);
        search.setClock(c);
        booking.setClock(c);
        ticketService.setClock(c);
        payoutService.setClock(c);
        views.setClock(c);
        tripService.setClock(c);
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(s -> work.get());
    }

    private static PassengerForm pax(String seat, String gender) {
        return new PassengerForm(seat, "Traveller " + seat, 30, gender);
    }

    private TicketView hold(UUID user, String key, long from, long to, PassengerForm... pax) {
        return hold(user, key, tripId, from, to, pax);
    }

    private TicketView hold(UUID user, String key, long trip, long from, long to, PassengerForm... pax) {
        long board = from == chennai ? koyambedu : trichyStand;
        long drop = to == madurai ? mattuthavani : trichyStand;
        HoldForm form = new HoldForm(trip, from, to, board, drop, "+91 98765 43210", "c@x.com", null, List.of(pax));
        return inTx(() -> booking.hold(user, key, form));
    }

    /** Pays and confirms through the checkout callback, with a genuine signature. */
    private TicketView payAndVerify(UUID user, TicketView ticket) {
        PayView order = inTx(() -> booking.pay(user, ticket.id()));
        String paymentId = "pay_" + order.orderId();
        return booking.verify(user, ticket.id(), new VerifyForm(order.orderId(), paymentId, sign(order.orderId(), paymentId)));
    }

    private static String sign(String orderId, String paymentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private BusTicket ticket(UUID id) {
        return tickets.findById(id).orElseThrow();
    }

    private SeatMap map(long from, long to) {
        return inTx(() -> search.seatMap(tripId, from, to));
    }

    private static String state(SeatMap map, String label) {
        return map.seats().stream().filter(s -> s.label().equals(label)).map(MapSeat::state).findFirst().orElseThrow();
    }

    private LocalDateTime boarding() {
        return day.atTime(21, 0);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("search: segments, seats left, boarding date at the chosen town; departed, paused and unpriced pairs are left out")
    void searchRules() {
        List<SearchResult> found = inTx(() -> search.search(chennai, madurai, day));
        assertEquals(1, found.size());
        SearchResult r = found.get(0);
        assertEquals("KPN Travels", r.travelsName());
        assertEquals(5, r.seatsLeft());
        assertEquals(3, r.singleSeatsLeft());
        assertEquals(new BigDecimal("500.00"), r.minFare());
        assertEquals(boarding(), r.departureAt());
        assertEquals(480, r.durationMinutes());
        assertTrue(r.hasSleeper() && r.hasSeater());

        // Seaters have no Chennai → Trichy fare: only the three berths are sold for it.
        assertEquals(3, inTx(() -> search.search(chennai, trichy, day)).get(0).seatsLeft());

        // U1 sold Chennai → Trichy stays free Trichy → Madurai (which boards the next day).
        TicketView t = hold(customers.get(0), "k1", chennai, trichy, pax("U1", "M"));
        payAndVerify(customers.get(0), t);
        assertTrue(inTx(() -> search.search(trichy, madurai, day)).isEmpty(), "Trichy boards after midnight");
        SearchResult later = inTx(() -> search.search(trichy, madurai, day.plusDays(1))).get(0);
        assertEquals(3, later.seatsLeft());
        assertEquals("AVAILABLE", state(map(trichy, madurai), "U1"));
        assertEquals("BOOKED_M", state(map(chennai, madurai), "U1"));
        assertEquals("MALE_ONLY", state(map(chennai, madurai), "U2"));
        assertEquals("FEMALE_ONLY", state(map(chennai, madurai), "L1"));
        assertEquals(4, inTx(() -> search.search(chennai, madurai, day)).get(0).seatsLeft());

        // Wrong direction, and a pair without fares.
        assertTrue(inTx(() -> search.search(madurai, chennai, day)).isEmpty());
        jdbc.update("DELETE FROM bus_route_fares WHERE from_seq = 1 AND to_seq = 2");
        assertTrue(inTx(() -> search.search(trichy, madurai, day.plusDays(1))).isEmpty());

        // Paused route, then a departed trip.
        jdbc.update("UPDATE bus_routes SET status = 'PAUSED'");
        assertTrue(inTx(() -> search.search(chennai, madurai, day)).isEmpty());
        jdbc.update("UPDATE bus_routes SET status = 'PUBLISHED'");
        jdbc.update("UPDATE bus_trips SET status = 'DEPARTED'");
        assertTrue(inTx(() -> search.search(chennai, madurai, day)).isEmpty());
        jdbc.update("UPDATE bus_trips SET status = 'SCHEDULED'");
        // Boarding time passed (clock after 21:00 on the day).
        at(boarding().plusMinutes(1));
        assertTrue(inTx(() -> search.search(chennai, madurai, day)).isEmpty());
    }

    @Test
    @DisplayName("hold: server price with 5% GST per seat, PENDING with a 10 minute hold and a NJB- number")
    void holdPricing() {
        LocalDateTime now = LocalDateTime.now(IST).withNano(0);
        at(now);
        TicketView t = hold(customers.get(0), "k1", chennai, madurai, pax("U1", "M"), pax("L2", "F"));
        assertEquals("PENDING", t.status());
        assertTrue(t.ticketNo().matches("NJB-[A-Z0-9]{8}"));
        assertEquals(new BigDecimal("1150.00"), t.fareTotal());
        assertEquals(new BigDecimal("57.50"), t.gstTotal());
        assertEquals(new BigDecimal("1207.50"), t.totalAmount());
        assertEquals(now.plusMinutes(10), t.holdExpiresAt());
        assertEquals("Koyambedu", t.boardingPoint().name());
        assertEquals("Chennai", t.fromPlace());
        assertEquals(new BigDecimal("8.00"), ticket(t.id()).getCommissionPercent());
        assertEquals("SOLD", state(map(chennai, madurai), "U1"), "a held seat shows as sold");

        PayView pay = inTx(() -> booking.pay(customers.get(0), t.id()));
        assertEquals(120750, pay.amountPaise());
        assertEquals(480, pay.timeoutSeconds(), "at most 8 minutes of checkout");
        assertEquals(pay.orderId(), inTx(() -> booking.pay(customers.get(0), t.id())).orderId(), "the order is reused");
        at(now.plusMinutes(5));
        assertEquals(300, inTx(() -> booking.pay(customers.get(0), t.id())).timeoutSeconds());
        at(now.plusMinutes(11));
        assertThrows(ConflictException.class, () -> inTx(() -> booking.pay(customers.get(0), t.id())));
    }

    @Test
    @DisplayName("hold limits: max seats, one hold per trip, three overall; the same Idempotency-Key returns the same ticket")
    void holdLimits() {
        UUID me = customers.get(0);
        assertThrows(IllegalArgumentException.class, () -> hold(me, null, chennai, madurai,
                pax("U1", "M"), pax("U2", "M"), pax("U3", "M"), pax("L2", "M"), pax("L1", "F")));
        assertThrows(IllegalArgumentException.class, () -> hold(me, null, chennai, trichy, pax("L2", "M")),
                "a seater has no Chennai → Trichy fare");

        TicketView first = hold(me, "same-key", chennai, madurai, pax("U3", "M"));
        TicketView again = hold(me, "same-key", chennai, madurai, pax("U3", "M"));
        assertEquals(first.id(), again.id());
        assertEquals(1, tickets.count());
        assertThrows(ConflictException.class, () -> hold(me, "other", chennai, madurai, pax("L2", "M")),
                "one running hold per trip");

        long busId = trips.findById(tripId).orElseThrow().getBusId();
        long t2 = trip(busId, day.plusDays(1));
        long t3 = trip(busId, day.plusDays(2));
        long t4 = trip(busId, day.plusDays(3));
        hold(me, "t2", t2, chennai, madurai, pax("U1", "M"));
        hold(me, "t3", t3, chennai, madurai, pax("U1", "M"));
        assertThrows(ConflictException.class, () -> hold(me, "t4", t4, chennai, madurai, pax("U1", "M")),
                "three running holds overall");
        // Another customer is not affected.
        assertEquals("PENDING", hold(customers.get(1), "x", t4, chennai, madurai, pax("U1", "M")).status());
    }

    @Test
    @DisplayName("gender rule: a man next to a woman refused, a family pair allowed, other stretches allowed, ladies' seat")
    void genderRule() {
        TicketView woman = hold(customers.get(0), "w", chennai, madurai, pax("U2", "F"));
        assertThrows(IllegalArgumentException.class, () -> hold(customers.get(1), "m", chennai, madurai, pax("U1", "M")));
        assertEquals("PENDING", hold(customers.get(2), "f", chennai, madurai, pax("U1", "F")).status());
        assertThrows(IllegalArgumentException.class, () -> hold(customers.get(3), "l", chennai, madurai, pax("L1", "M")));
        assertNotNull(woman);

        // A family takes both seats of a pair on another trip: any genders.
        long busId = trips.findById(tripId).orElseThrow().getBusId();
        long other = trip(busId, day.plusDays(1));
        assertEquals("PENDING", hold(customers.get(4), "fam", other, chennai, madurai, pax("U1", "M"), pax("U2", "F")).status());

        // A woman next to him only until Trichy; he boards at Trichy.
        long third = trip(busId, day.plusDays(2));
        hold(customers.get(5), "w2", third, chennai, trichy, pax("U2", "F"));
        assertEquals("PENDING", hold(customers.get(6), "m2", third, trichy, madurai, pax("U1", "M")).status());
    }

    @Test
    @DisplayName("20 customers hold the same seat at once: exactly one gets it")
    void concurrentHolds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            UUID user = customers.get(i);
            results.add(pool.submit(() -> {
                start.await();
                try {
                    hold(user, "race", chennai, madurai, pax("U3", "M"));
                    return "ok";
                } catch (ConflictException e) {
                    return "409";
                }
            }));
        }
        start.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<String> f : results) {
            String r = f.get(60, TimeUnit.SECONDS);
            ok += r.equals("ok") ? 1 : 0;
            conflict += r.equals("409") ? 1 : 0;
        }
        pool.shutdown();
        assertEquals(1, ok);
        assertEquals(19, conflict);
        assertEquals(1, tickets.count());
        assertEquals(1, allocations.count());
    }

    @Test
    @DisplayName("paid on time: confirmed, seats CONFIRMED, one email; verify then webhook confirm once")
    void payOnTime() {
        UUID me = customers.get(0);
        TicketView held = hold(me, "k", chennai, madurai, pax("U3", "M"));
        TicketView paid = payAndVerify(me, held);
        assertEquals("CONFIRMED", paid.status());
        assertNotNull(paid.confirmedAt());
        assertTrue(paid.canCancel());
        assertEquals("CONFIRMED", allocations.findByTicketId(held.id()).get(0).getStatus().name());
        BusPayment p = payments.findByTicketIdOrderByCreatedAtDesc(held.id()).get(0);
        assertEquals(BusPayment.Status.CAPTURED, p.getStatus());

        // The webhook for the same payment arrives afterwards: nothing more happens.
        assertTrue(booking.onPaymentCaptured(p.getRazorpayOrderId(), p.getRazorpayPaymentId(), 68250));
        verify(email, times(1)).sendBusTicketEmail(eq("c@x.com"), contains("confirmed"), anyString());
        assertTrue(refunds.findAll().isEmpty());
        assertFalse(booking.onPaymentCaptured("order_unknown", "pay_x", 100), "not a bus order");
    }

    @Test
    @DisplayName("verify and webhook at the same moment: one confirmation")
    void duplicateConcurrent() throws Exception {
        UUID me = customers.get(0);
        TicketView held = hold(me, "k", chennai, madurai, pax("U3", "M"));
        PayView order = inTx(() -> booking.pay(me, held.id()));
        String payId = "pay_dup";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> a = pool.submit(() -> {
            start.await();
            return booking.verify(me, held.id(), new VerifyForm(order.orderId(), payId, sign(order.orderId(), payId)));
        });
        Future<?> b = pool.submit(() -> {
            start.await();
            return booking.onPaymentCaptured(order.orderId(), payId, order.amountPaise());
        });
        start.countDown();
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(BusTicket.Status.CONFIRMED, ticket(held.id()).getStatus());
        verify(email, times(1)).sendBusTicketEmail(anyString(), contains("confirmed"), anyString());
        assertEquals(1, allocations.findByTicketId(held.id()).size());
        assertTrue(refunds.findAll().isEmpty());
    }

    @Test
    @DisplayName("paid after the hold ran out: confirmed if the seats are free, refunded in full if taken")
    void payLate() {
        LocalDateTime now = LocalDateTime.now(IST).withNano(0);
        at(now);
        UUID late = customers.get(0);
        TicketView free = hold(late, "a", chennai, madurai, pax("U3", "M"));
        PayView freeOrder = inTx(() -> booking.pay(late, free.id()));
        UUID slow = customers.get(1);
        TicketView taken = hold(slow, "b", chennai, madurai, pax("L2", "M"));
        PayView takenOrder = inTx(() -> booking.pay(slow, taken.id()));

        at(now.plusMinutes(11));
        booking.sweep();
        assertEquals(BusTicket.Status.EXPIRED, ticket(free.id()).getStatus());
        assertEquals("AVAILABLE", state(map(chennai, madurai), "U3"));
        // Someone else takes L2 meanwhile.
        hold(customers.get(2), "c", chennai, madurai, pax("L2", "M"));

        booking.onPaymentCaptured(freeOrder.orderId(), "pay_free", freeOrder.amountPaise());
        assertEquals(BusTicket.Status.CONFIRMED, ticket(free.id()).getStatus());
        assertEquals("BOOKED_M", state(map(chennai, madurai), "U3"));

        booking.onPaymentCaptured(takenOrder.orderId(), "pay_taken", takenOrder.amountPaise());
        assertEquals(BusTicket.Status.EXPIRED, ticket(taken.id()).getStatus());
        List<BusRefund> back = refunds.findByTicketIdOrderByCreatedAtAsc(taken.id());
        assertEquals(1, back.size());
        assertEquals(new BigDecimal("525.00"), back.get(0).getAmount());
        assertEquals(BusRefund.Status.PENDING, back.get(0).getStatus());
        verify(outbox).enqueue(eq(BusRefundJobHandler.TYPE), any());
        verify(email).sendBusTicketEmail(anyString(), contains("Refund"), anyString());
        // The money arriving again changes nothing.
        booking.onPaymentCaptured(takenOrder.orderId(), "pay_taken", takenOrder.amountPaise());
        assertEquals(1, refunds.findByTicketIdOrderByCreatedAtAsc(taken.id()).size());
    }

    @Test
    @DisplayName("release and payment.failed give the seats back at once; the sweeper expires and reconciles")
    void releaseAndSweeper() {
        UUID me = customers.get(0);
        TicketView t = hold(me, "a", chennai, madurai, pax("U3", "M"));
        assertEquals("EXPIRED", inTx(() -> booking.release(me, t.id())).status());
        assertEquals("AVAILABLE", state(map(chennai, madurai), "U3"));

        TicketView t2 = hold(customers.get(1), "b", chennai, madurai, pax("U3", "M"));
        PayView order = inTx(() -> booking.pay(customers.get(1), t2.id()));
        assertTrue(booking.onPaymentFailed(order.orderId()));
        assertEquals(BusTicket.Status.EXPIRED, ticket(t2.id()).getStatus());
        assertEquals(BusPayment.Status.FAILED, payments.findByRazorpayOrderId(order.orderId()).orElseThrow().getStatus());

        // A capture recorded with no confirmation (callback and webhook lost half way): the sweeper settles it.
        TicketView t3 = hold(customers.get(2), "c", chennai, madurai, pax("U3", "M"));
        PayView order3 = inTx(() -> booking.pay(customers.get(2), t3.id()));
        jdbc.update("UPDATE bus_payments SET status = 'CAPTURED', razorpay_payment_id = 'pay_lost', captured_at = now() "
                + "WHERE razorpay_order_id = ?", order3.orderId());
        booking.sweep();
        assertEquals(BusTicket.Status.CONFIRMED, ticket(t3.id()).getStatus());

        // An unpaid hold past its time.
        TicketView t4 = hold(customers.get(3), "d", chennai, madurai, pax("L2", "M"));
        at(LocalDateTime.now(IST).plusMinutes(11));
        booking.sweep();
        assertEquals(BusTicket.Status.EXPIRED, ticket(t4.id()).getStatus());
        assertEquals(BusSeatAllocation.Status.EXPIRED, allocations.findByTicketId(t4.id()).get(0).getStatus());
    }

    @Test
    @DisplayName("cancel: slab by time to boarding (47h59m vs 48h01m), GST back in full, the seat goes back on sale")
    void cancelSeats() {
        UUID me = customers.get(0);
        TicketView t = payAndVerify(me, hold(me, "a", chennai, madurai, pax("U1", "M"), pax("U2", "F")));

        at(boarding().minusHours(47).minusMinutes(59));
        CancelQuote late = inTx(() -> ticketService.cancelQuote(me, t.id(), "u1"));
        assertEquals(25, late.seats().get(0).chargePercent());
        at(boarding().minusHours(48).minusMinutes(1));
        CancelQuote early = inTx(() -> ticketService.cancelQuote(me, t.id(), "U1"));
        assertEquals(10, early.seats().get(0).chargePercent());
        assertEquals(new BigDecimal("65.00"), early.seats().get(0).charge());
        assertEquals(new BigDecimal("617.50"), early.seats().get(0).refund());
        assertEquals(new BigDecimal("617.50"), early.totalRefund());

        TicketView after = inTx(() -> ticketService.cancel(me, t.id(), List.of("U1")));
        assertEquals("PARTIALLY_CANCELLED", after.status());
        assertEquals("CANCELLED", after.passengers().get(0).status());
        assertEquals(new BigDecimal("617.50"), after.passengers().get(0).refundAmount());
        assertEquals(1, after.refunds().size());
        assertEquals("PENDING", after.refunds().get(0).status());
        assertTrue(after.canCancel());
        assertThrows(IllegalArgumentException.class, () -> inTx(() -> ticketService.cancel(me, t.id(), List.of("U1"))),
                "already cancelled");

        // U1 is on sale again, for a woman (a woman is next to it).
        assertEquals("FEMALE_ONLY", state(map(chennai, madurai), "U1"));
        assertEquals("PENDING", hold(customers.get(1), "b", chennai, madurai, pax("U1", "F")).status());

        TicketView all = inTx(() -> ticketService.cancel(me, t.id(), List.of("U2")));
        assertEquals("CANCELLED", all.status());
        assertFalse(all.canCancel());
        assertEquals(2, refunds.findByTicketIdOrderByCreatedAtAsc(t.id()).size());

        // At boarding time it is refused.
        TicketView other = payAndVerify(customers.get(2), hold(customers.get(2), "c", chennai, madurai, pax("U3", "M")));
        at(boarding());
        assertThrows(ConflictException.class, () -> inTx(() -> ticketService.cancelQuote(customers.get(2), other.id(), "U3")));
        // Someone else's ticket is not found.
        assertThrows(com.travelplatform.exception.ResourceNotFoundException.class,
                () -> inTx(() -> ticketService.get(customers.get(3), other.id())));
    }

    @Test
    @DisplayName("owner cancels the trip: every paid ticket refunded fare + GST, unpaid holds released")
    void tripCancelRefunds() {
        TicketView a = payAndVerify(customers.get(0), hold(customers.get(0), "a", chennai, madurai, pax("U3", "M")));
        TicketView b = payAndVerify(customers.get(1), hold(customers.get(1), "b", chennai, madurai, pax("U1", "F"), pax("U2", "F")));
        hold(customers.get(2), "c", chennai, madurai, pax("L2", "M"));

        OwnerScope.bind(owner, false);
        try {
            tx.execute(s -> tripService.cancel(tripId, "Bus breakdown"));
        } finally {
            OwnerScope.clear();
        }
        assertEquals(BusTicket.Status.SERVICE_CANCELLED, ticket(a.id()).getStatus());
        assertEquals(new BigDecimal("682.50"), refunds.findByTicketIdOrderByCreatedAtAsc(a.id()).get(0).getAmount());
        assertEquals(new BigDecimal("1365.00"), refunds.findByTicketIdOrderByCreatedAtAsc(b.id()).get(0).getAmount());
        assertEquals(2, refunds.count());
        verify(email, times(2)).sendBusTicketEmail(anyString(), contains("Bus cancelled"), anyString());
        assertTrue(allocations.findAll().stream().noneMatch(x -> x.getStatus().live()));
    }

    @Test
    @DisplayName("status job, review once, payout maths and idempotency, mark paid with a UTR")
    void afterTheTrip() {
        UUID me = customers.get(0);
        TicketView t1 = payAndVerify(me, hold(me, "a", chennai, madurai, pax("U1", "M"), pax("U2", "M")));
        TicketView t2 = payAndVerify(customers.get(1), hold(customers.get(1), "b", chennai, madurai, pax("U3", "F")));
        at(boarding().minusHours(49));
        inTx(() -> ticketService.cancel(me, t1.id(), List.of("U2"))); // 10%: 65.00 kept
        assertThrows(ConflictException.class, () -> inTx(() -> ticketService.review(me, t1.id(), new ReviewForm(5, "Nice"))),
                "not completed yet");

        at(boarding().plusMinutes(1));
        ticketService.advanceTripStatuses();
        assertEquals(BusTrip.Status.DEPARTED, trips.findById(tripId).orElseThrow().getStatus());
        at(day.plusDays(1).atTime(6, 59));
        ticketService.advanceTripStatuses();
        assertEquals(BusTrip.Status.DEPARTED, trips.findById(tripId).orElseThrow().getStatus(), "not 2 hours past arrival yet");
        at(day.plusDays(1).atTime(7, 0));
        ticketService.advanceTripStatuses();
        assertEquals(BusTrip.Status.COMPLETED, trips.findById(tripId).orElseThrow().getStatus());
        assertEquals(BusTicket.Status.COMPLETED, ticket(t1.id()).getStatus());
        assertEquals(BusTicket.Status.COMPLETED, ticket(t2.id()).getStatus());

        TicketView reviewed = inTx(() -> ticketService.review(me, t1.id(), new ReviewForm(4, " Clean bus ")));
        assertEquals(4, reviewed.review().rating());
        assertFalse(reviewed.canReview());
        assertThrows(ConflictException.class, () -> inTx(() -> ticketService.review(me, t1.id(), new ReviewForm(5, null))));
        assertThrows(IllegalArgumentException.class,
                () -> inTx(() -> ticketService.review(customers.get(1), t2.id(), new ReviewForm(6, null))));

        at(day.plusDays(2).atTime(6, 0));
        assertEquals(1, payoutService.createPayouts());
        assertEquals(0, payoutService.createPayouts(), "one payout per trip");
        BusOwnerPayout p = payouts.findByTripId(tripId).orElseThrow();
        assertEquals(new BigDecimal("1300.00"), p.getFaresTravelled());
        assertEquals(new BigDecimal("65.00"), p.getChargesKept());
        assertEquals(new BigDecimal("1365.00"), p.getGross());
        assertEquals(new BigDecimal("8.00"), p.getCommissionPercent());
        assertEquals(new BigDecimal("109.20"), p.getCommission());
        assertEquals(new BigDecimal("1255.80"), p.getNet());

        assertThrows(IllegalArgumentException.class, () -> inTx(() -> payoutService.markPaid(p.getId(), " ", 1L)));
        PayoutView paid = inTx(() -> payoutService.markPaid(p.getId(), "utr123456", 1L));
        assertEquals("PAID", paid.status());
        assertEquals("UTR123456", paid.utr());
        assertEquals("SBI", paid.bankName());
        assertThrows(ConflictException.class, () -> inTx(() -> payoutService.markPaid(p.getId(), "UTR999999", 1L)));
        assertEquals(1, inTx(() -> payoutService.all("PAID")).size());

        OwnerScope.bind(owner, false);
        try {
            List<PayoutView> mine = tx.execute(s -> payoutService.mine());
            assertEquals(1, mine.size());
            assertNull(mine.get(0).bankName(), "the owner's own view leaves the bank details out");
        } finally {
            OwnerScope.clear();
        }

        // The rating shows on search for the bus's next trip.
        long busId = trips.findById(tripId).orElseThrow().getBusId();
        long next = trip(busId, day.plusDays(3));
        at(LocalDateTime.now(IST));
        SearchResult r = inTx(() -> search.search(chennai, madurai, day.plusDays(3))).get(0);
        assertEquals(next, r.tripId());
        assertEquals(4.0, r.ratingAvg());
        assertEquals(1, r.ratingCount());
    }

    @Test
    @DisplayName("the reminder goes once, about two hours before the ticket's own boarding time")
    void reminder() {
        UUID me = customers.get(0);
        payAndVerify(me, hold(me, "a", trichy, madurai, pax("U3", "M"))); // boards at Trichy 01:00 next day
        at(boarding().minusMinutes(30)); // the bus leaves Chennai soon, but this passenger boards 4.5 hours later
        assertEquals(0, ticketService.sendReminders());
        at(day.plusDays(1).atTime(0, 0).minusMinutes(59));
        assertEquals(1, ticketService.sendReminders());
        assertEquals(0, ticketService.sendReminders());
        verify(email, atLeastOnce()).sendBusTicketEmail(anyString(), contains("Reminder"), contains("TN01AB1234"));
        assertNotNull(tickets.findAll().get(0).getReminderSentAt());
    }
}
