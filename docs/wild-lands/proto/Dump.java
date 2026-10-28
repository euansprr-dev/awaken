package dev.awaken.living.region;
import java.io.*;
/** Planning harness: the real region levels over a square, one row per line (step blocks). */
public class Dump {
    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]); int cx = Integer.parseInt(a[1]), cz = Integer.parseInt(a[2]);
        int x0 = Integer.parseInt(a[3]), z0 = Integer.parseInt(a[4]), size = Integer.parseInt(a[5]), step = Integer.parseInt(a[6]);
        RegionMap m = new RegionMap(seed, cx, cz, (x, z) -> RegionMap.Land.PLAIN, 3);
        try (PrintWriter w = new PrintWriter(new FileWriter(a[7]))) {
            for (int z = z0; z < z0 + size; z += step) {
                StringBuilder b = new StringBuilder();
                for (int x = x0; x < x0 + size; x += step) b.append(m.levelAt(x, z)).append(' ');
                w.println(b.toString().trim());
            }
        }
        long t = System.nanoTime(); int s = 0; for (int i = 0; i < 200000; i++) s += m.levelAt(i * 37 % 20000 - 10000, i * 91 % 20000 - 10000);
        System.out.println("levelAt ns/call " + (System.nanoTime() - t) / 200000.0 + " " + s);
    }
}
