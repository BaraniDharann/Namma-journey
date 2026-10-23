package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Finds a driver for a Ride Now booking the way a ride-hailing app does: the nearest online driver
 * around the pickup gets the offer, alone, for a few seconds. Silence or a decline moves it to the
 * next nearest. The search widens ring by ring (1, 3, 5 km by default), and a booking nobody near
 * enough takes is handed to the owner.
 *
 * <p>All state lives on the booking row ({@code dispatch_state}, {@code driver_id},
 * {@code offer_expires_at}), so any instance can pick up where another left off, and a restart
 * loses nothing: the sweep finds the open offers and carries on.
 *
 * <p>Two guards keep this correct under concurrency, and neither is Java:
 * <ul>
 *   <li>{@code no_driver_overlap} refuses an offer to a driver who was just given something else
 *       for the day - the attempt is retried with the next driver.</li>
 *   <li>{@code @Version} on the booking settles a driver accepting in the same instant the offer
 *       expires, or two instances sweeping the same booking: the loser's write is refused and it
 *       simply stops, because the winner has already moved the booking on.</li>
 * </ul>
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    /** Drivers tried per step when each in turn is claimed by another booking in the same instant. */
    private static final int MAX_ATTEMPTS = 5;

    private final TravelBookingRepository bookingRepository;
    private final DriverRepository driverRepository;
    private final BookingDriverRejectionRepository rejectionRepository;
    private final DriverPresenceService presence;
    private final NotificationService notificationService;
    private final DriverOfferPublisher offerPublisher;
    private final AfterCommitDispatcher dispatcher;
    private final TransactionTemplate transactionTemplate;
    private final DispatchProperties properties;

    /** Ride Now is "today" in India, as trip dates are. Not final so tests can move time. */
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public DispatchService(TravelBookingRepository bookingRepository,
                           DriverRepository driverRepository,
                           BookingDriverRejectionRepository rejectionRepository,
                           DriverPresenceService presence,
                           NotificationService notificationService,
                           DriverOfferPublisher offerPublisher,
                           AfterCommitDispatcher dispatcher,
                           TransactionTemplate transactionTemplate,
                           DispatchProperties properties) {
        this.bookingRepository = bookingRepository;
        this.driverRepository = driverRepository;
        this.rejectionRepository = rejectionRepository;
        this.presence = presence;
        this.notificationService = notificationService;
        this.offerPublisher = offerPublisher;
        this.dispatcher = dispatcher;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Runs {@link #dispatch} once the caller's transaction has committed the booking it refers to. */
    public void dispatchAfterCommit(UUID bookingId) {
        dispatcher.dispatch("dispatch", () -> dispatch(bookingId));
    }

    /**
     * Moves one booking's search forward: offers it if nobody holds it, takes it back from a driver
     * whose time ran out, or gives up once the search has run too long. Safe to call at any time
     * and from any instance; a booking that needs nothing is left alone.
     */
    public void dispatch(UUID bookingId) {
        List<Long> lost = new ArrayList<>();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(tx -> step(bookingId, lost));
                return;
            } catch (DriverTakenException taken) {
                lost.add(taken.driverId);
                Metrics.counter("namma.dispatch.driver_race_lost").increment();
            } catch (OptimisticLockingFailureException moved) {
                // Accepted, cancelled or advanced by someone else between our read and write.
                return;
            }
        }
    }

    /** Expired offers and running searches, every few seconds. */
    @Scheduled(fixedDelayString = "${app.dispatch.sweep-interval-ms:5000}",
               initialDelayString = "${app.dispatch.sweep-initial-delay-ms:15000}")
    public void sweep() {
        List<UUID> work;
        try {
            work = bookingRepository.findDispatchWork(now());
        } catch (RuntimeException e) {
            log.warn("Dispatch sweep could not read open bookings: {}", e.toString());
            return;
        }
        for (UUID bookingId : work) {
            try {
                dispatch(bookingId);
            } catch (RuntimeException e) {
                log.warn("Dispatch of booking {} failed; retrying next sweep: {}", bookingId, e.toString());
            }
        }
    }

    /**
     * A driver turned the trip down, or backed out after accepting. Called inside the caller's
     * transaction; the search resumes after it commits. Backing out of an accepted ride restarts
     * the search clock - the passenger has lost their driver and deserves the full search again.
     */
    public void release(TravelBooking booking, Long driverId) {
        if (booking.getStatus() == TravelBooking.BookingStatus.CONFIRMED) {
            booking.setDispatchStartedAt(now());
        }
        recordRejection(booking.getId(), driverId);
        clearOffer(booking);
        booking.setDriverId(null);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setDispatchState(DispatchState.SEARCHING);
        bookingRepository.save(booking);
        Metrics.counter("namma.dispatch.offers", "outcome", "declined").increment();
        dispatchAfterCommit(booking.getId());
    }

    /** Settles the booking's search once it has a driver for good (accepted, or set by the owner). */
    public static void markMatched(TravelBooking booking) {
        if (booking.getDispatchMode() == TravelBooking.DispatchMode.RIDE_NOW) {
            booking.setDispatchState(DispatchState.MATCHED);
            booking.setOfferExpiresAt(null);
        }
    }

    /** Is this driver's offer on this booking past its deadline? Accepting it then is refused. */
    public boolean offerExpired(TravelBooking booking) {
        return booking.getDispatchState() == DispatchState.OFFERED
                && booking.getOfferExpiresAt() != null
                && !booking.getOfferExpiresAt().isAfter(now());
    }

    private void step(UUID bookingId, List<Long> lost) {
        TravelBooking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null
                || booking.getDispatchMode() != TravelBooking.DispatchMode.RIDE_NOW
                || booking.getStatus() != TravelBooking.BookingStatus.PENDING) {
            return;
        }
        LocalDateTime now = now();

        if (booking.getDispatchState() == DispatchState.OFFERED) {
            if (!offerExpired(booking)) {
                return; // someone is still deciding
            }
            Long timedOut = booking.getDriverId();
            if (timedOut != null) {
                // Silence is a decline: they are not offered this ride again.
                recordRejection(booking.getId(), timedOut);
                long driverId = timedOut;
                dispatcher.dispatch("offer-push", () ->
                        offerPublisher.publish(driverId, bookingId, DriverOfferPublisher.Event.WITHDRAWN));
                Metrics.counter("namma.dispatch.offers", "outcome", "expired").increment();
            }
            clearOffer(booking);
            booking.setDriverId(null);
            booking.setDispatchState(DispatchState.SEARCHING);
        } else if (booking.getDispatchState() != DispatchState.SEARCHING) {
            return; // MATCHED or NO_DRIVER: nothing left for dispatch to do
        }

        Optional<DriverPresenceService.Nearby> pick = chooseDriver(booking, lost);
        if (pick.isPresent()) {
            offer(booking, pick.get(), now);
            return;
        }

        LocalDateTime started = booking.getDispatchStartedAt() != null ? booking.getDispatchStartedAt() : now;
        boolean outOfTime = !started.plusSeconds(properties.getSearchTimeoutSeconds()).isAfter(now);
        if (outOfTime || booking.getFromLat() == null || booking.getFromLon() == null) {
            booking.setDispatchState(DispatchState.NO_DRIVER);
            bookingRepository.save(booking);
            notificationService.notifyNoDriverNearby(booking);
            Metrics.counter("namma.dispatch.searches", "outcome", "no_driver").increment();
            log.info("Ride Now booking {}: no driver within {} km, handed to the owner",
                    booking.getId(), maxRadius());
        } else {
            // Still within the search time; the sweep tries again in a few seconds.
            bookingRepository.save(booking);
        }
    }

    private void offer(TravelBooking booking, DriverPresenceService.Nearby nearby, LocalDateTime now) {
        Driver driver = driverRepository.findById(nearby.driverId()).orElse(null);
        if (driver == null) {
            throw new DriverTakenException(nearby.driverId());
        }
        booking.setDriverId(driver.getId());
        booking.setDispatchState(DispatchState.OFFERED);
        booking.setOfferExpiresAt(now.plusSeconds(properties.getOfferSeconds()));
        booking.setOfferDistanceKm(Math.round(nearby.distanceKm() * 100.0) / 100.0);
        try {
            // Flushed here so a refusal by no_driver_overlap is caught for this driver, not at commit.
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
            if (ex instanceof PessimisticLockingFailureException || UserService.isDriverOverlap((DataIntegrityViolationException) ex)) {
                throw new DriverTakenException(driver.getId());
            }
            throw ex;
        }

        notificationService.notifyRideOffered(booking, driver);
        long driverId = driver.getId();
        UUID bookingId = booking.getId();
        dispatcher.dispatch("offer-push", () ->
                offerPublisher.publish(driverId, bookingId, DriverOfferPublisher.Event.OFFER));
        Metrics.counter("namma.dispatch.offers", "outcome", "sent").increment();
        log.info("Ride Now booking {} offered to driver {} ({} km from pickup)",
                bookingId, driverId, booking.getOfferDistanceKm());
    }

    /**
     * The nearest driver who may take this ride, searching the configured rings from the inside
     * out. Everyone who already declined it, or timed out on it, is skipped.
     */
    private Optional<DriverPresenceService.Nearby> chooseDriver(TravelBooking booking, List<Long> lost) {
        if (booking.getFromLat() == null || booking.getFromLon() == null) {
            return Optional.empty();
        }
        Set<Long> excluded = new HashSet<>(rejectionRepository.findDriverIdsByBookingId(booking.getId()));
        excluded.addAll(lost);

        for (Double radius : properties.getRadiiKm()) {
            if (radius == null || radius <= 0) {
                continue;
            }
            List<DriverPresenceService.Nearby> near = presence
                    .findWithin(booking.getFromLat(), booking.getFromLon(), radius)
                    .stream()
                    .filter(n -> !excluded.contains(n.driverId()))
                    .toList();
            if (near.isEmpty()) {
                continue;
            }
            Set<Long> free = new HashSet<>(driverRepository.findDispatchableAmong(
                    near.stream().map(DriverPresenceService.Nearby::driverId).toList(),
                    booking.getFromDate()));
            for (DriverPresenceService.Nearby n : near) {
                if (free.contains(n.driverId())) {
                    return Optional.of(n);
                }
            }
        }
        return Optional.empty();
    }

    private double maxRadius() {
        return properties.getRadiiKm().stream().filter(r -> r != null).mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private void recordRejection(UUID bookingId, Long driverId) {
        if (driverId == null || rejectionRepository.existsByBookingIdAndDriverId(bookingId, driverId)) {
            return;
        }
        BookingDriverRejection rejection = new BookingDriverRejection();
        rejection.setBookingId(bookingId);
        rejection.setDriverId(driverId);
        rejectionRepository.save(rejection);
    }

    private static void clearOffer(TravelBooking booking) {
        booking.setOfferExpiresAt(null);
        booking.setOfferDistanceKm(null);
    }

    /** Inside one attempt: this driver was claimed by another booking first. */
    private static final class DriverTakenException extends RuntimeException {
        private final Long driverId;

        DriverTakenException(Long driverId) {
            super(null, null, false, false);
            this.driverId = driverId;
        }
    }
}
