package com.travelplatform.service.dispatch;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Chooses Redis or in-memory for each matching store, on the same switch as the cache and the
 * location relay ({@code app.cache.type}). With Redis, every instance shares one view and the city
 * locks spread the work; without it, a single instance runs every city from memory, which is the
 * correct behaviour for the one-instance deployment that setting describes.
 */
@Configuration
public class DispatchStoresConfig {

    private static StringRedisTemplate redis(ObjectProvider<StringRedisTemplate> provider, String cacheType) {
        StringRedisTemplate template = provider.getIfAvailable();
        return "redis".equalsIgnoreCase(cacheType) ? template : null;
    }

    @Bean
    public DriverStateStore driverStateStore(ObjectProvider<StringRedisTemplate> redisTemplate,
                                             @Value("${app.cache.type:redis}") String cacheType,
                                             DriverCardLoader loader) {
        StringRedisTemplate redis = redis(redisTemplate, cacheType);
        return redis != null ? new RedisDriverStateStore(redis, loader) : new LocalDriverStateStore(loader);
    }
}
