package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.service.AfterCommitDispatcher;
import com.travelplatform.service.CityService;
import com.travelplatform.service.DispatchService;
import com.travelplatform.service.DriverOfferPublisher;
import com.travelplatform.service.DriverPresenceService;
import com.travelplatform.service.NotificationService;
import com.travelplatform.service.UserService;
import com.travelplatform.service.dispatch.AssignmentSolver.Edge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch matching for City rides. Every {@code round-interval-ms} (2 s), for each city this server
 * holds the lock of, all waiting rides and all free drivers near them are paired at once so that
 * total pickup time is least ({@link AssignmentSolver}), instead of each ride grabbing its own
 * nearest driver and leaving the next ride someone far away.
 *
 * <p>A round reads only the working copies - presence, {@link DriverStateStore}, {@link RideQueue}
 * - and touches Postgres for the few rows it changes: one short transaction per offer sent,
 * expired or given up on. {@code no_driver_overlap} and {@code @Version} still guard those writes,
 * so a stale working copy can cost a round, never a double booking.
 *
 * <p>One city never runs on two servers: {@link CityLock}. A server keeps about its share of the
 * cities, {@code ceil(active / live servers) + 1}, so they spread out, and a dead server's cities
 * are taken over within the lock's 5 s expiry. A city taken over with an empty queue (Redis
 * emptied or restarted) is rebuilt from its open bookings first.
 */
@Component
public class MatchingWorker {

    private static final Logger log = LoggerFactory.getLogger(MatchingWorker.class);

    /** Parallel cities per server. One slow city never holds the others back. */
    private static final int THREADS = 4;

    /**
     * Held cities are also merged from Postgres every this many ticks (about a minute), so a ride
     * whose queue write was lost (Redis blipped while the lock held) is never stranded.
     */
    private static final int RECOVER_EVERY_TICKS = 30;

    private enum Outcome { SENT, RIDE_GONE, DRIVER_BUSY, RACE_LOST }

    private final CityService cityService;
    private final DriverPresenceService presence;
    private final DriverStateStore states;
    private final RideQueue queue;
    private final CityLock lock;
    private final EtaService etaService;
    private final AssignmentSolver solver;
    private final DispatchService dispatch;
    private final TravelBookingRepository bookings;
    private final DriverRepository drivers;
    private final NotificationService notifications;
    private final DriverOfferPublisher publisher;
    private final AfterCommitDispatcher dispatcher;
    private final TransactionTemplate tx;
    private final DispatchProperties properties;

    private final Set<Long> held = ConcurrentHashMap.newKeySet();
    private final Map<Long, AtomicInteger> waitingGauge = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> candidateGauge = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(THREADS, r -> {
        Thread t = new Thread(r, "matching-round");
        t.setDaemon(true);
        return t;
    });
    private long ticks;

    public MatchingWorker(CityService cityService, DriverPresenceService presence, DriverStateStore states,
                          RideQueue queue, CityLock lock, EtaService etaService, AssignmentSolver solver,
                          DispatchService dispatch, TravelBookingRepository bookings, DriverRepository drivers,
                          NotificationService notifications, DriverOfferPublisher publisher,
                          AfterCommitDispatcher dispatcher, TransactionTemplate tx, DispatchProperties properties) {
        this.cityService = cityService;
        this.presence = presence;
        this.states = states;
        this.queue = queue;
        this.lock = lock;
        this.etaService = etaService;
        this.solver = solver;
        this.dispatch = dispatch;
        this.bookings = bookings;
        this.drivers = drivers;
        this.notifications = notifications;
        this.publisher = publisher;
        this.dispatcher = dispatcher;
        this.tx = tx;
        this.properties = properties;
    }

    /** The cities this server runs right now. */
    Set<Long> heldCities() {
        return Set.copyOf(held);
    }

