package com.travelplatform.config;

import com.travelplatform.service.LocationTrackingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes this instance to driver positions published by the others.
 *
 * <p>STOMP's simple broker only knows about the sessions attached to its own JVM, so with more
 * than one backend instance a passenger connected to instance B would never receive an update
 * the driver published to instance A. This container closes that gap: every instance listens on
 * one Redis channel and forwards what it hears to its own broker.
 *
 * <p>Gated on the same switch as the cache. With {@code app.cache.type=caffeine} there is no
 * Redis to listen to, the relay is skipped, and the application behaves as the single instance
 * it is being run as.
 */
@Configuration
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis", matchIfMissing = true)
public class LocationRelayConfig {

    @Bean
    public RedisMessageListenerContainer locationRelayContainer(RedisConnectionFactory connectionFactory,
                                                                LocationTrackingService locationTrackingService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> locationTrackingService.onRelayedLocation(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(LocationTrackingService.RELAY_CHANNEL));
        // Redis being down is a supported state here (tracking falls back to local delivery),
        // so reconnect on a slow cadence instead of the 5s default that would fill the log.
        container.setRecoveryInterval(30_000L);
        return container;
    }
}
