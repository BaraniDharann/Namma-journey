package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingViews.MapSeat;
import com.travelplatform.dto.bus.BusBookingViews.PhotoBrief;
import com.travelplatform.dto.bus.BusBookingViews.PointBrief;
import com.travelplatform.dto.bus.BusBookingViews.SearchResult;
import com.travelplatform.dto.bus.BusBookingViews.SeatMap;
import com.travelplatform.dto.bus.BusBookingViews.StopBrief;
import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRouteFare;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusSeatClass;
import com.travelplatform.entity.BusTrip;
import com.travelplatform.entity.Owner;
import com.travelplatform.exception.ConflictException;
import com.travelplatform.exception.ResourceNotFoundException;
import com.travelplatform.repository.BusPhotoRepository;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusReviewRepository;
import com.travelplatform.repository.BusRouteFareRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusSeatAllocationRepository;
import com.travelplatform.repository.BusSeatRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.repository.OwnerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The public side of bus booking: searching buses between two towns on a date and a trip's seat map
 * for a stretch of its route. Both read the seats of every candidate trip in one query.
 */
@Service
public class BusSearchService {

    /** A point's time is at most 3 days after its trip's date (bus_route_points.day_offset). */
    static final int MAX_DAY_OFFSET = 3;
    static final int MAX_DAYS_AHEAD = 90;

