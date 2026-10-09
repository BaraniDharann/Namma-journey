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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Treat every cache failure as a miss instead of an error.
     *
     * <p>Without this Spring rethrows whatever the cache throws. The Redis-or-Caffeine choice
     * below is made once at startup, so a Redis that goes away <em>while running</em> made every
     * {@code @Cacheable} method throw - including the price lookup inside booking creation,
     * turning a cache outage into nobody being able to book. With it, a failed read runs the real
     * method and a failed write or eviction is skipped: slower, never broken.
     *
     * <p>A skipped eviction can leave a stale entry in Redis once it comes back, for at most that
     * cache's TTL (see {@link #cacheTtls()}), which is the price of staying up.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache read failed on '{}', reading from source: {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache write failed on '{}', skipped: {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed on '{}', entry may be stale until TTL: {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed on '{}', entries may be stale until TTL: {}", cache.getName(), e.toString());
            }
        };
    }

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
        ttls.put("monthlyRevenueSeries", Duration.ofMinutes(10));
        ttls.put("placeSearch",         Duration.ofHours(24));   // External API — cache aggressively
        return ttls;
    }

    /**
     * JSON serializer for cached values. Package-private so its type rules can be tested directly.
     *
     * <p>Cached JSON records each value's class ("@class") so it can be rebuilt on read. That
     * class name is data, read back from Redis, so it must never be trusted wholesale: the
     * previous {@code allowIfSubType(Object.class)} let anyone who could write one Redis key make
     * the app instantiate any class on the classpath on its next cache read - the well-known
     * Jackson polymorphic-deserialisation path to remote code execution. Only what this
     * application actually caches is allowed: its own DTOs, JDK collections, java.time values
     * and boxed numbers. Caching a new type from another package means adding it here.
     */
    static RedisSerializer<Object> cacheValueSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.travelplatform.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType(Number.class)
                        .allowIfSubType(Boolean.class)
                        .allowIfSubType(String.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
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
        RedisSerializer<Object> jsonSerializer = cacheValueSerializer();

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
