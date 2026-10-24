package com.travelplatform.service.dispatch;

import com.travelplatform.entity.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-memory ride queue: what a round reads instead of the bookings table. Waiting rides come
 * out oldest first, an offer moves a ride out of waiting until its deadline, and the declined set
 * only ever grows.
 */
class LocalRideQueueTest {

    private static final long CITY = 1L;
    private static final long T0 = 1_800_000_000_000L;

    private final AtomicLong now = new AtomicLong(T0);
    private final RideQueue queue = new LocalRideQueue(now::get);

    private static RideTicket ticket(long city, long startedMs) {
        return new RideTicket(UUID.randomUUID(), city, new GeoPoint(12.97, 77.59), VehicleType.CAR, 1, startedMs);
    }

    @Test
    @DisplayName("waiting rides come out oldest first, capped at the limit, one city at a time")
    void waitingReturnsOldestFirst() {
        RideTicket newer = ticket(CITY, T0 - 1_000);
        RideTicket oldest = ticket(CITY, T0 - 30_000);
        RideTicket middle = ticket(CITY, T0 - 10_000);
        RideTicket elsewhere = ticket(2L, T0 - 60_000);
        queue.enqueue(newer);
        queue.enqueue(oldest);
        queue.enqueue(middle);
        queue.enqueue(elsewhere);

        assertEquals(List.of(oldest, middle, newer), queue.waiting(CITY, 10));
        assertEquals(List.of(oldest, middle), queue.waiting(CITY, 2));
        assertEquals(List.of(elsewhere), queue.waiting(2L, 10));
    }

    @Test
    @DisplayName("an offered ride leaves waiting and joins the offers; remove takes it out of both")
    void markOfferedMovesFromWaitingToOffers() {
        RideTicket t = ticket(CITY, T0);
        queue.enqueue(t);

        queue.markOffered(CITY, t.bookingId(), 7L, T0 + 15_000);

        assertTrue(queue.waiting(CITY, 10).isEmpty());
        assertFalse(queue.isEmpty(CITY), "an open offer still counts as work for the city");
        queue.remove(CITY, t.bookingId());
        assertTrue(queue.isEmpty(CITY));
        assertEquals(Optional.empty(), queue.currentOffer(7L));
    }

    @Test
    @DisplayName("only offers past their deadline are expired; clearing one stops it coming back")
    void expiredOffersOnlyPastDeadline() {
        RideTicket early = ticket(CITY, T0);
        RideTicket late = ticket(CITY, T0);
        queue.enqueue(early);
        queue.enqueue(late);
        queue.markOffered(CITY, early.bookingId(), 1L, T0 + 15_000);
        queue.markOffered(CITY, late.bookingId(), 2L, T0 + 20_000);

        assertTrue(queue.expiredOffers(CITY, T0 + 15_000).isEmpty(), "the deadline itself is still live");
        assertEquals(List.of(new OfferRef(early.bookingId(), 1L)), queue.expiredOffers(CITY, T0 + 15_001));
        assertEquals(2, queue.expiredOffers(CITY, T0 + 20_001).size());

        queue.clearOffer(CITY, early.bookingId());
        assertEquals(List.of(new OfferRef(late.bookingId(), 2L)), queue.expiredOffers(CITY, T0 + 20_001));
    }

    @Test
    @DisplayName("declined drivers accumulate per ride")
    void declinedAccumulates() {
        UUID ride = UUID.randomUUID();
        assertTrue(queue.declined(ride).isEmpty());

        queue.decline(ride, 1L);
        queue.decline(ride, 2L);
        queue.decline(ride, 1L);

        assertEquals(Set.of(1L, 2L), queue.declined(ride));
        assertTrue(queue.declined(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("a driver's current offer is found by driver, until its deadline or until cleared")
    void currentOfferByDriver() {
        RideTicket t = ticket(CITY, T0);
        queue.enqueue(t);
        queue.markOffered(CITY, t.bookingId(), 5L, T0 + 15_000);

        assertEquals(Optional.of(new OfferRef(t.bookingId(), 5L)), queue.currentOffer(5L));
        assertEquals(Optional.empty(), queue.currentOffer(6L));

        now.set(T0 + 15_000);
        assertEquals(Optional.empty(), queue.currentOffer(5L), "an offer at its deadline is gone");

        now.set(T0);
        queue.clearOffer(CITY, t.bookingId());
        assertEquals(Optional.empty(), queue.currentOffer(5L));
    }

    @Test
    @DisplayName("enqueueing a ride twice keeps one entry")
    void enqueueIsIdempotent() {
        RideTicket t = ticket(CITY, T0);
        queue.enqueue(t);
        queue.enqueue(t);

        assertEquals(1, queue.waiting(CITY, 10).size());
    }
}
