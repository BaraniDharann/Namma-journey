package com.travelplatform.service.dispatch;

import com.travelplatform.entity.Driver;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.TravelBookingRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds driver cards from Postgres for both store twins, on a cache miss and on reconcile.
 *
 * <p>BUSY means a booking that occupies the driver today and has not ended: CONFIRMED or STARTED,
 * and also a PENDING one already in their name that is not an offer (a Trip awaiting their accept
 * or the passenger's advance). {@code no_driver_overlap} counts those too, so offering such a
 * driver a City ride would only be refused at the database, round after round. A Trip next week
 * does not make anyone busy now. OFFERED means they hold an open Ride Now offer.
 */
@Component
public class DriverCardLoader {

    /** Ids per query: keeps the IN lists a size Postgres plans well. */
    private static final int CHUNK = 1000;

    private final DriverRepository drivers;
    private final TravelBookingRepository bookings;

    /** Ride dates are Indian calendar days, as everywhere else. */
    private final Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public DriverCardLoader(DriverRepository drivers, TravelBookingRepository bookings) {
        this.drivers = drivers;
        this.bookings = bookings;
    }

    public Map<Long, DriverCard> load(Collection<Long> ids) {
        Map<Long, DriverCard> cards = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return cards;
        }
        List<Long> all = new ArrayList<>(new HashSet<>(ids));
        LocalDate today = LocalDate.now(clock);
        for (int from = 0; from < all.size(); from += CHUNK) {
            List<Long> chunk = all.subList(from, Math.min(all.size(), from + CHUNK));
            Set<Long> busy = new HashSet<>(bookings.findBusyDriverIds(chunk, today));
            Set<Long> offered = new HashSet<>(bookings.findOfferedDriverIds(chunk));
            for (Driver d : drivers.findAllById(chunk)) {
                DriverState state = busy.contains(d.getId()) ? DriverState.BUSY
                        : offered.contains(d.getId()) ? DriverState.OFFERED : DriverState.IDLE;
                cards.put(d.getId(), profile(d, state));
            }
        }
        return cards;
    }

    /** The profile half of a card, with the given state. */
    static DriverCard profile(Driver d, DriverState state) {
        return new DriverCard(d.getId(), state, d.getVehicleType(), d.getVehicleSeats(), d.isServesCity(),
                d.getStatus() == Driver.Status.ACTIVE && d.isApproved());
    }
}
