package com.travelplatform.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    // Same origins as REST CORS (app.cors.allowed-origins); overridable per environment.
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${app.websocket.broker:simple}")
    private String broker;
    @Value("${app.websocket.relay.host:localhost}")
    private String relayHost;
    @Value("${app.websocket.relay.port:61613}")
    private int relayPort;
    @Value("${app.websocket.relay.login:guest}")
    private String relayLogin;
    @Value("${app.websocket.relay.passcode:guest}")
    private String relayPasscode;
    @Value("${app.websocket.relay.virtual-host:/}")
    private String relayVirtualHost;

    /**
     * {@code simple}: an in-memory broker per instance (plus the Redis relay for several instances).
     * {@code rabbitmq}: every instance relays to one RabbitMQ STOMP broker, so every socket is
     * reachable from every instance - the setup for running more than a couple of servers.
     *
     * <p>The relay's client and system connections use the same broker account. Clients never
     * authenticate to RabbitMQ themselves: their frames still pass WebSocketAuthInterceptor here
     * first, which only admits exact, permitted destinations - never RabbitMQ's "*" or "#".
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        if (BrokerMode.isRabbit(broker)) {
            config.enableStompBrokerRelay("/topic")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayLogin)
                    .setClientPasscode(relayPasscode)
                    .setSystemLogin(relayLogin)
                    .setSystemPasscode(relayPasscode)
                    .setVirtualHost(relayVirtualHost);
        } else {
            config.enableSimpleBroker("/topic");
        }
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/tracking")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
