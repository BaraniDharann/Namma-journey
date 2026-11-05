package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusForms.SeatForm;
import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seat classes, layout checks and the bus type label. */
class BusLayoutRulesTest {

    private static SeatForm seater(String label, int deck, int row, int col) {
        return new SeatForm(label, deck, row, col, "SEATER", null, null, false);
    }

    private static SeatForm sleeper(String label, int deck, int row, int col, String berth, String pair) {
        return new SeatForm(label, deck, row, col, "SLEEPER", berth, pair, false);
    }

    private static String error(List<SeatForm> seats) {
        return assertThrows(IllegalArgumentException.class, () -> BusLayoutRules.validate(seats)).getMessage();
    }

    @Test
    @DisplayName("seat class comes from kind, deck and berth")
    void seatClass() {
        assertEquals(BusSeatClass.SEATER, BusSeatClass.of(BusSeat.Kind.SEATER, null, 0));
        assertEquals(BusSeatClass.SEATER, BusSeatClass.of(BusSeat.Kind.SEATER, null, 1));
        assertEquals(BusSeatClass.LOWER_SINGLE, BusSeatClass.of(BusSeat.Kind.SLEEPER, BusSeat.Berth.SINGLE, 0));
        assertEquals(BusSeatClass.LOWER_DOUBLE, BusSeatClass.of(BusSeat.Kind.SLEEPER, BusSeat.Berth.DOUBLE, 0));
        assertEquals(BusSeatClass.UPPER_SINGLE, BusSeatClass.of(BusSeat.Kind.SLEEPER, BusSeat.Berth.SINGLE, 1));
        assertEquals(BusSeatClass.UPPER_DOUBLE, BusSeatClass.of(BusSeat.Kind.SLEEPER, BusSeat.Berth.DOUBLE, 1));
        assertEquals(BusSeatClass.UPPER_DOUBLE, BusSeatClass.parse(" upper_double "));
        assertNull(BusSeatClass.parse("FIRST"));
    }

    @Test
    @DisplayName("a valid layout comes back as seats, with pairs kept and labels trimmed")
    void validLayout() {
        List<BusSeat> seats = BusLayoutRules.validate(List.of(
                sleeper(" L1 ", 0, 0, 0, "single", null),
                sleeper("L2", 0, 0, 2, "SINGLE", "l3"),
                sleeper("L3", 0, 0, 3, "SINGLE", "L2"),
                new SeatForm("U1", 1, 0, 0, "SLEEPER", "DOUBLE", null, true)));

        assertEquals(4, seats.size());
        assertEquals("L1", seats.get(0).getLabel());
        assertEquals("L3", seats.get(1).getPairLabel());
        assertEquals("L2", seats.get(2).getPairLabel());
        assertTrue(seats.get(3).isLadiesOnly());
        assertEquals(BusSeatClass.UPPER_DOUBLE, seats.get(3).seatClass());
    }

