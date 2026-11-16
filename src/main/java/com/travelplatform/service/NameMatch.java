package com.travelplatform.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Is the name a person typed the same person as the name on their Aadhaar?
 *
 * <p>Indian names are written many ways: initials before or after ("S. Rajesh Kumar" / "Rajesh
 * Kumar S"), the father's name left off, one letter spelt differently. So: initials are ignored,
 * word order is ignored, the shorter name's words must all appear in the longer one, and a word of
 * five or more letters may differ by one letter.
 */
public final class NameMatch {

    private NameMatch() {
    }

    public static boolean sameName(String typed, String onCard) {
        List<String> a = words(typed);
        List<String> b = words(onCard);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        List<String> shorter = a.size() <= b.size() ? a : b;
        List<String> longer = shorter == a ? b : a;
        for (String w : shorter) {
            if (longer.stream().noneMatch(other -> close(w, other))) {
                return false;
            }
        }
        return true;
    }

    /** Upper-case words of two letters or more; initials and punctuation dropped. */
    static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        if (name == null) {
            return out;
        }
        for (String w : name.toUpperCase(Locale.ROOT).split("[^A-Z]+")) {
            if (w.length() >= 2) {
                out.add(w);
            }
        }
        return out;
    }

    private static boolean close(String x, String y) {
        if (x.equals(y)) {
            return true;
        }
        return Math.min(x.length(), y.length()) >= 5 && editDistance(x, y) <= 1;
    }

    private static int editDistance(String x, String y) {
        int[] prev = new int[y.length() + 1];
        int[] cur = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= x.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int cost = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[y.length()];
    }
}
