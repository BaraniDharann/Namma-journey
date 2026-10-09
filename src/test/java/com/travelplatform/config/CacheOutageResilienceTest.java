package com.travelplatform.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Redis cache is an optimisation, not a dependency. With no CacheErrorHandler, Spring
 * rethrows every cache failure, so Redis dropping out mid-run made every @Cacheable method throw
 * - including the price lookup inside createBooking, which turned a cache blip into "nobody can
 * book". These tests wire CacheConfig to a cache that fails on every call and expect the
 * application to carry on reading straight from the source.
 */
class CacheOutageResilienceTest {

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void start() {
        context = new AnnotationConfigApplicationContext();
        MockEnvironment env = new MockEnvironment().withProperty("app.cache.type", "outage-test");
        context.setEnvironment(env);
        context.register(CacheConfig.class, BrokenCacheSetup.class);
        context.refresh();
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    @DisplayName("a cache read that fails falls through to the real method")
    void readFailureFallsThroughToSource() {
        PricingLookup lookup = context.getBean(PricingLookup.class);

        assertEquals(14.0, lookup.pricePerKm());
        assertEquals(14.0, lookup.pricePerKm());
        assertEquals(2, lookup.callCount(), "with the cache down, every call reaches the source");
    }

    @Test
    @DisplayName("a cache eviction that fails does not abort the write it is attached to")
    void evictFailureDoesNotAbortTheWrite() {
        PricingLookup lookup = context.getBean(PricingLookup.class);

        assertDoesNotThrow(lookup::updatePrice);
        assertEquals(1, lookup.writeCount());
    }

    @Configuration
    static class BrokenCacheSetup {
        @Bean
        @Primary
        CacheManager brokenCacheManager() {
            return new CacheManager() {
                @Override
                public Cache getCache(String name) {
                    return new DownCache(name);
                }

                @Override
                public Collection<String> getCacheNames() {
                    return List.of("pricing");
                }
            };
        }

        @Bean
        PricingLookup pricingLookup() {
            return new PricingLookup();
        }
    }

    static class PricingLookup {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();

        // Read through methods: the bean is a caching proxy, and a field read on it bypasses the target.
        public int callCount() {
            return calls.get();
        }

        public int writeCount() {
            return writes.get();
        }

        @Cacheable("pricing")
        public Double pricePerKm() {
            calls.incrementAndGet();
            return 14.0;
        }

        @CacheEvict(value = "pricing", allEntries = true)
        public void updatePrice() {
            writes.incrementAndGet();
        }
    }

    /** Behaves like a Redis cache whose server has gone away. */
    static class DownCache extends AbstractValueAdaptingCache {
        private final String name;

        DownCache(String name) {
            super(false);
            this.name = name;
        }

        private static RedisConnectionFailureException down() {
            return new RedisConnectionFailureException("Unable to connect to Redis");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        protected Object lookup(Object key) {
            throw down();
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw down();
        }

        @Override
        public void put(Object key, Object value) {
            throw down();
        }

        @Override
        public void evict(Object key) {
            throw down();
        }

        @Override
        public void clear() {
            throw down();
        }
    }
}
