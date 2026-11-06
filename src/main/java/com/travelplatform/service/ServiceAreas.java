package com.travelplatform.service;

import com.travelplatform.entity.City;
import com.travelplatform.entity.Owner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Where a travels owner says they run trips, chosen at signup: the whole of India, all of a state,
 * or one city or town. Stored as short codes in {@code owner_service_areas}:
 * <ul>
 *   <li>{@code INDIA}</li>
 *   <li>{@code STATE:Tamil Nadu}</li>
 *   <li>{@code CITY:Tamil Nadu:Madurai} (the state may be empty for a town the owner typed in)</li>
 * </ul>
 * It describes the business for the admin and the owner; it does not drive trip matching (City
 * rides use the admin's {@link City} zones).
 */
public final class ServiceAreas {

    /** The 28 states and 8 union territories, as the client lists them. */
    public static final List<String> STATES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
            "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
            "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi",
            "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    private static final Map<String, String> STATE_BY_KEY = STATES.stream()
            .collect(Collectors.toMap(s -> s.toLowerCase(Locale.ROOT), s -> s));

    /** A place name: letters (any script), spaces and . ' ( ) - ; no commas or colons. */
    private static final Pattern PLACE = Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} .'()\\-]{0,59}$");

    public static final int MAX_AREAS = 60;

    private ServiceAreas() {
    }

    /**
     * Checks and tidies the owner's choices: names are matched to the official state list, and
     * anything already covered is dropped (whole India covers everything; a whole state covers its
     * cities). Throws {@link IllegalArgumentException} with a message for the owner.
     */
    public static List<String> normalize(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        if (codes.size() > MAX_AREAS) {
            throw new IllegalArgumentException("Choose at most " + MAX_AREAS + " places, or pick a whole state instead");
        }
        Set<String> states = new LinkedHashSet<>();
        // key "state|city" lower-case -> code; "|" sorts after letters, so towns typed in with no
        // state come after the listed ones.
        Map<String, String> cities = new TreeMap<>();
        boolean india = false;
        for (String raw : codes) {
            String code = raw == null ? "" : raw.trim();
            if (code.equals("INDIA")) {
                india = true;
            } else if (code.startsWith("STATE:")) {
                states.add(state(code.substring(6)));
            } else if (code.startsWith("CITY:")) {
                String[] parts = code.substring(5).split(":", -1);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("A chosen place is not valid");
                }
                String state = parts[0].isBlank() ? "" : state(parts[0]);
                String city = parts[1].trim().replaceAll("\\s+", " ");
                if (!PLACE.matcher(city).matches()) {
                    throw new IllegalArgumentException("\"" + city + "\" is not a place name we can save");
                }
                cities.putIfAbsent((state + "|" + city).toLowerCase(Locale.ROOT), "CITY:" + state + ":" + city);
            } else {
                throw new IllegalArgumentException("A chosen place is not valid");
            }
        }
        if (india) {
            return List.of("INDIA");
        }
        List<String> out = new ArrayList<>();
        states.stream().sorted().forEach(s -> out.add("STATE:" + s));
        cities.values().stream()
                .filter(c -> !states.contains(c.substring(5, c.indexOf(':', 5))))
                .forEach(out::add);
        return out;
    }

    private static String state(String name) {
        String s = STATE_BY_KEY.get(name.trim().toLowerCase(Locale.ROOT));
        if (s == null) {
            throw new IllegalArgumentException("\"" + name.trim() + "\" is not an Indian state or union territory");
        }
        return s;
    }

    /** "Whole of India", "All of Tamil Nadu", "Madurai, Tamil Nadu" or just "Madurai". */
    public static String label(String code) {
        if ("INDIA".equals(code)) {
            return "Whole of India";
        }
        if (code.startsWith("STATE:")) {
            return "All of " + code.substring(6);
        }
        if (code.startsWith("CITY:")) {
            String[] parts = code.substring(5).split(":", -1);
            return parts.length == 2 && !parts[0].isEmpty() ? parts[1] + ", " + parts[0] : parts[parts.length - 1];
        }
        return code;
    }

    /**
     * What to show for an owner: their service areas, then any City-ride zones chosen on the
     * earlier signup form.
     */
    public static List<String> describe(Owner o) {
        List<String> out = new ArrayList<>();
        if (o.getServiceAreas() != null) {
            normalizeQuietly(o.getServiceAreas()).forEach(c -> out.add(label(c)));
        }
        if (o.getCities() != null) {
            o.getCities().stream().map(City::getName).sorted().filter(n -> !out.contains(n)).forEach(out::add);
        }
        return out;
    }

    /** Stored codes were checked on the way in; order them the same way for display. */
    private static List<String> normalizeQuietly(Collection<String> codes) {
        try {
            return normalize(codes);
        } catch (IllegalArgumentException e) {
            return codes.stream().sorted().toList();
        }
    }
}
