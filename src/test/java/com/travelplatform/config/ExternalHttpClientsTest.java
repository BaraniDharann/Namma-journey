package com.travelplatform.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalHttpClientsTest {

    private static final String URL = "http://provider.test/call";

    /** Breakers are shared per name for the whole JVM, so every test gets its own. */
    private static String freshName() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("repeated 5xx opens the circuit, and then calls fail fast without reaching the provider")
    void serverErrorsOpenTheCircuit() {
        String name = freshName();
        RestTemplate http = ExternalHttpClients.forThirdPartyApis(name);
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(ExpectedCount.times(10), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        for (int i = 0; i < 10; i++) {
            assertThrows(HttpServerErrorException.class, () -> http.getForObject(URL, String.class));
        }
        assertEquals(CircuitBreaker.State.OPEN, ExternalHttpClients.breaker(name).getState());

        // The 11th call is refused here: the mock server would fail the test if it were reached.
        ResourceAccessException refused = assertThrows(ResourceAccessException.class,
                () -> http.getForObject(URL, String.class));
        assertTrue(refused.getMessage().contains("circuit open"));
        server.verify();
    }

    @Test
    @DisplayName("4xx answers are the caller's problem and never open the circuit")
    void clientErrorsDoNotOpenTheCircuit() {
        String name = freshName();
        RestTemplate http = ExternalHttpClients.forThirdPartyApis(name);
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(ExpectedCount.times(15), requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        for (int i = 0; i < 15; i++) {
            assertThrows(HttpClientErrorException.class, () -> http.getForObject(URL, String.class));
        }
        assertEquals(CircuitBreaker.State.CLOSED, ExternalHttpClients.breaker(name).getState());
        server.verify();
    }

    @Test
    @DisplayName("429 from the provider counts as a failure: back off instead of hammering it")
    void tooManyRequestsCountsAsFailure() {
        String name = freshName();
        RestTemplate http = ExternalHttpClients.forThirdPartyApis(name);
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        server.expect(ExpectedCount.times(10), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        for (int i = 0; i < 10; i++) {
            assertThrows(HttpClientErrorException.class, () -> http.getForObject(URL, String.class));
        }
        assertEquals(CircuitBreaker.State.OPEN, ExternalHttpClients.breaker(name).getState());
    }

    @Test
    @DisplayName("Razorpay's bulkhead stays well below the database pool, so a slow Razorpay can never hold every connection")
    @SuppressWarnings("unchecked")
    void razorpayBulkheadBelowPoolSize() throws Exception {
        int poolSize;
        try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> hikari = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                    root.get("spring")).get("datasource")).get("hikari");
            poolSize = Integer.parseInt(hikari.get("maximum-pool-size").toString());
        }
        int razorpay = ExternalHttpClients.bulkhead("razorpay").getBulkheadConfig().getMaxConcurrentCalls();

        assertTrue(razorpay <= poolSize / 2, "razorpay bulkhead " + razorpay + " vs pool " + poolSize);
        assertEquals(ExternalHttpClients.MAX_CONCURRENT_CALLS,
                ExternalHttpClients.bulkhead(freshName()).getBulkheadConfig().getMaxConcurrentCalls());
    }

    @Test
    @DisplayName("clients built with the same name share one breaker; other names are unaffected")
    void breakersArePerDependency() {
        String failing = freshName();
        RestTemplate first = ExternalHttpClients.forThirdPartyApis(failing);
        MockRestServiceServer server = MockRestServiceServer.bindTo(first).build();
        server.expect(ExpectedCount.times(10), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        for (int i = 0; i < 10; i++) {
            assertThrows(HttpServerErrorException.class, () -> first.getForObject(URL, String.class));
        }

        RestTemplate second = ExternalHttpClients.forThirdPartyApis(failing);
        MockRestServiceServer.bindTo(second).build();
        assertThrows(ResourceAccessException.class, () -> second.getForObject(URL, String.class));

        RestTemplate other = ExternalHttpClients.forThirdPartyApis(freshName());
        MockRestServiceServer healthy = MockRestServiceServer.bindTo(other).build();
        healthy.expect(requestTo(URL)).andRespond(withSuccess("ok", null));
        assertEquals("ok", other.getForObject(URL, String.class));
    }
}
