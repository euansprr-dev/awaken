package dev.awaken.living.region;
public class Bench {
    public static void main(String[] a) {
        long seed = 3500122753516418813L; int cx = -1238, cz = -736;
        RegionMap old = new RegionMap(seed, cx, cz, (x, z) -> new RegionMap.Land(RegionMap.Group.FOREST, 0));
        NewMap nm = new NewMap(seed, cx, cz);
        long sink = 0;
        for (int rep = 0; rep < 3; rep++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < 2_000_000; i++) sink += old.levelAt(cx + (i * 37) % 6000 - 3000, cz + (i * 91) % 6000 - 3000);
            long t1 = System.nanoTime();
            for (int i = 0; i < 2_000_000; i++) sink += nm.levelAt(cx + (i * 37) % 6000 - 3000, cz + (i * 91) % 6000 - 3000);
            long t2 = System.nanoTime();
            System.out.printf("current %.2f us/call, prototype %.2f us/call (prototype allocates 2 small arrays a call; the build would not)%n", (t1 - t0) / 2e6 / 1000, (t2 - t1) / 2e6 / 1000);
        }
        System.out.println(sink);
    }
}
