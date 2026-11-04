package com.travelplatform.service.bus;

import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusSeatAllocation.Gender;
import com.travelplatform.service.bus.BusSeatRules.State;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Seat states on the map and the gender rule, for stretches of a route with stops 0, 1, 2. */
class BusSeatRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final BigDecimal FARE = new BigDecimal("650.00");

    private static BusSeat seat(String label, String pair, boolean ladies) {
        BusSeat s = new BusSeat();
        s.setLabel(label);
        s.setPairLabel(pair);
        s.setLadiesOnly(ladies);
        s.setKind(BusSeat.Kind.SLEEPER);
        s.setBerth(BusSeat.Berth.SINGLE);
        return s;
    }

    private static BusSeatAllocation alloc(String label, int from, int to, BusSeatAllocation.Status status, Gender g,
                                           LocalDateTime expires) {
        BusSeatAllocation a = new BusSeatAllocation();
        a.setSeatLabel(label);
        a.setFromSeq(from);
        a.setToSeq(to);
        a.setStatus(status);
        a.setGender(g);
        a.setTicketId(UUID.randomUUID());
        a.setExpiresAt(expires);
        return a;
    }

    private static Map<String, List<BusSeatAllocation>> taken(BusSeatAllocation... rows) {
        return BusSeatRules.bySeat(List.of(rows));
    }

    @Test
    @DisplayName("a seat sold Chennai(0)→Trichy(1) is free for Trichy(1)→Madurai(2) but not for 0→2")
    void segments() {
        BusSeat u1 = seat("U1", null, false);
        var t = taken(alloc("U1", 0, 1, BusSeatAllocation.Status.CONFIRMED, Gender.M, null));
        assertEquals(State.AVAILABLE, BusSeatRules.state(u1, FARE, t, 1, 2, NOW));
        assertEquals(State.BOOKED_M, BusSeatRules.state(u1, FARE, t, 0, 2, NOW));
    }

    @Test
    @DisplayName("held shows as sold until it expires; blocked is sold; no fare is sold")
    void heldBlockedNoFare() {
        BusSeat u1 = seat("U1", null, false);
        assertEquals(State.SOLD, BusSeatRules.state(u1, FARE,
                taken(alloc("U1", 0, 2, BusSeatAllocation.Status.HELD, Gender.F, NOW.plusMinutes(5))), 0, 2, NOW));
        assertEquals(State.AVAILABLE, BusSeatRules.state(u1, FARE,
                taken(alloc("U1", 0, 2, BusSeatAllocation.Status.HELD, Gender.F, NOW.minusSeconds(1))), 0, 2, NOW));
        assertEquals(State.SOLD, BusSeatRules.state(u1, FARE,
                taken(alloc("U1", 0, 2, BusSeatAllocation.Status.BLOCKED, null, null)), 0, 2, NOW));
        assertEquals(State.SOLD, BusSeatRules.state(u1, null, taken(), 0, 2, NOW));
    }

    @Test
    @DisplayName("next to a woman: women only; next to a man: men only; a ladies' seat: women only")
    void pairStates() {
        BusSeat u1 = seat("U1", "U2", false);
        BusSeat l1 = seat("L1", null, true);
        assertEquals(State.FEMALE_ONLY, BusSeatRules.state(u1, FARE,
                taken(alloc("U2", 0, 2, BusSeatAllocation.Status.CONFIRMED, Gender.F, null)), 0, 2, NOW));
        assertEquals(State.MALE_ONLY, BusSeatRules.state(u1, FARE,
                taken(alloc("U2", 0, 2, BusSeatAllocation.Status.HELD, Gender.M, NOW.plusMinutes(3))), 0, 2, NOW));
        assertEquals(State.AVAILABLE, BusSeatRules.state(u1, FARE,
                taken(alloc("U2", 1, 2, BusSeatAllocation.Status.CONFIRMED, Gender.F, null)), 0, 1, NOW),
                "the woman next to it travels a different stretch");
        assertEquals(State.FEMALE_ONLY, BusSeatRules.state(l1, FARE, taken(), 0, 2, NOW));
    }

    @Test
    @DisplayName("gender rule: a man next to a woman is refused; a family pair in one hold is allowed")
    void genderRule() {
        BusSeat u1 = seat("U1", "U2", false);
        var womanNext = taken(alloc("U2", 0, 2, BusSeatAllocation.Status.CONFIRMED, Gender.F, null));
        assertThrows(IllegalArgumentException.class,
                () -> BusSeatRules.checkGender(u1, Gender.M, Set.of("U1"), womanNext, 0, 2, NOW));
        assertDoesNotThrow(() -> BusSeatRules.checkGender(u1, Gender.F, Set.of("U1"), womanNext, 0, 2, NOW));
        // Both seats of the pair in the same hold: anyone.
        assertDoesNotThrow(() -> BusSeatRules.checkGender(u1, Gender.M, Set.of("U1", "U2"), taken(), 0, 2, NOW));
        // The woman next to it gets off before this passenger gets on.
        var earlier = taken(alloc("U2", 0, 1, BusSeatAllocation.Status.CONFIRMED, Gender.F, null));
        assertDoesNotThrow(() -> BusSeatRules.checkGender(u1, Gender.M, Set.of("U1"), earlier, 1, 2, NOW));
        // Ladies' seat.
        assertThrows(IllegalArgumentException.class,
                () -> BusSeatRules.checkGender(seat("L1", null, true), Gender.M, Set.of("L1"), taken(), 0, 2, NOW));
    }
}
