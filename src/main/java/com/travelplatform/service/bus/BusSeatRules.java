package com.travelplatform.service.bus;

import com.travelplatform.entity.BusSeat;
import com.travelplatform.entity.BusSeatAllocation;
import com.travelplatform.entity.BusSeatAllocation.Gender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which seats a customer can take for a stretch of the route, and who may sit where.
 *
 * <p>A seat is taken for the legs [fromSeq, toSeq) when an allocation sharing a leg is CONFIRMED or
 * BLOCKED, or HELD and not yet expired. The <b>gender rule</b>: a ladies-only seat needs a woman;
 * when the seat next to it (its pair) is held or sold to someone else for an overlapping stretch,
 * the passenger must be of that person's gender. Both seats of a pair booked together may be anyone
 * (a family). The seat map shows the same rule as FEMALE_ONLY / MALE_ONLY; the server checks it
 * again during the hold.
 */
public final class BusSeatRules {

    public enum State {
        AVAILABLE, SOLD, BOOKED_F, BOOKED_M, FEMALE_ONLY, MALE_ONLY;

        public boolean selectable() {
            return this == AVAILABLE || this == FEMALE_ONLY || this == MALE_ONLY;
        }
    }

    /** Statuses that can occupy a seat (a HELD row only until it expires). */
    public static final Set<BusSeatAllocation.Status> LIVE = EnumSet.of(BusSeatAllocation.Status.HELD,
            BusSeatAllocation.Status.CONFIRMED, BusSeatAllocation.Status.BLOCKED);

    private BusSeatRules() {
    }

    public static String key(String label) {
        return label == null ? "" : label.trim().toUpperCase(Locale.ROOT);
    }

    /** Allocations grouped by seat (upper-case label). */
    public static Map<String, List<BusSeatAllocation>> bySeat(Collection<BusSeatAllocation> allocations) {
        return allocations.stream().collect(Collectors.groupingBy(a -> key(a.getSeatLabel())));
    }

    /** True when [aFrom, aTo) and [bFrom, bTo) share a leg; adjacent stretches do not. */
    public static boolean overlaps(int aFrom, int aTo, int bFrom, int bTo) {
        return aFrom < bTo && bFrom < aTo;
    }

    /** True when this allocation takes its seat right now. */
    public static boolean occupies(BusSeatAllocation a, LocalDateTime now) {
        return switch (a.getStatus()) {
            case CONFIRMED, BLOCKED -> true;
            case HELD -> a.getExpiresAt() != null && a.getExpiresAt().isAfter(now);
            default -> false;
        };
    }

    /** The allocations taking this seat for any leg of [from, to), other than those of {@code exceptTicket}. */
    public static List<BusSeatAllocation> occupying(Map<String, List<BusSeatAllocation>> bySeat, String label,
                                                    int from, int to, LocalDateTime now, UUID exceptTicket) {
        if (label == null) {
            return List.of();
        }
        return bySeat.getOrDefault(key(label), List.of()).stream()
                .filter(a -> occupies(a, now) && overlaps(a.getFromSeq(), a.getToSeq(), from, to))
                .filter(a -> exceptTicket == null || !exceptTicket.equals(a.getTicketId()))
                .toList();
    }

    /** Genders of the passengers next to this seat for the stretch (a blocked seat has none). */
    static Set<Gender> pairGenders(BusSeat seat, Map<String, List<BusSeatAllocation>> bySeat, int from, int to,
                                   LocalDateTime now) {
        Set<Gender> genders = EnumSet.noneOf(Gender.class);
        for (BusSeatAllocation a : occupying(bySeat, seat.getPairLabel(), from, to, now, null)) {
            if (a.getGender() != null) {
                genders.add(a.getGender());
            }
        }
        return genders;
    }

    /** What the seat map shows for a seat. {@code fare} null: the seat's class is not sold for this stretch. */
    public static State state(BusSeat seat, BigDecimal fare, Map<String, List<BusSeatAllocation>> bySeat,
                              int from, int to, LocalDateTime now) {
        if (fare == null) {
            return State.SOLD;
        }
        List<BusSeatAllocation> own = occupying(bySeat, seat.getLabel(), from, to, now, null);
        if (!own.isEmpty()) {
            Set<Gender> sold = new HashSet<>();
            for (BusSeatAllocation a : own) {
                sold.add(a.getStatus() == BusSeatAllocation.Status.CONFIRMED ? a.getGender() : null);
            }
            if (sold.size() == 1 && sold.contains(Gender.F)) {
                return State.BOOKED_F;
            }
            if (sold.size() == 1 && sold.contains(Gender.M)) {
                return State.BOOKED_M;
            }
            return State.SOLD; // held for payment, blocked by the owner, or a mix
        }
        Set<Gender> next = pairGenders(seat, bySeat, from, to, now);
        if (next.size() > 1) {
            return State.SOLD; // a woman and a man next to it on different stretches: nobody fits
        }
        if (next.contains(Gender.F)) {
            return State.FEMALE_ONLY;
        }
        if (next.contains(Gender.M)) {
            return seat.isLadiesOnly() ? State.SOLD : State.MALE_ONLY;
        }
        return seat.isLadiesOnly() ? State.FEMALE_ONLY : State.AVAILABLE;
    }

    /**
     * Refuses a passenger the gender rule does not allow on this seat; {@code heldTogether} are the
     * seat keys of the same booking (their pairs do not count).
     */
    public static void checkGender(BusSeat seat, Gender gender, Set<String> heldTogether,
                                   Map<String, List<BusSeatAllocation>> bySeat, int from, int to, LocalDateTime now) {
        if (seat.isLadiesOnly() && gender != Gender.F) {
            throw new IllegalArgumentException("Seat " + seat.getLabel() + " is reserved for women");
        }
        if (seat.getPairLabel() == null || heldTogether.contains(key(seat.getPairLabel()))) {
            return;
        }
        for (Gender next : pairGenders(seat, bySeat, from, to, now)) {
            if (next != gender) {
                String who = next == Gender.F ? "woman" : "man";
                throw new IllegalArgumentException("Seat " + seat.getLabel() + " is next to a " + who
                        + ", so only a " + who + " can book it");
            }
        }
    }
}