    private final BusTripRepository trips;
    private final BusRouteRepository routes;
    private final BusRepository buses;
    private final BusSeatRepository seats;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusPlaceRepository places;
    private final BusRouteFareRepository fares;
    private final BusSeatAllocationRepository allocations;
    private final BusPhotoRepository photos;
    private final BusReviewRepository reviews;
    private final OwnerRepository owners;
    private final BusSettings settings;
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusSearchService(BusTripRepository trips, BusRouteRepository routes, BusRepository buses,
                            BusSeatRepository seats, BusRouteStopRepository stops, BusRoutePointRepository points,
                            BusPlaceRepository places, BusRouteFareRepository fares,
                            BusSeatAllocationRepository allocations, BusPhotoRepository photos,
                            BusReviewRepository reviews, OwnerRepository owners, BusSettings settings) {
        this.trips = trips;
        this.routes = routes;
        this.buses = buses;
        this.seats = seats;
        this.stops = stops;
        this.points = points;
        this.places = places;
        this.fares = fares;
        this.allocations = allocations;
        this.photos = photos;
        this.reviews = reviews;
        this.owners = owners;
        this.settings = settings;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * Buses from {@code from} to {@code to} whose earliest boarding point at {@code from} is on
     * {@code date} (a trip that left its first town the day before still counts) and still ahead.
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(Long from, Long to, LocalDate date) {
        if (from == null || to == null || date == null) {
            throw new IllegalArgumentException("Choose where from, where to and the date");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("Choose two different towns");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate()) || date.isAfter(now.toLocalDate().plusDays(MAX_DAYS_AHEAD))) {
            return List.of();
        }
        List<Object[]> rows = trips.searchCandidates(from, to, date.minusDays(MAX_DAY_OFFSET), date);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<BusTrip> found = rows.stream().map(r -> (BusTrip) r[0]).toList();
        Map<Long, BusRoute> routeById = byId(routes.findAllById(ids(found, BusTrip::getRouteId)), BusRoute::getId);
        Map<Long, Bus> busById = byId(buses.findAllById(ids(found, BusTrip::getBusId)), Bus::getId);
        Map<Long, Owner> ownerById = byId(owners.findAllById(ids(found, BusTrip::getOwnerId)), Owner::getId);
        Map<Long, BusRouteParts> parts = BusRouteParts.load(routeById.keySet(), stops, points, places);
        Map<Long, List<BusSeat>> seatsByBus = seats.findByBusIdIn(busById.keySet()).stream()
                .collect(Collectors.groupingBy(BusSeat::getBusId));
        Map<Long, List<BusRouteFare>> faresByRoute = fares.findByRouteIdIn(routeById.keySet()).stream()
                .collect(Collectors.groupingBy(BusRouteFare::getRouteId));
        Map<Long, List<BusSeatAllocation>> allocByTrip = allocations
                .findByTripIdInAndStatusIn(found.stream().map(BusTrip::getId).toList(), BusSeatRules.LIVE).stream()
                .collect(Collectors.groupingBy(BusSeatAllocation::getTripId));
        Map<Long, double[]> ratings = ratings(busById.keySet());

        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            BusTrip t = (BusTrip) row[0];
            int fromSeq = ((Number) row[1]).intValue();
            int toSeq = ((Number) row[2]).intValue();
            BusRouteParts p = parts.get(t.getRouteId());
            Bus bus = busById.get(t.getBusId());
            if (p == null || bus == null) {
                continue;
            }
            List<BusRoutePoint> boarding = p.boarding(fromSeq);
            List<BusRoutePoint> dropping = p.dropping(toSeq);
            LocalDateTime firstBoarding = BusRouteParts.earliest(boarding, t.getTravelDate());
            if (firstBoarding == null || dropping.isEmpty() || !firstBoarding.toLocalDate().equals(date)) {
                continue;
            }
            List<BusRoutePoint> ahead = boarding.stream().filter(b -> b.at(t.getTravelDate()).isAfter(now)).toList();
            if (ahead.isEmpty()) {
                continue;
            }
            LocalDateTime departure = BusRouteParts.earliest(ahead, t.getTravelDate());
            LocalDateTime arrival = BusRouteParts.latest(dropping, t.getTravelDate());
            List<BusSeat> layout = seatsByBus.getOrDefault(bus.getId(), List.of());
            Map<BusSeatClass, BigDecimal> pairFares = pairFares(faresByRoute.getOrDefault(t.getRouteId(), List.of()), fromSeq, toSeq);
            Map<String, List<BusSeatAllocation>> taken = BusSeatRules.bySeat(allocByTrip.getOrDefault(t.getId(), List.of()));

            int left = 0;
            int singleLeft = 0;
            BigDecimal minFare = null;
            for (BusSeat s : layout) {
                BigDecimal fare = pairFares.get(s.seatClass());
                if (!BusSeatRules.state(s, fare, taken, fromSeq, toSeq, now).selectable()) {
                    continue;
                }
                left++;
                if (s.getPairLabel() == null) {
                    singleLeft++;
                }
                minFare = minFare == null || fare.compareTo(minFare) < 0 ? fare : minFare;
            }
            if (minFare == null) {
                minFare = pairFares.values().stream().min(Comparator.naturalOrder()).orElse(null);
            }
            Owner owner = ownerById.get(t.getOwnerId());
            double[] rating = ratings.get(bus.getId());
            results.add(new SearchResult(t.getId(), t.getOwnerId(), owner == null ? null : owner.getTravelsName(),
                    bus.getName(), BusLayoutRules.typeLabel(bus.isAc(), layout), bus.isAc(),
                    layout.stream().anyMatch(s -> s.getKind() == BusSeat.Kind.SLEEPER),
                    layout.stream().anyMatch(s -> s.getKind() == BusSeat.Kind.SEATER), fromSeq, toSeq, departure, arrival,
                    Duration.between(departure, arrival).toMinutes(), rating == null ? null : rating[0],
                    rating == null ? 0 : (long) rating[1], minFare, left, singleLeft, BusViewMapper.amenities(bus.getAmenities()),
                    ahead.stream().map(b -> BusRouteParts.brief(b, t.getTravelDate())).toList(),
                    dropping.stream().map(d -> BusRouteParts.brief(d, t.getTravelDate())).toList()));
        }
        results.sort(Comparator.comparing(SearchResult::departureAt).thenComparing(SearchResult::tripId));
        return results;
    }

    /** A trip's seat map for the stretch from town {@code fromPlace} to town {@code toPlace}. */
    @Transactional(readOnly = true)
    public SeatMap seatMap(Long tripId, Long fromPlace, Long toPlace) {
        BusTrip t = trips.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        BusRoute route = routes.findById(t.getRouteId()).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        Bus bus = buses.findById(t.getBusId()).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        BusRouteParts p = BusRouteParts.load(route.getId(), stops, points, places);
        Integer fromSeq = p.seqOf(fromPlace);
        Integer toSeq = p.seqOf(toPlace);
        if (fromSeq == null || toSeq == null || fromSeq >= toSeq) {
            throw new IllegalArgumentException("This bus does not go between these towns in that direction");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<BusRoutePoint> boarding = p.boarding(fromSeq).stream()
                .filter(b -> b.at(t.getTravelDate()).isAfter(now)).toList();
        if (t.getStatus() != BusTrip.Status.SCHEDULED || route.getStatus() != BusRoute.Status.PUBLISHED || boarding.isEmpty()) {
            throw new ConflictException("This bus is no longer taking bookings");
        }
        List<BusRoutePoint> dropping = p.dropping(toSeq);
        Map<BusSeatClass, BigDecimal> pairFares = pairFares(fares.findByRouteIdAndFromSeqAndToSeq(route.getId(), fromSeq, toSeq),
                fromSeq, toSeq);
        Map<String, List<BusSeatAllocation>> taken = BusSeatRules.bySeat(
                allocations.findByTripIdAndStatusInOrderBySeatLabelAscFromSeqAsc(t.getId(), BusSeatRules.LIVE));
        List<BusSeat> layout = seats.findByBusIdOrderByDeckAscRowNoAscColNoAsc(bus.getId());
        int from = fromSeq;
        int to = toSeq;
        List<MapSeat> mapSeats = layout.stream().map(s -> {
            BigDecimal fare = pairFares.get(s.seatClass());
            return new MapSeat(s.getLabel(), s.getDeck(), s.getRowNo(), s.getColNo(), s.getKind().name(),
                    s.getBerth() == null ? null : s.getBerth().name(), s.seatClass().name(), s.getPairLabel(), fare,
                    BusSeatRules.state(s, fare, taken, from, to, now).name());
        }).toList();
        Owner owner = owners.findById(t.getOwnerId()).orElse(null);
        double[] rating = ratings(List.of(bus.getId())).get(bus.getId());
        LocalDateTime departure = BusRouteParts.earliest(boarding, t.getTravelDate());
        return new SeatMap(t.getId(), owner == null ? null : owner.getTravelsName(), bus.getName(),
                BusLayoutRules.typeLabel(bus.isAc(), layout), t.getTravelDate(), fromSeq, toSeq, p.placeName(fromSeq),
                p.placeName(toSeq), departure, BusRouteParts.latest(dropping, t.getTravelDate()),
                route.getMaxSeatsPerBooking(), settings.gstPercent(), BusViewMapper.amenities(bus.getAmenities()),
                photos.findByBusIdOrderByPositionAscIdAsc(bus.getId()).stream()
                        .map(ph -> new PhotoBrief(ph.getId(), ph.getPath())).toList(),
                rating == null ? null : rating[0], rating == null ? 0 : (long) rating[1],
                p.stops().stream().map(s -> new StopBrief(s.getSeq(), p.placeName(s.getSeq()))).toList(),
                boarding.stream().map(b -> BusRouteParts.detail(b, t.getTravelDate())).toList(),
                dropping.stream().map(d -> BusRouteParts.detail(d, t.getTravelDate())).toList(),
                mapSeats, BusFares.slabViews(BusCancellationPolicy.fromJson(route.getCancellationPolicy()), departure));
    }

    /** Rating per bus as {average to one decimal, count}; buses without reviews are absent. */
    Map<Long, double[]> ratings(Collection<Long> busIds) {
        Map<Long, double[]> out = new HashMap<>();
        if (busIds.isEmpty()) {
            return out;
        }
        for (Object[] row : reviews.statsByBusIds(busIds)) {
            double avg = BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue();
            out.put((Long) row[0], new double[] {avg, ((Number) row[2]).doubleValue()});
        }
        return out;
    }

    static Map<BusSeatClass, BigDecimal> pairFares(List<BusRouteFare> routeFares, int fromSeq, int toSeq) {
        Map<BusSeatClass, BigDecimal> out = new HashMap<>();
        for (BusRouteFare f : routeFares) {
            if (f.getFromSeq() == fromSeq && f.getToSeq() == toSeq) {
                out.put(f.getSeatClass(), BusFares.money(f.getFare()));
            }
        }
        return out;
    }

    private static <T, K> Map<K, T> byId(Collection<T> rows, Function<T, K> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static <T> Set<Long> ids(Collection<T> rows, Function<T, Long> id) {
        return rows.stream().map(id).collect(Collectors.toSet());
    }
}
