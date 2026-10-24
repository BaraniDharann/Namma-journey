package com.travelplatform.config;

/**
 * Which STOMP broker carries live tracking and driver offers ({@code app.websocket.broker}).
 *
 * <ul>
 *   <li>{@code simple} (default): Spring's in-memory broker, one per instance. With several
 *       instances, every push is also relayed over Redis so a subscriber connected elsewhere
 *       still gets it (LocationRelayConfig).</li>
 *   <li>{@code rabbitmq}: RabbitMQ's STOMP plugin is the one broker every instance talks to, so
 *       a push reaches every subscriber directly and the Redis relay must stay off - otherwise
 *       each instance would forward the same message into the shared broker again.</li>
 * </ul>
 */
public final class BrokerMode {

    public static final String RABBITMQ = "rabbitmq";

    private BrokerMode() {
    }

    public static boolean isRabbit(String broker) {
        return RABBITMQ.equalsIgnoreCase(broker == null ? "" : broker.trim());
    }

    public static boolean relaysOverRedis(String broker) {
        return !isRabbit(broker);
    }
}
