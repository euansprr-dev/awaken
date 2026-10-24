package dev.awaken.farsight;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Far Sight's layout and its predictor, without a client: the rings nest, and the generator fills a patch. */
final class FarSightTests {
    private FarSightTests() {}

    static void register() {
        // Every ring's window holds the next finer one with room for its 14-cell blend zone, on whole cells of the
        // coarser ring, wherever the camera is and however it moved there.
        AwakenTests.register("farsight_rings_nest", 20, h -> {
            Random rng = new Random(20261024L);
            int[] wx = new int[Rings.RINGS], wz = new int[Rings.RINGS];
            double x = rng.nextInt(2_000_000) - 1_000_000, z = rng.nextInt(2_000_000) - 1_000_000;
            for (int r = 0; r < Rings.RINGS; r++) {
                wx[r] = Rings.windowPatch(x, r);
                wz[r] = Rings.windowPatch(z, r);
            }
            for (int step = 0; step < 4000; step++) {
                x += rng.nextGaussian() * 40;
                z += rng.nextGaussian() * 40;
                for (int r = 0; r < Rings.RINGS; r++) {
                    if (Rings.shouldMove(wx[r], x, r)) wx[r] = Rings.windowPatch(x, r);
                    if (Rings.shouldMove(wz[r], z, r)) wz[r] = Rings.windowPatch(z, r);
                    int pb = Rings.patchBlocks(r);
                    double cx = (wx[r] + Rings.PATCHES / 2.0) * pb, cz = (wz[r] + Rings.PATCHES / 2.0) * pb;
                    h.assertTrue(Math.abs(x - cx) <= pb * 0.75 + 1e-6 && Math.abs(z - cz) <= pb * 0.75 + 1e-6,
                            "ring " + r + " stays within three quarters of a patch of the camera");
                    if (r == 0) continue;
                    int s = Rings.cellSize(r);
                    // the finer window in this ring's cells: a whole number of cells, inside with blend room to spare
                    long fx0 = (long) wx[r - 1] * Rings.patchBlocks(r - 1), fz0 = (long) wz[r - 1] * Rings.patchBlocks(r - 1);
                    h.assertTrue(fx0 % s == 0 && fz0 % s == 0, "ring " + (r - 1) + "'s window starts on ring " + r + "'s cells");
                    long hx0 = fx0 / s - (long) wx[r] * Rings.PATCH, hz0 = fz0 / s - (long) wz[r] * Rings.PATCH;
                    h.assertTrue(hx0 >= 14 && hz0 >= 14 && hx0 + Rings.SIZE / 2 <= Rings.SIZE - 14 && hz0 + Rings.SIZE / 2 <= Rings.SIZE - 14,
                            "ring " + (r - 1) + " sits inside ring " + r + " with room: " + hx0 + "," + hz0);
                }
            }
            // a window's 256 patches take 256 different slots, so the atlas never holds two of them at once
            Set<Integer> slots = new HashSet<>();
            for (int j = 0; j < Rings.PATCHES; j++) for (int i = 0; i < Rings.PATCHES; i++) slots.add(Rings.slot(wx[3] + i, wz[3] + j));
            h.assertTrue(slots.size() == Rings.PATCHES * Rings.PATCHES, "slots are unique within a window");
            h.assertTrue(Rings.reach(Rings.RINGS - 1) == 16384 && Rings.cellSize(0) == 2 && Rings.cellSize(6) == 128, "the plan's sizes");
            h.succeed();
        });

        // The predictor fills a patch of every ring from the level's own generator: every cell known, the top inside the
        // world, water exactly at sea level.
        AwakenTests.register("farsight_columns_predict", 200, h -> {
            FarColumns columns = FarColumns.of(h.getLevel());
            int[] height = new int[Rings.PATCH * Rings.PATCH], colour = new int[Rings.PATCH * Rings.PATCH];
            int minY = h.getLevel().getMinY(), maxY = h.getLevel().getMaxY() + 1;
            for (int r = 0; r < Rings.RINGS; r++) {
                long t0 = System.nanoTime();
                int range = columns.patch(r, 3, -2, height, colour);
                long us = (System.nanoTime() - t0) / 1000;
                Awaken.LOG.info("[farsight] test patch ring {}: {} us ({} us/column)", r, us, us / height.length);
                h.assertTrue((range >>> 16) <= (range & 0xFFFF), "ring " + r + ": min below max");
                for (int i = 0; i < height.length; i++) {
                    int top = FarColumns.top(height[i]);
                    h.assertTrue(top != Integer.MIN_VALUE, "ring " + r + " cell " + i + " is known");
                    h.assertTrue(top >= minY && top <= maxY, "ring " + r + " cell " + i + " top " + top + " inside the world");
                    boolean water = (height[i] >> 16 & 255) == FarColumns.WATER;
                    h.assertTrue(!water || top == columns.seaLevel(), "ring " + r + " water lies at sea level");
                    h.assertTrue(colour[i] >>> 24 == 255, "ring " + r + " cell " + i + " has a colour");
                }
            }
            h.succeed();
        });
    }
}
