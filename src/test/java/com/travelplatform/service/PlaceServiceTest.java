package com.travelplatform.service;

import com.travelplatform.entity.City;
import com.travelplatform.entity.Driver;
import com.travelplatform.entity.Place;
import com.travelplatform.repository.DriverRepository;
import com.travelplatform.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceServiceTest {

    private PlaceRepository places;
    private DriverRepository drivers;
    private CityService cities;
    private PlaceService service;
    private Driver driver;

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        drivers = mock(DriverRepository.class);
        cities = mock(CityService.class);
        service = new PlaceService(places, drivers, cities);
        driver = new Driver();
        driver.setId(9L);
        driver.setVerificationStatus(Driver.Verification.UNDER_REVIEW);
        when(drivers.findById(9L)).thenReturn(Optional.of(driver));
        when(cities.zoneForPlace(any(Place.class), any(Boolean.class))).thenAnswer(i -> {
            Place p = i.getArgument(0);
            City z = new City();
            z.setId(100 + p.getId());
            z.setName(p.getName());
            z.setActive(i.getArgument(1));
            z.setRadiusKm(p.getRadiusKm());
            return z;
        });
        when(places.findExact(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(places.saveAndFlush(any(Place.class))).thenAnswer(i -> {
            Place p = i.getArgument(0);
            p.setId(77L);
            return p;
        });
    }

    private static Place listed(long id, String name) {
        Place p = new Place();
        p.setId(id);
        p.setState("Tamil Nadu");
        p.setDistrict("Kancheepuram");
        p.setName(name);
        p.setVerified(true);
        p.setRadiusKm(12);
        return p;
    }

    @Test
    @DisplayName("picking a listed town sets the driver's place and zone, and the zone is switched on")
    void listedTown() {
        when(places.findById(13L)).thenReturn(Optional.of(listed(13, "Kanchipuram")));

        Map<String, Object> area = service.setHomeArea(9L, new PlaceService.HomeAreaRequest(13L, null, null, null, null, null));

        assertEquals(113L, area.get("zoneId"));
        assertEquals("Kanchipuram", area.get("zoneName"));
        assertEquals(true, area.get("zoneActive"));
        assertEquals(13L, driver.getHomePlaceId());
        assertEquals(113L, driver.getHomeZoneId());
        verify(drivers).save(driver);
    }

    @Test
    @DisplayName("a town from the map is added unverified; its zone waits until the driver is approved")
    void customTownWaitsForApproval() {
        Map<String, Object> area = service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(null, "andhra pradesh", "Chittoor", "  Puttur ", 13.442, 79.553));

        ArgumentCaptor<Place> saved = ArgumentCaptor.forClass(Place.class);
        verify(places).saveAndFlush(saved.capture());
        assertEquals("Andhra Pradesh", saved.getValue().getState());
        assertEquals("Puttur", saved.getValue().getName());
        assertFalse(saved.getValue().isVerified());
        assertEquals(false, area.get("zoneActive"));
    }

    @Test
    @DisplayName("an approved driver's map town switches its zone on at once")
    void customTownApprovedDriver() {
        driver.setVerificationStatus(Driver.Verification.APPROVED);
        Map<String, Object> area = service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(null, "Andhra Pradesh", "Chittoor", "Puttur", 13.442, 79.553));
        assertEquals(true, area.get("zoneActive"));
    }

    @Test
    @DisplayName("bad choices are refused with a message the driver can act on")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.setHomeArea(9L, null));
        assertThrows(IllegalArgumentException.class, () -> service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(999L, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(null, "Atlantis", "X", "Y", 13.0, 80.0)));
        assertThrows(IllegalArgumentException.class, () -> service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(null, "Tamil Nadu", "Chennai", "T0wn<script>", 13.0, 80.0)));
        // outside India
        assertThrows(IllegalArgumentException.class, () -> service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(null, "Tamil Nadu", "Chennai", "Somewhere", 51.5, -0.1)));
        // no coordinates
        assertThrows(IllegalArgumentException.class, () -> service.setHomeArea(9L,
                new PlaceService.HomeAreaRequest(null, "Tamil Nadu", "Chennai", "Somewhere", null, null)));
        verify(drivers, never()).save(any());
    }

    @Test
    @DisplayName("approval switches on the zone of the driver's town")
    void approvalActivates() {
        Place custom = listed(77, "Puttur");
        custom.setVerified(false);
        when(places.findById(77L)).thenReturn(Optional.of(custom));
        driver.setHomePlaceId(77L);
        driver.setHomeZoneId(177L);

        service.onDriverApproved(driver);

        verify(cities).zoneForPlace(custom, true);
    }

    @Test
    @DisplayName("a driver with no area chosen says so")
    void noAreaChosen() {
        assertEquals(Map.of("chosen", false), service.homeAreaOf(9L));
        verify(cities, never()).zoneForPlace(any(), eq(false));
        assertTrue(true);
    }
}
