package com.travelplatform.service.dispatch;

import com.travelplatform.service.dispatch.AssignmentSolver.Edge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solver decides who gets which ride, so it is checked against the one answer nobody can
 * argue with: every possible assignment, tried by brute force. "Best" means the most rides matched
 * first, then the lowest total cost - a ride left unmatched to save a few seconds elsewhere would
 * be a passenger waiting another round for nothing.
 */
class AssignmentSolverTest {

    private final AssignmentSolver solver = new AssignmentSolver();

    private static UUID ride(int i) {
        return new UUID(0L, i);
    }

    @Test
    @DisplayName("Hungarian matches brute force: most rides matched, then lowest total cost")
    void hungarianMatchesBruteForce() {
        Random random = new Random(42);
        for (int c = 0; c < 200; c++) {
            int rides = 2 + random.nextInt(5);
            int drivers = 2 + random.nextInt(5);
            List<Edge> edges = new ArrayList<>();
            long[][] cost = new long[rides][drivers];
            boolean[][] present = new boolean[rides][drivers];
            for (int r = 0; r < rides; r++) {
                for (int d = 0; d < drivers; d++) {
                    if (random.nextInt(100) < 65) {
                        // Waiting bonuses make real costs negative too.
                        cost[r][d] = random.nextInt(1200) - 300;
                        present[r][d] = true;
                        edges.add(new Edge(ride(r), d, cost[r][d]));
                    }
                }
            }

            Map<UUID, Long> result = solver.solve(edges);

            assertValidMatching(edges, result);
            long[] best = bruteForce(0, rides, drivers, cost, present, new boolean[drivers]);
            long total = 0;
            for (Map.Entry<UUID, Long> e : result.entrySet()) {
                total += cost[(int) e.getKey().getLeastSignificantBits()][e.getValue().intValue()];
            }
            assertEquals(best[0], result.size(), "case " + c + ": rides matched");
            assertEquals(best[1], total, "case " + c + ": total cost");
        }
    }

    /** {matched, cost}: the most rides matchable, and the least cost among assignments matching that many. */
    private static long[] bruteForce(int r, int rides, int drivers, long[][] cost, boolean[][] present, boolean[] used) {
        if (r == rides) {
            return new long[]{0, 0};
        }
        long[] best = bruteForce(r + 1, rides, drivers, cost, present, used); // ride r unmatched
        for (int d = 0; d < drivers; d++) {
            if (present[r][d] && !used[d]) {
                used[d] = true;
                long[] rest = bruteForce(r + 1, rides, drivers, cost, present, used);
                used[d] = false;
                long matched = rest[0] + 1;
                long total = rest[1] + cost[r][d];
                if (matched > best[0] || (matched == best[0] && total < best[1])) {
                    best = new long[]{matched, total};
                }
            }
        }
        return best;
    }

    @Test
    @DisplayName("a group above the Hungarian limit is solved greedily, and still validly")
    void greedyUsedAboveLimit() {
        int rides = AssignmentSolver.HUNGARIAN_MAX + 1;
        List<Edge> edges = new ArrayList<>();
        // A chain: ride i may take driver i or i+1, so all 301 rides are one connected group.
        for (int i = 0; i < rides; i++) {
            edges.add(new Edge(ride(i), i, 100 + (i % 7)));
            edges.add(new Edge(ride(i), i + 1, 90 + (i % 5)));
        }

        Map<UUID, Long> result = solver.solve(edges);

        assertValidMatching(edges, result);
        assertTrue(result.size() > rides / 2, "greedy still matches most rides, got " + result.size());
    }

    @Test
    @DisplayName("rides far apart are separate problems, each solved on its own")
    void componentsSolvedIndependently() {
        List<Edge> edges = List.of(
                // Group 1: R0 should take D2 so R1 can have D1.
                new Edge(ride(0), 1, 10), new Edge(ride(0), 2, 20), new Edge(ride(1), 1, 30),
                // Group 2: a single pair.
                new Edge(ride(2), 9, 500),
                // Group 3: two rides want one driver; the cheaper pairing wins.
                new Edge(ride(3), 5, 50), new Edge(ride(4), 5, 40));

        Map<UUID, Long> result = solver.solve(edges);

        assertEquals(2L, result.get(ride(0)));
        assertEquals(1L, result.get(ride(1)));
        assertEquals(9L, result.get(ride(2)));
        assertEquals(5L, result.get(ride(4)));
        assertEquals(4, result.size());
    }

    @Test
    @DisplayName("no edges, no assignments")
    void emptyInputGivesEmpty() {
        assertTrue(solver.solve(List.of()).isEmpty());
    }

    @Test
    @DisplayName("a driver is never given two rides, whatever the shape of the graph")
    void noDriverSharedEverAcrossComponents() {
        Random random = new Random(7);
        for (int c = 0; c < 50; c++) {
            List<Edge> edges = new ArrayList<>();
            int rides = 1 + random.nextInt(40);
            for (int r = 0; r < rides; r++) {
                int k = 1 + random.nextInt(4);
                for (int j = 0; j < k; j++) {
                    edges.add(new Edge(ride(r), random.nextInt(25), random.nextInt(900) - 200));
                }
            }
            assertValidMatching(edges, solver.solve(edges));
        }
    }

    /** Every pair is a real edge, and no driver appears twice. */
    private static void assertValidMatching(List<Edge> edges, Map<UUID, Long> result) {
        Set<String> real = new HashSet<>();
        for (Edge e : edges) {
            real.add(e.ride() + "/" + e.driver());
        }
        Map<Long, UUID> byDriver = new HashMap<>();
        for (Map.Entry<UUID, Long> pair : result.entrySet()) {
            assertTrue(real.contains(pair.getKey() + "/" + pair.getValue()), "not an edge: " + pair);
            UUID previous = byDriver.put(pair.getValue(), pair.getKey());
            assertTrue(previous == null, "driver " + pair.getValue() + " given two rides");
        }
    }
}
