package dev.awaken.farsight;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Far Sight without a client: the rings nest, the generator fills a patch, and real chunks fill the generated layer
 * through the disk cache.
 */
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
                    boolean water = (height[i] >> 16 & 255) > 0;
                    h.assertTrue(!water || top == columns.seaLevel(), "ring " + r + " water lies at sea level");
                    h.assertTrue(colour[i] >>> 24 == 255, "ring " + r + " cell " + i + " has a colour");
                }
            }
            h.succeed();
        });

        // The generated layer: a real chunk summed into 2-block cells (a stone pillar's top, a pool's surface and depth),
        // kept through the disk cache, and read back as a ring 0 patch.
        AwakenTests.registerIsolated("farsight_generated_layer", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int x0 = (o.getX() & ~15) + 4, z0 = (o.getZ() & ~15) + 4, y0 = o.getY() + 2;
            // the pillar: chunk cell (2, 2), 12 high; the pool: cell (4, 2), 3 deep on a stone floor
            for (int y = y0; y < y0 + 12; y++) {
                for (int k = 0; k < 4; k++) level.setBlockAndUpdate(new BlockPos(x0 + (k & 1), y, z0 + (k >> 1)), Blocks.STONE.defaultBlockState());
            }
            for (int k = 0; k < 4; k++) {
                int x = x0 + 4 + (k & 1), z = z0 + (k >> 1);
                level.setBlockAndUpdate(new BlockPos(x, y0, z), Blocks.STONE.defaultBlockState());
                for (int y = y0 + 1; y <= y0 + 3; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
            }
            LevelChunk chunk = level.getChunkAt(new BlockPos(x0, y0, z0));
            FarChunk c = FarChunk.of(chunk);
            int pillar = 2 * FarChunk.SIDE + 2, pool = 2 * FarChunk.SIDE + 4;
            h.assertTrue(FarChunk.top(c.cell()[pillar]) == y0 + 12, "the pillar's top: " + FarChunk.top(c.cell()[pillar]) + " not " + (y0 + 12));
            h.assertTrue(FarChunk.depth(c.cell()[pillar]) == 0 && c.colour()[pillar] != 0, "the pillar is dry stone");
            h.assertTrue(FarChunk.top(c.cell()[pool]) == y0 + 4 && FarChunk.depth(c.cell()[pool]) == 3,
                    "the pool: top " + FarChunk.top(c.cell()[pool]) + ", depth " + FarChunk.depth(c.cell()[pool]));
            var biome = chunk.getNoiseBiome(QuartPos.fromBlock(x0 + 4), QuartPos.fromBlock(y0 + 3), QuartPos.fromBlock(z0));
            h.assertTrue(c.colour()[pool] == FarPalette.water(biome), "the pool takes its biome's water colour");

            // through the disk cache and back
            FarStore.of(level).put(c);
            FarStore.closeAll();
            FarStore again = FarStore.of(level);
            FarChunk back = again.get(c.x(), c.z());
            h.assertTrue(back != null && Arrays.equals(back.cell(), c.cell()) && Arrays.equals(back.colour(), c.colour()),
                    "the chunk comes back from disk unchanged");

            // ring 0's patch over it shows the pillar where the prediction would have shown flat ground
            int[] height = new int[Rings.PATCH * Rings.PATCH], colour = new int[Rings.PATCH * Rings.PATCH];
            int px = Math.floorDiv(x0, Rings.patchBlocks(0)), pz = Math.floorDiv(z0, Rings.patchBlocks(0));
            h.assertTrue(again.patch(0, px, pz, height, colour) != -1, "a ring 0 patch over the chunk has cells");
            int i = (Math.floorMod(z0, Rings.patchBlocks(0)) / 2) * Rings.PATCH + Math.floorMod(x0, Rings.patchBlocks(0)) / 2;
            h.assertTrue(FarColumns.top(height[i]) == y0 + 12, "the patch's pillar cell: " + FarColumns.top(height[i]));
            FarStore.closeAll();
            h.succeed();
        });
    }
}
