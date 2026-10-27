package dev.awaken.living.region;

import java.util.*;

public class Compare {
    interface F { int at(int x, int z); }
    static final int RAYS = 32;

    public static void main(String[] a) {
        int seeds = a.length > 0 ? Integer.parseInt(a[0]) : 200;
        Random rnd = new Random(12345);
        String[] names = {"current", "proposed"};
        // metrics accumulators per model
        List<List<Double>> nearest = new ArrayList<>(), camp = new ArrayList<>(), cliff = new ArrayList<>(), apron = new ArrayList<>();
        Map<String, List<Double>> m1 = new LinkedHashMap<>(), m2 = new LinkedHashMap<>();
        for (int m = 0; m < 2; m++) { nearest.add(new ArrayList<>()); camp.add(new ArrayList<>()); cliff.add(new ArrayList<>()); apron.add(new ArrayList<>()); }
        int[] thr = {10, 20, 45, 70, 100};
        double[][][] firstAt = new double[2][thr.length][]; // per model, per threshold: list over seeds of nearest radius
        List<List<List<Double>>> nearestThr = new ArrayList<>(), allRayThr = new ArrayList<>();
        for (int m = 0; m < 2; m++) { List<List<Double>> l1 = new ArrayList<>(), l2 = new ArrayList<>(); for (int t = 0; t < thr.length; t++) { l1.add(new ArrayList<>()); l2.add(new ArrayList<>()); } nearestThr.add(l1); allRayThr.add(l2); }
        int[] lp = {3, 5, 8, 12, 18, 25, 35, 50, 70};
        List<List<List<Double>>> fit = new ArrayList<>(), share = new ArrayList<>();
        for (int m = 0; m < 2; m++) { List<List<Double>> l1 = new ArrayList<>(), l2 = new ArrayList<>(); for (int t = 0; t < lp.length; t++) { l1.add(new ArrayList<>()); l2.add(new ArrayList<>()); } fit.add(l1); share.add(l2); }
        double[] hamlet = new double[2]; int[] hamletBad = new int[2], hamletOk = new int[2];
        List<List<Double>> stepP = new ArrayList<>(); stepP.add(new ArrayList<>()); stepP.add(new ArrayList<>());

        for (int s = 0; s < seeds; s++) {
            long seed = rnd.nextLong(); int cx = rnd.nextInt(2000) - 1000, cz = rnd.nextInt(2000) - 1000;
            // current model with the same biome-tilt distribution
            RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> {
                long h = RegionMap.mix(seed ^ RegionMap.mix((long) Math.floorDiv(x, 900) * 31 + Math.floorDiv(z, 900) * 17 + 99));
                double u = RegionMap.unit(h);
                return new RegionMap.Land(RegionMap.Group.FOREST, u < 0.10 ? 1 : u < 0.35 ? -1 : 0);
            });
            NewMap nm = new NewMap(seed, cx, cz);
            F[] fs = {old::levelAt, nm::levelAt};
            for (int m = 0; m < 2; m++) {
                F f = fs[m];
                double best[] = new double[thr.length]; Arrays.fill(best, 1e9);
                double maxStep = 0; int stepN = 0, stepBig = 0;
                for (int k = 0; k < RAYS; k++) {
                    double ang = 2 * Math.PI * k / RAYS + 0.01;
                    int[] first = new int[thr.length]; Arrays.fill(first, -1);
                    int prev = 0; int[] hist = new int[5];
                    int[] lv = new int[120];
                    for (int i = 0; i < 120; i++) { // 500..6450 step 50
                        int d = 500 + i * 50;
                        int x = (int) Math.round(cx + Math.cos(ang) * d), z = (int) Math.round(cz + Math.sin(ang) * d);
                        lv[i] = f.at(x, z);
                        for (int t = 0; t < thr.length; t++) if (first[t] < 0 && lv[i] >= thr[t]) first[t] = d;
                    }
                    for (int t = 0; t < thr.length; t++) { int fv = first[t] < 0 ? 7000 : first[t]; allRayThr.get(m).get(t).add((double) fv); best[t] = Math.min(best[t], fv); }
                    for (int i = 2; i < 120; i++) { int step = Math.abs(lv[i] - lv[i - 2]); stepN++; if (step > 12) stepBig++; maxStep = Math.max(maxStep, step); }
                }
                for (int t = 0; t < thr.length; t++) nearestThr.get(m).get(t).add(best[t]);
                stepP.get(m).add(100.0 * stepBig / stepN);
                // hamlet: a point 700-1100 out along a random bearing (the first hamlet goes up the camp road; any bearing is a fair test)
                double ang = rnd.nextDouble() * 2 * Math.PI; double dh = 700 + rnd.nextDouble() * 400;
                int hl = f.at((int) Math.round(cx + Math.cos(ang) * dh), (int) Math.round(cz + Math.sin(ang) * dh));
                if (hl > 15) hamletBad[m]++; else if (hl <= 10) hamletOk[m]++;
                hamlet[m] += hl;
                // fit availability for player levels: nearest ring radius with a point at level in [lp-3, lp+3], and share of land in 700..4000 within [lp-5, lp+2]
                for (int pi = 0; pi < lp.length; pi++) {
                    int L = lp[pi]; double nearestFit = 7000; int in = 0, tot = 0;
                    for (int gz = -4000; gz <= 4000; gz += 200) for (int gx = -4000; gx <= 4000; gx += 200) {
                        double dd = Math.hypot(gx, gz); if (dd < 500 || dd > 4000) continue;
                        int v = f.at(cx + gx, cz + gz); tot++;
                        if (v >= L - 5 && v <= L + 2) in++;
                        if (v >= L - 3 && v <= L + 3) nearestFit = Math.min(nearestFit, dd);
                    }
                    fit.get(m).get(pi).add(nearestFit); share.get(m).get(pi).add(100.0 * in / tot);
                }
            }
        }
        for (int m = 0; m < 2; m++) {
            System.out.println("=== " + names[m] + " (" + seeds + " worlds, 32 bearings each, 500..6450 out)");
            for (int t = 0; t < thr.length; t++) {
                List<Double> n = nearestThr.get(m).get(t), r = allRayThr.get(m).get(t);
                System.out.printf("  first Lv>=%3d: nearest anywhere  P10 %5.0f  median %5.0f  P90 %5.0f | per bearing  P10 %5.0f  median %5.0f  P90 %5.0f%n", thr[t], pct(n, .1), pct(n, .5), pct(n, .9), pct(r, .1), pct(r, .5), pct(r, .9));
            }
            System.out.printf("  worlds with Lv>=20 within 1,200 on some bearing: %.0f%%   Lv>=45 within 1,800: %.0f%%   Lv>=45 within 2,500: %.0f%%%n", 100 * frac(nearestThr.get(m).get(1), 1200), 100 * frac(nearestThr.get(m).get(2), 1800), 100 * frac(nearestThr.get(m).get(2), 2500));
            System.out.printf("  first hamlet spot (700-1,100 out): mean level %.1f, >15 in %.0f%% of worlds, <=10 in %.0f%%%n", hamlet[m] / seeds, 100.0 * hamletBad[m] / seeds, 100.0 * hamletOk[m] / seeds);
            System.out.printf("  border cliffs: share of 100-block steps that gain/lose >12 levels: median %.2f%%  P90 %.2f%%%n", pct(stepP.get(m), .5), pct(stepP.get(m), .9));
            System.out.println("  on-level land (700..4,000 out): player level -> nearest land within +-3 (median / P90 blocks) | share of that land in [L-5, L+2] (median %)");
            for (int pi = 0; pi < lp.length; pi++) System.out.printf("     L%-3d  %5.0f / %5.0f   |  %4.1f%%%n", lp[pi], pct(fit.get(m).get(pi), .5), pct(fit.get(m).get(pi), .9), pct(share.get(m).get(pi), .5));
        }
    }
    static double pct(List<Double> l, double p) { List<Double> s = new ArrayList<>(l); Collections.sort(s); return s.get(Math.min(s.size() - 1, (int) Math.floor(p * s.size()))); }
    static double frac(List<Double> l, double below) { int n = 0; for (double v : l) if (v <= below) n++; return (double) n / l.size(); }
}
