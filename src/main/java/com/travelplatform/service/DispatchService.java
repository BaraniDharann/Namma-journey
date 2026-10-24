package com.travelplatform.service;

import com.travelplatform.entity.BookingDriverRejection;
import com.travelplatform.entity.TravelBooking;
import com.travelplatform.entity.TravelBooking.DispatchState;
import com.travelplatform.repository.BookingDriverRejectionRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.service.dispatch.DriverStateUpdates;
import com.travelplatform.service.dispatch.GeoPoint;
import com.travelplatform.service.dispatch.RideQueue;
import com.travelplatform.service.dispatch.RideTicket;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What happens to a City ride between rounds of the matching engine
 * ({@link com.travelplatform.service.dispatch.MatchingWorker}, which finds drivers and sends
 * offers): a new ride joins its city's queue, a driver declines or backs out, a driver accepts or
 * the owner assigns one, the traveller cancels.
 *
 * <p>The booking row stays the record ({@code dispatch_state}, {@code driver_id},
 * {@code offer_expires_at}); the ride queue and driver states are a working copy for the rounds,
 * written only after the row's change commits. If one of those writes is lost, the next lock
 * take-over rebuilds the queue from the rows and reconcile rebuilds the driver states.
 *
 * <p>{@code @Version} on the booking still settles an accept racing an offer's expiry: the
 * loser's write is refused.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    /** Ride Now is "today" in India, as trip dates are. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final TravelBookingRepository bookingRepository;
    private final BookingDriverRejectionRepository rejectionRepository;
    private final NotificationService notificationService;
    private final DriverOfferPublisher offerPublisher;
    private final AfterCommitDispatcher dispatcher;
    private final RideQueue rideQueue;
    private final DriverStateUpdates driverStates;

    /** Not final so tests can move time. */
    private Clock clock = Clock.system(ZONE);

    public DispatchService(TravelBookingRepository bookingRepository,
                           BookingDriverRejectionRepository rejectionRepository,
                           NotificationService notificationService,
                           DriverOfferPublisher offerPublisher,
                           AfterCommitDispatcher dispatcher,
                           RideQueue rideQueue,
                           DriverStateUpdates driverStates) {
        this.bookingRepository = bookingRepository;
        this.rejectionRepository = rejectionRepository;
        this.notificationService = notificationService;
        this.offerPublisher = offerPublisher;
        this.dispatcher = dispatcher;
        this.rideQueue = rideQueue;
        this.driverStates = driverStates;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** The clock rounds and offers run on, so a test that moves it moves everything. */
    public Clock clock() {
        return clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * A new City ride: into its city's waiting set once the caller's transaction commits, where the
     * next round (within ~2 s) picks it up. A ride with no city or pickup (booked before cities
     * existed, V15) has no location set to search, so the owner gets it at once. Anything that is
     * not a Ride Now search - and any booking still owing an advance - is never queued.
     */
    public void enqueueAfterCommit(TravelBooking booking) {
        if (booking.getDispatchMode() != TravelBooking.DispatchMode.RIDE_NOW
                || booking.getDispatchState() != DispatchState.SEARCHING
                || booking.isAdvanceDue()) {
            return;
        }
        if (booking.getCityId() == null || booking.getFromLat() == null || booking.getFromLon() == null) {
            booking.setDispatchState(DispatchState.NO_DRIVER);
            bookingRepository.save(booking);
            notificationService.notifyNoDriverNearby(booking);
            Metrics.counter("namma.dispatch.searches", "outcome", "no_driver").increment();
            return;
        }
        RideTicket ticket = ticket(booking);
        afterCommit(() -> rideQueue.enqueue(ticket));
    }

    /**
     * A driver turned the ride down, or backed out after accepting. Called inside the caller's
     * transaction. Backing out of an accepted ride restarts the search clock - the passenger has
     * lost their driver and deserves the full search again. Once it commits, the driver is free
     * and on the ride's declined list, and the ride is back in waiting for the next round.
     */
    public void release(TravelBooking booking, Long driverId) {
        if (booking.getStatus() == TravelBooking.BookingStatus.CONFIRMED) {
            booking.setDispatchStartedAt(now());
        }
        recordRejection(booking.getId(), driverId);
        // Everyone who ever declined, so a re-queued ride (whose queue data was dropped when it
        // was matched) still never goes back to them.
        Set<Long> declined = new LinkedHashSet<>(rejectionRepository.findDriverIdsByBookingId(booking.getId()));
        if (driverId != null) {
            declined.add(driverId);
        }
        clearOffer(booking);
        booking.setDriverId(null);
        booking.setStatus(TravelBooking.BookingStatus.PENDING);
        booking.setDispatchState(DispatchState.SEARCHING);
        bookingRepository.save(booking);
        Metrics.counter("namma.dispatch.offers", "outcome", "declined").increment();

        if (driverStates != null) {
            driverStates.freed(driverId, booking);
        }
        Long cityId = booking.getCityId();
        UUID bookingId = booking.getId();
        RideTicket ticket = booking.getCityId() == null || booking.getFromLat() == null || booking.getFromLon() == null
                ? null : ticket(booking);
        afterCommit(() -> {
            if (cityId != null) {
                rideQueue.clearOffer(cityId, bookingId);
            }
            declined.forEach(d -> rideQueue.decline(bookingId, d));
            if (ticket != null) {
                rideQueue.enqueue(ticket);
            }
        });
    }

    /**
     * Settles the booking's search once it has a driver for good: accepted, or set by the owner.
     * The ride leaves the queue and the driver is busy, once this commits.
     */
    public void markMatched(TravelBooking booking) {
        settle(booking);
        if (booking.getDispatchMode() == TravelBooking.DispatchMode.RIDE_NOW && booking.getCityId() != null) {
            long cityId = booking.getCityId();
            UUID bookingId = booking.getId();
            afterCommit(() -> rideQueue.remove(cityId, bookingId));
        }
        if (driverStates != null) {
            driverStates.took(booking);
        }
    }

    /**
     * The owner put a driver on this booking by hand. A different driver who was holding its
     * offer loses it: free again, and their open offer card is withdrawn.
     */
    public void ownerAssigned(TravelBooking booking, Long previousDriverId) {
        boolean wasOffered = booking.getDispatchState() == DispatchState.OFFERED;
        if (previousDriverId != null && !previousDriverId.equals(booking.getDriverId())) {
            if (driverStates != null) {
                driverStates.freed(previousDriverId, booking);
            }
            if (wasOffered) {
                withdrawAfterCommit(previousDriverId, booking.getId());
            }
        }
        markMatched(booking);
    }

    /**
     * The traveller cancelled a City ride: it leaves the queue, and a driver holding its offer is
     * told it is gone. (Their state is freed by the cancel itself, as for any booking.)
     */
    public void cancelled(TravelBooking booking) {
        if (booking.getDispatchMode() != TravelBooking.DispatchMode.RIDE_NOW) {
            return;
        }
        if (booking.getCityId() != null) {
            long cityId = booking.getCityId();
            UUID bookingId = booking.getId();
            afterCommit(() -> rideQueue.remove(cityId, bookingId));
        }
        if (booking.getDispatchState() == DispatchState.OFFERED && booking.getDriverId() != null) {
            withdrawAfterCommit(booking.getDriverId(), booking.getId());
        }
    }

    /** The search part of {@link #markMatched}, for callers built without a DispatchService. */
    public static void settle(TravelBooking booking) {
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

    /** This driver declined or timed out on this ride; recorded once, kept for good. */
    public void recordRejection(UUID bookingId, Long driverId) {
        if (driverId == null || rejectionRepository.existsByBookingIdAndDriverId(bookingId, driverId)) {
            return;
        }
        BookingDriverRejection rejection = new BookingDriverRejection();
        rejection.setBookingId(bookingId);
        rejection.setDriverId(driverId);
        rejectionRepository.save(rejection);
    }

    /** Everyone who declined or timed out on this ride, from the record. */
    public Set<Long> rejectedDrivers(UUID bookingId) {
        return new LinkedHashSet<>(rejectionRepository.findDriverIdsByBookingId(bookingId));
    }

    /** The queue's view of a ride. Its search started when the booking says; a missing start counts as now. */
    public RideTicket ticket(TravelBooking b) {
        LocalDateTime started = b.getDispatchStartedAt() != null ? b.getDispatchStartedAt() : now();
        return new RideTicket(b.getId(), b.getCityId(), new GeoPoint(b.getFromLat(), b.getFromLon()),
                b.getVehicleType(), b.getTravelMembers() == null ? 1 : Math.max(1, b.getTravelMembers()),
                epochMs(started));
    }

    /** Booking times are Indian wall-clock times; the queues score by epoch milliseconds. */
    public static long epochMs(LocalDateTime istTime) {
        return istTime.atZone(ZONE).toInstant().toEpochMilli();
    }

    private void withdrawAfterCommit(long driverId, UUID bookingId) {
        dispatcher.dispatch("offer-push", () ->
                offerPublisher.publish(driverId, bookingId, DriverOfferPublisher.Event.WITHDRAWN));
    }

    private static void clearOffer(TravelBooking booking) {
        booking.setOfferExpiresAt(null);
        booking.setOfferDistanceKm(null);
    }

    /** Queue writes mirror committed rows; a failed one is logged and healed by the next rebuild. */
    private static void afterCommit(Runnable write) {
        Runnable safe = () -> {
            try {
                write.run();
            } catch (RuntimeException e) {
                log.warn("Ride queue update failed; the next rebuild restores it: {}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
