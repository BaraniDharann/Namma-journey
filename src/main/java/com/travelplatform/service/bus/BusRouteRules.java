package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusForms.FareForm;
import com.travelplatform.dto.bus.BusForms.PointForm;
import com.travelplatform.dto.bus.BusForms.RouteForm;
import com.travelplatform.dto.bus.BusForms.Slab;
import com.travelplatform.dto.bus.BusForms.StopForm;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusSeatClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks a route the owner sends, before anything is stored, and turns it into a {@link Plan}.
 *
 * <p>A route has 2 to 30 stops at distinct towns, numbered 0, 1, 2... in travel order. Every stop has
 * at least one point; the first stop only boards and needs a boarding point, the last only drops and
 * needs a dropping point. Each stop's times come after the previous stop's. Fares go from a stop to
 * a later one, per seat class. Whether the bus and towns exist is the service's job.
 */
public final class BusRouteRules {

    static final int MAX_STOPS = 30;
    static final int MAX_POINTS_PER_STOP = 20;
    static final BigDecimal MAX_FARE = new BigDecimal("50000");

    private BusRouteRules() {
    }

    public record Plan(Long busId, String name, short runDays, LocalDate activeFrom, LocalDate activeTo,
                       int maxSeatsPerBooking, List<Slab> cancellationPolicy, List<StopPlan> stops,
                       List<FarePlan> fares) {

        public List<Long> placeIds() {
            return stops.stream().map(StopPlan::placeId).toList();
        }
    }

    public record StopPlan(int seq, Long placeId, List<PointPlan> points) {
    }

    public record PointPlan(Long id, BusRoutePoint.Kind kind, String name, String address, String landmark,
                            Double lat, Double lng, int dayOffset, LocalTime time) {

        /** Minutes from midnight of the travel date. */
        int minuteOfTrip() {
            return dayOffset * 24 * 60 + time.getHour() * 60 + time.getMinute();
        }
    }

    public record FarePlan(int fromSeq, int toSeq, BusSeatClass seatClass, BigDecimal fare) {
    }

    public static Plan validate(RouteForm f) {
        if (f == null) {
            throw new IllegalArgumentException("Give the route's details");
        }
        if (f.busId() == null) {
            throw new IllegalArgumentException("Choose the bus that runs this route");
        }
        String name = text(f.name(), 120, "Route name");
        if (name == null) {
            throw new IllegalArgumentException("Give the route a name, e.g. Chennai - Madurai Night");
        }
        short runDays = runDaysMask(f.runDays());
        if (f.activeFrom() == null) {
            throw new IllegalArgumentException("Choose the date the route starts running");
        }
        if (f.activeTo() != null && f.activeTo().isBefore(f.activeFrom())) {
            throw new IllegalArgumentException("The end date is before the start date");
        }
        int maxSeats = f.maxSeatsPerBooking() == null ? 6 : f.maxSeatsPerBooking();
        if (maxSeats < 1 || maxSeats > 10) {
            throw new IllegalArgumentException("Seats per booking must be between 1 and 10");
        }
        List<Slab> policy = BusCancellationPolicy.normalize(f.cancellationPolicy());
        List<StopPlan> stops = stops(f.stops());
        List<FarePlan> fares = fares(f.fares(), stops.size());
        return new Plan(f.busId(), name, runDays, f.activeFrom(), f.activeTo(), maxSeats, policy, stops, fares);
    }

