package dev.awaken.terrain.hero;

/** Buttress roots for the hero trees: fins of bark that leave the trunk high and arch down into the ground. */
final class Roots {
    private Roots() {}

    /**
     * One fin along angle {@code a} (bending by {@code bend} radians over its length), from {@code from} to {@code tip}
     * blocks out; its crest falls from {@code top} at the trunk to the ground at the tip, its half-width from
     * {@code wide} to {@code narrow}. Every column of it is a wall down to the ground (placement fills below the
     * blueprint), so a root on a slope reaches the slope and a root over a hump sinks into it.
     */
    static void fin(Blueprint bp, double a, double bend, double from, double tip, double top, double wide, double narrow, long seed) {
        for (double s = 0; s <= tip; s += 0.35) {
            double t = Math.max(0, Math.min(1, (s - from) / (tip - from)));
            double ang = a + bend * s / tip;
            double cx = Math.cos(ang) * s, cz = Math.sin(ang) * s;
            double crest = top * Math.pow(1 - t, 1.7);
            double w = wide + (narrow - wide) * t;
            double px = -Math.sin(ang), pz = Math.cos(ang);
            for (double o = -w; o <= w; o += 0.35) {
                int x = (int) Math.floor(cx + px * o + 0.5), z = (int) Math.floor(cz + pz * o + 0.5);
                // the crest thins toward the fin's edges
                double edge = 1 - Math.abs(o) / (w + 0.5);
                int hi = (int) Math.round(crest * (0.55 + 0.45 * edge));
                for (int y = bp.minY; y <= hi; y++) bp.fill(x, y, z, Blueprint.WOOD);
                bp.ground(x, bp.minY, z);
                // moss along the crest of the low, outer half of the root
                if (t > 0.35 && Blueprint.hash(seed, x, hi, z) < 0.3) bp.fill(x, hi + 1, z, Blueprint.MOSS);
            }
        }
    }
}
