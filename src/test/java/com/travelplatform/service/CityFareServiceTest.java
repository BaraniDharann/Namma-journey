package com.travelplatform.service;

import com.travelplatform.dto.RouteInfo;
import com.travelplatform.entity.CityFare;
import com.travelplatform.entity.VehicleType;
import com.travelplatform.repository.CityFareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityFareServiceTest {

    private CityFareRepository repository;
    private RoutingService routingService;
    private CityFareService service;

    @BeforeEach
    void setUp() {
        repository = mock(CityFareRepository.class);
        routingService = mock(RoutingService.class);
        service = new CityFareService(repository, routingService, 50);
        when(repository.findById(VehicleType.AUTO)).thenReturn(Optional.of(fare(VehicleType.AUTO, "30", "12", "1.50", "40")));
        when(repository.findAll()).thenReturn(List.of(
                fare(VehicleType.CAR, "50", "16", "2", "80"),
                fare(VehicleType.BIKE, "20", "6", "1", "30"),
                fare(VehicleType.AUTO, "30", "12", "1.50", "40")));
        when(repository.save(any(CityFare.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static CityFare fare(VehicleType type, String base, String perKm, String perMinute, String minimum) {
        CityFare f = new CityFare();
        f.setVehicleType(type);
        f.setBaseFare(new BigDecimal(base));
        f.setPerKm(new BigDecimal(perKm));
        f.setPerMinute(new BigDecimal(perMinute));
        f.setMinimumFare(new BigDecimal(minimum));
        return f;
    }

    @Test
    @DisplayName("base + per km + per minute, rounded to the paisa")
    void meteredFare() {
        // 30 + 12 x 7.333 + 1.5 x 21 = 30 + 87.996 + 31.5 = 149.496
        assertEquals(new BigDecimal("149.50"), service.fare(VehicleType.AUTO, 7.333, 21));
    }

    @Test
    @DisplayName("a short hop pays the minimum fare")
    void minimumFare() {
        // 30 + 12 x 0.5 + 1.5 x 2 = 39, under the 40 minimum
        assertEquals(new BigDecimal("40.00"), service.fare(VehicleType.AUTO, 0.5, 2));
    }

    @Test
    @DisplayName("the estimate lists bike, auto and car in that order, and flags rides beyond the city limit")
    void estimate() {
        when(routingService.calculateRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteInfo(62.0, 95L, "r"));

        Map<String, Object> e = service.estimate(13.0, 80.2, 13.5, 80.6);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) e.get("options");
        assertEquals(List.of("BIKE", "AUTO", "CAR"), options.stream().map(o -> o.get("vehicleType")).toList());
        assertEquals(1, options.get(0).get("maxPassengers"));
        assertEquals(false, e.get("withinCity"));
    }

    @Test
    @DisplayName("the owner cannot set a negative fare")
    void refusesNegativeFare() {
        when(repository.findById(VehicleType.BIKE)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.update(VehicleType.BIKE,
                new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L));
        assertEquals(new BigDecimal("6.50"), service.update(VehicleType.BIKE,
                new BigDecimal("15"), new BigDecimal("6.5"), BigDecimal.ONE, new BigDecimal("25"), 1L).getPerKm());
    }
}
