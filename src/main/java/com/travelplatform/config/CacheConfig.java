package com.travelplatform.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    // Per-cache TTLs — keys below are referenced by @Cacheable(value = "...")
    private static Map<String, Duration> cacheTtls() {
        Map<String, Duration> ttls = new HashMap<>();
        ttls.put("pricing",             Duration.ofHours(12));   // Rarely changes, evicted on write
        ttls.put("hourlyPricing",       Duration.ofHours(12));
        ttls.put("publicPackages",      Duration.ofMinutes(30)); // Public package listings
        ttls.put("publicPackageById",   Duration.ofMinutes(30));
        ttls.put("packagesByCategory",  Duration.ofMinutes(30));
        ttls.put("packagesByState",     Duration.ofMinutes(30));
        ttls.put("packagesByCatState",  Duration.ofMinutes(30));
        ttls.put("allDrivers",          Duration.ofMinutes(5));
        ttls.put("driverById",          Duration.ofMinutes(5));
        ttls.put("dailyRevenue",        Duration.ofMinutes(3));
        ttls.put("monthlyRevenue",      Duration.ofMinutes(10));
        ttls.put("yearlyRevenue",       Duration.ofMinutes(30));
        ttls.put("placeSearch",         Duration.ofHours(24));   // External API — cache aggressively
        return ttls;
    }

    /**
     * Redis-backed cache manager — active when app.cache.type=redis (default).
     * Falls back to Caffeine when Redis connection is unavailable or type=caffeine.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.cache.type", havingValue = "redis", matchIfMissing = true)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                          @Value("${app.cache.default-ttl-minutes:10}") long defaultTtlMinutes) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtlMinutes))
                .disableCachingNullValues()
                .prefixCacheNameWith("travelplatform::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        cacheTtls().forEach((name, ttl) -> perCache.put(name, defaultConfig.entryTtl(ttl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }

    /**
     * In-memory Caffeine cache manager — fallback for local dev when Redis isn't running
     * (activate with app.cache.type=caffeine) or if the Redis bean cannot be created.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
