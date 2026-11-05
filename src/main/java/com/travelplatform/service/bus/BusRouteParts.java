package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusBookingViews.PointBrief;
import com.travelplatform.dto.bus.BusBookingViews.PointDetail;
import com.travelplatform.entity.BusPlace;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.repository.BusPlaceRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteStopRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A route's stops (with town names) and the boarding and dropping points at each, loaded for many
 * routes in three queries (search results, ticket lists).
 */
record BusRouteParts(List<BusRouteStop> stops, Map<Integer, String> placeBySeq, Map<Long, List<BusRoutePoint>> pointsByStop,
                     Map<Long, BusRoutePoint> pointById) {

    static Map<Long, BusRouteParts> load(Collection<Long> routeIds, BusRouteStopRepository stopRepo,
                                         BusRoutePointRepository pointRepo, BusPlaceRepository placeRepo) {
        if (routeIds.isEmpty()) {
            return Map.of();
        }
        List<BusRouteStop> all = stopRepo.findByRouteIdInOrderByRouteIdAscSeqAsc(routeIds);
        Map<Long, String> names = placeRepo.findAllById(all.stream().map(BusRouteStop::getPlaceId).distinct().toList())
                .stream().collect(Collectors.toMap(BusPlace::getId, BusPlace::getName));
        List<BusRoutePoint> points = all.isEmpty() ? List.of()
                : pointRepo.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(all.stream().map(BusRouteStop::getId).toList());
        Map<Long, List<BusRoutePoint>> byStop = points.stream().collect(Collectors.groupingBy(BusRoutePoint::getStopId));
        Map<Long, BusRoutePoint> byId = points.stream().collect(Collectors.toMap(BusRoutePoint::getId, Function.identity()));
        Map<Long, BusRouteParts> result = new HashMap<>();
        for (Map.Entry<Long, List<BusRouteStop>> e : all.stream()
                .collect(Collectors.groupingBy(BusRouteStop::getRouteId)).entrySet()) {
            List<BusRouteStop> stops = new ArrayList<>(e.getValue());
            stops.sort(Comparator.comparingInt(BusRouteStop::getSeq));
            Map<Integer, String> placeBySeq = new HashMap<>();
            Map<Long, List<BusRoutePoint>> mine = new HashMap<>();
            for (BusRouteStop s : stops) {
                placeBySeq.put(s.getSeq(), names.get(s.getPlaceId()));
                mine.put(s.getId(), byStop.getOrDefault(s.getId(), List.of()));
            }
            result.put(e.getKey(), new BusRouteParts(stops, placeBySeq, mine, byId));
        }
        return result;
    }

    static BusRouteParts load(Long routeId, BusRouteStopRepository stopRepo, BusRoutePointRepository pointRepo,
                              BusPlaceRepository placeRepo) {
        return load(List.of(routeId), stopRepo, pointRepo, placeRepo)
                .getOrDefault(routeId, new BusRouteParts(List.of(), Map.of(), Map.of(), Map.of()));
    }

    String placeName(int seq) {
        return placeBySeq.getOrDefault(seq, "Stop " + (seq + 1));
    }

    /** The stop number of a town on this route, or null when the route does not stop there. */
    Integer seqOf(Long placeId) {
        return stops.stream().filter(s -> Objects.equals(s.getPlaceId(), placeId)).map(BusRouteStop::getSeq)
                .findFirst().orElse(null);
    }

    BusRouteStop stop(int seq) {
        return stops.stream().filter(s -> s.getSeq() == seq).findFirst().orElse(null);
    }

    int lastSeq() {
        return stops.isEmpty() ? 0 : stops.get(stops.size() - 1).getSeq();
    }

    /** Where passengers can get on at stop {@code seq}, earliest first. */
    List<BusRoutePoint> boarding(int seq) {
        BusRouteStop s = stop(seq);
        return s == null ? List.of() : pointsByStop.getOrDefault(s.getId(), List.of()).stream()
                .filter(p -> p.getKind().boards()).toList();
    }

    /** Where passengers can get off at stop {@code seq}, earliest first. */
    List<BusRoutePoint> dropping(int seq) {
        BusRouteStop s = stop(seq);
        return s == null ? List.of() : pointsByStop.getOrDefault(s.getId(), List.of()).stream()
                .filter(p -> p.getKind().drops()).toList();
    }

    /** True when the point is at stop {@code seq}. */
    boolean isAt(BusRoutePoint p, int seq) {
        BusRouteStop s = stop(seq);
        return p != null && s != null && s.getId().equals(p.getStopId());
    }

    static PointBrief brief(BusRoutePoint p, LocalDate travelDate) {
        return new PointBrief(p.getId(), p.getName(), p.at(travelDate));
    }

    static PointDetail detail(BusRoutePoint p, LocalDate travelDate) {
        return p == null ? null : new PointDetail(p.getId(), p.getName(), p.getAddress(), p.getLandmark(), p.at(travelDate));
    }

    static LocalDateTime earliest(List<BusRoutePoint> points, LocalDate travelDate) {
        return points.stream().map(p -> p.at(travelDate)).min(Comparator.naturalOrder()).orElse(null);
    }

    static LocalDateTime latest(List<BusRoutePoint> points, LocalDate travelDate) {
        return points.stream().map(p -> p.at(travelDate)).max(Comparator.naturalOrder()).orElse(null);
    }
}
