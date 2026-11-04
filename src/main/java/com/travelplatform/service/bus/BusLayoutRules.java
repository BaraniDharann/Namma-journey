package com.travelplatform.service.bus;

import com.travelplatform.dto.bus.BusForms.SeatForm;
import com.travelplatform.entity.BusSeat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Checks a seat layout the owner sends and names the bus type ("A/C Sleeper (2+1)").
 *
 * <p>The layout is a grid per deck. A seater fills one cell; a sleeper is twice as long and fills
 * its row and the next one in its column (the frontend puts sleepers on rows 0, 2, 4...). No two
 * seats may share a cell. Labels are unique ignoring case, and a pair (the seat right next to this
 * one, for the gender rule) must point both ways, on the same deck.
 */
public final class BusLayoutRules {

    public static final int MAX_SEATS = 60;
    static final int MAX_ROW = 59;
    static final int MAX_COL = 9;
    private static final Pattern LABEL = Pattern.compile("^[A-Za-z0-9-]{1,8}$");

    private BusLayoutRules() {
    }

    /** The seats to store (without bus and owner), or IllegalArgumentException naming the first problem. */
    public static List<BusSeat> validate(List<SeatForm> forms) {
        if (forms == null || forms.isEmpty()) {
            throw new IllegalArgumentException("Add at least one seat");
        }
        if (forms.size() > MAX_SEATS) {
            throw new IllegalArgumentException("A bus can have at most " + MAX_SEATS + " seats");
        }
        Map<String, BusSeat> byKey = new HashMap<>();
        Map<String, String> cells = new HashMap<>();
        List<BusSeat> seats = new ArrayList<>();
        for (SeatForm f : forms) {
            if (f == null) {
                throw new IllegalArgumentException("A seat is empty");
            }
            String label = f.label() == null ? "" : f.label().trim();
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("Seat label \"" + label + "\" must be 1 to 8 letters or digits");
            }
            if (byKey.containsKey(key(label))) {
                throw new IllegalArgumentException("Seat " + label + " appears twice");
            }
            if (f.deck() == null || (f.deck() != 0 && f.deck() != 1)) {
                throw new IllegalArgumentException("Seat " + label + ": deck must be lower (0) or upper (1)");
            }
            if (f.row() == null || f.row() < 0 || f.row() > MAX_ROW || f.col() == null || f.col() < 0 || f.col() > MAX_COL) {
                throw new IllegalArgumentException("Seat " + label + " is outside the seat grid");
            }
            BusSeat.Kind kind = parse(BusSeat.Kind.class, f.kind());
            if (kind == null) {
                throw new IllegalArgumentException("Seat " + label + ": choose seater or sleeper");
            }
            BusSeat.Berth berth = null;
            if (kind == BusSeat.Kind.SEATER) {
                if (f.berth() != null && !f.berth().isBlank()) {
                    throw new IllegalArgumentException("Seat " + label + " is a seater, so it has no berth");
                }
            } else {
                berth = parse(BusSeat.Berth.class, f.berth());
                if (berth == null) {
                    throw new IllegalArgumentException("Sleeper " + label + ": choose a single or double berth");
                }
            }

            BusSeat s = new BusSeat();
            s.setLabel(label);
            s.setDeck(f.deck());
            s.setRowNo(f.row());
            s.setColNo(f.col());
            s.setKind(kind);
            s.setBerth(berth);
            s.setPairLabel(f.pairLabel() == null || f.pairLabel().isBlank() ? null : f.pairLabel().trim());
            s.setLadiesOnly(Boolean.TRUE.equals(f.ladiesOnly()));
            int length = kind == BusSeat.Kind.SLEEPER ? 2 : 1;
            for (int r = s.getRowNo(); r < s.getRowNo() + length; r++) {
                String other = cells.putIfAbsent(s.getDeck() + "|" + r + "|" + s.getColNo(), label);
                if (other != null) {
                    throw new IllegalArgumentException("Seats " + other + " and " + label + " overlap");
                }
            }
            byKey.put(key(label), s);
            seats.add(s);
        }

        for (BusSeat s : seats) {
            if (s.getPairLabel() == null) {
                continue;
            }
            BusSeat pair = byKey.get(key(s.getPairLabel()));
            if (pair == null) {
                throw new IllegalArgumentException("Seat " + s.getLabel() + " is paired with " + s.getPairLabel()
                        + ", which is not in the layout");
            }
            if (pair == s) {
                throw new IllegalArgumentException("Seat " + s.getLabel() + " cannot be paired with itself");
            }
            if (pair.getDeck() != s.getDeck()) {
                throw new IllegalArgumentException("Seats " + s.getLabel() + " and " + pair.getLabel()
                        + " are on different decks, so they cannot be a pair");
            }
            if (pair.getPairLabel() == null || !key(pair.getPairLabel()).equals(key(s.getLabel()))) {
                throw new IllegalArgumentException("Seat " + s.getLabel() + " is paired with " + pair.getLabel()
                        + ", so " + pair.getLabel() + " must be paired with " + s.getLabel());
            }
            s.setPairLabel(pair.getLabel());
        }
        return seats;
    }

    /**
     * "A/C Sleeper (2+1)", "Non-A/C Seater (2+2)", "A/C Seater / Sleeper". The 2+1 / 2+2 part comes
     * from the fullest row of the lower deck (the upper one if there is no lower deck), split at the
     * aisle; it is left out for mixed layouts or when the row is not two blocks.
     */
    public static String typeLabel(boolean ac, Collection<BusSeat> seats) {
        String prefix = ac ? "A/C" : "Non-A/C";
        if (seats == null || seats.isEmpty()) {
            return prefix;
        }
        boolean seater = seats.stream().anyMatch(s -> s.getKind() == BusSeat.Kind.SEATER);
        boolean sleeper = seats.stream().anyMatch(s -> s.getKind() == BusSeat.Kind.SLEEPER);
        if (seater && sleeper) {
            return prefix + " Seater / Sleeper";
        }
        String type = prefix + (sleeper ? " Sleeper" : " Seater");
        String split = split(seats);
        return split == null ? type : type + " (" + split + ")";
    }

    private static String split(Collection<BusSeat> seats) {
        boolean hasLower = seats.stream().anyMatch(s -> s.getDeck() == 0);
        int deck = hasLower ? 0 : 1;
        Map<Integer, List<Integer>> colsByRow = new TreeMap<>();
        for (BusSeat s : seats) {
            if (s.getDeck() == deck) {
                colsByRow.computeIfAbsent(s.getRowNo(), r -> new ArrayList<>()).add(s.getColNo());
            }
        }
        List<Integer> fullest = colsByRow.values().stream()
                .max((a, b) -> Integer.compare(a.size(), b.size())).orElse(List.of());
        List<Integer> cols = fullest.stream().sorted().toList();
        List<Integer> blocks = new ArrayList<>();
        int run = 0;
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0 && cols.get(i) - cols.get(i - 1) > 1) {
                blocks.add(run);
                run = 0;
            }
            run++;
        }
        if (run > 0) {
            blocks.add(run);
        }
        if (blocks.size() != 2) {
            return null;
        }
        return Math.max(blocks.get(0), blocks.get(1)) + "+" + Math.min(blocks.get(0), blocks.get(1));
    }

    private static String key(String label) {
        return label.toUpperCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
