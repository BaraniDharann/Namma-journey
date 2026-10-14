package com.travelplatform.config;

import com.travelplatform.ratelimit.BucketStore;
import com.travelplatform.ratelimit.LocalBucketStore;
import com.travelplatform.ratelimit.RedisBucketStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Where rate-limit buckets live. Follows the same switch as the cache (app.cache.type): with
 * Redis configured, buckets are shared by every instance; without it, they are per-JVM - exactly
 * the old behaviour, and correct for one instance.
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * A dedicated Lettuce client: Bucket4j needs a byte[] codec connection, which Spring's
     * RedisConnectionFactory does not hand out. Same host, port, password and 2s timeout.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "app.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisClient rateLimitRedisClient(@Value("${spring.data.redis.host:localhost}") String host,
                                            @Value("${spring.data.redis.port:6379}") int port,
                                            @Value("${spring.data.redis.password:}") String password) {
        RedisURI.Builder uri = RedisURI.builder().withHost(host).withPort(port).withTimeout(Duration.ofSeconds(2));
        if (password != null && !password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.type", havingValue = "redis", matchIfMissing = true)
    public BucketStore redisBucketStore(RedisClient rateLimitRedisClient) {
        log.info("Rate limits shared across instances through Redis");
        return new RedisBucketStore(rateLimitRedisClient, new LocalBucketStore());
    }

    @Bean
    @ConditionalOnMissingBean(BucketStore.class)
    public BucketStore localBucketStore() {
        log.info("Rate limits kept in memory - per instance");
        return new LocalBucketStore();
    }
}
