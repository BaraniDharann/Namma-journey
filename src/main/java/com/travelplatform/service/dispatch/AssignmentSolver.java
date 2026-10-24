package com.travelplatform.service.dispatch;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pairs waiting rides with free drivers so that the whole city's pickups cost the least, instead
 * of each ride grabbing its own nearest driver and leaving the next ride with someone far away.
 *
 * <p>Plain Java, no I/O, so every rule here is unit-tested against brute force:
 * <ol>
 *   <li><b>Split.</b> Union-find over rides and drivers joined by a candidate pair. Rides on
 *       opposite sides of a city never share a candidate, so one big problem falls apart into many
 *       small independent ones - most of them a single ride.</li>
 *   <li><b>Hungarian</b> (Kuhn-Munkres, potentials and shortest augmenting paths) for a group of
 *       up to {@value #HUNGARIAN_MAX} rides: the exact minimum, O(rows² × columns).</li>
 *   <li><b>Greedy</b> above that: cheapest pair first. Not optimal, but O(E log E), and a group
 *       that large only happens in a surge where speed matters more than the last few seconds.</li>
 * </ol>
 *
 * <p>Matching the most rides comes before saving cost: a pair that does not exist is priced far
 * above any real total, so the minimum never leaves a ride unmatched that could have had someone.
 */
@Component
public class AssignmentSolver {

    /** Largest group, in rides, solved exactly. About 30 ms at the limit. */
    public static final int HUNGARIAN_MAX = 300;

    /** Upper bound for the price of a missing pair. The price actually used is far smaller, see {@link #missingPairCost}. */
    static final long INF = Long.MAX_VALUE / 4;

    /** One candidate pair: this driver could take this ride, at this cost (lower is better). */
    public record Edge(UUID ride, long driver, long cost) {
    }

    /**
     * @return ride -> driver. Each ride at most once, each driver at most once, and only pairs that
     *         were offered as edges. Rides with no pair are simply absent.
     */
    public Map<UUID, Long> solve(List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            return Map.of();
        }
        // The same pair twice keeps its cheaper cost.
        Map<UUID, Integer> rideIndex = new LinkedHashMap<>();
        Map<Long, Integer> driverIndex = new LinkedHashMap<>();
        Map<String, Edge> unique = new LinkedHashMap<>();
        for (Edge e : edges) {
            rideIndex.putIfAbsent(e.ride(), rideIndex.size());
            driverIndex.putIfAbsent(e.driver(), driverIndex.size());
            unique.merge(e.ride() + "/" + e.driver(), e, (a, b) -> a.cost() <= b.cost() ? a : b);
        }

        int rides = rideIndex.size();
        UnionFind groups = new UnionFind(rides + driverIndex.size());
        for (Edge e : unique.values()) {
            groups.union(rideIndex.get(e.ride()), rides + driverIndex.get(e.driver()));
        }
        Map<Integer, List<Edge>> byGroup = new LinkedHashMap<>();
        for (Edge e : unique.values()) {
            byGroup.computeIfAbsent(groups.find(rideIndex.get(e.ride())), k -> new ArrayList<>()).add(e);
        }

        Map<UUID, Long> result = new HashMap<>();
        for (List<Edge> group : byGroup.values()) {
            long groupRides = group.stream().map(Edge::ride).distinct().count();
            result.putAll(groupRides <= HUNGARIAN_MAX ? hungarian(group) : greedy(group));
        }
        return result;
    }

    /** Cheapest pair first; ties broken by ride then driver so a round is reproducible. */
    static Map<UUID, Long> greedy(List<Edge> edges) {
        List<Edge> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparingLong(Edge::cost).thenComparing(Edge::ride).thenComparingLong(Edge::driver));
        Map<UUID, Long> taken = new HashMap<>();
        Set<Long> busy = new HashSet<>();
        for (Edge e : sorted) {
            if (!taken.containsKey(e.ride()) && !busy.contains(e.driver())) {
                taken.put(e.ride(), e.driver());
                busy.add(e.driver());
            }
        }
        return taken;
    }

    /**
     * Exact minimum for one group. The matrix is rectangular with the smaller side as rows, so a
     * ride with ten candidates does not grow it into a ten-times-larger square: O(rows² × columns).
     */
    static Map<UUID, Long> hungarian(List<Edge> edges) {
        List<UUID> rideIds = edges.stream().map(Edge::ride).distinct().toList();
        List<Long> driverIds = edges.stream().map(Edge::driver).distinct().toList();
        boolean ridesAreRows = rideIds.size() <= driverIds.size();
        int n = ridesAreRows ? rideIds.size() : driverIds.size();
        int m = ridesAreRows ? driverIds.size() : rideIds.size();

        Map<UUID, Integer> ri = new HashMap<>();
        for (int i = 0; i < rideIds.size(); i++) {
            ri.put(rideIds.get(i), i);
        }
        Map<Long, Integer> di = new HashMap<>();
        for (int i = 0; i < driverIds.size(); i++) {
            di.put(driverIds.get(i), i);
        }

        long missing = missingPairCost(edges, n);
        long[][] a = new long[n + 1][m + 1];
        boolean[][] real = new boolean[n + 1][m + 1];
        for (long[] row : a) {
            java.util.Arrays.fill(row, missing);
        }
        for (Edge e : edges) {
            int r = ri.get(e.ride()) + 1;
            int d = di.get(e.driver()) + 1;
            int row = ridesAreRows ? r : d;
            int col = ridesAreRows ? d : r;
            a[row][col] = e.cost();
            real[row][col] = true;
        }

        int[] p = solveRectangular(a, n, m);

        Map<UUID, Long> result = new HashMap<>();
        for (int col = 1; col <= m; col++) {
            int row = p[col];
            if (row == 0 || !real[row][col]) {
                continue; // an unmatched column, or a pair that was only padding
            }
            UUID ride = rideIds.get((ridesAreRows ? row : col) - 1);
            long driver = driverIds.get((ridesAreRows ? col : row) - 1);
            result.put(ride, driver);
        }
        return result;
    }

    /**
     * The price of a pair that does not exist: above the widest possible spread of real totals, so
     * one more matched ride always beats any saving, yet small enough that the potentials (which
     * grow by up to this much per augmentation) cannot overflow. {@link #INF} itself would: a few
     * augmentations through padded pairs add up past {@code Long.MAX_VALUE}.
     */
    private static long missingPairCost(List<Edge> edges, int pairs) {
        long maxAbs = 0;
        for (Edge e : edges) {
            maxAbs = Math.max(maxAbs, Math.abs(e.cost()));
        }
        long spread = 2L * (pairs + 1) * (maxAbs + 1) + 1;
        return spread > 0 && spread < INF / (pairs + 2) ? spread : INF / (pairs + 2);
    }

    /**
     * Kuhn-Munkres with potentials, rows 1..n each matched to a distinct column 1..m (n ≤ m).
     *
     * @return p where p[column] is the row matched to it, 0 for none
     */
    private static int[] solveRectangular(long[][] a, int n, int m) {
        long[] u = new long[n + 1];
        long[] v = new long[m + 1];
        int[] p = new int[m + 1];
        int[] way = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            long[] minv = new long[m + 1];
            java.util.Arrays.fill(minv, Long.MAX_VALUE);
            boolean[] used = new boolean[m + 1];
            do {
                used[j0] = true;
                int i0 = p[j0];
                long delta = Long.MAX_VALUE;
                int j1 = 0;
                for (int j = 1; j <= m; j++) {
                    if (!used[j]) {
                        long cur = a[i0][j] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        return p;
    }

    /** Disjoint sets with path compression and union by rank: near-constant time per operation. */
    private static final class UnionFind {
        private final int[] parent;
        private final byte[] rank;

        UnionFind(int size) {
            parent = new int[size];
            rank = new byte[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            int root = x;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[x] != root) {
                int next = parent[x];
                parent[x] = root;
                x = next;
            }
            return root;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) {
                return;
            }
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
        }
    }
}
