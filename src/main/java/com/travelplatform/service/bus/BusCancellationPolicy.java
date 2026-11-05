package com.travelplatform.service.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.dto.bus.BusForms.Slab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A route's cancellation slabs, stored as JSON on the route and copied onto each ticket. Slabs are
 * kept ordered by minHours, highest first; the charge for a cancellation is the first slab whose
 * minHours is at or below the hours left before boarding, so a 0-hour slab must exist. Charges may
 * only grow as departure gets closer.
 */
public final class BusCancellationPolicy {

    /** The template every new route starts from. */
    public static final List<Slab> DEFAULT = List.of(new Slab(48, 10), new Slab(24, 25), new Slab(12, 50), new Slab(0, 100));

    static final int MAX_SLABS = 8;
    static final int MAX_HOURS = 720;

    private static final ObjectMapper JSON = new ObjectMapper();

    private BusCancellationPolicy() {
    }

    /** The slabs sorted highest minHours first; the default when none are given. */
    public static List<Slab> normalize(List<Slab> slabs) {
        if (slabs == null || slabs.isEmpty()) {
            return DEFAULT;
        }
        if (slabs.size() > MAX_SLABS) {
            throw new IllegalArgumentException("Use at most " + MAX_SLABS + " cancellation slabs");
        }
        Set<Integer> hours = new HashSet<>();
        for (Slab s : slabs) {
            if (s == null || s.minHours() == null || s.percent() == null) {
                throw new IllegalArgumentException("Each cancellation slab needs hours and a percentage");
            }
            if (s.minHours() < 0 || s.minHours() > MAX_HOURS) {
                throw new IllegalArgumentException("Cancellation hours must be between 0 and " + MAX_HOURS);
            }
            if (s.percent() < 0 || s.percent() > 100) {
                throw new IllegalArgumentException("A cancellation charge must be between 0% and 100%");
            }
            if (!hours.add(s.minHours())) {
                throw new IllegalArgumentException("Two cancellation slabs start at " + s.minHours() + " hours");
            }
        }
        if (!hours.contains(0)) {
            throw new IllegalArgumentException("Add a slab for 0 hours: the charge for cancelling just before boarding");
        }
        List<Slab> sorted = new ArrayList<>(slabs);
        sorted.sort(Comparator.comparing(Slab::minHours).reversed());
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).percent() < sorted.get(i - 1).percent()) {
                throw new IllegalArgumentException("The charge cannot drop as departure gets closer ("
                        + sorted.get(i).minHours() + " hours: " + sorted.get(i).percent() + "%, "
                        + sorted.get(i - 1).minHours() + " hours: " + sorted.get(i - 1).percent() + "%)");
            }
        }
        return List.copyOf(sorted);
    }

    public static String toJson(List<Slab> slabs) {
        try {
            return JSON.writeValueAsString(slabs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<Slab> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT;
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Slab>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable cancellation policy", e);
        }
    }
}
