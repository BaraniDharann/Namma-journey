package com.travelplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.dto.DriverLocationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Where a driver currently is, for every trip being tracked.
 *
 * <p>This used to be a bare {@link ConcurrentHashMap} in one JVM, which made live tracking the
 * only piece of state in the application that could not survive a restart: a redeploy mid-trip
 * left the passenger's map frozen for the rest of the journey with no way to recover it, and a
 * second backend instance saw an empty map for trips the other instance was tracking.
 *
 * <p>Two things are shared through Redis now:
 *
 * <ul>
 *   <li><b>The last known position</b>, stored per booking with a TTL. This is what the REST
 *       polling fallback reads, so any instance can answer for any trip, and a restart resumes
 *       with the position intact.
 *   <li><b>The push</b>, relayed over a pub/sub channel. STOMP's simple broker is in-process, so
 *       a subscriber connected to instance B would never hear updates published on instance A.
 *       Each instance forwards relayed positions to its own broker.
 * </ul>
 *
 * <p>Redis is optional here, exactly as it is for the cache: if it is switched off or
 * unreachable the in-memory map and a direct local broadcast take over, which is precisely the
 * old single-instance behaviour. Degraded, never broken.
 */
@Service
public class LocationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LocationTrackingService.class);

    private static final String KEY_PREFIX = "travelplatform::location:";

    /** Channel carrying position updates between instances. */
    public static final String RELAY_CHANNEL = "travelplatform::location-relay";

    /**
     * Long enough to outlive a redeploy or a phone dropping signal mid-trip, short enough that
     * a trip abandoned without payment does not leave its position in Redis forever. Every
     * update pushes the expiry out, so a live trip never ages out from under itself.
     */
    private static final Duration LOCATION_TTL = Duration.ofMinutes(30);

    /**
     * How long Redis is left alone after a failure. Positions arrive every two seconds per
     * active trip, and the Lettuce timeout is 2s — retrying on every single update would turn
     * one Redis outage into a stalled tracking pipeline.
     */
    private static final long REDIS_COOLDOWN_MS = 60_000L;

    /** Fallback store, and the only store when Redis is unavailable. */
    private final ConcurrentHashMap<String, DriverLocationDTO> localLocations = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisConfigured;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Identifies messages this instance published, so it does not rebroadcast its own. */
    private final String instanceId = UUID.randomUUID().toString();

    private final AtomicLong redisMutedUntil = new AtomicLong(0L);

    public LocationTrackingService(SimpMessagingTemplate messagingTemplate,
                                   ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                   @Value("${app.cache.type:redis}") String cacheType) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.redisConfigured = "redis".equalsIgnoreCase(cacheType) && this.redisTemplate != null;
    }

    /**
     * Records a position and gets it in front of everyone watching that booking.
     *
     * <p>The local broadcast is unconditional rather than left to the relay coming back around:
     * the passenger on this instance must see the car move even when Redis is down, and the
     * origin tag on the relayed copy keeps the two from doubling up.
     */
    public DriverLocationDTO updateLocation(DriverLocationDTO dto) {
        store(dto);
        broadcast(dto);
        relay(dto);
        return dto;
    }

    public DriverLocationDTO getLocation(String bookingId) {
        if (redisUsable()) {
            try {
                String json = redisTemplate.opsForValue().get(KEY_PREFIX + bookingId);
                if (json != null) {
                    return mapper.readValue(json, DriverLocationDTO.class);
                }
                // Miss in Redis is authoritative once Redis is the store: a trip whose key has
                // expired is not being tracked, and falling through to a stale local copy would
                // show a passenger a position from an hour ago as if it were live.
                return null;
            } catch (Exception e) {
                muteRedis("read", e);
            }
        }
        return localLocations.get(bookingId);
    }

    public void removeLocation(String bookingId) {
        localLocations.remove(bookingId);
        if (redisUsable()) {
            try {
                redisTemplate.delete(KEY_PREFIX + bookingId);
            } catch (Exception e) {
                muteRedis("delete", e);
            }
        }
    }

    /**
     * Handles a position published by another instance. Anything this instance sent is dropped:
     * it has already gone to the local broker in {@link #updateLocation}.
     */
    public void onRelayedLocation(String payload) {
        try {
            JsonNode envelope = mapper.readTree(payload);
            String origin = envelope.path("origin").asText(null);
            if (instanceId.equals(origin)) {
                return;
            }
            DriverLocationDTO dto = mapper.treeToValue(envelope.get("location"), DriverLocationDTO.class);
            if (dto != null && dto.getBookingId() != null) {
                broadcast(dto);
            }
        } catch (Exception e) {
            log.warn("Ignoring malformed relayed location payload: {}", e.toString());
        }
    }

    private void store(DriverLocationDTO dto) {
        if (redisUsable()) {
            try {
                redisTemplate.opsForValue().set(
                        KEY_PREFIX + dto.getBookingId(),
                        mapper.writeValueAsString(dto),
                        LOCATION_TTL);
                return;
            } catch (Exception e) {
                muteRedis("write", e);
            }
        }
        localLocations.put(dto.getBookingId(), dto);
    }

    private void broadcast(DriverLocationDTO dto) {
        messagingTemplate.convertAndSend("/topic/booking/" + dto.getBookingId() + "/location", dto);
    }

    private void relay(DriverLocationDTO dto) {
        if (!redisUsable()) {
            return;
        }
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("origin", instanceId);
            envelope.put("location", dto);
            redisTemplate.convertAndSend(RELAY_CHANNEL, mapper.writeValueAsString(envelope));
        } catch (Exception e) {
            muteRedis("relay", e);
        }
    }

    private boolean redisUsable() {
        return redisConfigured && System.currentTimeMillis() >= redisMutedUntil.get();
    }

    /** Trips the cool-off so a Redis outage costs one timeout a minute, not one per update. */
    private void muteRedis(String operation, Exception e) {
        long until = System.currentTimeMillis() + REDIS_COOLDOWN_MS;
        // Log once per cool-off window rather than on every failed update.
        if (redisMutedUntil.getAndSet(until) <= System.currentTimeMillis() - REDIS_COOLDOWN_MS) {
            log.warn("Redis location {} failed; falling back to in-memory tracking for {}s: {}",
                    operation, REDIS_COOLDOWN_MS / 1000, e.toString());
        }
    }
}
