package dev.awaken.living.region;
import java.util.*;
public class Cliffs {
    interface F { int at(int x, int z); }
    public static void main(String[] a) {
        Random rnd = new Random(4242); int seeds = 150;
        for (int zone = 0; zone < 3; zone++) {
            int dmax = zone == 0 ? 1800 : zone == 1 ? 3000 : 5000; int dmin = 650;
            List<Double> mo = new ArrayList<>(), mn = new ArrayList<>(); List<Double> eo = new ArrayList<>(), en = new ArrayList<>();
            for (int s = 0; s < seeds; s++) {
                long seed = rnd.nextLong(); int cx = rnd.nextInt(2000) - 1000, cz = rnd.nextInt(2000) - 1000;
                RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> { long h = RegionMap.mix(seed ^ RegionMap.mix((long) Math.floorDiv(x, 900) * 31 + Math.floorDiv(z, 900) * 17 + 99)); double u = RegionMap.unit(h); return new RegionMap.Land(RegionMap.Group.FOREST, u < 0.10 ? 1 : u < 0.35 ? -1 : 0); });
                NewMap nm = new NewMap(seed, cx, cz);
                F[] fs = {old::levelAt, nm::levelAt}; double[] worst = new double[2]; int[] nbad = new int[2], n = new int[2];
                for (int k = 0; k < 32; k++) { double ang = 2 * Math.PI * k / 32 + 0.01; int prev[] = new int[2]; boolean first = true;
                    for (int d = dmin; d <= dmax; d += 25) { int x = (int) Math.round(cx + Math.cos(ang) * d), z = (int) Math.round(cz + Math.sin(ang) * d);
                        for (int m = 0; m < 2; m++) { int v = fs[m].at(x, z); if (!first) { // jump over 100 blocks needs 4 samples: compare with the value 4 samples back
                            } prev[m] = v; }
                        first = false; }
                    int[][] buf = new int[2][(dmax - dmin) / 25 + 1];
                    for (int d = dmin, i = 0; d <= dmax; d += 25, i++) { int x = (int) Math.round(cx + Math.cos(ang) * d), z = (int) Math.round(cz + Math.sin(ang) * d); for (int m = 0; m < 2; m++) buf[m][i] = fs[m].at(x, z); }
                    for (int m = 0; m < 2; m++) for (int i = 4; i < buf[m].length; i++) { int st = Math.abs(buf[m][i] - buf[m][i - 4]); worst[m] = Math.max(worst[m], st); n[m]++; if (st > 10) nbad[m]++; }
                }
                mo.add(worst[0]); mn.add(worst[1]); eo.add(100.0 * nbad[0] / n[0]); en.add(100.0 * nbad[1] / n[1]);
            }
            System.out.printf("within %d: biggest jump over 100 blocks  current median %.0f P90 %.0f max %.0f | proposed median %.0f P90 %.0f max %.0f   ||  share of steps >10 levels: current %.1f%%, proposed %.1f%%%n", dmax, pct(mo, .5), pct(mo, .9), pct(mo, 1), pct(mn, .5), pct(mn, .9), pct(mn, 1), pct(eo, .5), pct(en, .5));
        }
    }
    static double pct(List<Double> l, double p) { List<Double> s = new ArrayList<>(l); Collections.sort(s); return s.get(Math.min(s.size() - 1, (int) Math.floor(p * s.size()))); }
}
