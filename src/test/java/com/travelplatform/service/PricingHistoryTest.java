package com.travelplatform.service;

import com.travelplatform.entity.PricingConfig;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PricingConfigRepository;
import com.travelplatform.repository.TravelBookingRepository;
import com.travelplatform.repository.TripDriverPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each price change is a new pricing row, and the current price is the newest row. Changing the
 * per-km price saved a row with no hourly price, so the hourly price silently fell back to ₹150 -
 * visible only once the hourly cache expired. Changing the hourly price edited the latest row in
 * place, destroying pricing history.
 */
class PricingHistoryTest {

    private PricingConfigRepository repo;
    private OwnerService service;
    private final List<PricingConfig> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repo = mock(PricingConfigRepository.class);
        service = new OwnerService(mock(TravelBookingRepository.class), repo, mock(DriverRepository.class),
                mock(TripDriverPhotoRepository.class), mock(NotificationService.class));
        when(repo.save(any(PricingConfig.class))).thenAnswer(i -> {
            PricingConfig c = i.getArgument(0);
            if (!rows.contains(c)) rows.add(c);
            return c;
        });
        when(repo.findTopByOrderByUpdatedAtDesc()).thenAnswer(i -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(rows.size() - 1)));

        PricingConfig initial = new PricingConfig();
        initial.setPricePerKm(new BigDecimal("12.00"));
        initial.setPricePerHour(new BigDecimal("200.00"));
        rows.add(initial);
    }

    @Test
    @DisplayName("changing the per-km price keeps the hourly price")
    void kmChangeKeepsHourly() {
        service.setPricePerKm(new BigDecimal("15.00"), 1L);
        assertEquals(new BigDecimal("15.00"), service.getCurrentPricePerKm());
        assertEquals(new BigDecimal("200.00"), service.getCurrentPricePerHour());
    }

    @Test
    @DisplayName("changing the hourly price adds a new row and keeps the per-km price")
    void hourlyChangeAddsHistory() {
        PricingConfig before = rows.get(rows.size() - 1);
        service.setPricePerHour(new BigDecimal("250.00"), 1L);
        assertNotSame(before, rows.get(rows.size() - 1), "the old row must stay as history");
        assertEquals(new BigDecimal("200.00"), before.getPricePerHour());
        assertEquals(new BigDecimal("250.00"), service.getCurrentPricePerHour());
        assertEquals(new BigDecimal("12.00"), service.getCurrentPricePerKm());
    }

    @Test
    @DisplayName("either price change clears both price caches")
    void bothCachesAreEvicted() throws Exception {
        for (String name : new String[] {"setPricePerKm", "setPricePerHour"}) {
            Method m = OwnerService.class.getMethod(name, BigDecimal.class, Long.class);
            Set<String> evicted = Arrays.stream(m.getAnnotation(Caching.class) != null
                            ? m.getAnnotation(Caching.class).evict() : new CacheEvict[] {m.getAnnotation(CacheEvict.class)})
                    .flatMap(e -> Arrays.stream(e.value()))
                    .collect(Collectors.toSet());
            assertTrue(evicted.containsAll(Set.of("pricing", "hourlyPricing")), name + " evicts " + evicted);
        }
    }
}
