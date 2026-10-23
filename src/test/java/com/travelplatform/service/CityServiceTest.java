package com.travelplatform.service;

import com.travelplatform.entity.City;
import com.travelplatform.repository.CityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityServiceTest {

    private static final double BLR_LAT = 12.9716;
    private static final double BLR_LON = 77.5946;
    /** One degree of latitude is about 111.2 km. */
    private static final double KM_PER_DEG = 111.2;

    private List<City> stored;
    private CityService service;

    @BeforeEach
    void setUp() {
        CityRepository repository = mock(CityRepository.class);
        stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(i -> new ArrayList<>(stored));
        when(repository.saveAndFlush(any(City.class))).thenAnswer(i -> {
            City c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId((long) stored.size() + 1);
                stored.add(c);
            }
            return c;
        });
        service = new CityService(repository);
    }

    private void city(long id, String name, double lat, double lon, double radius, boolean active) {
        City c = new City();
        c.setId(id);
        c.setName(name);
        c.setCenterLat(lat);
        c.setCenterLon(lon);
        c.setRadiusKm(radius);
        c.setActive(active);
        stored.add(c);
        service.refresh();
    }

    private static double northBy(double km) {
        return BLR_LAT + km / KM_PER_DEG;
    }

    @Test
    @DisplayName("a point inside the radius finds the city")
    void pointInsideRadiusFindsCity() {
        city(1, "Bengaluru", BLR_LAT, BLR_LON, 30, true);
        assertEquals("Bengaluru", service.cityFor(northBy(10), BLR_LON).orElseThrow().getName());
    }

    @Test
    @DisplayName("a point outside every city finds none")
    void pointOutsideEveryCityFindsNone() {
        city(1, "Bengaluru", BLR_LAT, BLR_LON, 30, true);
        assertTrue(service.cityFor(northBy(40), BLR_LON).isEmpty());
    }

    @Test
    @DisplayName("where cities overlap, the one with the nearest centre wins")
    void overlappingCitiesPickNearestCenter() {
        city(1, "Big", BLR_LAT, BLR_LON, 100, true);
        city(2, "Satellite", northBy(30), BLR_LON, 20, true);
        // 25 km north is inside both, but 5 km from Satellite's centre and 25 km from Big's.
        assertEquals("Satellite", service.cityFor(northBy(25), BLR_LON).orElseThrow().getName());
    }

    @Test
    @DisplayName("an inactive city is ignored")
    void inactiveCityIgnored() {
        city(1, "Bengaluru", BLR_LAT, BLR_LON, 30, false);
        assertTrue(service.cityFor(BLR_LAT, BLR_LON).isEmpty());
        assertTrue(service.activeIds().isEmpty());
    }

    @Test
    @DisplayName("saving refreshes the in-memory list")
    void saveRefreshesList() {
        City c = new City();
        c.setName("Mysuru");
        c.setCenterLat(12.2958);
        c.setCenterLon(76.6394);
        c.setRadiusKm(15);
        service.save(c);
        assertEquals(List.of(1L), service.activeIds());
        assertTrue(service.cityFor(12.2958, 76.6394).isPresent());
    }

    @Test
    @DisplayName("a radius outside 1 to 100 km is rejected")
    void saveRejectsRadiusOutside1to100() {
        City c = new City();
        c.setName("X");
        c.setCenterLat(BLR_LAT);
        c.setCenterLon(BLR_LON);
        c.setRadiusKm(0.5);
        assertThrows(IllegalArgumentException.class, () -> service.save(c));
        c.setRadiusKm(101);
        assertThrows(IllegalArgumentException.class, () -> service.save(c));
    }

    @Test
    @DisplayName("a blank name is rejected")
    void saveRejectsBlankName() {
        City c = new City();
        c.setName("  ");
        c.setRadiusKm(10);
        assertThrows(IllegalArgumentException.class, () -> service.save(c));
    }
}
