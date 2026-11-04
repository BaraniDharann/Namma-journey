package com.travelplatform.service.bus;

import com.travelplatform.config.OwnerScope;
import com.travelplatform.dto.bus.BusForms.RouteForm;
import com.travelplatform.dto.bus.BusViews.FareView;
import com.travelplatform.dto.bus.BusViews.PointView;
import com.travelplatform.dto.bus.BusViews.RouteView;
import com.travelplatform.dto.bus.BusViews.StopView;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusPlace;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRouteFare;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRouteFareRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusTicketRepository;
import com.travelplatform.service.bus.BusRouteRules.FarePlan;
import com.travelplatform.service.bus.BusRouteRules.Plan;
import com.travelplatform.service.bus.BusRouteRules.PointPlan;
import com.travelplatform.service.bus.BusRouteRules.StopPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A travels owner's bus routes (V30). A route is drafted, then published, which creates its trips
 * for the next 60 days; pausing stops new trips. Once published, its bus and stops are fixed, while
 * points, fares, run days, dates and slabs can still change (for bookings made after the change).
 * Points keep their ids when edited, because tickets point at them.
 */
@Service
public class BusRouteService {

    private final BusRouteRepository routes;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusRouteFareRepository fares;
    private final BusPlaceRepository places;
    private final BusRepository buses;
    private final BusTicketRepository tickets;
    private final BusService busService;
    private final BusTripGenerator generator;
    private final OwnerScope ownerScope = new OwnerScope();

    public BusRouteService(BusRouteRepository routes, BusRouteStopRepository stops, BusRoutePointRepository points,
                           BusRouteFareRepository fares, BusPlaceRepository places, BusRepository buses,
                           BusTicketRepository tickets, BusService busService, BusTripGenerator generator) {
        this.routes = routes;
        this.stops = stops;
        this.points = points;
        this.fares = fares;
        this.places = places;
        this.buses = buses;
        this.tickets = tickets;
        this.busService = busService;
        this.generator = generator;
    }

