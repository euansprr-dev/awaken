package dev.awaken.living.region;
public class EuanWorld {
    public static void main(String[] a) {
        long seed = 3500122753516418813L; int cx = -1238, cz = -736;
        RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> new RegionMap.Land(RegionMap.Group.FOREST, 0));
        NewMap nm = new NewMap(seed, cx, cz);
        String[] dirs = {"N (camp road)", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[] ds = {450, 700, 800, 870, 1000, 1300, 1700, 2000, 2500, 3000, 4000};
        System.out.printf("%-14s", "out (blocks)"); for (int d : ds) System.out.printf("%6d", d); System.out.println();
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(-90 + 45 * k);
            System.out.printf("%-14s", dirs[k] + " now"); for (int d : ds) System.out.printf("%6d", old.levelAt((int) Math.round(cx + Math.cos(ang) * d), (int) Math.round(cz + Math.sin(ang) * d))); System.out.println();
            System.out.printf("%-14s", dirs[k] + " new"); for (int d : ds) System.out.printf("%6d", nm.levelAt((int) Math.round(cx + Math.cos(ang) * d), (int) Math.round(cz + Math.sin(ang) * d))); System.out.println();
        }
        // Black Glades: its heart and the edge nearest home, new rules
        int hx = -1192, hz = -2438; System.out.println("Black Glades heart (-1192,-2438) now " + old.levelAt(hx, hz) + ", new " + nm.levelAt(hx, hz));
        int ex = -1238, ez = -1560; System.out.println("Black Glades Commons edge (-1238,-1560) now " + old.levelAt(ex, ez) + ", new " + nm.levelAt(ex, ez));
        System.out.println("first hamlet (-1234,-1604) now " + old.levelAt(-1234, -1604) + ", new " + nm.levelAt(-1234, -1604));
        System.out.println("death point (-1287,-1444) now " + old.levelAt(-1287, -1444) + ", new " + nm.levelAt(-1287, -1444));
        System.out.println("camp (-1231,-1183) now " + old.levelAt(-1231, -1183) + ", new " + nm.levelAt(-1231, -1183));
        // walk the camp road north every 100 from the camp to 2600 out
        StringBuilder o = new StringBuilder("north road now: "), n = new StringBuilder("north road new: ");
        for (int d = 400; d <= 2600; d += 200) { o.append(d).append(":").append(old.levelAt(cx, cz - d)).append(" "); n.append(d).append(":").append(nm.levelAt(cx, cz - d)).append(" "); }
        System.out.println(o); System.out.println(n);
    }
}
