package dev.awaken.living.region;
import java.io.*;
public class MapOut {
    static int band(int l) { return l < 10 ? 0 : l < 25 ? 1 : l < 45 ? 2 : l < 70 ? 3 : l < 100 ? 4 : 5; }
    public static void main(String[] a) throws Exception {
        long seed = 3500122753516418813L; int cx = -1238, cz = -736;
        RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> new RegionMap.Land(RegionMap.Group.FOREST, 0));
        NewMap nm = new NewMap(seed, cx, cz);
        int R = 5000, step = 100, n = 2 * R / step + 1;
        StringBuilder jo = new StringBuilder("["), jn = new StringBuilder("[");
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int x = cx - R + i * step, z = cz - R + j * step;
            jo.append(old.levelAt(x, z)).append(','); jn.append(nm.levelAt(x, z)).append(',');
        }
        jo.setLength(jo.length() - 1); jn.setLength(jn.length() - 1); jo.append("]"); jn.append("]");
        try (PrintWriter w = new PrintWriter("map-euan.json")) { w.println("{\"n\":" + n + ",\"step\":" + step + ",\"R\":" + R + ",\"now\":" + jo + ",\"proposed\":" + jn + "}"); }
        // ASCII at 250 blocks, letters by rank, '#' = city (the Commons core)
        String letters = "EDCBAS";
        for (int m = 0; m < 2; m++) {
            System.out.println(m == 0 ? "NOW (Euan's seed)" : "PROPOSED (Euan's seed)");
            for (int j = -20; j <= 20; j++) { StringBuilder sb = new StringBuilder();
                for (int i = -20; i <= 20; i++) { int x = cx + i * 250, z = cz + j * 250; double d = Math.hypot(i * 250, j * 250);
                    if (i == 0 && j == 0) { sb.append('*'); continue; }
                    int lv = m == 0 ? old.levelAt(x, z) : nm.levelAt(x, z);
                    sb.append(d <= 700 ? '.' : letters.charAt(band(lv))); sb.append(' '); }
                System.out.println(sb); }
        }
    }
}