    @Transactional(readOnly = true)
    public List<RouteView> list() {
        return routes.findByOwnerIdOrderByIdDesc(ownerScope.ownerId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RouteView get(Long id) {
        return view(mine(id));
    }

    @Transactional
    public RouteView create(RouteForm form) {
        Plan plan = BusRouteRules.validate(form);
        Bus bus = busService.mine(plan.busId());
        requirePlaces(plan);
        BusRoute r = new BusRoute();
        r.setOwnerId(bus.getOwnerId());
        r.setBusId(bus.getId());
        applyFields(r, plan);
        r = routes.save(r);
        for (StopPlan s : plan.stops()) {
            BusRouteStop stop = newStop(r, s);
            for (PointPlan p : s.points()) {
                points.save(fill(new BusRoutePoint(), stop, p));
            }
        }
        saveFares(r, plan);
        return view(r);
    }

    @Transactional
    public RouteView update(Long id, RouteForm form) {
        BusRoute r = mine(id);
        Plan plan = BusRouteRules.validate(form);
        List<BusRouteStop> existing = stops.findByRouteIdOrderBySeqAsc(id);
        boolean sameStops = existing.stream().map(BusRouteStop::getPlaceId).toList().equals(plan.placeIds());
        if (r.getStatus() != BusRoute.Status.DRAFT) {
            if (!Objects.equals(r.getBusId(), plan.busId())) {
                throw new ConflictException("The bus of a published route cannot change. Create a new route instead.");
            }
            if (!sameStops) {
                throw new ConflictException("The stops of a published route cannot change. Create a new route instead.");
            }
        } else if (!Objects.equals(r.getBusId(), plan.busId())) {
            r.setBusId(busService.mine(plan.busId()).getId());
        }
        requirePlaces(plan);
        applyFields(r, plan);
        r = routes.save(r);

        if (sameStops) {
            List<BusRoutePoint> current = points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(
                    existing.stream().map(BusRouteStop::getId).toList());
            for (int i = 0; i < existing.size(); i++) {
                BusRouteStop stop = existing.get(i);
                List<BusRoutePoint> mine = current.stream().filter(p -> stop.getId().equals(p.getStopId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                replacePoints(stop, mine, plan.stops().get(i).points());
            }
        } else {
            stops.deleteByRouteId(id);
            for (StopPlan s : plan.stops()) {
                BusRouteStop stop = newStop(r, s);
                for (PointPlan p : s.points()) {
                    points.save(fill(new BusRoutePoint(), stop, p));
                }
            }
        }
        fares.deleteByRouteId(id);
        saveFares(r, plan);
        return view(routes.findById(id).orElseThrow());
    }

    /** Opens the route for sale and creates its trips. Needs an approved bus and at least one fare. */
    @Transactional
    public RouteView publish(Long id) {
        BusRoute r = mine(id);
        Bus bus = buses.findById(r.getBusId()).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        if (bus.getStatus() != Bus.Status.APPROVED) {
            throw new ConflictException("The bus must be approved by NammaJourney before this route can be published");
        }
        if (fares.countByRouteId(id) == 0) {
            throw new IllegalArgumentException("Add at least one fare before publishing");
        }
        if (r.getStatus() != BusRoute.Status.PUBLISHED) {
            r.setStatus(BusRoute.Status.PUBLISHED);
            r = routes.save(r);
        }
        generator.generate(r);
        return view(r);
    }

    /** No new trips are created and future trips leave search; existing bookings stand. */
    @Transactional
    public RouteView pause(Long id) {
        BusRoute r = mine(id);
        if (r.getStatus() == BusRoute.Status.DRAFT) {
            throw new ConflictException("Only a published route can be paused");
        }
        r.setStatus(BusRoute.Status.PAUSED);
        return view(routes.save(r));
    }

    /** One of my routes, or "not found". */
    public BusRoute mine(Long id) {
        Long me = ownerScope.ownerId();
        return routes.findById(id).filter(r -> me.equals(r.getOwnerId()))
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
    }

    RouteView view(BusRoute r) {
        String busName = buses.findById(r.getBusId()).map(Bus::getName).orElse(null);
        List<BusRouteStop> routeStops = stops.findByRouteIdOrderBySeqAsc(r.getId());
        Map<Long, BusPlace> placeById = places.findAllById(routeStops.stream().map(BusRouteStop::getPlaceId).toList())
                .stream().collect(Collectors.toMap(BusPlace::getId, Function.identity()));
        List<BusRoutePoint> allPoints = routeStops.isEmpty() ? List.of()
                : points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(routeStops.stream().map(BusRouteStop::getId).toList());
        List<StopView> stopViews = routeStops.stream().map(s -> new StopView(s.getSeq(), s.getPlaceId(),
                placeById.containsKey(s.getPlaceId()) ? placeById.get(s.getPlaceId()).getName() : null,
                allPoints.stream().filter(p -> s.getId().equals(p.getStopId()))
                        .map(p -> new PointView(p.getId(), p.getKind().name(), p.getName(), p.getAddress(),
                                p.getLandmark(), p.getLat(), p.getLng(), p.getDayOffset(),
                                BusRouteRules.formatTime(p.getTime())))
                        .toList())).toList();
        List<FareView> fareViews = fares.findByRouteIdOrderByFromSeqAscToSeqAscSeatClassAsc(r.getId()).stream()
                .map(f -> new FareView(f.getFromSeq(), f.getToSeq(), f.getSeatClass().name(), f.getFare())).toList();
        return new RouteView(r.getId(), r.getBusId(), busName, r.getName(), BusRouteRules.runDaysList(r.getRunDays()),
                r.getActiveFrom(), r.getActiveTo(), r.getMaxSeatsPerBooking(),
                BusCancellationPolicy.fromJson(r.getCancellationPolicy()), r.getStatus().name(), stopViews, fareViews);
    }

    private void requirePlaces(Plan plan) {
        Set<Long> wanted = new HashSet<>(plan.placeIds());
        Set<Long> found = places.findAllById(wanted).stream().map(BusPlace::getId).collect(Collectors.toSet());
        if (!found.containsAll(wanted)) {
            throw new IllegalArgumentException("A town on the route was not found. Pick it from the list or add it.");
        }
    }

    private static void applyFields(BusRoute r, Plan plan) {
        r.setName(plan.name());
        r.setRunDays(plan.runDays());
        r.setActiveFrom(plan.activeFrom());
        r.setActiveTo(plan.activeTo());
        r.setMaxSeatsPerBooking(plan.maxSeatsPerBooking());
        r.setCancellationPolicy(BusCancellationPolicy.toJson(plan.cancellationPolicy()));
    }

    private BusRouteStop newStop(BusRoute r, StopPlan s) {
        BusRouteStop stop = new BusRouteStop();
        stop.setRouteId(r.getId());
        stop.setOwnerId(r.getOwnerId());
        stop.setSeq(s.seq());
        stop.setPlaceId(s.placeId());
        return stops.save(stop);
    }

    /**
     * Updates a stop's points in place: a point sent with its id (or, failing that, with the same
     * name) keeps that id; new ones are added; ones no longer listed are removed, unless a ticket uses them.
     */
    private void replacePoints(BusRouteStop stop, List<BusRoutePoint> current, List<PointPlan> wanted) {
        Map<Long, BusRoutePoint> byId = current.stream().collect(Collectors.toMap(BusRoutePoint::getId, Function.identity()));
        List<BusRoutePoint> unmatched = new ArrayList<>(current);
        List<PointPlan> pending = new ArrayList<>();
        for (PointPlan p : wanted) {
            BusRoutePoint match = p.id() == null ? null : byId.get(p.id());
            if (match != null && unmatched.remove(match)) {
                points.save(fill(match, stop, p));
            } else {
                pending.add(p);
            }
        }
        for (PointPlan p : pending) {
            BusRoutePoint match = unmatched.stream()
                    .filter(x -> x.getName().toLowerCase(Locale.ROOT).equals(p.name().toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
            if (match != null) {
                unmatched.remove(match);
                points.save(fill(match, stop, p));
            } else {
                points.save(fill(new BusRoutePoint(), stop, p));
            }
        }
        for (BusRoutePoint gone : unmatched) {
            if (tickets.countUsingPoint(gone.getId()) > 0) {
                throw new ConflictException(gone.getName() + " has bookings, so it cannot be removed");
            }
            points.delete(gone);
        }
    }

    private static BusRoutePoint fill(BusRoutePoint target, BusRouteStop stop, PointPlan p) {
        target.setStopId(stop.getId());
        target.setOwnerId(stop.getOwnerId());
        target.setKind(p.kind());
        target.setName(p.name());
        target.setAddress(p.address());
        target.setLandmark(p.landmark());
        target.setLat(p.lat());
        target.setLng(p.lng());
        target.setDayOffset(p.dayOffset());
        target.setTime(p.time());
        return target;
    }

    private void saveFares(BusRoute r, Plan plan) {
        List<BusRouteFare> rows = new ArrayList<>();
        for (FarePlan f : plan.fares()) {
            BusRouteFare row = new BusRouteFare();
            row.setRouteId(r.getId());
            row.setOwnerId(r.getOwnerId());
            row.setFromSeq(f.fromSeq());
            row.setToSeq(f.toSeq());
            row.setSeatClass(f.seatClass());
            row.setFare(f.fare());
            rows.add(row);
        }
        fares.saveAll(rows);
    }
}
