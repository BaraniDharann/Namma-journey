package com.travelplatform.service;

import com.travelplatform.config.BrokerMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes Ride Now offers to a driver's open dashboard over {@code /topic/driver.{id}.offers}.
 *
 * <p>An offer only lives for seconds, so it cannot wait for the dashboard's next poll. The push
 * is the fast path; the poll is the safety net. Like live tracking, the STOMP broker is
 * per-instance, so every push is also relayed over Redis for drivers connected elsewhere.
 *
 * <p>The payload is a nudge, not the trip: {@code {type, bookingId}}. The dashboard reloads its
 * trips through the authorised REST endpoint, so nothing about a passenger rides on the socket.
 */
@Service
public class DriverOfferPublisher {

    private static final Logger log = LoggerFactory.getLogger(DriverOfferPublisher.class);

    public static final String RELAY_CHANNEL = "travelplatform::driver-offer-relay";

    public enum Event { OFFER, WITHDRAWN }

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisConfigured;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String instanceId = UUID.randomUUID().toString();

    // Field-injected so hand-built tests are unaffected; null reads as "simple".
    @Value("${app.websocket.broker:simple}")
    private String broker;

    public DriverOfferPublisher(SimpMessagingTemplate messagingTemplate,
                                ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                @Value("${app.cache.type:redis}") String cacheType) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.redisConfigured = "redis".equalsIgnoreCase(cacheType) && this.redisTemplate != null;
    }

    public static String topic(long driverId) {
        return "/topic/driver." + driverId + ".offers";
    }

    public void publish(long driverId, UUID bookingId, Event event) {
        Map<String, Object> payload = Map.of("type", event.name(), "bookingId", bookingId.toString());
        messagingTemplate.convertAndSend(topic(driverId), payload);
        if (!redisConfigured || !BrokerMode.relaysOverRedis(broker)) {
            return;
        }
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("origin", instanceId);
            envelope.put("driverId", driverId);
            envelope.put("payload", payload);
            redisTemplate.convertAndSend(RELAY_CHANNEL, mapper.writeValueAsString(envelope));
        } catch (Exception e) {
            // The local push already went out and the dashboard polls; a lost relay costs seconds.
            log.debug("Driver offer relay failed: {}", e.toString());
        }
    }

    /** A push published by another instance, forwarded to this instance's subscribers. */
    public void onRelayed(String message) {
        try {
            JsonNode envelope = mapper.readTree(message);
            if (instanceId.equals(envelope.path("origin").asText(null))) {
                return;
            }
            long driverId = envelope.path("driverId").asLong();
            Map<?, ?> payload = mapper.treeToValue(envelope.get("payload"), Map.class);
            if (driverId > 0 && payload != null) {
                messagingTemplate.convertAndSend(topic(driverId), payload);
            }
        } catch (Exception e) {
            log.warn("Ignoring malformed relayed driver offer: {}", e.toString());
        }
    }
}
