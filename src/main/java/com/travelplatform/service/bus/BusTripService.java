package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusForms.CrewForm;
import com.travelplatform.dto.bus.BusViews.AllocationView;
import com.travelplatform.dto.bus.BusViews.SeatView;
import com.travelplatform.dto.bus.BusViews.TripDetail;
import com.travelplatform.dto.bus.BusViews.TripSeatView;
import com.travelplatform.dto.bus.BusViews.TripStopView;
import com.travelplatform.dto.bus.BusViews.TripView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusPlace;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusTicket;
import com.travelplatform.entity.BusTicketPassenger;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusSeatAllocationRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTicketPassengerRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.repository.BusTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A travels owner's bus trips (V30): the trips list, a trip's seat chart with its passengers,
 * blocking seats, the crew, the passenger manifest and cancelling a trip. Every call acts for the
 * owner of the current request; another owner's trip is "not found".
 */
@Service
public class BusTripService {

    private static final Logger log = LoggerFactory.getLogger(BusTripService.class);

    /** Tickets whose passengers are travelling (counted as sold, listed on the manifest). */
    static final Set<BusTicket.Status> SOLD = Set.of(BusTicket.Status.CONFIRMED,
            BusTicket.Status.PARTIALLY_CANCELLED, BusTicket.Status.COMPLETED);
    /** Tickets an owner's trip cancellation turns into SERVICE_CANCELLED. */
    static final Set<BusTicket.Status> LIVE = Set.of(BusTicket.Status.PENDING, BusTicket.Status.CONFIRMED,
            BusTicket.Status.PARTIALLY_CANCELLED);
    private static final Set<BusSeatAllocation.Status> ACTIVE = Set.of(BusSeatAllocation.Status.HELD,
            BusSeatAllocation.Status.CONFIRMED, BusSeatAllocation.Status.BLOCKED);
    private static final Pattern PHONE = Pattern.compile("^[6-9][0-9]{9}$");
    static final int MAX_LIST_DAYS = 92;

