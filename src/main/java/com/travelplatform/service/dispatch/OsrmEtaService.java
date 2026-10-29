package com.travelplatform.service.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelplatform.config.ExternalHttpClients;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Road ETAs from a self-hosted OSRM ({@code app.dispatch.eta=osrm}, docs/guides/OSRM_SETUP.md).
 *
 * <p>One {@code /table} call per ride: every candidate driver as a source, the pickup as the one
 * destination, so a matching round costs one small request per waiting ride instead of one
 * route per pair. Straight-line ETAs rank a driver across a river or a highway with no exit as
 * the nearest; road ETAs do not.
 *
 * <p>Matching must not stall on routing. A failed or slow call (bounded by the third-party
 * timeouts) falls back to the straight-line estimate for that ride and is counted, so a dead
 * OSRM shows up on the dashboard rather than as rides nobody gets.
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.dispatch.eta", havingValue = "osrm")
public class OsrmEtaService implements EtaService {

    private static final Logger log = LoggerFactory.getLogger(OsrmEtaService.class);

    private final HaversineEtaService fallback;
    private final String baseUrl;
    private final RestTemplate http;

    public OsrmEtaService(HaversineEtaService fallback,
                          @Value("${routing.osrm.base-url}") String baseUrl) {
        this(fallback, baseUrl, ExternalHttpClients.forThirdPartyApis("osrm-eta"));
    }

    OsrmEtaService(HaversineEtaService fallback, String baseUrl, RestTemplate http) {
        this.fallback = fallback;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
        if (this.baseUrl.contains("router.project-osrm.org")) {
            log.warn("app.dispatch.eta=osrm is using the public OSRM demo server; it is rate-limited. Self-host it (docs/guides/OSRM_SETUP.md)");
        }
    }

    @Override
    public Map<Long, Integer> etaSeconds(GeoPoint pickup, Map<Long, GeoPoint> drivers) {
        if (drivers.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(drivers.keySet());
        StringBuilder coords = new StringBuilder();
        for (Long id : ids) {
            append(coords, drivers.get(id));
        }
        append(coords, pickup);
        String url = baseUrl + "/table/v1/driving/" + coords
                + "?sources=" + range(ids.size()) + "&destinations=" + ids.size() + "&annotations=duration";
        try {
            JsonNode body = http.getForObject(url, JsonNode.class);
            if (body == null || !"Ok".equals(body.path("code").asText())) {
                throw new IllegalStateException("OSRM table answered " + (body == null ? "nothing" : body.path("code").asText()));
            }
            JsonNode durations = body.path("durations");
            Map<Long, Integer> eta = new HashMap<>(ids.size() * 2);
            for (int i = 0; i < ids.size(); i++) {
                JsonNode d = durations.path(i).path(0);
                // null: OSRM found no road from this driver to the pickup - they must not get it.
                if (d.isNumber()) {
                    eta.put(ids.get(i), (int) Math.ceil(d.asDouble()));
                }
            }
            Metrics.counter("namma.dispatch.eta", "source", "osrm").increment();
            return eta;
        } catch (RuntimeException e) {
            log.warn("OSRM ETA failed, using straight-line estimates for this ride: {}", e.toString());
            Metrics.counter("namma.dispatch.eta", "source", "fallback").increment();
            return fallback.etaSeconds(pickup, drivers);
        }
    }

    /** OSRM takes lon,lat; ROOT so a comma-decimal JVM locale cannot corrupt the URL. */
    private static void append(StringBuilder coords, GeoPoint p) {
        if (!coords.isEmpty()) {
            coords.append(';');
        }
        coords.append(String.format(Locale.ROOT, "%.6f,%.6f", p.lon(), p.lat()));
    }

    private static String range(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                s.append(';');
            }
            s.append(i);
        }
        return s.toString();
    }
}
