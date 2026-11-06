package com.travelplatform.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where an owner says they serve: whole India, whole states, or cities. */
class ServiceAreasTest {

    @Test
    @DisplayName("whole India covers everything else")
    void indiaWins() {
        assertEquals(List.of("INDIA"), ServiceAreas.normalize(List.of("CITY:Kerala:Kochi", "INDIA", "STATE:Goa")));
    }

    @Test
    @DisplayName("a whole state covers its cities; names are matched to the official state list")
    void stateCoversItsCities() {
        assertEquals(List.of("STATE:Kerala", "CITY:Tamil Nadu:Madurai"),
                ServiceAreas.normalize(List.of("STATE:KERALA", "CITY:Kerala:Kochi",
                        "CITY:tamil nadu:Madurai", "CITY:Tamil Nadu:madurai")));
    }

    @Test
    @DisplayName("unknown states, odd codes, names with commas or colons, and too many places are refused")
    void refusesBadInput() {
        assertThrows(IllegalArgumentException.class, () -> ServiceAreas.normalize(List.of("STATE:Atlantis")));
        assertThrows(IllegalArgumentException.class, () -> ServiceAreas.normalize(List.of("COUNTRY:India")));
        assertThrows(IllegalArgumentException.class, () -> ServiceAreas.normalize(List.of("CITY:Kerala:Kochi, Ernakulam")));
        assertThrows(IllegalArgumentException.class, () -> ServiceAreas.normalize(List.of("CITY:Kerala:a:b")));
        assertThrows(IllegalArgumentException.class, () -> ServiceAreas.normalize(List.of("CITY:Kerala:<script>")));
        assertThrows(IllegalArgumentException.class,
                () -> ServiceAreas.normalize(Collections.nCopies(ServiceAreas.MAX_AREAS + 1, "INDIA")));
    }

    @Test
    @DisplayName("a typed-in town needs no state, and Tamil script names are fine")
    void typedTownsAndOtherScripts() {
        assertEquals(List.of("CITY:Tamil Nadu:மதுரை", "CITY::Ooty"),
                ServiceAreas.normalize(List.of("CITY::Ooty", "CITY:Tamil Nadu:மதுரை")));
        assertEquals("Ooty", ServiceAreas.label("CITY::Ooty"));
        assertEquals("Whole of India", ServiceAreas.label("INDIA"));
        assertEquals("All of Tamil Nadu", ServiceAreas.label("STATE:Tamil Nadu"));
        assertEquals("Madurai, Tamil Nadu", ServiceAreas.label("CITY:Tamil Nadu:Madurai"));
        assertTrue(ServiceAreas.STATES.size() == 36);
    }
}
