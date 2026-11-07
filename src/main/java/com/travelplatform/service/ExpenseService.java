package com.travelplatform.service;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusViews.TripView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.Vehicle;
import com.travelplatform.entity.VehicleExpense;
import com.travelplatform.entity.VehicleExpense.Category;
import com.travelplatform.entity.VehicleExpense.Kind;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.VehicleExpenseRepository;
import com.travelplatform.repository.VehicleRepository;
import com.travelplatform.service.bus.BusTripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An owner's running costs per car and per bus (V33), and the profit they leave: revenue minus
 * expenses, per vehicle for a period, and per bus trip for the bus "Today" screen. Every call acts
 * for the owner of the current request; another owner's vehicle, trip or expense is "not found".
 */
@Service
public class ExpenseService {

    static final int MAX_DAYS = 370;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");

    /** What the owner's form sends. */
    public record ExpenseForm(String vehicleKind, Long vehicleId, Long busTripId, LocalDate spentOn,
                              String category, BigDecimal amount, String note) {
    }

    public record ExpenseView(Long id, String vehicleKind, Long vehicleId, String vehicleLabel, Long busTripId,
                              LocalDate spentOn, String category, BigDecimal amount, String note) {
    }

    /** One car or bus over a period: what it brought in, what it cost, and what is left. */
    public record VehicleProfit(String vehicleKind, Long vehicleId, String label, BigDecimal revenue,
                                BigDecimal expenses, BigDecimal profit, Map<String, BigDecimal> byCategory) {
    }

    public record ProfitSummary(LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal expenses,
                                BigDecimal profit, List<VehicleProfit> vehicles) {
    }

    /** One of today's bus trips, as the bus dashboard shows it. */
    public record TodayTrip(TripView trip, int seatsOpen, LocalDateTime bookingClosesAt, boolean bookingOpen,
                            BigDecimal expenses, BigDecimal profit) {
    }

    public record BusToday(LocalDate date, int trips, int seatsTotal, int seatsSold, BigDecimal revenue,
                           BigDecimal expenses, BigDecimal profit, List<TodayTrip> list) {
    }

