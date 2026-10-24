package com.travelplatform.config;

import com.travelplatform.service.DriverOfferPublisher;
import com.travelplatform.service.LocationTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.ReactorNettyTcpStompClient;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The topics the app publishes, against a real RabbitMQ STOMP broker - the one the relay talks
 * to with app.websocket.broker=rabbitmq. Proves the dot-separated names route, and that one
 * booking's subscriber hears nothing of another's.
 */
@Testcontainers(disabledWithoutDocker = true)
class RabbitStompRelayTest {

    @Container
    private static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-alpine")
            .withCommand("sh", "-c", "rabbitmq-plugins enable --offline rabbitmq_stomp && exec docker-entrypoint.sh rabbitmq-server")
            .withExposedPorts(61613)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private ReactorNettyTcpStompClient client;

    @AfterEach
    void close() {
        if (client != null) {
            client.shutdown();
        }
    }

    private StompSession connect() throws Exception {
        client = new ReactorNettyTcpStompClient(RABBIT.getHost(), RABBIT.getMappedPort(61613));
        client.setMessageConverter(new StringMessageConverter());
        StompHeaders connect = new StompHeaders();
        connect.setLogin("guest");
        connect.setPasscode("guest");
        connect.setHost("/");
        return client.connectAsync(connect, new StompSessionHandlerAdapter() { }).get(20, TimeUnit.SECONDS);
    }

    private static BlockingQueue<String> subscribe(StompSession session, String topic) {
        BlockingQueue<String> received = new ArrayBlockingQueue<>(10);
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        });
        return received;
    }

    @Test
    @DisplayName("driver offers and booking locations route through RabbitMQ, each only to its own subscriber")
    void dotTopicsRoute() throws Exception {
        StompSession session = connect();
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        BlockingQueue<String> offers = subscribe(session, DriverOfferPublisher.topic(42));
        BlockingQueue<String> myTrip = subscribe(session, LocationTrackingService.topic(mine));
        Thread.sleep(500); // let the subscriptions bind before publishing

        session.send(DriverOfferPublisher.topic(42), "{\"type\":\"OFFER\"}");
        session.send(LocationTrackingService.topic(other), "{\"lat\":1}");
        session.send(LocationTrackingService.topic(mine), "{\"lat\":2}");

        assertEquals("{\"type\":\"OFFER\"}", offers.poll(10, TimeUnit.SECONDS));
        assertEquals("{\"lat\":2}", myTrip.poll(10, TimeUnit.SECONDS));
        assertNull(myTrip.poll(1, TimeUnit.SECONDS), "another booking's position must not arrive");
    }
}
