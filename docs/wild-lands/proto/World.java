package dev.awaken.living.region;
import java.io.*;
/** Planning harness: Euan's world plan (region hearts, towns, settlement anchors, levels) as JSON for the wildness prototype. */
public class World {
    static long mix(long z) { z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L; z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL; return z ^ (z >>> 31); }
    static double unit(long h) { return (h >>> 11) * 0x1.0p-53; }
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]);
        int x0 = Integer.parseInt(a[3]), z0 = Integer.parseInt(a[4]), size = Integer.parseInt(a[5]), step = Integer.parseInt(a[6]);
        RegionMap m = new RegionMap(seed, cx, cz, (x, z) -> RegionMap.Land.PLAIN, 3);
        StringBuilder o = new StringBuilder("{\"levels\":[");
        for (int z = z0; z < z0 + size; z += step) {
            o.append(z == z0 ? "[" : ",[");
            for (int x = x0; x < x0 + size; x += step) o.append(x == x0 ? "" : ",").append(m.levelAt(x, z));
            o.append("]");
        }
        o.append("],\"hearts\":[");
        int p = m.pitch(); boolean first = true;
        for (int gx = Math.floorDiv(x0, p) - 1; gx <= Math.floorDiv(x0 + size, p) + 1; gx++)
            for (int gz = Math.floorDiv(z0, p) - 1; gz <= Math.floorDiv(z0 + size, p) + 1; gz++) {
                if (m.swallowed(gx, gz)) continue;
                int[] h = m.heart(gx, gz);
                boolean town = m.roll(gx, gz, 0x70A7_5EEDL) < 0.35;
                o.append(first ? "" : ",").append("[").append(gx).append(",").append(gz).append(",").append(h[0]).append(",").append(h[1]).append(",").append(town ? 1 : 0).append("]");
                first = false;
            }
        o.append("],\"anchors\":[");
        first = true;
        int P = 640;
        for (int i = Math.floorDiv(x0, P); i <= Math.floorDiv(x0 + size, P); i++)
            for (int j = Math.floorDiv(z0, P); j <= Math.floorDiv(z0 + size, P); j++) {
                long h = mix(seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ 0x5E771EL);
                double roll = unit(h);
                if (roll < 0.10 || unit(mix(h + 9)) < 0.5) continue;
                String kind = roll < 0.35 ? "village" : "hamlet";
                int x = i * P + (int) Math.round(P * (0.18 + 0.64 * unit(mix(h + 1))));
                int z = j * P + (int) Math.round(P * (0.18 + 0.64 * unit(mix(h + 2))));
                if (Math.hypot(x - cx, z - cz) < 760) continue;
                o.append(first ? "" : ",").append("[\"").append(kind).append("\",").append(x).append(",").append(z).append("]");
                first = false;
            }
        o.append("]}");
        try (PrintWriter w = new PrintWriter(new FileWriter(a[7]))) { w.print(o); }
    }
}
