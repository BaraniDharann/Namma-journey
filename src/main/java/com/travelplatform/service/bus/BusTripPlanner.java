package com.travelplatform.service.bus;

import com.travelplatform.entity.BusRoutePoint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Which dates a route runs on, and when a trip departs and arrives. Pure date maths for
 * {@link BusTripGenerator}.
 */
public final class BusTripPlanner {

    private BusTripPlanner() {
    }

    /** Monday is bit 0 ... Sunday bit 6. */
    public static boolean runsOn(short runDays, LocalDate date) {
        return (runDays & (1 << (date.getDayOfWeek().getValue() - 1))) != 0;
    }

    /**
     * The dates in {@code from..to} (both included) on which a trip is due: a run day, within the
     * route's active dates, and no later than the day the first of the bus's documents expires.
     * {@code documentsValidUntil} null means the bus does not have all its documents: no dates.
     */
    public static List<LocalDate> runningDates(short runDays, LocalDate activeFrom, LocalDate activeTo,
                                               LocalDate documentsValidUntil, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        if (documentsValidUntil == null) {
            return dates;
        }
        LocalDate start = activeFrom != null && activeFrom.isAfter(from) ? activeFrom : from;
        LocalDate end = to;
        if (activeTo != null && activeTo.isBefore(end)) {
            end = activeTo;
        }
        if (documentsValidUntil.isBefore(end)) {
            end = documentsValidUntil;
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (runsOn(runDays, d)) {
                dates.add(d);
            }
        }
        return dates;
    }

    /** The earliest point time at the first stop. */
    public static LocalDateTime departure(LocalDate travelDate, Collection<BusRoutePoint> firstStopPoints) {
        return firstStopPoints.stream().map(p -> p.at(travelDate)).min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("The first stop has no points"));
    }

    /** The latest point time at the last stop. */
    public static LocalDateTime arrival(LocalDate travelDate, Collection<BusRoutePoint> lastStopPoints) {
        return lastStopPoints.stream().map(p -> p.at(travelDate)).max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("The last stop has no points"));
    }
}
