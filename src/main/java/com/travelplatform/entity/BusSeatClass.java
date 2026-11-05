package com.travelplatform.entity;

import java.util.Locale;

/**
 * The price group of a seat, derived from its layout and never entered: every seater is SEATER;
 * a sleeper is LOWER_/UPPER_ by deck and _SINGLE/_DOUBLE by berth. Fares are set per class.
 */
public enum BusSeatClass {
    SEATER, LOWER_SINGLE, LOWER_DOUBLE, UPPER_SINGLE, UPPER_DOUBLE;

    /** The class of a seat; a sleeper without a berth counts as a single berth. */
    public static BusSeatClass of(BusSeat.Kind kind, BusSeat.Berth berth, int deck) {
        if (kind == null || kind == BusSeat.Kind.SEATER) {
            return SEATER;
        }
        boolean dbl = berth == BusSeat.Berth.DOUBLE;
        if (deck == 1) {
            return dbl ? UPPER_DOUBLE : UPPER_SINGLE;
        }
        return dbl ? LOWER_DOUBLE : LOWER_SINGLE;
    }

    /** "lower_single" or "LOWER_SINGLE" → LOWER_SINGLE; null for anything else. */
    public static BusSeatClass parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
