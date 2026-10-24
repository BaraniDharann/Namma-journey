package com.travelplatform.service.dispatch;

import com.travelplatform.config.DispatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsrmEtaServiceTest {

    private MockRestServiceServer osrm;
    private OsrmEtaService service;
    private final GeoPoint pickup = new GeoPoint(13.0827, 80.2707);
    private final Map<Long, GeoPoint> drivers = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        RestTemplate http = new RestTemplate();
        osrm = MockRestServiceServer.bindTo(http).build();
        service = new OsrmEtaService(new HaversineEtaService(new DispatchProperties()), "http://osrm:5000/", http);
        drivers.put(7L, new GeoPoint(13.0500, 80.2824));
        drivers.put(9L, new GeoPoint(13.1000, 80.2500));
    }

    @Test
    @DisplayName("one table call: drivers are sources, the pickup the destination, lon,lat order")
    void roadEtas() {
        osrm.expect(requestTo("http://osrm:5000/table/v1/driving/80.282400,13.050000;80.250000,13.100000;80.270700,13.082700"
                        + "?sources=0;1&destinations=2&annotations=duration"))
                .andRespond(withSuccess("{\"code\":\"Ok\",\"durations\":[[412.3],[180.0]]}", MediaType.APPLICATION_JSON));

        Map<Long, Integer> eta = service.etaSeconds(pickup, drivers);

        assertEquals(413, eta.get(7L));
        assertEquals(180, eta.get(9L));
        osrm.verify();
    }

    @Test
    @DisplayName("no road from a driver: left out, so they are never offered the ride")
    void unreachableDriverDropped() {
        osrm.expect(requestTo(org.hamcrest.Matchers.startsWith("http://osrm:5000/table/")))
                .andRespond(withSuccess("{\"code\":\"Ok\",\"durations\":[[null],[95.0]]}", MediaType.APPLICATION_JSON));

        Map<Long, Integer> eta = service.etaSeconds(pickup, drivers);

        assertFalse(eta.containsKey(7L));
        assertEquals(95, eta.get(9L));
    }

    @Test
    @DisplayName("OSRM down: straight-line ETAs, matching carries on")
    void fallsBackWhenDown() {
        osrm.expect(requestTo(org.hamcrest.Matchers.startsWith("http://osrm:5000/table/"))).andRespond(withServerError());

        Map<Long, Integer> eta = service.etaSeconds(pickup, drivers);

        assertEquals(2, eta.size());
        assertTrue(eta.get(7L) > 0);
    }

    @Test
    @DisplayName("coordinates stay dot-decimal on a comma-decimal JVM")
    void localeIndependent() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            osrm.expect(requestTo(org.hamcrest.Matchers.containsString("80.282400,13.050000")))
                    .andRespond(withSuccess("{\"code\":\"Ok\",\"durations\":[[1],[2]]}", MediaType.APPLICATION_JSON));
            service.etaSeconds(pickup, drivers);
            osrm.verify();
        } finally {
            Locale.setDefault(before);
        }
    }
}
