package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingForms.ReviewForm;
import com.travelplatform.dto.bus.BusBookingViews.CancelQuote;
import com.travelplatform.dto.bus.BusBookingViews.QuoteSeat;
import com.travelplatform.dto.bus.BusBookingViews.TicketView;
import com.travelplatform.dto.bus.BusForms.Slab;
import com.travelplatform.entity.BusPayment;
import com.travelplatform.entity.BusReview;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusPaymentRepository;
import com.travelplatform.repository.BusReviewRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusSeatAllocationRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.repository.BusTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A customer's bus tickets after the hold (spec "After booking"): the list and detail, cancelling
 * seats with a quote first, reviews, and the jobs that move trips and tickets along (status and the
 * boarding reminder). Also refunds and tells everyone when the owner cancels a trip.
 */
@Service
public class BusTicketService {

    private static final Logger log = LoggerFactory.getLogger(BusTicketService.class);

    static final int MAX_LIST = 100;
    /** A trip counts as finished this long after its arrival time. */
    static final Duration COMPLETE_AFTER = Duration.ofHours(2);
    /** The reminder goes out once the boarding time is this close. */
    static final Duration REMIND_BEFORE = Duration.ofHours(2);

    private final BusTicketRepository tickets;
    private final BusTicketPassengerRepository passengers;
    private final BusSeatAllocationRepository allocations;
    private final BusTripRepository trips;
    private final BusRoutePointRepository points;
    private final BusPaymentRepository payments;
    private final BusReviewRepository reviews;
    private final BusRefunds refunds;
    private final BusNotifier notifier;
    private final BusTicketViews views;
    private final TransactionTemplate newTx;
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusTicketService(BusTicketRepository tickets, BusTicketPassengerRepository passengers,
                            BusSeatAllocationRepository allocations, BusTripRepository trips,
                            BusRoutePointRepository points, BusPaymentRepository payments, BusReviewRepository reviews,
                            BusRefunds refunds, BusNotifier notifier, BusTicketViews views,
                            PlatformTransactionManager txManager) {
        this.tickets = tickets;
        this.passengers = passengers;
        this.allocations = allocations;
        this.trips = trips;
        this.points = points;
        this.payments = payments;
        this.reviews = reviews;
        this.refunds = refunds;
        this.notifier = notifier;
        this.views = views;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * The customer's tickets, newest first. Holds that were never paid for are left out (an
     * abandoned checkout is not a ticket); a hold still running is included and the screen hides it.
     */
    @Transactional(readOnly = true)
    public List<TicketView> list(UUID userId) {
        List<BusTicket> mine = tickets.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_LIST));
        Set<UUID> paid = mine.isEmpty() ? Set.of() : payments.findByTicketIdInAndStatus(
                mine.stream().map(BusTicket::getId).toList(), BusPayment.Status.CAPTURED).stream()
                .map(BusPayment::getTicketId).collect(Collectors.toSet());
        return views.many(mine.stream()
                .filter(t -> t.getStatus() != BusTicket.Status.EXPIRED || paid.contains(t.getId())).toList());
    }

    @Transactional(readOnly = true)
    public TicketView get(UUID userId, UUID ticketId) {
        return views.one(mine(userId, ticketId));
    }

    // ---------------------------------------------------------------------------------------------
    // Cancelling seats
    // ---------------------------------------------------------------------------------------------

    /** What cancelling these seats now would cost and refund. */
    @Transactional(readOnly = true)
    public CancelQuote cancelQuote(UUID userId, UUID ticketId, String seatList) {
        BusTicket ticket = mine(userId, ticketId);
        return quote(ticket, chosen(ticket, BusBookingService.labels(seatList)), now()).quote();
    }

    /**
     * Cancels the seats: the passengers are CANCELLED with their charge and refund, the seats go
     * back on sale, the refund is queued and the customer told. The ticket row is locked, so two
     * cancellations of one ticket run one after the other.
     */
    @Transactional
    public TicketView cancel(UUID userId, UUID ticketId, List<String> seatLabels) {
        BusTicket ticket = tickets.lockById(ticketId).filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        Set<String> wanted = new java.util.LinkedHashSet<>();
        if (seatLabels != null) {
            seatLabels.stream().filter(s -> s != null && !s.isBlank()).map(BusSeatRules::key).forEach(wanted::add);
        }
        List<BusTicketPassenger> chosen = chosen(ticket, wanted);
        LocalDateTime now = now();
        Quoted q = quote(ticket, chosen, now);

        for (int i = 0; i < chosen.size(); i++) {
            BusTicketPassenger p = chosen.get(i);
            QuoteSeat s = q.quote().seats().get(i);
            p.setStatus(BusTicketPassenger.Status.CANCELLED);
            p.setCancelCharge(s.charge());
            p.setRefundAmount(s.refund());
            p.setCancelledAt(now);
        }
        passengers.saveAll(chosen);
        Set<String> keys = chosen.stream().map(p -> BusSeatRules.key(p.getSeatLabel())).collect(Collectors.toSet());
        List<BusSeatAllocation> freed = allocations.findByTicketId(ticket.getId()).stream()
                .filter(a -> a.getStatus() == BusSeatAllocation.Status.CONFIRMED && keys.contains(BusSeatRules.key(a.getSeatLabel())))
                .toList();
        freed.forEach(a -> a.setStatus(BusSeatAllocation.Status.CANCELLED));
        allocations.saveAll(freed);

        boolean anyLeft = passengers.findByTicketIdOrderByIdAsc(ticket.getId()).stream()
                .anyMatch(p -> p.getStatus() == BusTicketPassenger.Status.CONFIRMED && !keys.contains(BusSeatRules.key(p.getSeatLabel())));
        ticket.setStatus(anyLeft ? BusTicket.Status.PARTIALLY_CANCELLED : BusTicket.Status.CANCELLED);
        ticket = tickets.saveAndFlush(ticket);

        BigDecimal refund = q.quote().totalRefund();
        refunds.queue(ticket, refunds.paidWith(ticket), refund,
                "Cancelled seat" + (keys.size() == 1 ? " " : "s ") + String.join(", ", keys));
        notifier.cancelled(ticket, chosen.stream().map(BusTicketPassenger::getSeatLabel).toList(), refund);
        log.info("Bus ticket {}: cancelled {} seat(s), refund {}", ticket.getTicketNo(), chosen.size(), refund);
        return views.one(ticket);
    }

    private record Quoted(CancelQuote quote) {
    }

    /** Charge on the fare by the ticket's slabs, measured to its boarding point; the seat's GST comes back in full. */
    private Quoted quote(BusTicket ticket, List<BusTicketPassenger> chosen, LocalDateTime now) {
        if (!BusTicketViews.CANCELLABLE.contains(ticket.getStatus())) {
            throw new ConflictException("This ticket cannot be cancelled");
        }
        BusTrip trip = trips.findById(ticket.getTripId()).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        if (trip.getStatus() == BusTrip.Status.CANCELLED || trip.getStatus() == BusTrip.Status.COMPLETED) {
            throw new ConflictException("This ticket cannot be cancelled");
        }
        BusRoutePoint board = points.findById(ticket.getBoardingPointId())
                .orElseThrow(() -> new IllegalStateException("Boarding point missing"));
        LocalDateTime boardingAt = board.at(trip.getTravelDate());
        if (!now.isBefore(boardingAt)) {
            throw new ConflictException("Seats cannot be cancelled at or after the boarding time");
        }
        long minutes = Duration.between(now, boardingAt).toMinutes();
        int percent = BusFares.chargePercent(BusCancellationPolicy.fromJson(ticket.getCancellationPolicy()), minutes);
        List<QuoteSeat> seats = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BusTicketPassenger p : chosen) {
            BigDecimal charge = BusFares.percentOf(p.getFare(), BigDecimal.valueOf(percent));
            BigDecimal refund = BusFares.money(p.getFare().subtract(charge).add(p.getGst()));
            seats.add(new QuoteSeat(p.getSeatLabel(), p.getFare(), p.getGst(), percent, charge, refund));
            total = total.add(refund);
        }
        double hours = BigDecimal.valueOf(Duration.between(now, boardingAt).getSeconds())
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.DOWN).doubleValue();
        return new Quoted(new CancelQuote(seats, BusFares.money(total), hours));
    }

    /** The ticket's still-travelling passengers in these seats; every seat must be one of them. */
    private List<BusTicketPassenger> chosen(BusTicket ticket, Collection<String> wanted) {
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Choose the seats to cancel");
        }
        Map<String, BusTicketPassenger> live = passengers.findByTicketIdOrderByIdAsc(ticket.getId()).stream()
                .filter(p -> p.getStatus() == BusTicketPassenger.Status.CONFIRMED)
                .collect(Collectors.toMap(p -> BusSeatRules.key(p.getSeatLabel()), p -> p, (a, b) -> a, java.util.LinkedHashMap::new));
        List<BusTicketPassenger> out = new ArrayList<>();
        for (String key : wanted) {
            BusTicketPassenger p = live.get(key);
            if (p == null) {
                throw new IllegalArgumentException("Seat " + key + " is not a booked seat on this ticket");
            }
            out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Reviews
    // ---------------------------------------------------------------------------------------------

    /** One review per completed ticket. */
    @Transactional
    public TicketView review(UUID userId, UUID ticketId, ReviewForm form) {
        BusTicket ticket = mine(userId, ticketId);
        if (ticket.getStatus() != BusTicket.Status.COMPLETED) {
            throw new ConflictException("You can review a bus after your journey is completed");
        }
        if (form == null || form.rating() == null || form.rating() < 1 || form.rating() > 5) {
            throw new IllegalArgumentException("Give a rating from 1 to 5");
        }
        String comment = form.comment() == null || form.comment().isBlank() ? null : form.comment().trim();
        if (comment != null && comment.length() > 1000) {
            throw new IllegalArgumentException("Keep the review under 1000 characters");
        }
        if (reviews.findByTicketId(ticketId).isPresent()) {
            throw new ConflictException("You have already reviewed this journey");
        }
        BusTrip trip = trips.findById(ticket.getTripId()).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        BusReview r = new BusReview();
        r.setTicketId(ticketId);
        r.setBusId(trip.getBusId());
        r.setOwnerId(ticket.getOwnerId());
        r.setUserId(userId);
        r.setRating(form.rating());
        r.setComment(comment);
        try {
            reviews.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You have already reviewed this journey");
        }
        return views.one(ticket);
    }

    // ---------------------------------------------------------------------------------------------
    // The owner cancelled the trip
    // ---------------------------------------------------------------------------------------------

    /**
     * Called by BusTripService.cancel, in its transaction, once the trip is CANCELLED and its live
     * tickets SERVICE_CANCELLED: each paid ticket's travelling passengers get fare + GST back, and
     * every ticket's contacts are told.
     */
    public void tripCancelled(BusTrip trip, List<BusTicket> cancelled) {
        LocalDateTime now = now();
        for (BusTicket ticket : cancelled) {
            List<BusTicketPassenger> travelling = passengers.findByTicketIdOrderByIdAsc(ticket.getId()).stream()
                    .filter(p -> p.getStatus() == BusTicketPassenger.Status.CONFIRMED).toList();
            BusPayment paid = refunds.paidWith(ticket);
            BigDecimal refund = BigDecimal.ZERO;
            if (paid != null) {
                for (BusTicketPassenger p : travelling) {
                    p.setStatus(BusTicketPassenger.Status.CANCELLED);
                    p.setCancelCharge(BigDecimal.ZERO.setScale(2));
                    p.setRefundAmount(BusFares.money(p.getFare().add(p.getGst())));
                    p.setCancelledAt(now);
                    refund = refund.add(p.getRefundAmount());
                }
                passengers.saveAll(travelling);
                refunds.queue(ticket, paid, refund, "Bus cancelled by the operator");
                notifier.tripCancelled(ticket, trip.getCancelReason(), BusFares.money(refund));
            }
            // An unpaid hold needs no message: its seats are simply gone. A payment that lands
            // later is refunded when it is settled (the ticket is SERVICE_CANCELLED).
        }
        if (!cancelled.isEmpty()) {
            log.info("Bus trip {} cancelled: {} ticket(s) refunded and told", trip.getId(), cancelled.size());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Jobs
    // ---------------------------------------------------------------------------------------------

    /**
     * Every 5 minutes: trips past their departure become DEPARTED; DEPARTED trips 2 hours past their
     * arrival become COMPLETED, and so do their travelling tickets.
     */
    public void advanceTripStatuses() {
        LocalDateTime now = now();
        newTx.executeWithoutResult(s -> {
            int departed = trips.markDeparted(now);
            List<Long> arrived = trips.findArrivedIds(now.minus(COMPLETE_AFTER));
            if (!arrived.isEmpty()) {
                trips.markCompleted(arrived);
                tickets.completeForTrips(arrived);
            }
            if (departed > 0 || !arrived.isEmpty()) {
                log.info("Bus trips: {} departed, {} completed", departed, arrived.size());
            }
        });
    }

    /** Every 5 minutes: the "your bus leaves in about 2 hours" message, once per ticket. */
    public int sendReminders() {
        LocalDateTime now = now();
        LocalDateTime horizon = now.plus(REMIND_BEFORE);
        List<BusTicket> candidates = tickets.findReminderCandidates(now, horizon);
        if (candidates.isEmpty()) {
            return 0;
        }
        Map<Long, BusTrip> tripById = trips.findAllById(candidates.stream().map(BusTicket::getTripId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BusTrip::getId, t -> t));
        Map<Long, BusRoutePoint> pointById = points.findAllById(candidates.stream().map(BusTicket::getBoardingPointId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(BusRoutePoint::getId, p -> p));
        int sent = 0;
        for (BusTicket candidate : candidates) {
            BusTrip trip = tripById.get(candidate.getTripId());
            BusRoutePoint board = pointById.get(candidate.getBoardingPointId());
            if (trip == null || board == null) {
                continue;
            }
            LocalDateTime boardingAt = board.at(trip.getTravelDate());
            if (!boardingAt.isAfter(now) || boardingAt.isAfter(horizon)) {
                continue;
            }
            try {
                Boolean done = newTx.execute(s -> {
                    BusTicket t = tickets.findById(candidate.getId()).orElse(null);
                    if (t == null || t.getReminderSentAt() != null || !BusTicketViews.CANCELLABLE.contains(t.getStatus())) {
                        return false;
                    }
                    t.setReminderSentAt(now);
                    tickets.saveAndFlush(t);
                    notifier.reminder(t);
                    return true;
                });
                sent += Boolean.TRUE.equals(done) ? 1 : 0;
            } catch (RuntimeException e) {
                log.warn("Could not send the reminder for bus ticket {}: {}", candidate.getTicketNo(), e.toString());
            }
        }
        return sent;
    }

    private BusTicket mine(UUID userId, UUID ticketId) {
        return tickets.findById(ticketId).filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }
}