    private final VehicleExpenseRepository expenses;
    private final VehicleRepository cars;
    private final BusRepository buses;
    private final BusTripRepository trips;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusTripService busTrips;
    private final OwnerScope ownerScope = new OwnerScope();
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public ExpenseService(VehicleExpenseRepository expenses, VehicleRepository cars, BusRepository buses,
                          BusTripRepository trips, BusRouteStopRepository stops, BusRoutePointRepository points,
                          BusTripService busTrips) {
        this.expenses = expenses;
        this.cars = cars;
        this.buses = buses;
        this.trips = trips;
        this.stops = stops;
        this.points = points;
        this.busTrips = busTrips;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------------ the expense log

    @Transactional(readOnly = true)
    public List<ExpenseView> list(LocalDate from, LocalDate to, String kind) {
        LocalDate[] range = range(from, to);
        Kind only = kind == null || kind.isBlank() ? null : parseKind(kind);
        Long me = ownerScope.ownerId();
        Map<String, String> labels = labels(me);
        return expenses.findByOwnerIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(me, range[0], range[1]).stream()
                .filter(e -> only == null || e.getVehicleKind() == only)
                .map(e -> view(e, labels))
                .toList();
    }

    @Transactional
    public ExpenseView add(ExpenseForm f) {
        if (f == null) {
            throw new IllegalArgumentException("Give the expense details");
        }
        Long me = ownerScope.ownerId();
        Kind kind = parseKind(f.vehicleKind());
        if (f.vehicleId() == null) {
            throw new IllegalArgumentException(kind == Kind.CAR ? "Choose a car" : "Choose a bus");
        }
        String label = kind == Kind.CAR ? carLabel(myCar(me, f.vehicleId())) : busLabel(myBus(me, f.vehicleId()));

        Long tripId = null;
        if (f.busTripId() != null) {
            if (kind != Kind.BUS) {
                throw new IllegalArgumentException("Only a bus expense can be for a bus trip");
            }
            BusTrip trip = trips.findById(f.busTripId()).filter(t -> me.equals(t.getOwnerId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
            if (!trip.getBusId().equals(f.vehicleId())) {
                throw new IllegalArgumentException("That trip runs on a different bus");
            }
            tripId = trip.getId();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate day = f.spentOn() == null ? today : f.spentOn();
        if (day.isAfter(today)) {
            throw new IllegalArgumentException("The date can't be in the future");
        }
        if (day.isBefore(today.minusYears(2))) {
            throw new IllegalArgumentException("The date is more than two years ago");
        }

        Category category = parseCategory(f.category());
        BigDecimal amount = f.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Enter an amount above zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("An expense can be at most ₹10,00,000");
        }
        String note = f.note() == null ? null : f.note().trim();
        if (note != null && note.length() > 200) {
            throw new IllegalArgumentException("Keep the note under 200 characters");
        }

        VehicleExpense e = new VehicleExpense();
        e.setOwnerId(me);
        e.setVehicleKind(kind);
        e.setVehicleId(f.vehicleId());
        e.setBusTripId(tripId);
        e.setSpentOn(day);
        e.setCategory(category);
        e.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        e.setNote(note == null || note.isEmpty() ? null : note);
        VehicleExpense saved = expenses.save(e);
        return view(saved, Map.of(key(kind, f.vehicleId()), label));
    }

    @Transactional
    public void delete(Long id) {
        Long me = ownerScope.ownerId();
        VehicleExpense e = expenses.findById(id).filter(x -> me.equals(x.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        expenses.delete(e);
    }

    // ------------------------------------------------------------------ profit

    /** Revenue, expenses and profit per car and per bus (every one the owner has, even at zero). */
    @Transactional(readOnly = true)
    public ProfitSummary summary(LocalDate from, LocalDate to, String kind) {
        LocalDate[] range = range(from, to);
        Kind only = kind == null || kind.isBlank() ? null : parseKind(kind);
        Long me = ownerScope.ownerId();

        Map<String, VehicleRow> rows = new LinkedHashMap<>();
        if (only != Kind.BUS) {
            for (Vehicle v : cars.findByOwnerIdOrderByIdAsc(me)) {
                rows.put(key(Kind.CAR, v.getId()), new VehicleRow(Kind.CAR, v.getId(), carLabel(v)));
            }
            for (Object[] r : expenses.carRevenue(me, range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay())) {
                VehicleRow row = rows.get(key(Kind.CAR, (Long) r[0]));
                if (row != null) {
                    row.revenue = row.revenue.add(money(r[1]));
                }
            }
        }
        if (only != Kind.CAR) {
            for (Bus b : buses.findByOwnerIdOrderByIdAsc(me)) {
                rows.put(key(Kind.BUS, b.getId()), new VehicleRow(Kind.BUS, b.getId(), busLabel(b)));
            }
            for (Object[] r : expenses.busRevenue(me, range[0], range[1])) {
                VehicleRow row = rows.get(key(Kind.BUS, (Long) r[0]));
                if (row != null) {
                    row.revenue = row.revenue.add(money(r[1]));
                }
            }
        }
        for (VehicleExpense e : expenses.findByOwnerIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(me, range[0], range[1])) {
            VehicleRow row = rows.get(key(e.getVehicleKind(), e.getVehicleId()));
            if (row != null) {
                row.expenses = row.expenses.add(e.getAmount());
                row.byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
            }
        }

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        List<VehicleProfit> out = new ArrayList<>();
        for (VehicleRow r : rows.values()) {
            revenue = revenue.add(r.revenue);
            spent = spent.add(r.expenses);
            Map<String, BigDecimal> cats = new LinkedHashMap<>();
            r.byCategory.forEach((c, v) -> cats.put(c.name(), v));
            out.add(new VehicleProfit(r.kind.name(), r.id, r.label, r.revenue, r.expenses,
                    r.revenue.subtract(r.expenses), cats));
        }
        return new ProfitSummary(range[0], range[1], revenue, spent, revenue.subtract(spent), out);
    }

    /**
     * A day's bus trips for the bus dashboard: seats sold and open, when booking closes (the last
     * boarding point's time), the fares collected, what was spent on that trip and the profit.
     */
    @Transactional(readOnly = true)
    public BusToday busDay(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        Long me = ownerScope.ownerId();
        List<TripView> list = busTrips.list(day, day);
        if (list.isEmpty()) {
            return new BusToday(day, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        Map<Long, LocalDateTime> closes = closingTimes(list);
        Map<Long, BigDecimal> spentByTrip = new HashMap<>();
        for (VehicleExpense e : expenses.findByOwnerIdAndBusTripIdIn(me, list.stream().map(TripView::id).toList())) {
            spentByTrip.merge(e.getBusTripId(), e.getAmount(), BigDecimal::add);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int seatsTotal = 0;
        int seatsSold = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        List<TodayTrip> out = new ArrayList<>();
        for (TripView t : list) {
            BigDecimal collected = t.amountCollected() == null ? BigDecimal.ZERO : t.amountCollected();
            BigDecimal tripSpent = spentByTrip.getOrDefault(t.id(), BigDecimal.ZERO);
            LocalDateTime closesAt = closes.getOrDefault(t.id(), t.departureAt());
            boolean open = "SCHEDULED".equals(t.status()) && closesAt != null && closesAt.isAfter(now)
                    && t.seatsSold() < t.seatsTotal();
            out.add(new TodayTrip(t, Math.max(0, t.seatsTotal() - t.seatsSold()), closesAt, open, tripSpent,
                    collected.subtract(tripSpent)));
            if (!"CANCELLED".equals(t.status())) {
                seatsTotal += t.seatsTotal();
                seatsSold += t.seatsSold();
            }
            revenue = revenue.add(collected);
            spent = spent.add(tripSpent);
        }
        return new BusToday(day, out.size(), seatsTotal, seatsSold, revenue, spent, revenue.subtract(spent), out);
    }

    /** Per trip: when its last boarding point is passed, after which no seat can be sold. */
    private Map<Long, LocalDateTime> closingTimes(List<TripView> list) {
        Set<Long> routeIds = list.stream().map(TripView::routeId).collect(Collectors.toSet());
        List<BusRouteStop> routeStops = stops.findByRouteIdInOrderByRouteIdAscSeqAsc(routeIds);
        Map<Long, Long> routeOfStop = routeStops.stream().collect(Collectors.toMap(BusRouteStop::getId, BusRouteStop::getRouteId));
        Map<Long, List<BusRoutePoint>> boardingByRoute = new HashMap<>();
        for (BusRoutePoint p : points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(routeOfStop.keySet())) {
            if (p.getKind() != null && p.getKind().boards()) {
                boardingByRoute.computeIfAbsent(routeOfStop.get(p.getStopId()), k -> new ArrayList<>()).add(p);
            }
        }
        Map<Long, LocalDateTime> out = new HashMap<>();
        for (TripView t : list) {
            List<BusRoutePoint> boarding = boardingByRoute.get(t.routeId());
            if (boarding == null || boarding.isEmpty()) {
                continue;
            }
            LocalDateTime last = null;
            for (BusRoutePoint p : boarding) {
                LocalDateTime at = p.at(t.travelDate());
                if (last == null || at.isAfter(last)) {
                    last = at;
                }
            }
            out.put(t.id(), last);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static final class VehicleRow {
        final Kind kind;
        final Long id;
        final String label;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        final Map<Category, BigDecimal> byCategory = new EnumMap<>(Category.class);

        VehicleRow(Kind kind, Long id, String label) {
            this.kind = kind;
            this.id = id;
            this.label = label;
        }
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from == null ? today.withDayOfMonth(1) : from;
        LocalDate end = to == null ? today : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end date is before the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new IllegalArgumentException("Show at most " + MAX_DAYS + " days at a time");
        }
        return new LocalDate[]{start, end};
    }

    private Vehicle myCar(Long me, Long id) {
        return cars.findById(id).filter(v -> me.equals(v.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Car not found"));
    }

    private Bus myBus(Long me, Long id) {
        return buses.findById(id).filter(b -> me.equals(b.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
    }

    private Map<String, String> labels(Long me) {
        Map<String, String> out = new HashMap<>();
        cars.findByOwnerIdOrderByIdAsc(me).forEach(v -> out.put(key(Kind.CAR, v.getId()), carLabel(v)));
        buses.findByOwnerIdOrderByIdAsc(me).forEach(b -> out.put(key(Kind.BUS, b.getId()), busLabel(b)));
        return out;
    }

    private static ExpenseView view(VehicleExpense e, Map<String, String> labels) {
        return new ExpenseView(e.getId(), e.getVehicleKind().name(), e.getVehicleId(),
                labels.getOrDefault(key(e.getVehicleKind(), e.getVehicleId()), "Removed vehicle"),
                e.getBusTripId(), e.getSpentOn(), e.getCategory().name(), e.getAmount(), e.getNote());
    }

    private static String key(Kind kind, Long id) {
        return kind.name() + ':' + id;
    }

    private static String carLabel(Vehicle v) {
        return v.getModel() == null || v.getModel().isBlank()
                ? v.getRegistrationNumber() : v.getRegistrationNumber() + " · " + v.getModel();
    }

    private static String busLabel(Bus b) {
        return b.getName() + " · " + b.getRegistrationNumber();
    }

    private static BigDecimal money(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal d ? d : new BigDecimal(o.toString());
    }

    static Kind parseKind(String s) {
        try {
            return Kind.valueOf(s == null ? "" : s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Choose car or bus");
        }
    }

    static Category parseCategory(String s) {
        try {
            return Category.valueOf(s == null ? "" : s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Choose what the money was spent on");
        }
    }
}