    /** [1, 3, 5] (Mon, Wed, Fri) → bits 0, 2, 4. */
    public static short runDaysMask(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("Choose the days the bus runs");
        }
        int mask = 0;
        for (Integer d : days) {
            if (d == null || d < 1 || d > 7) {
                throw new IllegalArgumentException("Run days go from 1 (Monday) to 7 (Sunday)");
            }
            mask |= 1 << (d - 1);
        }
        return (short) mask;
    }

    public static List<Integer> runDaysList(short mask) {
        List<Integer> days = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            if ((mask & (1 << (d - 1))) != 0) {
                days.add(d);
            }
        }
        return days;
    }

    /** "21:30" (or "21:30:00") → 21:30. */
    public static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim()).withSecond(0).withNano(0);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String formatTime(LocalTime t) {
        return t == null ? null : String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    private static List<StopPlan> stops(List<StopForm> forms) {
        if (forms == null || forms.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two stops");
        }
        if (forms.size() > MAX_STOPS) {
            throw new IllegalArgumentException("A route can have at most " + MAX_STOPS + " stops");
        }
        List<StopForm> ordered = new ArrayList<>(forms);
        if (ordered.stream().anyMatch(s -> s == null)) {
            throw new IllegalArgumentException("A stop is empty");
        }
        boolean numbered = ordered.stream().allMatch(s -> s.seq() != null);
        if (numbered) {
            ordered.sort(Comparator.comparing(StopForm::seq));
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i).seq() != i) {
                    throw new IllegalArgumentException("Stops must be numbered 0, 1, 2... in travel order");
                }
            }
        }

        Set<Long> places = new HashSet<>();
        List<StopPlan> stops = new ArrayList<>();
        int last = ordered.size() - 1;
        for (int seq = 0; seq <= last; seq++) {
            StopForm s = ordered.get(seq);
            String stopName = "Stop " + (seq + 1);
            if (s.placeId() == null) {
                throw new IllegalArgumentException(stopName + ": choose a town");
            }
            if (!places.add(s.placeId())) {
                throw new IllegalArgumentException("A town can appear only once on a route");
            }
            if (s.points() == null || s.points().isEmpty()) {
                throw new IllegalArgumentException(stopName + ": add at least one boarding or dropping point");
            }
            if (s.points().size() > MAX_POINTS_PER_STOP) {
                throw new IllegalArgumentException(stopName + ": at most " + MAX_POINTS_PER_STOP + " points");
            }
            List<PointPlan> points = new ArrayList<>();
            for (PointForm p : s.points()) {
                points.add(point(p, stopName));
            }
            if (seq == 0) {
                if (points.stream().anyMatch(p -> !p.kind().boards())) {
                    throw new IllegalArgumentException("The first stop can only have boarding points");
                }
            } else if (seq == last) {
                if (points.stream().anyMatch(p -> !p.kind().drops())) {
                    throw new IllegalArgumentException("The last stop can only have dropping points");
                }
            }
            if (!stops.isEmpty()) {
                int previousLatest = stops.get(stops.size() - 1).points().stream()
                        .mapToInt(PointPlan::minuteOfTrip).max().orElse(0);
                int earliest = points.stream().mapToInt(PointPlan::minuteOfTrip).min().orElse(0);
                if (earliest <= previousLatest) {
                    throw new IllegalArgumentException(stopName + "'s times must be after stop " + seq
                            + "'s times (use the day number for the next day)");
                }
            }
            stops.add(new StopPlan(seq, s.placeId(), points));
        }
        return stops;
    }

    private static PointPlan point(PointForm p, String stopName) {
        if (p == null) {
            throw new IllegalArgumentException(stopName + ": a point is empty");
        }
        BusRoutePoint.Kind kind;
        try {
            kind = BusRoutePoint.Kind.valueOf(String.valueOf(p.kind()).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(stopName + ": a point must be boarding, dropping or both");
        }
        String name = text(p.name(), 120, "Point name");
        if (name == null) {
            throw new IllegalArgumentException(stopName + ": give each point a name");
        }
        LocalTime time = parseTime(p.time());
        if (time == null) {
            throw new IllegalArgumentException(stopName + ", " + name + ": give the time as HH:mm");
        }
        int dayOffset = p.dayOffset() == null ? 0 : p.dayOffset();
        if (dayOffset < 0 || dayOffset > 3) {
            throw new IllegalArgumentException(stopName + ", " + name + ": the day must be 0 to 3 days after departure");
        }
        if (p.lat() != null && (p.lat() < -90 || p.lat() > 90) || p.lng() != null && (p.lng() < -180 || p.lng() > 180)) {
            throw new IllegalArgumentException(stopName + ", " + name + ": the map location is not valid");
        }
        return new PointPlan(p.id(), kind, name, text(p.address(), 300, "Address"), text(p.landmark(), 150, "Landmark"),
                p.lat(), p.lng(), dayOffset, time);
    }

    private static List<FarePlan> fares(List<FareForm> forms, int stopCount) {
        List<FarePlan> fares = new ArrayList<>();
        if (forms == null) {
            return fares;
        }
        Set<String> keys = new HashSet<>();
        for (FareForm f : forms) {
            if (f == null || f.fromSeq() == null || f.toSeq() == null) {
                throw new IllegalArgumentException("Each fare needs a from stop and a to stop");
            }
            if (f.fromSeq() < 0 || f.toSeq() >= stopCount || f.fromSeq() >= f.toSeq()) {
                throw new IllegalArgumentException("A fare must go from a stop to a later stop on the route");
            }
            BusSeatClass seatClass = BusSeatClass.parse(f.seatClass());
            if (seatClass == null) {
                throw new IllegalArgumentException("Unknown seat class " + f.seatClass());
            }
            if (f.fare() == null || f.fare().signum() <= 0 || f.fare().compareTo(MAX_FARE) > 0) {
                throw new IllegalArgumentException("Fares must be more than ₹0 and at most ₹" + MAX_FARE.toPlainString());
            }
            if (!keys.add(f.fromSeq() + "|" + f.toSeq() + "|" + seatClass)) {
                throw new IllegalArgumentException("Two fares are given for the same stops and seat class");
            }
            fares.add(new FarePlan(f.fromSeq(), f.toSeq(), seatClass, f.fare().setScale(2, RoundingMode.HALF_UP)));
        }
        return fares;
    }

    private static String text(String value, int max, String what) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        if (t.length() > max) {
            throw new IllegalArgumentException(what + " is too long (at most " + max + " characters)");
        }
        return t.isEmpty() ? null : t;
    }
}