    /**
     * Heartbeat, keep or take city locks, then one round for every held city. Fixed delay, so a
     * slow round pushes the next tick back instead of piling rounds up.
     */
    @Scheduled(fixedDelayString = "${app.dispatch.round-interval-ms:2000}",
               initialDelayString = "${app.dispatch.round-initial-delay-ms:5000}")
    public void tick() {
        boolean recoverAll = ++ticks % RECOVER_EVERY_TICKS == 0;
        List<Long> fresh = new ArrayList<>();
        try {
            lock.heartbeat();
            List<Long> active = cityService.activeIds();
            for (Long city : new ArrayList<>(held)) {
                if (!active.contains(city)) {
                    lock.release(city); // switched off by the owner
                    held.remove(city);
                } else if (!lock.renew(city)) {
                    held.remove(city); // expired and taken: another server runs it now
                }
            }
            int share = (int) Math.ceil(active.size() / (double) Math.max(1, lock.liveServers())) + 1;
            for (Long city : new ArrayList<>(held)) {
                if (held.size() <= share) {
                    break;
                }
                lock.release(city); // more servers now: hand the extras over
                held.remove(city);
            }
            for (Long city : active) {
                if (held.size() >= share) {
                    break;
                }
                if (!held.contains(city) && lock.tryAcquire(city)) {
                    held.add(city);
                    fresh.add(city);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Matching tick could not reach the lock store; skipping this tick: {}", e.toString());
            return;
        }

        List<Callable<Void>> rounds = new ArrayList<>();
        for (Long city : held) {
            boolean rebuild = fresh.contains(city);
            rounds.add(() -> {
                try {
                    if ((rebuild && queue.isEmpty(city)) || recoverAll) {
                        rebuild(city);
                    }
                    runRound(city);
                } catch (RuntimeException e) {
                    log.warn("Matching round for city {} failed; retrying next tick: {}", city, e.toString());
                }
                return null;
            });
        }
        try {
            pool.invokeAll(rounds, Math.max(10_000L, properties.getRoundIntervalMs() * 5L), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
        for (Long city : held) {
            try {
                lock.release(city); // let another server take it at once instead of after expiry
            } catch (RuntimeException ignored) {
                // it expires on its own
            }
        }
        held.clear();
    }

    /**
     * Restores a city's queue from Postgres: SEARCHING rides back into waiting, OFFERED ones back
     * into the offers with their deadlines, each with its declined drivers. Uses the V13 partial
     * index on open dispatch states. Idempotent: adding a ride already queued changes nothing.
     */
    void rebuild(long cityId) {
        int rides = 0;
        for (TravelBooking b : bookings.findOpenDispatchInCity(cityId)) {
            if (b.getDispatchMode() != TravelBooking.DispatchMode.RIDE_NOW || b.isAdvanceDue()
                    || b.getFromLat() == null || b.getFromLon() == null) {
                continue;
            }
            queue.enqueue(dispatch.ticket(b));
            dispatch.rejectedDrivers(b.getId()).forEach(d -> queue.decline(b.getId(), d));
            if (b.getDispatchState() == DispatchState.OFFERED && b.getDriverId() != null && b.getOfferExpiresAt() != null) {
                long driverId = b.getDriverId();
                queue.markOffered(cityId, b.getId(), driverId, DispatchService.epochMs(b.getOfferExpiresAt()));
                states.cards(List.of(driverId)); // a card must exist for the state to stick
                states.setState(driverId, DriverState.OFFERED);
            }
            rides++;
        }
        if (rides > 0) {
            log.info("Matching queue for city {} rebuilt from the database: {} open rides", cityId, rides);
        }
    }

    /** One matching round for one city. */
    void runRound(long cityId) {
        long started = System.nanoTime();
        long nowMs = dispatch.clock().millis();

        // 1. Offers nobody answered in time: silence is a decline.
        for (OfferRef expired : queue.expiredOffers(cityId, nowMs)) {
            try {
                expire(cityId, expired);
            } catch (RuntimeException e) {
                log.warn("Expiring offer {} failed; retrying next round: {}", expired, e.toString());
            }
        }

        // 2. Waiting rides; those out of time go to the owner.
        List<RideTicket> live = new ArrayList<>();
        for (RideTicket t : queue.waiting(cityId, properties.getMaxRidesPerRound())) {
            if (waitedSeconds(t, nowMs) >= properties.getSearchTimeoutSeconds()) {
                giveUp(cityId, t);
            } else {
                live.add(t);
            }
        }
        gauge(waitingGauge, "namma.match.rides.waiting", cityId).set(live.size());
        if (live.isEmpty()) {
            gauge(candidateGauge, "namma.match.drivers.candidate", cityId).set(0);
            record(started);
            return;
        }

        // 3. Candidates: nearest drivers in the ride's current radius, then one batched card read.
        Map<UUID, List<DriverPresenceService.Nearby>> nearby = new HashMap<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (RideTicket t : live) {
            double radius = radiusFor(waitedSeconds(t, nowMs));
            if (radius <= 0) {
                continue;
            }
            Set<Long> declined = queue.declined(t.bookingId());
            List<DriverPresenceService.Nearby> found = presence
                    .findWithin(cityId, t.pickup().lat(), t.pickup().lon(), radius, properties.getSearchCount())
                    .stream().filter(n -> !declined.contains(n.driverId())).toList();
            nearby.put(t.bookingId(), found);
            found.forEach(n -> seen.add(n.driverId()));
        }
        Map<Long, DriverCard> cards = seen.isEmpty() ? Map.of() : states.cards(seen);

        List<Edge> edges = new ArrayList<>();
        Map<String, Double> distance = new HashMap<>();
        Set<Long> candidates = new HashSet<>();
        for (RideTicket t : live) {
            List<DriverPresenceService.Nearby> fit = nearby.getOrDefault(t.bookingId(), List.of()).stream()
                    .filter(n -> eligible(cards.get(n.driverId()), t))
                    .limit(Math.max(1, properties.getCandidatesPerRide()))
                    .toList();
            if (fit.isEmpty()) {
                continue;
            }
            Map<Long, GeoPoint> at = new HashMap<>();
            fit.forEach(n -> at.put(n.driverId(), new GeoPoint(n.lat(), n.lon())));
            Map<Long, Integer> eta = etaService.etaSeconds(t.pickup(), at);
            long bonus = (long) properties.getWaitWeight() * waitedSeconds(t, nowMs);
            for (DriverPresenceService.Nearby n : fit) {
                Integer seconds = eta.get(n.driverId());
                if (seconds == null || seconds > properties.getMaxEtaSeconds()) {
                    continue; // no route, or too far by road
                }
                edges.add(new Edge(t.bookingId(), n.driverId(), seconds - bonus));
                distance.put(t.bookingId() + "/" + n.driverId(), n.distanceKm());
                candidates.add(n.driverId());
            }
        }
        gauge(candidateGauge, "namma.match.drivers.candidate", cityId).set(candidates.size());

        // 4. The assignment with the least total cost.
        Map<UUID, Long> pairs = solver.solve(edges);

        // 5. Offers, oldest ride first.
        LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), DispatchService.ZONE);
        for (RideTicket t : live) {
            Long driverId = pairs.get(t.bookingId());
            if (driverId == null) {
                continue;
            }
            // The guard against one driver being handed two rides by two rounds or two servers.
            if (!states.compareAndSetState(driverId, DriverState.IDLE, DriverState.OFFERED)) {
                continue;
            }
            try {
                offer(cityId, t, driverId, distance.getOrDefault(t.bookingId() + "/" + driverId, 0.0), now, nowMs);
            } catch (RuntimeException e) {
                states.compareAndSetState(driverId, DriverState.OFFERED, DriverState.IDLE);
                log.warn("Offering ride {} to driver {} failed: {}", t.bookingId(), driverId, e.toString());
            }
        }
        record(started);
    }

    private void offer(long cityId, RideTicket t, long driverId, double km, LocalDateTime now, long nowMs) {
        long deadlineMs = nowMs + properties.getOfferSeconds() * 1000L;
        LocalDateTime deadline = now.plusSeconds(properties.getOfferSeconds());
        Outcome outcome;
        try {
            outcome = tx.execute(s -> {
                TravelBooking b = bookings.findById(t.bookingId()).orElse(null);
                // Matched, cancelled or handed to the owner since it was queued; never a Trip, and
                // never anything still owing an advance.
                if (b == null || b.getStatus() != TravelBooking.BookingStatus.PENDING
                        || b.getDispatchMode() != TravelBooking.DispatchMode.RIDE_NOW
                        || b.getDispatchState() != DispatchState.SEARCHING || b.isAdvanceDue()) {
                    return Outcome.RIDE_GONE;
                }
                Driver driver = drivers.findById(driverId).orElse(null);
                if (driver == null) {
                    return Outcome.RACE_LOST;
                }
                b.setDriverId(driverId);
                b.setDispatchState(DispatchState.OFFERED);
                b.setOfferExpiresAt(deadline);
                b.setOfferDistanceKm(Math.round(km * 100.0) / 100.0);
                // Flushed here so a refusal by no_driver_overlap surfaces now, for this pair.
                TravelBooking saved = bookings.saveAndFlush(b);
                notifications.notifyRideOffered(saved, driver);
                return Outcome.SENT;
            });
        } catch (DataIntegrityViolationException e) {
            if (!UserService.isDriverOverlap(e)) {
                throw e;
            }
            outcome = Outcome.DRIVER_BUSY;
        } catch (PessimisticLockingFailureException e) {
            outcome = Outcome.DRIVER_BUSY; // another booking for this driver is mid-insert
        } catch (OptimisticLockingFailureException e) {
            outcome = Outcome.RACE_LOST;
        }

        switch (outcome == null ? Outcome.RACE_LOST : outcome) {
            case SENT -> {
                queue.markOffered(cityId, t.bookingId(), driverId, deadlineMs);
                // After markOffered, so the poll the push triggers already finds the offer.
                dispatcher.dispatch("offer-push", () ->
                        publisher.publish(driverId, t.bookingId(), DriverOfferPublisher.Event.OFFER));
                Metrics.counter("namma.match.offers").increment();
                Metrics.counter("namma.dispatch.offers", "outcome", "sent").increment();
            }
            case RIDE_GONE -> {
                states.compareAndSetState(driverId, DriverState.OFFERED, DriverState.IDLE);
                queue.remove(cityId, t.bookingId());
            }
            case DRIVER_BUSY -> {
                // The database holds a booking for them today that the store missed. Busy until
                // reconcile says otherwise, so the next rounds do not try them again and again.
                states.setState(driverId, DriverState.BUSY);
                Metrics.counter("namma.dispatch.driver_race_lost").increment();
            }
            case RACE_LOST -> states.compareAndSetState(driverId, DriverState.OFFERED, DriverState.IDLE);
        }
    }

    /** An offer ran out: the ride goes back to waiting without that driver, who is free again. */
    private void expire(long cityId, OfferRef ref) {
        TravelBooking moved;
        try {
            moved = tx.execute(s -> {
                TravelBooking b = bookings.findById(ref.bookingId()).orElse(null);
                if (b == null || b.getStatus() != TravelBooking.BookingStatus.PENDING
                        || b.getDispatchState() != DispatchState.OFFERED
                        || !Objects.equals(b.getDriverId(), ref.driverId())) {
                    return null; // accepted, cancelled or reassigned meanwhile: they settled it
                }
                dispatch.recordRejection(b.getId(), ref.driverId());
                b.setDriverId(null);
                b.setDispatchState(DispatchState.SEARCHING);
                b.setOfferExpiresAt(null);
                b.setOfferDistanceKm(null);
                return bookings.save(b);
            });
        } catch (OptimisticLockingFailureException e) {
            return; // an accept landed in the same instant; the next round sees who won
        }
        queue.clearOffer(cityId, ref.bookingId());
        if (moved == null) {
            return;
        }
        queue.decline(ref.bookingId(), ref.driverId());
        states.setState(ref.driverId(), DriverState.IDLE);
        queue.enqueue(dispatch.ticket(moved));
        dispatcher.dispatch("offer-push", () ->
                publisher.publish(ref.driverId(), ref.bookingId(), DriverOfferPublisher.Event.WITHDRAWN));
        Metrics.counter("namma.dispatch.offers", "outcome", "expired").increment();
    }

    /** Two minutes and nobody: the owner places it by hand. */
    private void giveUp(long cityId, RideTicket t) {
        tx.executeWithoutResult(s -> {
            TravelBooking b = bookings.findById(t.bookingId()).orElse(null);
            if (b != null && b.getStatus() == TravelBooking.BookingStatus.PENDING
                    && b.getDispatchState() == DispatchState.SEARCHING) {
                b.setDispatchState(DispatchState.NO_DRIVER);
                bookings.save(b);
                notifications.notifyNoDriverNearby(b);
                Metrics.counter("namma.dispatch.searches", "outcome", "no_driver").increment();
                log.info("City ride {}: no driver matched within {} s, handed to the owner",
                        b.getId(), properties.getSearchTimeoutSeconds());
            }
        });
        queue.remove(cityId, t.bookingId());
    }

    private static boolean eligible(DriverCard card, RideTicket t) {
        return card != null && card.state() == DriverState.IDLE && card.active() && card.servesCity()
                && card.vehicle() == t.vehicle() && card.seats() >= t.passengers();
    }

    private static long waitedSeconds(RideTicket t, long nowMs) {
        return Math.max(0, (nowMs - t.searchStartedEpochMs()) / 1000);
    }

    /** The largest radius whose waiting threshold has passed; 0 when none has. */
    double radiusFor(long waitedSeconds) {
        List<Double> radii = properties.getRadiiKm();
        List<Integer> after = properties.getRadiusAfterSeconds();
        double radius = 0;
        for (int i = 0; i < Math.min(radii.size(), after.size()); i++) {
            Double r = radii.get(i);
            Integer from = after.get(i);
            if (r != null && from != null && from <= waitedSeconds && r > radius) {
                radius = r;
            }
        }
        return radius;
    }

    private static AtomicInteger gauge(Map<Long, AtomicInteger> gauges, String name, long cityId) {
        return gauges.computeIfAbsent(cityId, id ->
                Metrics.gauge(name, Tags.of("city", Long.toString(id)), new AtomicInteger()));
    }

    private static void record(long startedNanos) {
        Metrics.timer("namma.match.round.duration").record(Duration.ofNanos(System.nanoTime() - startedNanos));
    }
}
