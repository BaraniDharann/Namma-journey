package com.travelplatform.service;

import com.travelplatform.config.DispatchProperties;
import com.travelplatform.entity.City;
import com.travelplatform.entity.Place;
import com.travelplatform.repository.CityRepository;
import com.travelplatform.service.dispatch.ZoneGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The zone layer behind City rides (V38): towns become zones, the H3 grid agrees with the circles,
 * and a search near a border sees drivers on both sides. The case that started it: a Chennai-only
 * zone list refused a Kanchipuram pickup and hid a Kanchipuram driver.
 */
class CityZonesTest {

    // Real coordinates: the rider's pickup at Ayyangar Kulam and Kanchipuram bus stand.
    private static final double AYYANGAR_KULAM_LAT = 12.8920, AYYANGAR_KULAM_LON = 79.6780;
    private static final double KANCHI_BUS_STAND_LAT = 12.8380, KANCHI_BUS_STAND_LON = 79.7050;

    private List<City> stored;
    private CityService cities;

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
        when(repository.findByPlaceId(anyLong())).thenAnswer(i -> stored.stream()
                .filter(c -> i.getArgument(0).equals(c.getPlaceId())).findFirst());
        when(repository.findByNameIgnoreCase(anyString())).thenAnswer(i -> stored.stream()
                .filter(c -> c.getName().equalsIgnoreCase(i.getArgument(0))).findFirst());
        cities = new CityService(repository);
        // Chennai as it is in production today: the only zone.
        City chennai = new City();
        chennai.setName("Chennai");
        chennai.setCenterLat(13.0827);
        chennai.setCenterLon(80.2707);
        chennai.setRadiusKm(25);
        cities.save(chennai);
    }

    private static Place place(long id, String state, String district, String name, double lat, double lon,
                               String kind, double radius, boolean verified) {
        Place p = new Place();
        p.setId(id);
        p.setState(state);
        p.setDistrict(district);
        p.setName(name);
        p.setLat(lat);
        p.setLon(lon);
        p.setKind(kind);
        p.setRadiusKm(radius);
        p.setVerified(verified);
        return p;
    }

    private static Place kanchipuram() {
        return place(13, "Tamil Nadu", "Kancheepuram", "Kanchipuram", 12.8342, 79.7036, City.KIND_TOWN, 12, true);
    }

    @Test
    @DisplayName("the reported bug: a Kanchipuram pickup is outside the Chennai-only zones")
    void kanchipuramWasOutsideEveryZone() {
        assertTrue(cities.cityFor(AYYANGAR_KULAM_LAT, AYYANGAR_KULAM_LON).isEmpty());
        assertEquals("Chennai", cities.nearestCity(AYYANGAR_KULAM_LAT, AYYANGAR_KULAM_LON).orElseThrow().getName());
    }

    @Test
    @DisplayName("a driver picking Kanchipuram creates its TOWN zone, and the rider's pickup is then inside it")
    void pickingATownCreatesItsZone() {
        City zone = cities.zoneForPlace(kanchipuram(), true);

        assertTrue(zone.isActive());
        assertTrue(zone.isTown());
        assertTrue(zone.isAutoCreated());
        assertEquals(13L, zone.getPlaceId());
        assertEquals(zone.getId(), cities.cityFor(AYYANGAR_KULAM_LAT, AYYANGAR_KULAM_LON).orElseThrow().getId());
        assertEquals(zone.getId(), cities.cityFor(KANCHI_BUS_STAND_LAT, KANCHI_BUS_STAND_LON).orElseThrow().getId());
        assertTrue(cities.isTown(zone.getId()));
    }

    @Test
    @DisplayName("a second driver in the same town joins the same zone")
    void secondDriverJoinsTheSameZone() {
        City first = cities.zoneForPlace(kanchipuram(), true);
        City second = cities.zoneForPlace(kanchipuram(), true);
        assertEquals(first.getId(), second.getId());
        assertEquals(2, stored.size()); // Chennai + Kanchipuram
    }

    @Test
    @DisplayName("a zone the admin already drew under the town's name is linked, not copied")
    void existingZoneWithTheSameNameIsLinked() {
        Place chennai = place(1, "Tamil Nadu", "Chennai", "Chennai", 13.0827, 80.2707, City.KIND_METRO, 25, true);
        City zone = cities.zoneForPlace(chennai, true);
        assertEquals("Chennai", zone.getName());
        assertEquals(1L, zone.getPlaceId());
        assertEquals(1, stored.size());
    }

    @Test
    @DisplayName("an unverified town's zone starts off and switches on once an approved driver works there")
    void unverifiedTownWaitsForAnApprovedDriver() {
        Place custom = place(500, "Andhra Pradesh", "Chittoor", "Puttur", 13.4420, 79.5530, City.KIND_TOWN, 10, false);
        City off = cities.zoneForPlace(custom, false);
        assertFalse(off.isActive());
        assertTrue(cities.cityFor(13.4420, 79.5530).isEmpty());

        City on = cities.zoneForPlace(custom, true);
        assertTrue(on.isActive());
        assertEquals(off.getId(), on.getId());
        assertTrue(cities.cityFor(13.4420, 79.5530).isPresent());
    }

    @Test
    @DisplayName("an admin's 'off' sticks: a driver joining does not switch an admin-edited zone back on")
    void adminOffSticks() {
        City zone = cities.zoneForPlace(kanchipuram(), true);
        City changes = new City();
        changes.setName(zone.getName());
        changes.setCenterLat(zone.getCenterLat());
        changes.setCenterLon(zone.getCenterLon());
        changes.setRadiusKm(zone.getRadiusKm());
        changes.setCommissionPercent(zone.getCommissionPercent());
        changes.setActive(false);
        // the old admin screen sends no kind
        changes.setKind(null);
        CityRepository repo = (CityRepository) ReflectionTestUtils.getField(cities, "repository");
        when(repo.findById(zone.getId())).thenReturn(Optional.of(zone));
        cities.update(zone.getId(), changes);

        assertTrue(zone.isTown()); // kind kept
        assertFalse(cities.zoneForPlace(kanchipuram(), true).isActive());
    }

    @Test
    @DisplayName("two towns with one name in different districts get distinct zone names")
    void sameNameDifferentDistrict() {
        cities.zoneForPlace(place(70, "Tamil Nadu", "Tiruvallur", "Avadi", 13.1067, 80.0970, City.KIND_TOWN, 8, true), true);
        City other = cities.zoneForPlace(place(71, "Karnataka", "Mysuru", "Avadi", 12.30, 76.65, City.KIND_TOWN, 8, true), true);
        assertEquals("Avadi, Mysuru", other.getName());
    }

    @Test
    @DisplayName("the H3 grid gives the same zone as the circles, nearest centre winning where they overlap")
    void gridAgreesWithCircles() {
        City kanchi = cities.zoneForPlace(kanchipuram(), true);
        if (!ZoneGrid.available()) {
            return; // the circle scan already answered the other tests
        }
        assertTrue(cities.gridCells() > 0);
        double[][] points = {
                {AYYANGAR_KULAM_LAT, AYYANGAR_KULAM_LON}, {KANCHI_BUS_STAND_LAT, KANCHI_BUS_STAND_LON},
                {13.0827, 80.2707}, {12.98, 80.22}, {13.20, 80.30}};
        long[] expected = {kanchi.getId(), kanchi.getId(), 1L, 1L, 1L};
        for (int i = 0; i < points.length; i++) {
            assertEquals(expected[i], cities.cityFor(points[i][0], points[i][1]).orElseThrow().getId(),
                    "point " + i);
        }
        assertTrue(cities.cityFor(11.0, 78.0).isEmpty()); // nowhere near either
    }

    @Test
    @DisplayName("citiesNear lists the zone the point is in and any whose edge is within reach")
    void citiesNearCrossesBorders() {
        City kanchi = cities.zoneForPlace(kanchipuram(), true);
        // Kanchipuram's edge is 12 km out; Chennai's 25 km circle reaches to about 49 km from Kanchipuram.
        assertEquals(List.of(kanchi.getId()),
                cities.citiesNear(KANCHI_BUS_STAND_LAT, KANCHI_BUS_STAND_LON, 2).stream().map(City::getId).toList());
        // A point between the two centres, inside both circles, sees both within 5 km.
        Place walajabad = place(44, "Tamil Nadu", "Kancheepuram", "Walajabad", 12.7920, 79.8230, City.KIND_TOWN, 8, true);
        City w = cities.zoneForPlace(walajabad, true);
        List<Long> near = cities.citiesNear(12.8150, 79.7700, 5).stream().map(City::getId).toList();
        assertTrue(near.contains(kanchi.getId()));
        assertTrue(near.contains(w.getId()));
    }

    @Test
    @DisplayName("findNear: a driver across the zone border is found; findWithin alone would miss them")
    void findNearSeesDriversAcrossTheBorder() {
        // Two towns on one latitude: Kanchipuram (12 km) and a town 21.7 km east (8 km), 1.7 km of gap.
        // One degree of longitude here is about 108.5 km.
        double lat = 12.8342, kanchiLon = 79.7036;
        City kanchi = cities.zoneForPlace(kanchipuram(), true);
        City east = cities.zoneForPlace(
                place(45, "Tamil Nadu", "Kancheepuram", "East Town", lat, kanchiLon + 0.2, City.KIND_TOWN, 8, true), true);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> noRedis = mock(ObjectProvider.class);
        DriverPresenceService presence = new DriverPresenceService(noRedis, "caffeine", new DispatchProperties());
        ReflectionTestUtils.setField(presence, "cityService", cities);

        // The rider 11 km east of Kanchipuram's centre; the driver at 14.5 km, inside the east town.
        double riderLat = lat, riderLon = kanchiLon + 11 / 108.55;
        assertEquals(kanchi.getId(), cities.cityFor(riderLat, riderLon).orElseThrow().getId());
        presence.report(7L, lat, kanchiLon + 14.5 / 108.55);
        assertEquals(east.getId(), presence.cityOf(7L).orElseThrow());

        assertTrue(presence.findWithin(kanchi.getId(), riderLat, riderLon, 5, 10).isEmpty());
        List<DriverPresenceService.Nearby> found = presence.findNear(kanchi.getId(), riderLat, riderLon, 5, 10, true);
        assertEquals(1, found.size());
        assertEquals(7L, found.get(0).driverId());
        assertTrue(presence.findNear(kanchi.getId(), riderLat, riderLon, 5, 10, false).isEmpty());
    }
}
