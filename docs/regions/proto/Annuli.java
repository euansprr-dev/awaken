package dev.awaken.living.region;

import java.util.*;

public class Annuli {
    interface F { int at(int x, int z); }
    static final int[] EDGES = {700, 1000, 1500, 2000, 3000, 4000, 5500, 7500};
    static final String[] BAND = {"E 1-9", "D 10-24", "C 25-44", "B 45-69", "A 70-99", "S 100+"};
    static int band(int l) { return l < 10 ? 0 : l < 25 ? 1 : l < 45 ? 2 : l < 70 ? 3 : l < 100 ? 4 : 5; }

    public static void main(String[] a) {
        double scale = a.length > 0 ? Double.parseDouble(a[0]) : 1.0;
        int seeds = a.length > 1 ? Integer.parseInt(a[1]) : 120;
        Random rnd = new Random(777);
        int[][][] count = new int[2][EDGES.length - 1][6]; long[][] tot = new long[2][EDGES.length - 1];
        List<List<Integer>> samples = new ArrayList<>(); for (int m = 0; m < 2; m++) for (int i = 0; i < EDGES.length - 1; i++) samples.add(new ArrayList<>());
        for (int s = 0; s < seeds; s++) {
            long seed = rnd.nextLong(); int cx = rnd.nextInt(2000) - 1000, cz = rnd.nextInt(2000) - 1000;
            RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> {
                long h = RegionMap.mix(seed ^ RegionMap.mix((long) Math.floorDiv(x, 900) * 31 + Math.floorDiv(z, 900) * 17 + 99));
                double u = RegionMap.unit(h);
                return new RegionMap.Land(RegionMap.Group.FOREST, u < 0.10 ? 1 : u < 0.35 ? -1 : 0);
            });
            NewMap nm = new NewMap(seed, cx, cz); nm.reachScale = scale;
            F[] fs = {old::levelAt, nm::levelAt};
            for (int m = 0; m < 2; m++) for (int gz = -7500; gz <= 7500; gz += 150) for (int gx = -7500; gx <= 7500; gx += 150) {
                double d = Math.hypot(gx, gz);
                for (int e = 0; e < EDGES.length - 1; e++) if (d >= EDGES[e] && d < EDGES[e + 1]) {
                    int v = fs[m].at(cx + gx, cz + gz); count[m][e][band(v)]++; tot[m][e]++; samples.get(m * (EDGES.length - 1) + e).add(v);
                }
            }
        }
        String[] names = {"current", "proposed (reach x" + scale + ")"};
        for (int m = 0; m < 2; m++) {
            System.out.println("=== " + names[m] + ": share of land by rank, per distance from the city centre");
            System.out.printf("%-14s", "distance"); for (String b : BAND) System.out.printf("%9s", b); System.out.println("    level P10/P50/P90");
            for (int e = 0; e < EDGES.length - 1; e++) {
                System.out.printf("%5d-%-8d", EDGES[e], EDGES[e + 1]);
                for (int b = 0; b < 6; b++) System.out.printf("%8.0f%%", 100.0 * count[m][e][b] / tot[m][e]);
                List<Integer> l = samples.get(m * (EDGES.length - 1) + e); Collections.sort(l);
                System.out.printf("    %d / %d / %d%n", l.get((int) (l.size() * .1)), l.get(l.size() / 2), l.get((int) (l.size() * .9)));
            }
        }
    }
}
