package com.travelplatform.service.bus;

import com.travelplatform.entity.Bus;
import com.travelplatform.entity.BusDocument;
import com.travelplatform.entity.BusRoute;
import com.travelplatform.entity.BusRoutePoint;
import com.travelplatform.entity.BusRouteStop;
import com.travelplatform.repository.BusDocumentRepository;
import com.travelplatform.repository.BusRepository;
import com.travelplatform.repository.BusRoutePointRepository;
import com.travelplatform.repository.BusRouteRepository;
import com.travelplatform.repository.BusRouteStopRepository;
import com.travelplatform.repository.BusTripRepository;
import com.travelplatform.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Creates the trips of published routes for the next {@value #DAYS_AHEAD} days: when a route is
 * published, and every night at 01:00. A trip is created for each date the route runs, within its
 * active dates, while the bus is APPROVED and all four of its documents are valid on that date, and
 * only if it has not departed yet. Running it again is harmless: a route has one trip per date
 * (uq_bus_trips_route_date) and existing trips are left alone.
 *
 * <p>The nightly run also tells owners about bus documents that expire in {@value #EXPIRY_NOTICE_DAYS}
 * days, once (on that exact day).
 */
@Component
public class BusTripGenerator {

    private static final Logger log = LoggerFactory.getLogger(BusTripGenerator.class);

    public static final int DAYS_AHEAD = 60;
    static final int EXPIRY_NOTICE_DAYS = 15;

    private final BusRouteRepository routes;
    private final BusRepository buses;
    private final BusDocumentRepository documents;
    private final BusRouteStopRepository stops;
    private final BusRoutePointRepository points;
    private final BusTripRepository trips;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private Clock clock = Clock.system(ZoneId.of("Asia/Kolkata"));

    public BusTripGenerator(BusRouteRepository routes, BusRepository buses, BusDocumentRepository documents,
                            BusRouteStopRepository stops, BusRoutePointRepository points, BusTripRepository trips,
                            NotificationService notifications, TransactionTemplate tx) {
        this.routes = routes;
        this.buses = buses;
        this.documents = documents;
        this.stops = stops;
        this.points = points;
        this.trips = trips;
        this.notifications = notifications;
        this.tx = tx;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Scheduled(cron = "${app.bus.trip-generation-cron:0 0 1 * * *}", zone = "Asia/Kolkata")
    public void nightly() {
        int created = 0;
        for (BusRoute r : routes.findByStatus(BusRoute.Status.PUBLISHED)) {
            try {
                Integer n = tx.execute(s -> generate(r));
                created += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.warn("Could not create trips for bus route {}: {}", r.getId(), e.toString());
            }
        }
        log.info("Bus trip creation: {} new trip(s)", created);
        try {
            remindExpiringDocuments();
        } catch (RuntimeException e) {
            log.warn("Could not send bus document reminders: {}", e.toString());
        }
    }

    /** Creates the route's missing trips from today for {@value #DAYS_AHEAD} days. Returns how many were created. */
    @Transactional
    public int generate(BusRoute route) {
        if (route.getStatus() != BusRoute.Status.PUBLISHED) {
            return 0;
        }
        Bus bus = buses.findById(route.getBusId()).orElse(null);
        if (bus == null || bus.getStatus() != Bus.Status.APPROVED) {
            return 0;
        }
        List<BusDocument> docs = documents.findByBusIdOrderByTypeAsc(bus.getId());
        LocalDate validUntil = docs.size() < BusDocument.Type.values().length ? null
                : docs.stream().map(BusDocument::getExpiresOn).min(Comparator.naturalOrder()).orElse(null);

        List<BusRouteStop> routeStops = stops.findByRouteIdOrderBySeqAsc(route.getId());
        if (routeStops.size() < 2) {
            return 0;
        }
        Long firstStop = routeStops.get(0).getId();
        Long lastStop = routeStops.get(routeStops.size() - 1).getId();
        List<BusRoutePoint> all = points.findByStopIdInOrderByDayOffsetAscTimeAscIdAsc(List.of(firstStop, lastStop));
        List<BusRoutePoint> first = all.stream().filter(p -> firstStop.equals(p.getStopId())).toList();
        List<BusRoutePoint> last = all.stream().filter(p -> lastStop.equals(p.getStopId())).toList();
        if (first.isEmpty() || last.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int created = 0;
        for (LocalDate date : BusTripPlanner.runningDates(route.getRunDays(), route.getActiveFrom(), route.getActiveTo(),
                validUntil, today, today.plusDays(DAYS_AHEAD - 1))) {
            LocalDateTime departure = BusTripPlanner.departure(date, first);
            if (!departure.isAfter(now)) {
                continue;
            }
            created += trips.insertIfAbsent(route.getId(), bus.getId(), route.getOwnerId(), date, departure,
                    BusTripPlanner.arrival(date, last));
        }
        return created;
    }

    /** One in-app notice per document of an approved bus that expires exactly {@value #EXPIRY_NOTICE_DAYS} days from today. */
    void remindExpiringDocuments() {
        LocalDate day = LocalDate.now(clock).plusDays(EXPIRY_NOTICE_DAYS);
        for (BusDocument d : documents.findApprovedExpiringOn(day)) {
            buses.findById(d.getBusId()).ifPresent(b -> notifications.notifyBusDocumentExpiring(
                    d.getOwnerId(), b.getName() + " (" + b.getRegistrationNumber() + ")",
                    BusService.documentName(d.getType()), d.getExpiresOn()));
        }
    }
}