    private final BusTripRepository trips;
    private final BusRouteRepository routes;
    private final BusRepository buses;
    private final BusSeatRepository seats;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusPlaceRepository places;
    private final BusSeatAllocationRepository allocations;
    private final BusTicketRepository tickets;
    private final BusTicketPassengerRepository passengers;
    private final OwnerScope ownerScope = new OwnerScope();
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusTripService(BusTripRepository trips, BusRouteRepository routes, BusRepository buses,
                          BusSeatRepository seats, BusRouteStopRepository stops, BusRoutePointRepository points,
                          BusPlaceRepository places, BusSeatAllocationRepository allocations,
                          BusTicketRepository tickets, BusTicketPassengerRepository passengers) {
        this.trips = trips;
        this.routes = routes;
        this.buses = buses;
        this.seats = seats;
        this.stops = stops;
        this.points = points;
        this.places = places;
        this.allocations = allocations;
        this.tickets = tickets;
        this.passengers = passengers;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** My trips travelling from {@code from} to {@code to} (default: today and the next 14 days), earliest first. */
    @Transactional(readOnly = true)
    public List<TripView> list(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(clock) : from;
        LocalDate end = to == null ? start.plusDays(14) : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("The end date is before the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_LIST_DAYS) {
            throw new IllegalArgumentException("Show at most " + MAX_LIST_DAYS + " days at a time");
        }
        List<BusTrip> found = trips.findByOwnerIdAndTravelDateBetweenOrderByDepartureAtAscIdAsc(ownerScope.ownerId(), start, end);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, BusRoute> routeById = byId(routes.findAllById(ids(found, BusTrip::getRouteId)), BusRoute::getId);
        Map<Long, Bus> busById = byId(buses.findAllById(ids(found, BusTrip::getBusId)), Bus::getId);
        Map<Long, Integer> seatCount = new HashMap<>();
        for (Object[] row : seats.countByBusIds(busById.keySet())) {
            seatCount.put((Long) row[0], ((Number) row[1]).intValue());
        }
        Map<Long, Object[]> sold = new HashMap<>();
        for (Object[] row : passengers.soldPerTrip(found.stream().map(BusTrip::getId).toList(), SOLD)) {
            sold.put((Long) row[0], row);
        }
        return found.stream().map(t -> listItem(t, routeById.get(t.getRouteId()), busById.get(t.getBusId()),
                seatCount.getOrDefault(t.getBusId(), 0), sold.get(t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TripDetail detail(Long id) {
        return detail(mine(id));
    }

    @Transactional
    public TripDetail setCrew(Long id, CrewForm form) {
        BusTrip t = mine(id);
        if (t.getStatus() == BusTrip.Status.CANCELLED || t.getStatus() == BusTrip.Status.COMPLETED) {
            throw new ConflictException("This trip is " + t.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (form == null) {
            throw new IllegalArgumentException("Give the crew's details");
        }
        t.setDriverName(name(form.driverName(), "Driver"));
        t.setDriverPhone(phone(form.driverPhone(), "Driver"));
        t.setConductorName(name(form.conductorName(), "Conductor"));
        t.setConductorPhone(phone(form.conductorPhone(), "Conductor"));
        return detail(trips.save(t));
    }

    /** Holds seats back from sale over the whole route (e.g. sold at the counter). */
    @Transactional
    public TripDetail block(Long id, List<String> labels) {
        BusTrip t = scheduled(id);
        Map<String, BusSeat> layout = seatsByKey(t.getBusId());
        List<BusSeat> wanted = resolve(layout, labels);
        int lastSeq = stops.findByRouteIdOrderBySeqAsc(t.getRouteId()).size() - 1;
        allocations.expireStaleHolds(t.getId(), LocalDateTime.now(clock));
        Set<String> taken = allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(t.getId(), ACTIVE).stream()
                .map(a -> a.getSeatLabel().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        for (BusSeat s : wanted) {
            if (taken.contains(s.getLabel().toUpperCase(Locale.ROOT))) {
                throw new ConflictException("Seat " + s.getLabel() + " is already booked or blocked");
            }
        }
        List<BusSeatAllocation> rows = new ArrayList<>();
        for (BusSeat s : wanted) {
            BusSeatAllocation a = new BusSeatAllocation();
            a.setTripId(t.getId());
            a.setOwnerId(t.getOwnerId());
            a.setSeatLabel(s.getLabel());
            a.setFromSeq(0);
            a.setToSeq(lastSeq);
            a.setStatus(BusSeatAllocation.Status.BLOCKED);
            rows.add(a);
        }
        // A customer holding the same seat at this instant: the exclusion constraint refuses one of
        // the two, and the handler answers 409.
        allocations.saveAllAndFlush(rows);
        return detail(t);
    }

    @Transactional
    public TripDetail unblock(Long id, List<String> labels) {
        BusTrip t = scheduled(id);
        Set<String> keys = resolve(seatsByKey(t.getBusId()), labels).stream()
                .map(s -> s.getLabel().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        for (BusSeatAllocation a : allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(t.getId(),
                Set.of(BusSeatAllocation.Status.BLOCKED))) {
            if (keys.contains(a.getSeatLabel().toUpperCase(Locale.ROOT))) {
                a.setStatus(BusSeatAllocation.Status.CANCELLED);
                allocations.save(a);
            }
        }
        return detail(t);
    }

    /**
     * Cancels the trip: every live ticket becomes SERVICE_CANCELLED and every seat is released.
     * Refunds and messages to passengers follow in {@link #refundAndNotifyCancelledTrip}.
     */
    @Transactional
    public TripDetail cancel(Long id, String reason) {
        BusTrip t = mine(id);
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 500) {
            throw new IllegalArgumentException("Give a reason for cancelling (up to 500 characters); passengers will see it");
        }
        if (t.getStatus() != BusTrip.Status.SCHEDULED) {
            throw new ConflictException("Only a trip that has not departed can be cancelled");
        }
        t.setStatus(BusTrip.Status.CANCELLED);
        t.setCancelReason(why);
        t = trips.saveAndFlush(t);
        allocations.cancelLive(t.getId());
        List<BusTicket> affected = tickets.findByTripIdAndStatusIn(t.getId(), LIVE);
        for (BusTicket ticket : affected) {
            ticket.setStatus(BusTicket.Status.SERVICE_CANCELLED);
        }
        tickets.saveAll(affected);
        refundAndNotifyCancelledTrip(t, affected);
        return detail(t);
    }

    /**
     * Hook for the owner cancelling a trip: a full refund (fare + GST) for each paid ticket and a
     * message to every passenger. Filled in by part 3 of the bus module (after booking); until then
     * the tickets are only marked SERVICE_CANCELLED.
     */
    protected void refundAndNotifyCancelledTrip(BusTrip trip, List<BusTicket> cancelledTickets) {
        if (!cancelledTickets.isEmpty()) {
            log.warn("Bus trip {} cancelled with {} ticket(s); refunds and messages are not automated yet",
                    trip.getId(), cancelledTickets.size());
        }
    }

    /** Origin-destination passenger list for the permit inspector and the crew, as CSV. */
    @Transactional(readOnly = true)
    public String manifestCsv(Long id) {
        BusTrip t = mine(id);
        Bus bus = buses.findById(t.getBusId()).orElse(null);
        TripContext ctx = context(t);
        StringBuilder csv = new StringBuilder();
        csv.append("Bus,").append(csv(bus == null ? "" : bus.getRegistrationNumber()))
                .append(",Date,").append(t.getTravelDate()).append('\n');
        csv.append("Seat,Name,Age,Gender,Phone,From,To,Boarding point,Boarding time,Dropping point,Ticket\n");
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        for (BusTicket ticket : ctx.tickets.values().stream()
                .filter(x -> SOLD.contains(x.getStatus()))
                .sorted(Comparator.comparing(BusTicket::getFromSeq).thenComparing(BusTicket::getTicketNo)).toList()) {
            BusRoutePoint board = ctx.points.get(ticket.getBoardingPointId());
            BusRoutePoint drop = ctx.points.get(ticket.getDroppingPointId());
            for (BusTicketPassenger p : ctx.passengersByTicket.getOrDefault(ticket.getId(), List.of())) {
                if (p.getStatus() != BusTicketPassenger.Status.CONFIRMED) {
                    continue;
                }
                csv.append(csv(p.getSeatLabel())).append(',')
                        .append(csv(p.getName())).append(',')
                        .append(p.getAge()).append(',')
                        .append(p.getGender()).append(',')
                        .append(csv(ticket.getContactPhone())).append(',')
                        .append(csv(ctx.placeName(ticket.getFromSeq()))).append(',')
                        .append(csv(ctx.placeName(ticket.getToSeq()))).append(',')
                        .append(csv(board == null ? "" : board.getName())).append(',')
                        .append(board == null ? "" : board.at(t.getTravelDate()).format(hhmm)).append(',')
                        .append(csv(drop == null ? "" : drop.getName())).append(',')
                        .append(csv(ticket.getTicketNo())).append('\n');
            }
        }
        return csv.toString();
    }

    /** One of my trips, or "not found". */
    public BusTrip mine(Long id) {
        Long me = ownerScope.ownerId();
        return trips.findById(id).filter(t -> me.equals(t.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
    }

    private BusTrip scheduled(Long id) {
        BusTrip t = mine(id);
        if (t.getStatus() != BusTrip.Status.SCHEDULED) {
            throw new ConflictException("Seats can only be blocked or released before the trip departs");
        }
        return t;
    }

    private TripDetail detail(BusTrip t) {
        BusRoute route = routes.findById(t.getRouteId()).orElse(null);
        Bus bus = buses.findById(t.getBusId()).orElse(null);
        List<BusSeat> layout = seats.findByBusIdOrderByDeckAscRowNoAscColNoAsc(t.getBusId());
        TripContext ctx = context(t);

        List<BusSeatAllocation> active = allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(t.getId(), ACTIVE);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, List<AllocationView>> bySeat = new HashMap<>();
        int sold = 0;
        BigDecimal collected = BigDecimal.ZERO;
        for (BusSeatAllocation a : active) {
            if (a.getStatus() == BusSeatAllocation.Status.HELD && a.getExpiresAt() != null && !a.getExpiresAt().isAfter(now)) {
                continue;
            }
            BusTicket ticket = a.getTicketId() == null ? null : ctx.tickets.get(a.getTicketId());
            BusTicketPassenger p = ticket == null ? null : ctx.passengersByTicket.getOrDefault(ticket.getId(), List.of())
                    .stream().filter(x -> x.getSeatLabel().equalsIgnoreCase(a.getSeatLabel())
                            && x.getStatus() == BusTicketPassenger.Status.CONFIRMED)
                    .findFirst().orElse(null);
            BusRoutePoint board = ticket == null ? null : ctx.points.get(ticket.getBoardingPointId());
            BusRoutePoint drop = ticket == null ? null : ctx.points.get(ticket.getDroppingPointId());
            bySeat.computeIfAbsent(a.getSeatLabel().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(new AllocationView(
                    a.getFromSeq(), a.getToSeq(), a.getStatus().name(), a.getGender() == null ? null : a.getGender().name(),
                    ticket == null ? null : ticket.getTicketNo(), p == null ? null : p.getName(),
                    p == null ? null : p.getAge(), ticket == null ? null : ticket.getContactPhone(),
                    board == null ? null : board.getName(), drop == null ? null : drop.getName()));
            if (a.getStatus() == BusSeatAllocation.Status.CONFIRMED && p != null && ticket != null
                    && SOLD.contains(ticket.getStatus())) {
                sold++;
                collected = collected.add(p.getFare());
            }
        }
        List<TripSeatView> seatViews = layout.stream().map(s -> TripSeatView.of(SeatView.of(s),
                bySeat.getOrDefault(s.getLabel().toUpperCase(Locale.ROOT), List.of()))).toList();
        List<TripStopView> stopViews = ctx.stops.stream()
                .map(s -> new TripStopView(s.getSeq(), ctx.placeName(s.getSeq()))).toList();
        return new TripDetail(t.getId(), t.getRouteId(), route == null ? null : route.getName(), t.getBusId(),
                bus == null ? null : bus.getName(), bus == null ? null : bus.getRegistrationNumber(), t.getTravelDate(),
                t.getDepartureAt(), t.getArrivalAt(), t.getStatus().name(), layout.size(), sold,
                collected.setScale(2, java.math.RoundingMode.HALF_UP), t.getDriverName(), t.getDriverPhone(),
                t.getConductorName(), t.getConductorPhone(), t.getCancelReason(), stopViews, seatViews);
    }

    private static TripView listItem(BusTrip t, BusRoute route, Bus bus, int seatsTotal, Object[] sold) {
        int count = sold == null ? 0 : ((Number) sold[1]).intValue();
        BigDecimal amount = sold == null ? BigDecimal.ZERO : new BigDecimal(sold[2].toString());
        return new TripView(t.getId(), t.getRouteId(), route == null ? null : route.getName(), t.getBusId(),
                bus == null ? null : bus.getName(), bus == null ? null : bus.getRegistrationNumber(), t.getTravelDate(),
                t.getDepartureAt(), t.getArrivalAt(), t.getStatus().name(), seatsTotal, count,
                amount.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /** The route's stops and points and the trip's tickets and passengers, loaded once. */
    private record TripContext(List<BusRouteStop> stops, Map<Integer, String> placeBySeq,
                               Map<Long, BusRoutePoint> points, Map<UUID, BusTicket> tickets,
                               Map<UUID, List<BusTicketPassenger>> passengersByTicket) {

        String placeName(int seq) {
            return placeBySeq.getOrDefault(seq, "Stop " + (seq + 1));
        }
    }

    private TripContext context(BusTrip t) {
        List<BusRouteStop> routeStops = stops.findByRouteIdOrderBySeqAsc(t.getRouteId());
        Map<Long, String> placeNames = places.findAllById(routeStops.stream().map(BusRouteStop::getPlaceId).toList())
                .stream().collect(Collectors.toMap(BusPlace::getId, BusPlace::getName));
        Map<Integer, String> placeBySeq = new HashMap<>();
        routeStops.forEach(s -> placeBySeq.put(s.getSeq(), placeNames.get(s.getPlaceId())));
        Map<Long, BusRoutePoint> pointById = routeStops.isEmpty() ? Map.of()
                : byId(points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(
                        routeStops.stream().map(BusRouteStop::getId).toList()), BusRoutePoint::getId);
        Map<UUID, BusTicket> ticketById = byId(tickets.findByTripIdAndStatusIn(t.getId(),
                Set.of(BusTicket.Status.values())), BusTicket::getId);
        Map<UUID, List<BusTicketPassenger>> byTicket = ticketById.isEmpty() ? Map.of()
                : passengers.findByTicketIdIn(ticketById.keySet()).stream()
                        .collect(Collectors.groupingBy(BusTicketPassenger::getTicketId));
        return new TripContext(routeStops, placeBySeq, pointById, ticketById, byTicket);
    }

    private Map<String, BusSeat> seatsByKey(Long busId) {
        return seats.findByBusIdOrderByDeckAscRowNoAscColNoAsc(busId).stream()
                .collect(Collectors.toMap(s -> s.getLabel().toUpperCase(Locale.ROOT), Function.identity()));
    }

    private static List<BusSeat> resolve(Map<String, BusSeat> layout, List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one seat");
        }
        Set<BusSeat> found = new LinkedHashSet<>();
        for (String l : labels) {
            BusSeat s = l == null ? null : layout.get(l.trim().toUpperCase(Locale.ROOT));
            if (s == null) {
                throw new IllegalArgumentException("Seat " + l + " is not on this bus");
            }
            found.add(s);
        }
        return new ArrayList<>(found);
    }

    private static String name(String value, String who) {
        String t = value == null ? "" : value.trim();
        if (t.length() > 80) {
            throw new IllegalArgumentException(who + "'s name is too long");
        }
        return t.isEmpty() ? null : t;
    }

    /** "+91 98765 43210" → "9876543210"; blank → null. */
    static String phone(String value, String who) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[\\s-]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!PHONE.matcher(digits).matches()) {
            throw new IllegalArgumentException(who + "'s phone must be a 10-digit mobile number");
        }
        return digits;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        // A leading =, +, - or @ would run as a formula in a spreadsheet.
        String v = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static <T, K> Map<K, T> byId(Collection<T> rows, Function<T, K> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static <T> Set<Long> ids(Collection<T> rows, Function<T, Long> id) {
        return rows.stream().map(id).collect(Collectors.toSet());
    }
}
