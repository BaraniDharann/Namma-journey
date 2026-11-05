package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusForms.FareForm;
import com.travelplatform.dto.bus.BusForms.PointForm;
import com.travelplatform.dto.bus.BusForms.RouteForm;
import com.travelplatform.dto.bus.BusForms.Slab;
import com.travelplatform.dto.bus.BusForms.StopForm;
import com.travelplatform.entity.BusSeatClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Route checks: stops, points, times, fares, run days and cancellation slabs. */
class BusRouteRulesTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 10);

    private static PointForm point(String kind, String name, int day, String time) {
        return new PointForm(null, kind, name, null, null, null, null, day, time);
    }

    private static StopForm stop(int seq, long place, PointForm... points) {
        return new StopForm(seq, place, List.of(points));
    }

    /** Chennai (21:00, 21:30) → Trichy (02:00 next day) → Madurai (05:00 next day). */
    private static List<StopForm> threeStops() {
        return List.of(
                stop(0, 1, point("BOARDING", "Koyambedu", 0, "21:00"), point("BOARDING", "Guindy", 0, "21:30")),
                stop(1, 2, point("BOTH", "Trichy Central", 1, "02:00")),
                stop(2, 3, point("DROPPING", "Mattuthavani", 1, "05:00")));
    }

    private static RouteForm route(List<StopForm> stops, List<FareForm> fares, List<Slab> policy) {
        return new RouteForm(5L, " Chennai - Madurai ", List.of(1, 3, 7), FROM, null, null, policy, stops, fares);
    }

    private static String error(RouteForm f) {
        return assertThrows(IllegalArgumentException.class, () -> BusRouteRules.validate(f)).getMessage();
    }

    @Test
    @DisplayName("a valid route becomes a plan with run days as bits and the default slabs")
    void validRoute() {
        BusRouteRules.Plan plan = BusRouteRules.validate(route(threeStops(),
                List.of(new FareForm(0, 2, "lower_single", new BigDecimal("899.5"))), null));

        assertEquals("Chennai - Madurai", plan.name());
        assertEquals((short) 0b1000101, plan.runDays());
        assertEquals(List.of(1, 3, 7), BusRouteRules.runDaysList(plan.runDays()));
        assertEquals(6, plan.maxSeatsPerBooking());
        assertEquals(BusCancellationPolicy.DEFAULT, plan.cancellationPolicy());
        assertEquals(List.of(1L, 2L, 3L), plan.placeIds());
        assertEquals(LocalTime.of(21, 30), plan.stops().get(0).points().get(1).time());
        assertEquals(new BigDecimal("899.50"), plan.fares().get(0).fare());
        assertEquals(BusSeatClass.LOWER_SINGLE, plan.fares().get(0).seatClass());
    }

    @Test
    @DisplayName("stops: at least two, distinct towns, numbered 0, 1, 2...")
    void stops() {
        assertTrue(error(route(threeStops().subList(0, 1), null, null)).contains("at least two"));
        assertTrue(error(route(List.of(stop(0, 1, point("BOARDING", "A", 0, "21:00")),
                stop(1, 1, point("DROPPING", "B", 0, "23:00"))), null, null)).contains("only once"));
        assertTrue(error(route(List.of(stop(0, 1, point("BOARDING", "A", 0, "21:00")),
                stop(2, 2, point("DROPPING", "B", 0, "23:00"))), null, null)).contains("numbered"));
    }

    @Test
    @DisplayName("the first stop only boards, the last only drops, every stop has a point")
    void pointKinds() {
        assertTrue(error(route(List.of(stop(0, 1, point("DROPPING", "A", 0, "21:00")),
                stop(1, 2, point("DROPPING", "B", 0, "23:00"))), null, null)).contains("first stop"));
        assertTrue(error(route(List.of(stop(0, 1, point("BOARDING", "A", 0, "21:00")),
                stop(1, 2, point("BOARDING", "B", 0, "23:00"))), null, null)).contains("last stop"));
        assertTrue(error(route(List.of(stop(0, 1, point("BOARDING", "A", 0, "21:00")),
                new StopForm(1, 2L, List.of())), null, null)).contains("at least one"));
    }

    @Test
    @DisplayName("each stop's times come after the previous stop's, counting the day")
    void timesInOrder() {
        assertTrue(error(route(List.of(stop(0, 1, point("BOARDING", "A", 0, "21:00")),
                stop(1, 2, point("DROPPING", "B", 0, "02:00"))), null, null)).contains("after stop 1"));
        assertTrue(error(route(List.of(stop(0, 1, point("BOARDING", "A", 0, "21:00")),
                stop(1, 2, point("DROPPING", "B", 0, "9pm"))), null, null)).contains("HH:mm"));
    }

    @Test
    @DisplayName("fares go forward between existing stops, per seat class, once each")
    void fares() {
        assertTrue(error(route(threeStops(), List.of(new FareForm(2, 1, "SEATER", BigDecimal.TEN)), null))
                .contains("later stop"));
        assertTrue(error(route(threeStops(), List.of(new FareForm(0, 3, "SEATER", BigDecimal.TEN)), null))
                .contains("later stop"));
        assertTrue(error(route(threeStops(), List.of(new FareForm(0, 1, "FIRST", BigDecimal.TEN)), null))
                .contains("seat class"));
        assertTrue(error(route(threeStops(), List.of(new FareForm(0, 1, "SEATER", BigDecimal.ZERO)), null))
                .contains("more than"));
        assertTrue(error(route(threeStops(), List.of(new FareForm(0, 1, "SEATER", BigDecimal.TEN),
                new FareForm(0, 1, "seater", BigDecimal.ONE)), null)).contains("same stops"));
    }

    @Test
    @DisplayName("run days 1..7 required; end date not before start")
    void daysAndDates() {
        assertTrue(error(new RouteForm(5L, "R", List.of(), FROM, null, null, null, threeStops(), null)).contains("days"));
        assertTrue(error(new RouteForm(5L, "R", List.of(8), FROM, null, null, null, threeStops(), null)).contains("1 (Monday)"));
        assertTrue(error(new RouteForm(5L, "R", List.of(1), FROM, FROM.minusDays(1), null, null, threeStops(), null))
                .contains("before the start"));
        assertTrue(error(new RouteForm(5L, "R", List.of(1), FROM, null, 11, null, threeStops(), null)).contains("1 and 10"));
    }

    @Test
    @DisplayName("slabs are sorted, need a 0-hour slab, and charges never drop nearer departure")
    void cancellationSlabs() {
        assertEquals(List.of(new Slab(24, 20), new Slab(0, 100)),
                BusCancellationPolicy.normalize(List.of(new Slab(0, 100), new Slab(24, 20))));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BusCancellationPolicy.normalize(List.of(new Slab(24, 20)))).getMessage().contains("0 hours"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BusCancellationPolicy.normalize(List.of(new Slab(24, 50), new Slab(0, 10)))).getMessage().contains("drop"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BusCancellationPolicy.normalize(List.of(new Slab(0, 50), new Slab(0, 60)))).getMessage().contains("Two"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BusCancellationPolicy.normalize(List.of(new Slab(0, 150)))).getMessage().contains("100%"));
        String json = BusCancellationPolicy.toJson(BusCancellationPolicy.DEFAULT);
        assertEquals(BusCancellationPolicy.DEFAULT, BusCancellationPolicy.fromJson(json));
    }
}
