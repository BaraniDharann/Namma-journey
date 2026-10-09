package com.travelplatform.config;

import com.travelplatform.dto.DriverDetailsResponse;
import com.travelplatform.dto.TravelPackageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Redis cache stores values as JSON carrying a class name ("@class") so they can be rebuilt
 * on the way out. It used to accept ANY class name ({@code allowIfSubType(Object.class)}), so
 * anyone able to write one key into Redis could make the app instantiate an arbitrary class on
 * its next cache read - the classic Jackson polymorphic-deserialisation route to remote code
 * execution. Only the types the application actually caches may now be rebuilt.
 */
class CacheSerializerTypeSafetyTest {

    private final RedisSerializer<Object> serializer = CacheConfig.cacheValueSerializer();

    private Object roundTrip(Object value) {
        return serializer.deserialize(serializer.serialize(value));
    }

    @Test
    @DisplayName("revenue maps, driver lists and package DTOs still survive the cache")
    void cachedShapesRoundTrip() {
        Map<String, Object> revenue = new HashMap<>();
        revenue.put("totalTrips", 3);
        revenue.put("totalRevenue", 12500.5);
        revenue.put("bookingDate", LocalDateTime.of(2026, 10, 9, 10, 30));
        revenue.put("day", LocalDate.of(2026, 10, 9));
        revenue.put("trips", new ArrayList<>(List.of(Map.of("id", "x", "distanceKm", 40.0))));
        Object back = roundTrip(revenue);
        assertInstanceOf(Map.class, back);
        assertEquals(12500.5, ((Map<?, ?>) back).get("totalRevenue"));

        DriverDetailsResponse driver = new DriverDetailsResponse();
        driver.setName("Murugan");
        driver.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        Object drivers = roundTrip(new ArrayList<>(List.of(driver)));
        assertEquals("Murugan", ((DriverDetailsResponse) ((List<?>) drivers).get(0)).getName());

        TravelPackageResponse pkg = new TravelPackageResponse();
        pkg.setName("Ooty getaway");
        assertEquals("Ooty getaway", ((TravelPackageResponse) roundTrip(pkg)).getName());

        assertEquals(14.0, roundTrip(14.0));
    }

    @Test
    @DisplayName("a cache entry naming a class outside the allow-list is refused, not instantiated")
    void foreignClassNamesAreRefused() {
        // java.awt.Point is harmless; it stands in for any gadget class an attacker would name.
        byte[] planted = "{\"@class\":\"java.awt.Point\",\"x\":1,\"y\":2}".getBytes(StandardCharsets.UTF_8);

        assertThrows(SerializationException.class, () -> serializer.deserialize(planted));
    }
}
