package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How reliable a driver has been lately, as extra seconds on their matching cost (Phase 5).
 *
 * <p>ETA alone sends every ride to the nearest driver even when that driver lets most offers run
 * out, so the rider waits 15 s per ignored offer. Ride-hailing dispatchers weigh acceptance and
 * rating alongside ETA; here a driver who accepted 50 % of their last 30 days' City offers costs
 * half of {@code accept-penalty-seconds} more, and every star of average rating below
 * {@code rating-pivot} costs {@code rating-penalty-seconds}. A clearly closer driver still wins:
 * the defaults (120 s, 60 s) are a couple of minutes, not the whole 15-minute ETA cap.
 *
 * <p>Read in one batch per round for the drivers it has not seen in {@link #TTL_MS}, then served
 * from memory, so a round adds at most one small query.
 */
@Component
public class DriverQualityService {

    private static final Logger log = LoggerFactory.getLogger(DriverQualityService.class);

    static final long TTL_MS = 10 * 60_000L;
    private static final int CHUNK = 1000;

    /** Accepted and declined City offers in the window, and the average rating (null if never rated). */
    public record Quality(int accepted, int declined, Double rating) {
        static final Quality NONE = new Quality(0, 0, null);

        public int offers() {
            return accepted + declined;
        }

        public double acceptance() {
            return offers() == 0 ? 1.0 : accepted / (double) offers();
        }
    }

    private record Cached(Quality quality, long loadedAtMs) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final DispatchProperties properties;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    public DriverQualityService(NamedParameterJdbcTemplate jdbc, DispatchProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Extra seconds of cost for each driver; 0 for drivers with too little history or on any error. */
    public Map<Long, Integer> penaltySeconds(Collection<Long> driverIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (driverIds == null || driverIds.isEmpty()) {
            return out;
        }
        Map<Long, Quality> q = qualities(driverIds);
        for (Long id : driverIds) {
            out.put(id, penalty(q.getOrDefault(id, Quality.NONE)));
        }
        return out;
    }

    int penalty(Quality q) {
        double seconds = 0;
        if (q.offers() >= properties.getQualityMinOffers()) {
            seconds += (1.0 - q.acceptance()) * properties.getAcceptPenaltySeconds();
        }
        if (q.rating() != null && q.rating() < properties.getRatingPivot()) {
            seconds += (properties.getRatingPivot() - q.rating()) * properties.getRatingPenaltySeconds();
        }
        return (int) Math.round(seconds);
    }

    public Map<Long, Quality> qualities(Collection<Long> driverIds) {
        long now = clock.millis();
        Map<Long, Quality> out = new HashMap<>();
        List<Long> missing = new ArrayList<>();
        for (Long id : driverIds) {
            Cached c = cache.get(id);
            if (c != null && now - c.loadedAtMs() < TTL_MS) {
                out.put(id, c.quality());
            } else {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return out;
        }
        try {
            for (int from = 0; from < missing.size(); from += CHUNK) {
                List<Long> chunk = missing.subList(from, Math.min(missing.size(), from + CHUNK));
                Map<Long, Quality> loaded = load(chunk);
                for (Long id : chunk) {
                    Quality q = loaded.getOrDefault(id, Quality.NONE);
                    cache.put(id, new Cached(q, now));
                    out.put(id, q);
                }
            }
        } catch (RuntimeException e) {
            // Scoring is a refinement: a failed read scores on ETA alone this round.
            log.warn("Driver quality read failed; matching on ETA alone: {}", e.toString());
        }
        if (cache.size() > 200_000) {
            cache.entrySet().removeIf(e -> now - e.getValue().loadedAtMs() >= TTL_MS);
        }
        return out;
    }

    private Map<Long, Quality> load(List<Long> ids) {
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
        Map<Long, int[]> counts = new HashMap<>();
        // Accepted: City rides this driver took on in the last 30 days and did not just hold as an offer.
        jdbc.query("""
                SELECT driver_id, COUNT(*) AS n FROM travel_bookings
                WHERE driver_id IN (:ids) AND service_mode = 'CITY'
                  AND status IN ('CONFIRMED', 'STARTED', 'COMPLETED')
                  AND created_at > now() - interval '30 days'
                GROUP BY driver_id""", p, rs -> {
            counts.computeIfAbsent(rs.getLong("driver_id"), k -> new int[2])[0] = rs.getInt("n");
        });
        // Declined: City offers turned down or left to run out (both are recorded as a rejection).
        jdbc.query("""
                SELECT r.driver_id, COUNT(*) AS n FROM booking_driver_rejections r
                JOIN travel_bookings b ON b.id = r.booking_id
                WHERE r.driver_id IN (:ids) AND b.service_mode = 'CITY'
                  AND r.rejected_at > now() - interval '30 days'
                GROUP BY r.driver_id""", p, rs -> {
            counts.computeIfAbsent(rs.getLong("driver_id"), k -> new int[2])[1] = rs.getInt("n");
        });
        Map<Long, Double> ratings = new HashMap<>();
        jdbc.query("""
                SELECT driver_id, AVG(rating) AS r FROM reviews
                WHERE driver_id IN (:ids) AND created_at > now() - interval '180 days'
                GROUP BY driver_id""", p, rs -> {
            ratings.put(rs.getLong("driver_id"), rs.getDouble("r"));
        });
        Map<Long, Quality> out = new HashMap<>();
        for (Long id : ids) {
            int[] c = counts.getOrDefault(id, new int[2]);
            out.put(id, new Quality(c[0], c[1], ratings.get(id)));
        }
        return out;
    }
}
