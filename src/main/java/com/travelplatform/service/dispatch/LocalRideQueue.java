package com.travelplatform.service.dispatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.LongSupplier;

/**
 * The single-instance twin of {@link RedisRideQueue}. Sorted maps per city stand in for Redis's
 * sorted sets. Every method is synchronized: a round, an accept and a cancel can touch one ride at
 * once, and a single lock is plenty for the one instance this twin serves.
 */
public class LocalRideQueue implements RideQueue {

    /** A sorted-set member: ordered by score, then by ride so equal scores stay distinct. */
    private record Key(long score, UUID bookingId) {
    }

    private record Open(long cityId, long deadline, long driverId) {
    }

    private record Held(UUID bookingId, long deadline) {
    }

    private static final Comparator<Key> ORDER =
            Comparator.comparingLong(Key::score).thenComparing(Key::bookingId);

    private final Map<UUID, RideTicket> tickets = new HashMap<>();
    private final Map<Long, ConcurrentSkipListMap<Key, RideTicket>> waiting = new HashMap<>();
    private final Map<Long, ConcurrentSkipListMap<Key, Long>> offers = new HashMap<>();
    private final Map<UUID, Open> openOffer = new HashMap<>();
    private final Map<Long, Held> driverOffer = new HashMap<>();
    private final Map<UUID, Set<Long>> declined = new HashMap<>();
    private final LongSupplier nowMs;

    public LocalRideQueue() {
        this(System::currentTimeMillis);
    }

    /** @param nowMs the clock a driver's current offer is checked against; tests move it */
    public LocalRideQueue(LongSupplier nowMs) {
        this.nowMs = nowMs;
    }

    @Override
    public synchronized void enqueue(RideTicket t) {
        RideTicket old = tickets.put(t.bookingId(), t);
        if (old != null) {
            waitingOf(old.cityId()).remove(new Key(old.searchStartedEpochMs(), old.bookingId()));
        }
        waitingOf(t.cityId()).put(new Key(t.searchStartedEpochMs(), t.bookingId()), t);
    }

    @Override
    public synchronized List<RideTicket> waiting(long cityId, int limit) {
        List<RideTicket> out = new ArrayList<>();
        for (RideTicket t : waitingOf(cityId).values()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(t);
        }
        return out;
    }

    @Override
    public synchronized void remove(long cityId, UUID bookingId) {
        clearOffer(cityId, bookingId);
        RideTicket t = tickets.remove(bookingId);
        if (t != null) {
            waitingOf(t.cityId()).remove(new Key(t.searchStartedEpochMs(), bookingId));
        }
        declined.remove(bookingId);
    }

    @Override
    public synchronized void markOffered(long cityId, UUID bookingId, long driverId, long deadlineEpochMs) {
        clearOffer(cityId, bookingId);
        RideTicket t = tickets.get(bookingId);
        if (t != null) {
            waitingOf(t.cityId()).remove(new Key(t.searchStartedEpochMs(), bookingId));
        }
        offersOf(cityId).put(new Key(deadlineEpochMs, bookingId), driverId);
        openOffer.put(bookingId, new Open(cityId, deadlineEpochMs, driverId));
        driverOffer.put(driverId, new Held(bookingId, deadlineEpochMs));
    }

    @Override
    public synchronized List<OfferRef> expiredOffers(long cityId, long nowEpochMs) {
        List<OfferRef> out = new ArrayList<>();
        // Strictly before now: the smallest possible UUID makes every offer due exactly now "not yet".
        Key now = new Key(nowEpochMs, new UUID(Long.MIN_VALUE, Long.MIN_VALUE));
        for (Map.Entry<Key, Long> e : offersOf(cityId).headMap(now, false).entrySet()) {
            out.add(new OfferRef(e.getKey().bookingId(), e.getValue()));
        }
        return out;
    }

    @Override
    public synchronized void clearOffer(long cityId, UUID bookingId) {
        Open open = openOffer.remove(bookingId);
        if (open == null) {
            return;
        }
        offersOf(open.cityId()).remove(new Key(open.deadline(), bookingId));
        Held held = driverOffer.get(open.driverId());
        if (held != null && held.bookingId().equals(bookingId)) {
            driverOffer.remove(open.driverId());
        }
    }

    @Override
    public synchronized void decline(UUID bookingId, long driverId) {
        declined.computeIfAbsent(bookingId, k -> new HashSet<>()).add(driverId);
    }

    @Override
    public synchronized Set<Long> declined(UUID bookingId) {
        Set<Long> set = declined.get(bookingId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    @Override
    public synchronized Optional<OfferRef> currentOffer(long driverId) {
        Held held = driverOffer.get(driverId);
        if (held == null || held.deadline() <= nowMs.getAsLong()) {
            return Optional.empty();
        }
        return Optional.of(new OfferRef(held.bookingId(), driverId));
    }

    @Override
    public synchronized boolean isEmpty(long cityId) {
        return waitingOf(cityId).isEmpty() && offersOf(cityId).isEmpty();
    }

    private ConcurrentSkipListMap<Key, RideTicket> waitingOf(long cityId) {
        return waiting.computeIfAbsent(cityId, k -> new ConcurrentSkipListMap<>(ORDER));
    }

    private ConcurrentSkipListMap<Key, Long> offersOf(long cityId) {
        return offers.computeIfAbsent(cityId, k -> new ConcurrentSkipListMap<>(ORDER));
    }
}
