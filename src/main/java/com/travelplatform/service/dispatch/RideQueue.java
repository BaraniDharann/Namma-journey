package com.travelplatform.service.dispatch;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The City rides a round works on: per city, the rides waiting for a driver (oldest first) and the
 * open offers by deadline (the timeout queue), plus each ride's declined drivers and each driver's
 * current offer. A working copy, like {@link DriverStateStore}: the bookings table stays the truth,
 * and a city's queue is rebuilt from it when a server takes the city over and finds it empty.
 */
public interface RideQueue {

    /** Puts the ride in its city's waiting set. Enqueueing a ride already there changes nothing. */
    void enqueue(RideTicket ticket);

    /** Up to {@code limit} waiting rides in this city, oldest search first. */
    List<RideTicket> waiting(long cityId, int limit);

    /** The ride leaves for good (matched, cancelled, handed to the owner): waiting, offers and its data. */
    void remove(long cityId, UUID bookingId);

    /** The ride was offered to this driver until the deadline: out of waiting, into the offers. */
    void markOffered(long cityId, UUID bookingId, long driverId, long deadlineEpochMs);

    /** Offers whose deadline is before {@code nowEpochMs}. */
    List<OfferRef> expiredOffers(long cityId, long nowEpochMs);

    /** Drops the ride's open offer, if any. The ride is not put back in waiting; the caller decides. */
    void clearOffer(long cityId, UUID bookingId);

    /** This driver declined or timed out on this ride: never offer it to them again. */
    void decline(UUID bookingId, long driverId);

    Set<Long> declined(UUID bookingId);

    /** The offer this driver holds right now, if any. What the driver app polls. */
    Optional<OfferRef> currentOffer(long driverId);

    /** No ride waiting and no offer open in this city: the sign its queue may have been lost. */
    boolean isEmpty(long cityId);
}