    @Test
    @DisplayName("1 to 60 seats")
    void seatCount() {
        assertTrue(error(List.of()).contains("at least one"));
        List<SeatForm> many = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            many.add(seater("S" + i, 0, i / 5, i % 5));
        }
        assertTrue(error(many).contains("at most 60"));
        assertEquals(60, BusLayoutRules.validate(many.subList(0, 60)).size());
    }

    @Test
    @DisplayName("labels are unique ignoring case, and positions are unique per deck")
    void uniqueLabelsAndPositions() {
        assertTrue(error(List.of(seater("A1", 0, 0, 0), seater("a1", 0, 0, 1))).contains("twice"));
        assertTrue(error(List.of(seater("A1", 0, 0, 0), seater("A2", 0, 0, 0))).contains("overlap"));
        // The same cell on the other deck is fine.
        assertEquals(2, BusLayoutRules.validate(List.of(seater("A1", 0, 0, 0), seater("A2", 1, 0, 0))).size());
    }

    @Test
    @DisplayName("a sleeper covers its row and the next one in its column")
    void sleeperSpansTwoRows() {
        assertTrue(error(List.of(sleeper("L1", 0, 0, 0, "SINGLE", null), seater("S1", 0, 1, 0))).contains("overlap"));
        assertTrue(error(List.of(sleeper("L1", 0, 0, 0, "SINGLE", null), sleeper("L2", 0, 1, 0, "SINGLE", null)))
                .contains("overlap"));
        // Sleepers on rows 0 and 2, and a seater below an odd row, all fit.
        assertEquals(3, BusLayoutRules.validate(List.of(sleeper("L1", 0, 0, 0, "SINGLE", null),
                sleeper("L2", 0, 2, 0, "SINGLE", null), seater("S1", 0, 5, 0))).size());
    }

    @Test
    @DisplayName("deck must be 0 or 1, and the seat inside the grid")
    void deckAndGrid() {
        assertTrue(error(List.of(seater("A1", 2, 0, 0))).contains("deck"));
        assertTrue(error(List.of(seater("A1", 0, -1, 0))).contains("grid"));
        assertTrue(error(List.of(new SeatForm("A1", 0, null, 0, "SEATER", null, null, false))).contains("grid"));
    }

    @Test
    @DisplayName("a seater has no berth and a sleeper needs one")
    void kindAndBerth() {
        assertTrue(error(List.of(new SeatForm("A1", 0, 0, 0, "SEATER", "SINGLE", null, false))).contains("no berth"));
        assertTrue(error(List.of(sleeper("A1", 0, 0, 0, null, null))).contains("berth"));
        assertTrue(error(List.of(new SeatForm("A1", 0, 0, 0, "BENCH", null, null, false))).contains("seater or sleeper"));
    }

    @Test
    @DisplayName("pairs must exist, point both ways and stay on one deck")
    void pairs() {
        assertTrue(error(List.of(sleeper("L1", 0, 0, 0, "SINGLE", "L9"))).contains("not in the layout"));
        assertTrue(error(List.of(sleeper("L1", 0, 0, 0, "SINGLE", "L1"))).contains("itself"));
        assertTrue(error(List.of(sleeper("L1", 0, 0, 0, "SINGLE", "L2"), sleeper("L2", 0, 0, 1, "SINGLE", null)))
                .contains("must be paired with L1"));
        assertTrue(error(List.of(sleeper("L1", 0, 0, 0, "SINGLE", "U1"), sleeper("U1", 1, 0, 0, "SINGLE", "L1")))
                .contains("different decks"));
    }

    private static List<BusSeat> seats(List<SeatForm> forms) {
        return BusLayoutRules.validate(forms);
    }

    @Test
    @DisplayName("type label: AC, kind and the 2+1 / 2+2 split from the fullest lower row")
    void typeLabel() {
        // 2+1 sleeper: one berth left, aisle at col 1, two berths right.
        List<BusSeat> sleeper21 = seats(List.of(sleeper("L1", 0, 0, 0, "SINGLE", null),
                sleeper("L2", 0, 0, 2, "SINGLE", "L3"), sleeper("L3", 0, 0, 3, "SINGLE", "L2"),
                sleeper("U1", 1, 0, 0, "SINGLE", null)));
        assertEquals("A/C Sleeper (2+1)", BusLayoutRules.typeLabel(true, sleeper21));

        // 2+2 seater: aisle at col 2.
        List<BusSeat> seater22 = seats(List.of(seater("1", 0, 0, 0), seater("2", 0, 0, 1),
                seater("3", 0, 0, 3), seater("4", 0, 0, 4), seater("5", 0, 1, 0)));
        assertEquals("Non-A/C Seater (2+2)", BusLayoutRules.typeLabel(false, seater22));

        List<BusSeat> mixed = seats(List.of(seater("1", 0, 0, 0), sleeper("U1", 1, 0, 0, "SINGLE", null)));
        assertEquals("Non-A/C Seater / Sleeper", BusLayoutRules.typeLabel(false, mixed));

        // No aisle to split at: no suffix.
        List<BusSeat> oneBlock = seats(List.of(seater("1", 0, 0, 0), seater("2", 0, 0, 1)));
        assertEquals("A/C Seater", BusLayoutRules.typeLabel(true, oneBlock));
        assertEquals("A/C", BusLayoutRules.typeLabel(true, List.of()));
    }
}
