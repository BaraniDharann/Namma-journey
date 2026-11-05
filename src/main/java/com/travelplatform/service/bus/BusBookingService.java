package com.travelplatform.service.bus;

import com.travelplatform.config.RazorpayProperties;
import com.travelplatform.dto.bus.BusBookingForms.HoldForm;
import com.travelplatform.dto.bus.BusBookingForms.PassengerForm;
import com.travelplatform.dto.bus.BusBookingForms.VerifyForm;
import com.travelplatform.dto.bus.BusBookingViews.PayView;
import com.travelplatform.dto.bus.BusBookingViews.TicketView;
import com.travelplatform.entity.BusPayment;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusSeatAllocation.Gender;
import com.travelplatform.entity.BusSeatClass;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.Owner;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusPaymentRepository;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRefundRepository;
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
import com.travelplatform.service.RazorpayGateway;
import com.travelplatform.service.RazorpaySignatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Booking bus seats: hold → pay → confirm (spec "Hold, pay, ticket").
 *
 * <ol>
 *   <li>{@link #hold}: in one transaction the seats are checked (limits, the gender rule, price) and
 *       inserted HELD for {@code bus.hold-minutes}, with a PENDING ticket. The database's exclusion
 *       constraint decides between two customers taking the same seat at once.</li>
 *   <li>{@link #pay}: a Razorpay order for the ticket's total, as the server priced it.</li>
 *   <li>{@link #verify} (checkout callback) and the webhook both end in {@link #settle}: idempotent
 *       per payment, and the ticket's {@code @Version} lets only one of two simultaneous runs win;
 *       the loser retries and finds the work done.</li>
 *   <li>{@link #release} and payment.failed give the seats back at once; {@link #sweep} expires
 *       holds that ran out and settles captured payments nobody settled.</li>
 * </ol>
 */
@Service
public class BusBookingService {

    private static final Logger log = LoggerFactory.getLogger(BusBookingService.class);

    static final int MAX_ACTIVE_HOLDS = 3;
    private static final int MAX_SETTLE_ATTEMPTS = 3;
    private static final String TICKET_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** Postgres' message for no_seat_overlap: "Key (trip_id, seat_label, legs)=(5, U3, [0,2)) conflicts ...". */
    private static final Pattern OVERLAP_SEAT = Pattern.compile("seat_label, legs\\)=\\(\\d+, ([^,]+),");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BusTripRepository trips;
    private final BusRouteRepository routes;
    private final BusSeatRepository seats;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusPlaceRepository places;
    private final BusRouteFareRepository fares;
    private final BusSeatAllocationRepository allocations;
    private final BusTicketRepository tickets;
    private final BusTicketPassengerRepository passengers;
    private final BusPaymentRepository payments;
    private final BusRefundRepository refundRows;
    private final OwnerRepository owners;
    private final RazorpayGateway gateway;
    private final RazorpayProperties razorpay;
    private final BusRefunds refunds;
    private final BusNotifier notifier;
    private final BusTicketViews views;
    private final BusSettings settings;
    private final TransactionTemplate newTx;
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusBookingService(BusTripRepository trips, BusRouteRepository routes, BusSeatRepository seats,
                             BusRouteStopRepository stops, BusRoutePointRepository points, BusPlaceRepository places,
                             BusRouteFareRepository fares, BusSeatAllocationRepository allocations,
                             BusTicketRepository tickets, BusTicketPassengerRepository passengers,
                             BusPaymentRepository payments, BusRefundRepository refundRows, OwnerRepository owners,
                             RazorpayGateway gateway, RazorpayProperties razorpay, BusRefunds refunds,
                             BusNotifier notifier, BusTicketViews views, BusSettings settings,
                             PlatformTransactionManager txManager) {
        this.trips = trips;
        this.routes = routes;
        this.seats = seats;
        this.stops = stops;
        this.points = points;
        this.places = places;
        this.fares = fares;
        this.allocations = allocations;
        this.tickets = tickets;
        this.passengers = passengers;
        this.payments = payments;
        this.refundRows = refundRows;
        this.owners = owners;
        this.gateway = gateway;
        this.razorpay = razorpay;
        this.refunds = refunds;
        this.notifier = notifier;
        this.views = views;
        this.settings = settings;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    // ---------------------------------------------------------------------------------------------
    // Hold
    // ---------------------------------------------------------------------------------------------

    /**
     * Holds the seats for payment and returns the PENDING ticket. The same Idempotency-Key from
     * the same customer returns the ticket the first request made, without holding anything again.
     */
    @Transactional
    public TicketView hold(UUID userId, String idempotencyKey, HoldForm form) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key is too long");
        }
        if (key != null) {
            BusTicket earlier = tickets.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
            if (earlier != null) {
                return views.one(earlier);
            }
        }
        if (form == null || form.tripId() == null || form.fromPlaceId() == null || form.toPlaceId() == null) {
            throw new IllegalArgumentException("Choose the bus and where you travel from and to");
        }
        // Holds on one trip run one at a time (see BusTripRepository.lockById); the checks below then
        // see every earlier hold, and the exclusion constraint stays the last line of defence.
        BusTrip trip = trips.lockById(form.tripId()).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        BusRoute route = routes.findById(trip.getRouteId()).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (trip.getStatus() != BusTrip.Status.SCHEDULED || route.getStatus() != BusRoute.Status.PUBLISHED) {
            throw new ConflictException("This bus is no longer taking bookings");
        }
        BusRouteParts parts = BusRouteParts.load(route.getId(), stops, points, places);
        Integer fromSeq = parts.seqOf(form.fromPlaceId());
        Integer toSeq = parts.seqOf(form.toPlaceId());
        if (fromSeq == null || toSeq == null || fromSeq >= toSeq) {
            throw new IllegalArgumentException("This bus does not go between these towns in that direction");
        }
        BusRoutePoint board = form.boardingPointId() == null ? null : parts.pointById().get(form.boardingPointId());
        if (board == null || !parts.isAt(board, fromSeq) || !board.getKind().boards()) {
            throw new IllegalArgumentException("Choose a boarding point in " + parts.placeName(fromSeq));
        }
        BusRoutePoint drop = form.droppingPointId() == null ? null : parts.pointById().get(form.droppingPointId());
        if (drop == null || !parts.isAt(drop, toSeq) || !drop.getKind().drops()) {
            throw new IllegalArgumentException("Choose a dropping point in " + parts.placeName(toSeq));
        }
        LocalDateTime now = now();
        if (!board.at(trip.getTravelDate()).isAfter(now)) {
            throw new ConflictException("The bus has already left " + board.getName() + "; choose another boarding point");
        }
        String contactPhone = BusTripService.phone(form.contactPhone(), "Contact");
        if (contactPhone == null) {
            throw new IllegalArgumentException("Enter a 10-digit mobile number for the ticket");
        }
        String extraPhone = BusTripService.phone(form.extraPhone(), "The extra number");
        String email = form.contactEmail() == null || form.contactEmail().isBlank() ? null : form.contactEmail().trim();
        if (email != null && (email.length() > 255 || !EMAIL.matcher(email).matches())) {
            throw new IllegalArgumentException("Enter a valid email address for the ticket");
        }

        List<PassengerForm> pax = form.passengers() == null ? List.of() : form.passengers();
        if (pax.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one seat");
        }
        if (pax.size() > route.getMaxSeatsPerBooking()) {
            throw new IllegalArgumentException("You can book up to " + route.getMaxSeatsPerBooking() + " seats at a time");
        }
        Map<String, BusSeat> layout = seats.findByBusIdOrderByDeckAscRowNoAscColNoAsc(trip.getBusId()).stream()
                .collect(Collectors.toMap(s -> BusSeatRules.key(s.getLabel()), Function.identity()));
        Map<BusSeatClass, BigDecimal> pairFares = BusSearchService.pairFares(
                fares.findByRouteIdAndFromSeqAndToSeq(route.getId(), fromSeq, toSeq), fromSeq, toSeq);
        Map<String, BusSeat> chosen = new LinkedHashMap<>();
        List<Gender> genders = new ArrayList<>();
        for (PassengerForm p : pax) {
            BusSeat seat = p == null || p.seatLabel() == null ? null : layout.get(BusSeatRules.key(p.seatLabel()));
            if (seat == null) {
                throw new IllegalArgumentException("Seat " + (p == null ? "" : p.seatLabel()) + " is not on this bus");
            }
            if (chosen.put(BusSeatRules.key(seat.getLabel()), seat) != null) {
                throw new IllegalArgumentException("Seat " + seat.getLabel() + " is chosen twice");
            }
            if (pairFares.get(seat.seatClass()) == null) {
                throw new IllegalArgumentException("Seat " + seat.getLabel() + " is not sold for this journey");
            }
            String name = p.name() == null ? "" : p.name().trim();
            if (name.length() < 2 || name.length() > 80) {
                throw new IllegalArgumentException("Enter the name for seat " + seat.getLabel());
            }
            if (p.age() == null || p.age() < 0 || p.age() > 120) {
                throw new IllegalArgumentException("Enter an age (0 to 120) for seat " + seat.getLabel());
            }
            genders.add(gender(p.gender(), seat.getLabel()));
        }

        // Stale holds on this trip stop counting, so their seats can be taken right now.
        allocations.expireStaleHolds(trip.getId(), now);
        if (tickets.countActiveHoldsOnTrip(userId, trip.getId(), now) > 0) {
            throw new ConflictException("You already have seats held on this bus. Pay for them or release them first.");
        }
        if (tickets.countActiveHolds(userId, now) >= MAX_ACTIVE_HOLDS) {
            throw new ConflictException("You have " + MAX_ACTIVE_HOLDS + " unpaid seat holds. Pay for one or wait a few minutes.");
        }
        Map<String, List<BusSeatAllocation>> taken = BusSeatRules.bySeat(
                allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(trip.getId(), BusSeatRules.LIVE));
        List<BusSeat> chosenSeats = new ArrayList<>(chosen.values());
        for (BusSeat seat : chosenSeats) {
            if (!BusSeatRules.occupying(taken, seat.getLabel(), fromSeq, toSeq, now, null).isEmpty()) {
                throw new ConflictException("Seat " + seat.getLabel() + " was just booked by someone else");
            }
        }
        for (int i = 0; i < chosenSeats.size(); i++) {
            BusSeatRules.checkGender(chosenSeats.get(i), genders.get(i), chosen.keySet(), taken, fromSeq, toSeq, now);
        }

        BigDecimal gstPercent = settings.gstPercent();
        Owner owner = owners.findById(trip.getOwnerId()).orElse(null);
        BusTicket ticket = new BusTicket();
        ticket.setTicketNo(newTicketNo());
        ticket.setUserId(userId);
        ticket.setOwnerId(trip.getOwnerId());
        ticket.setTripId(trip.getId());
        ticket.setFromSeq(fromSeq);
        ticket.setToSeq(toSeq);
        ticket.setBoardingPointId(board.getId());
        ticket.setDroppingPointId(drop.getId());
        ticket.setContactPhone(contactPhone);
        ticket.setContactEmail(email);
        ticket.setExtraPhone(extraPhone);
        ticket.setGstPercent(gstPercent);
        ticket.setCommissionPercent(owner == null || owner.getBusCommissionPercent() == null
                ? new BigDecimal("8.00") : owner.getBusCommissionPercent());
        ticket.setCancellationPolicy(route.getCancellationPolicy() == null
                ? BusCancellationPolicy.toJson(BusCancellationPolicy.DEFAULT) : route.getCancellationPolicy());
        ticket.setStatus(BusTicket.Status.PENDING);
        ticket.setHoldExpiresAt(now.plusMinutes(settings.holdMinutes()));
        ticket.setIdempotencyKey(key);

        List<BusTicketPassenger> rows = new ArrayList<>();
        BigDecimal fareTotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;
        for (int i = 0; i < pax.size(); i++) {
            BusSeat seat = chosenSeats.get(i);
            BigDecimal fare = pairFares.get(seat.seatClass());
            BigDecimal gst = BusFares.gst(fare, gstPercent);
            BusTicketPassenger row = new BusTicketPassenger();
            row.setOwnerId(trip.getOwnerId());
            row.setSeatLabel(seat.getLabel());
            row.setSeatClass(seat.seatClass());
            row.setName(pax.get(i).name().trim());
            row.setAge(pax.get(i).age());
            row.setGender(genders.get(i));
            row.setFare(fare);
            row.setGst(gst);
            row.setStatus(BusTicketPassenger.Status.CONFIRMED);
            rows.add(row);
            fareTotal = fareTotal.add(fare);
            gstTotal = gstTotal.add(gst);
        }
        ticket.setFareTotal(BusFares.money(fareTotal));
        ticket.setGstTotal(BusFares.money(gstTotal));
        ticket.setTotalAmount(BusFares.money(fareTotal.add(gstTotal)));

        try {
            ticket = tickets.saveAndFlush(ticket);
            List<BusSeatAllocation> held = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setTicketId(ticket.getId());
                held.add(allocation(ticket, rows.get(i).getSeatLabel(), genders.get(i), BusSeatAllocation.Status.HELD,
                        ticket.getHoldExpiresAt()));
            }
            passengers.saveAll(rows);
            // Flushed here, inside the try: another customer who took one of these seats a moment
            // ago makes the exclusion constraint refuse it now, and this transaction rolls back whole.
            allocations.saveAllAndFlush(held);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        } catch (org.springframework.dao.PessimisticLockingFailureException deadlock) {
            // Another writer of these seats outside the trip lock (an owner blocking seats).
            throw new ConflictException("One of your seats was just booked by someone else");
        }
        log.info("Bus ticket {} held: trip {}, seats {}, until {}", ticket.getTicketNo(), trip.getId(),
                chosen.keySet(), ticket.getHoldExpiresAt());
        return views.one(ticket);
    }

    private static Gender gender(String value, String seat) {
        if (value != null) {
            String v = value.trim().toUpperCase(Locale.ROOT);
            if (v.equals("M") || v.equals("F")) {
                return Gender.valueOf(v);
            }
        }
        throw new IllegalArgumentException("Choose a gender (M or F) for seat " + seat);
    }

    private static BusSeatAllocation allocation(BusTicket ticket, String label, Gender gender,
                                                BusSeatAllocation.Status status, LocalDateTime expiresAt) {
        BusSeatAllocation a = new BusSeatAllocation();
        a.setTripId(ticket.getTripId());
        a.setOwnerId(ticket.getOwnerId());
        a.setSeatLabel(label);
        a.setFromSeq(ticket.getFromSeq());
        a.setToSeq(ticket.getToSeq());
        a.setStatus(status);
        a.setGender(gender);
        a.setTicketId(ticket.getId());
        a.setExpiresAt(expiresAt);
        return a;
    }

    private String newTicketNo() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder s = new StringBuilder("NJB-");
            for (int i = 0; i < 8; i++) {
                s.append(TICKET_CHARS.charAt(RANDOM.nextInt(TICKET_CHARS.length())));
            }
            if (!tickets.existsByTicketNo(s.toString())) {
                return s.toString();
            }
        }
        throw new IllegalStateException("Could not pick a free ticket number");
    }

    /** A seat taken by someone else between our check and our insert: a readable 409. */
    static RuntimeException translate(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "23P01".equals(sql.getSQLState())) {
                Matcher m = OVERLAP_SEAT.matcher(String.valueOf(sql.getMessage()));
                return new ConflictException(m.find() ? "Seat " + m.group(1).trim() + " was just booked by someone else"
                        : "One of your seats was just booked by someone else");
            }
            if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && String.valueOf(sql.getMessage()).contains("uq_bus_tickets_idempotency")) {
                return new ConflictException("This booking is already being processed. Please wait a moment.");
            }
        }
        return e;
    }

    // ---------------------------------------------------------------------------------------------
    // Pay
    // ---------------------------------------------------------------------------------------------

    /** Opens Razorpay Checkout for the ticket's total; reuses the order already made for it. */
    @Transactional
    public PayView pay(UUID userId, UUID ticketId) {
        BusTicket ticket = mine(userId, ticketId);
        LocalDateTime now = now();
        if (ticket.getStatus() != BusTicket.Status.PENDING) {
            throw new ConflictException(ticket.getStatus() == BusTicket.Status.EXPIRED
                    ? "Your seat hold has expired. Please choose your seats again." : "This ticket is already paid");
        }
        if (ticket.getHoldExpiresAt() == null || !ticket.getHoldExpiresAt().isAfter(now)) {
            throw new ConflictException("Your seat hold has expired. Please choose your seats again.");
        }
        if (!razorpay.isOperational()) {
            throw new ConflictException("Online payment is not set up yet. Please try again later.");
        }
        long paise = toPaise(ticket.getTotalAmount());
        BusPayment payment = payments.findByTicketIdOrderByCreatedAtDesc(ticket.getId()).stream()
                .filter(p -> p.getStatus() == BusPayment.Status.CREATED && p.getRazorpayOrderId() != null
                        && p.getAmount().compareTo(ticket.getTotalAmount()) == 0)
                .findFirst().orElse(null);
        if (payment == null) {
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("kind", "bus");
            notes.put("ticket_id", ticket.getId().toString());
            notes.put("ticket_no", ticket.getTicketNo());
            String orderId = gateway.createOrder(paise, ticket.getTicketNo(), notes);
            payment = new BusPayment();
            payment.setTicketId(ticket.getId());
            payment.setUserId(userId);
            payment.setOwnerId(ticket.getOwnerId());
            payment.setAmount(ticket.getTotalAmount());
            payment.setStatus(BusPayment.Status.CREATED);
            payment.setRazorpayOrderId(orderId);
            payment = payments.save(payment);
        }
        long left = Duration.between(now, ticket.getHoldExpiresAt()).getSeconds();
        return new PayView(payment.getId(), ticket.getId(), razorpay.getKeyId(), payment.getRazorpayOrderId(),
                ticket.getTotalAmount(), paise, "INR", razorpay.getMerchantName(),
                "Bus ticket " + ticket.getTicketNo(), Math.max(0, Math.min(left, BusSettings.MAX_CHECKOUT_SECONDS)));
    }

    /** The checkout callback: a genuine signature for an order of this ticket settles the payment. */
    public TicketView verify(UUID userId, UUID ticketId, VerifyForm form) {
        if (form == null || blank(form.razorpayOrderId()) || blank(form.razorpayPaymentId()) || blank(form.razorpaySignature())) {
            throw new IllegalArgumentException("Payment details are missing");
        }
        mine(userId, ticketId);
        // The order must be one this server created for this ticket; a genuine signature for some
        // other, cheaper order must not settle this one.
        BusPayment payment = payments.findByRazorpayOrderId(form.razorpayOrderId())
                .filter(p -> p.getTicketId().equals(ticketId) && p.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("This payment does not belong to this ticket"));
        if (!RazorpaySignatures.isValidCheckoutSignature(form.razorpayOrderId(), form.razorpayPaymentId(),
                form.razorpaySignature(), razorpay.getKeySecret())) {
            log.warn("Rejected Razorpay checkout signature for bus ticket {} order {}", ticketId, form.razorpayOrderId());
            throw new IllegalArgumentException("Payment could not be verified");
        }
        settle(payment.getId(), form.razorpayPaymentId());
        return views.one(tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket not found")));
    }

    /** The customer closed checkout (or the payment failed): the seats go back on sale now. */
    @Transactional
    public TicketView release(UUID userId, UUID ticketId) {
        BusTicket ticket = mine(userId, ticketId);
        if (ticket.getStatus() == BusTicket.Status.PENDING) {
            ticket = expire(ticket);
        }
        return views.one(ticket);
    }

    // ---------------------------------------------------------------------------------------------
    // Webhook (RazorpayPaymentService passes on orders it does not know)
    // ---------------------------------------------------------------------------------------------

    /** True when the order belongs to a bus ticket. */
    public boolean isBusOrder(String orderId) {
        return orderId != null && payments.findByRazorpayOrderId(orderId).isPresent();
    }

    /** payment.captured / order.paid for a bus order. Returns false when the order is not a bus order. */
    public boolean onPaymentCaptured(String orderId, String razorpayPaymentId, long paidPaise) {
        BusPayment payment = orderId == null ? null : payments.findByRazorpayOrderId(orderId).orElse(null);
        if (payment == null) {
            return false;
        }
        if (paidPaise != toPaise(payment.getAmount())) {
            log.error("Razorpay paid {} paise for bus payment {} which is {}: not settling", paidPaise,
                    payment.getId(), payment.getAmount());
            return true;
        }
        settle(payment.getId(), razorpayPaymentId);
        return true;
    }

    /** payment.failed for a bus order: the payment is FAILED and the hold released. */
    public boolean onPaymentFailed(String orderId) {
        BusPayment found = orderId == null ? null : payments.findByRazorpayOrderId(orderId).orElse(null);
        if (found == null) {
            return false;
        }
        withRetry(() -> {
            BusPayment payment = payments.findById(found.getId()).orElseThrow();
            if (payment.getStatus() == BusPayment.Status.CREATED) {
                payment.setStatus(BusPayment.Status.FAILED);
                payments.save(payment);
            }
            tickets.findById(payment.getTicketId()).filter(t -> t.getStatus() == BusTicket.Status.PENDING)
                    .ifPresent(this::expire);
            return null;
        });
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Confirming
    // ---------------------------------------------------------------------------------------------

    /**
     * Money arrived for this payment: confirm its ticket, or send the money back. Each attempt runs
     * in its own transaction; a run that lost a race to another (the webhook and the callback at the
     * same moment) retries and finds the work done.
     */
    public void settle(UUID paymentId, String razorpayPaymentId) {
        withRetry(() -> {
            settleNow(paymentId, razorpayPaymentId);
            return null;
        });
    }

    private <T> T withRetry(java.util.function.Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return newTx.execute(s -> work.get());
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException raced) {
                if (attempt >= MAX_SETTLE_ATTEMPTS) {
                    throw raced;
                }
                log.info("Bus payment settlement raced ({}); trying again", raced.getClass().getSimpleName());
            }
        }
    }

    private void settleNow(UUID paymentId, String razorpayPaymentId) {
        BusPayment payment = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        LocalDateTime now = now();
        if (payment.getStatus() != BusPayment.Status.CAPTURED) {
            payment.setStatus(BusPayment.Status.CAPTURED);
            if (payment.getRazorpayPaymentId() == null) {
                payment.setRazorpayPaymentId(razorpayPaymentId);
            }
            payment.setCapturedAt(now);
            payment = payments.saveAndFlush(payment);
        }
        BusTicket ticket = tickets.findById(payment.getTicketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        switch (ticket.getStatus()) {
            case PENDING, EXPIRED -> confirmOrRefund(ticket, payment, now);
            case SERVICE_CANCELLED -> {
                if (!refundRows.existsByPaymentId(payment.getId())) {
                    refunds.queue(ticket, payment, payment.getAmount(), "Bus cancelled by the operator");
                }
            }
            default -> {
                // Already confirmed. A second payment for the same ticket goes back in full.
                BusPayment first = refunds.paidWith(ticket);
                if (first != null && !first.getId().equals(payment.getId()) && !refundRows.existsByPaymentId(payment.getId())) {
                    log.warn("Bus ticket {} was paid twice; refunding payment {}", ticket.getTicketNo(), payment.getId());
                    refunds.queue(ticket, payment, payment.getAmount(), "Paid twice for one ticket");
                }
            }
        }
    }

    private void confirmOrRefund(BusTicket ticket, BusPayment payment, LocalDateTime now) {
        BusTrip trip = trips.findById(ticket.getTripId()).orElseThrow();
        List<BusTicketPassenger> pax = passengers.findByTicketIdOrderByIdAsc(ticket.getId());
        List<BusSeatAllocation> own = allocations.findByTicketId(ticket.getId());
        BusRoutePoint board = points.findById(ticket.getBoardingPointId()).orElse(null);
        boolean open = trip.getStatus() == BusTrip.Status.SCHEDULED && board != null
                && board.at(trip.getTravelDate()).isAfter(now);

        List<BusSeatAllocation> held = own.stream().filter(a -> a.getStatus() == BusSeatAllocation.Status.HELD
                && a.getExpiresAt() != null && a.getExpiresAt().isAfter(now)).toList();
        if (open && held.size() == pax.size()) {
            for (BusSeatAllocation a : held) {
                a.setStatus(BusSeatAllocation.Status.CONFIRMED);
                a.setExpiresAt(null);
            }
            allocations.saveAll(held);
            confirm(ticket, now);
            return;
        }

        // The hold ran out (or was released): whatever it still holds goes, then try the same seats again.
        List<BusSeatAllocation> stale = own.stream().filter(a -> a.getStatus() == BusSeatAllocation.Status.HELD).toList();
        stale.forEach(a -> a.setStatus(BusSeatAllocation.Status.EXPIRED));
        allocations.saveAllAndFlush(stale);
        if (open) {
            trips.lockById(trip.getId()); // queue behind any hold being made on this trip right now
        }
        if (open && seatsFree(ticket, pax, now)) {
            List<BusSeatAllocation> rows = pax.stream().map(p -> allocation(ticket, p.getSeatLabel(), p.getGender(),
                    BusSeatAllocation.Status.CONFIRMED, null)).toList();
            // Someone taking a seat at this very instant fails this attempt; the retry then refunds.
            allocations.saveAllAndFlush(rows);
            confirm(ticket, now);
            log.info("Bus ticket {} paid after its hold ran out; seats were still free and are now confirmed",
                    ticket.getTicketNo());
            return;
        }
        ticket.setStatus(BusTicket.Status.EXPIRED);
        tickets.saveAndFlush(ticket);
        refunds.queue(ticket, payment, payment.getAmount(), "Seats were taken before the payment arrived");
        notifier.paidTooLate(ticket, payment.getAmount());
        log.warn("Bus ticket {} paid too late: seats taken, payment {} refunded in full", ticket.getTicketNo(), payment.getId());
    }

    private boolean seatsFree(BusTicket ticket, List<BusTicketPassenger> pax, LocalDateTime now) {
        Map<String, List<BusSeatAllocation>> taken = BusSeatRules.bySeat(
                allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(ticket.getTripId(), BusSeatRules.LIVE));
        return pax.stream().allMatch(p -> BusSeatRules.occupying(taken, p.getSeatLabel(), ticket.getFromSeq(),
                ticket.getToSeq(), now, ticket.getId()).isEmpty());
    }

    private void confirm(BusTicket ticket, LocalDateTime now) {
        ticket.setStatus(BusTicket.Status.CONFIRMED);
        ticket.setConfirmedAt(now);
        // Flushed now: a version clash with a simultaneous run surfaces here, before any message is queued.
        BusTicket saved = tickets.saveAndFlush(ticket);
        notifier.confirmed(saved);
        log.info("Bus ticket {} confirmed", saved.getTicketNo());
    }

    /** An unpaid ticket gives its seats back. */
    private BusTicket expire(BusTicket ticket) {
        List<BusSeatAllocation> held = allocations.findByTicketId(ticket.getId()).stream()
                .filter(a -> a.getStatus() == BusSeatAllocation.Status.HELD).toList();
        held.forEach(a -> a.setStatus(BusSeatAllocation.Status.EXPIRED));
        allocations.saveAll(held);
        ticket.setStatus(BusTicket.Status.EXPIRED);
        return tickets.saveAndFlush(ticket);
    }

    // ---------------------------------------------------------------------------------------------
    // Sweeper
    // ---------------------------------------------------------------------------------------------

    /**
     * Every minute (BusJobs): holds that ran out become EXPIRED, unpaid tickets past their hold
     * expire, and captured payments that nobody settled (lost callback and webhook) are settled.
     */
    public void sweep() {
        LocalDateTime now = now();
        newTx.executeWithoutResult(s -> allocations.expireAllStaleHolds(now));
        for (UUID id : tickets.findExpiredHolds(now)) {
            try {
                newTx.executeWithoutResult(s -> tickets.findById(id)
                        .filter(t -> t.getStatus() == BusTicket.Status.PENDING && t.getHoldExpiresAt().isBefore(now))
                        .ifPresent(this::expire));
            } catch (OptimisticLockingFailureException raced) {
                log.info("Bus ticket {} changed while expiring it (just paid?); leaving it", id);
            }
        }
        for (BusPayment p : payments.findCapturedUnsettled()) {
            try {
                settle(p.getId(), p.getRazorpayPaymentId());
            } catch (RuntimeException e) {
                log.warn("Could not settle captured bus payment {}: {}", p.getId(), e.toString());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    BusTicket mine(UUID userId, UUID ticketId) {
        return tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    static long toPaise(BigDecimal rupees) {
        if (rupees == null || rupees.signum() <= 0) {
            throw new IllegalStateException("Refusing to charge a non-positive amount");
        }
        return rupees.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** Distinct labels, upper case, as typed in a comma list ("u3, U4" → [U3, U4]). */
    static Set<String> labels(String commaList) {
        Set<String> out = new LinkedHashSet<>();
        if (commaList != null) {
            for (String part : commaList.split(",")) {
                if (!part.isBlank()) {
                    out.add(BusSeatRules.key(part));
                }
            }
        }
        return out;
    }
}
