package dev.awaken.farsight;

import dev.awaken.Awaken;
import dev.awaken.city.CityLayout;
import dev.awaken.city.CityPlan;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Far Sight without a client: the rings nest, the generator fills a patch, and real chunks fill the generated layer
 * through the disk cache.
 */
final class FarSightTests {
    private FarSightTests() {}

    static void register() {
        // Every ring's window holds the next finer one with room for its 16-cell blend zone, on whole cells of the
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
                    // (the outer ring's own band, where it eases into the next coarser ring, must never reach the hole)
                    h.assertTrue(hx0 >= 16 && hz0 >= 16 && hx0 + Rings.SIZE / 2 <= Rings.SIZE - 16 && hz0 + Rings.SIZE / 2 <= Rings.SIZE - 16,
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

        // The height model on vanilla's own overworld (seed 777): every ring's patch filled (every corner known, inside
        // the world, water at sea level), and the predicted ground matches the generator's exact base height (median within
        // 1.5 blocks, nine in ten within 4: the 3D noise's overhangs and the density's 4-block interpolation are the rest),
        // in a few microseconds a column.
        AwakenTests.register("farsight_columns_predict", 600, h -> {
            ServerLevel level = h.getLevel();
            var access = level.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), settings);
            RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), 777L, settings.value());
            LevelHeightAccessor heights = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
            FarColumns columns = new FarColumns(gen, rs, access, heights, false, null);
            h.assertTrue(columns.modelled(), "vanilla's overworld has a terrain density to solve");
            int n = Rings.PATCH * Rings.PATCH;
            int[] height = new int[n], colour = new int[n], info = new int[n], crown = new int[n];
            long t0 = System.nanoTime(), p0 = columns.probes;
            for (int r = 0; r < Rings.RINGS; r++) {
                int range = columns.patch(r, 3, -2, height, colour, info, crown);
                h.assertTrue((range >>> 16) <= (range & 0xFFFF), "ring " + r + ": min below max");
                for (int i = 0; i < n; i++) {
                    float top = FarColumns.unpackHeight(height[i]);
                    h.assertTrue(!Float.isNaN(top), "ring " + r + " corner " + i + " is known");
                    h.assertTrue(top >= heights.getMinY() && top <= heights.getMaxY() + 1, "ring " + r + " corner " + i + " top " + top + " inside the world");
                    boolean water = (info[i] & 255) > 0;
                    h.assertTrue(!water || Math.abs(top - columns.seaLevel()) < 0.07f, "ring " + r + " water lies at sea level: " + top);
                    h.assertTrue(water || (info[i] >>> 16 & 255) == 0 || (crown[i] & 0xFFFFFF) != 0, "ring " + r + " corner " + i + ": a forest has a crown colour");
                }
            }
            double us = (System.nanoTime() - t0) / 1000.0 / (Rings.RINGS * n);
            double probes = (columns.probes - p0) / (double) (Rings.RINGS * n);
            // against the generator's exact ground (a full column of its 3D density), on land
            java.util.List<Float> errors = new java.util.ArrayList<>(), coarseErrors = new java.util.ArrayList<>();
            long tb = 0;
            for (int z = -1600; z < 1600; z += 50) {
                for (int x = -1600; x < 1600; x += 50) {
                    long t1 = System.nanoTime();
                    int real = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, rs);
                    tb += System.nanoTime() - t1;
                    float predicted = columns.ground(x, z, true), coarse = columns.ground(x, z);
                    if (real <= columns.seaLevel() || predicted <= columns.seaLevel()) continue;
                    errors.add(Math.abs(predicted - real));
                    coarseErrors.add(Math.abs(coarse - real));
                }
            }
            h.assertTrue(errors.size() >= 300, "enough land to judge: " + errors.size() + " columns");
            java.util.Collections.sort(errors);
            float median = errors.get(errors.size() / 2), p90 = errors.get(errors.size() * 9 / 10);
            Awaken.LOG.info("[farsight] height model: {} us and {} probes a column (the exact base height: {} us); vs the generator on {} land columns: median {}, p90 {}, max {}",
                    String.format(java.util.Locale.ROOT, "%.1f", us), String.format(java.util.Locale.ROOT, "%.1f", probes),
                    String.format(java.util.Locale.ROOT, "%.0f", tb / 1000.0 / 4096), errors.size(), median, p90, errors.getLast());
            h.assertTrue(median <= 1.5f && p90 <= 4f, "halving to one block: median " + median + ", p90 " + p90);
            // the bracket's straight line (what every ring uses) is as close as halving to one block, or closer
            java.util.Collections.sort(coarseErrors);
            float coarseMedian = coarseErrors.get(coarseErrors.size() / 2), coarseP90 = coarseErrors.get(coarseErrors.size() * 9 / 10);
            Awaken.LOG.info("[farsight] the bracket's line: median {}, p90 {}", coarseMedian, coarseP90);
            h.assertTrue(coarseMedian <= 1.0f && coarseP90 <= 3f, "the bracket's line: median " + coarseMedian + ", p90 " + coarseP90);
            h.succeed();
        });

        // The generated layer: a real chunk summed into 2-block cells (a stone pillar's top, a pool's surface and depth),
        // kept through the disk cache, and read back as a ring 0 patch.
        AwakenTests.registerIsolated("farsight_generated_layer", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int x0 = (o.getX() & ~15) + 4, z0 = (o.getZ() & ~15) + 4, y0 = o.getY() + 2;
            // the isolated test can land under real terrain (new worlds' mountains), so the fixture owns its columns: open
            // to the sky over the pillar, the pool and the cells around them, whatever the ground there
            for (int x = x0 - 2; x < x0 + 8; x++) {
                for (int z = z0 - 2; z < z0 + 4; z++) {
                    for (int y = y0; y <= level.getMaxY(); y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
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
            h.assertTrue(FarChunk.top(c.cell()[pillar]) == y0 + 12, "the pillar's top: " + FarChunk.top(c.cell()[pillar]) + " not " + (y0 + 12)
                    + " (motion blocking " + chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x0 & 15, z0 & 15) + ", world surface "
                    + chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x0 & 15, z0 & 15) + ", block " + level.getBlockState(new BlockPos(x0, y0 + 11, z0))
                    + ", min y " + chunk.getMinY() + ", cell " + Integer.toHexString(c.cell()[pillar]) + ")");
            h.assertTrue(FarChunk.depth(c.cell()[pillar]) == 0 && c.colour()[pillar] != 0 && FarChunk.canopy(c.cell()[pillar]) == 0,
                    "the pillar is dry stone, no tree");
            h.assertTrue(FarChunk.top(c.cell()[pool]) == y0 + 4 && FarChunk.depth(c.cell()[pool]) == 3,
                    "the pool: top " + FarChunk.top(c.cell()[pool]) + ", depth " + FarChunk.depth(c.cell()[pool]));
            var biome = chunk.getNoiseBiome(QuartPos.fromBlock(x0 + 4), QuartPos.fromBlock(y0 + 3), QuartPos.fromBlock(z0));
            h.assertTrue(c.colour()[pool] == FarPalette.water(biome), "the pool takes its biome's water colour");

            // through the disk cache and back
            FarStore.of(level).put(c);
            FarStore.closeAll();
            FarStore again = FarStore.of(level);
            FarChunk back = again.get(c.x(), c.z());
            h.assertTrue(back != null && Arrays.equals(back.cell(), c.cell()) && Arrays.equals(back.colour(), c.colour())
                            && Arrays.equals(back.crown(), c.crown()),
                    "the chunk comes back from disk unchanged");

            // ring 0's patch over it shows the pillar where the prediction would have shown flat ground: the corner at
            // (x0 + 2, z0 + 2) averages the 2-block cell just before it, the pillar's
            int n = Rings.PATCH * Rings.PATCH;
            int[] height = new int[n], colour = new int[n], info = new int[n], crown = new int[n];
            int cxb = x0 + 2, czb = z0 + 2, pb = Rings.patchBlocks(0);
            int px = Math.floorDiv(cxb, pb), pz = Math.floorDiv(czb, pb);
            h.assertTrue(again.patch(0, px, pz, height, colour, info, crown) != -1, "a ring 0 patch over the chunk has corners");
            int i = (Math.floorMod(czb, pb) / 2) * Rings.PATCH + Math.floorMod(cxb, pb) / 2;
            h.assertTrue(FarColumns.unpackHeight(height[i]) == y0 + 12, "the patch's pillar corner: " + FarColumns.unpackHeight(height[i]));
            h.assertTrue((colour[i] >>> 24 & FarColumns.GENERATED) != 0, "marked as the real chunks'");
            FarStore.closeAll();
            h.succeed();
        });

        AwakenTests.registerIsolated("farsight_city", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            // the plan alone: a chunk on a test city's ring wall stands at the wall's height, built
            List<int[]> river = new ArrayList<>();
            for (int[] p : CityBasin.RIVER) river.add(new int[]{o.getX() + p[0], o.getZ() + p[1]});
            CityLayout city = new CityLayout(CityPlan.get(), new CitySite(o.getX(), o.getZ(), CityBasin.CORE_RADIUS,
                    CityBasin.BLEND_RADIUS, o.getY(), river, CityBasin.RIVER_HALF_WIDTH, CityBasin.RIVER_FLOOR_Y, true));
            int wallX = city.cx + city.plan.wallRadius;
            Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            FarChunk planned = FarCity.planned(city, wallX >> 4, city.cz >> 4, level.getSeed(), plains);
            h.assertTrue(planned != null, "the plan lays out the wall's chunk");
            int high = Integer.MIN_VALUE;
            boolean built = false;
            for (int i = 0; i < FarChunk.CELLS; i++) {
                if (planned.cell()[i] == 0) continue;
                int top = FarChunk.top(planned.cell()[i]);
                if (top > high) {
                    high = top;
                    built = FarCity.built(planned.colour()[i]);
                }
            }
            h.assertTrue(high >= city.streetY + city.plan.wallHeight && built,
                    "the wall stands at its height, built: top " + high + ", street " + city.streetY + " + wall " + city.plan.wallHeight);
            h.assertTrue(FarCity.planned(city, (city.cx >> 4) + 200, city.cz >> 4, level.getSeed(), plains) == null, "nothing beyond the city");
            // the far prediction reads the same plan column by column: across the wall, its top stands at the wall's height
            int[] yAt = new int[1];
            int wallTop = Integer.MIN_VALUE;
            for (int dx = -6; dx <= 6; dx++) {
                if (FarCity.plannedAt(city, level.getSeed(), wallX + dx, city.cz, yAt) != null) wallTop = Math.max(wallTop, yAt[0]);
            }
            h.assertTrue(wallTop + 1 >= city.streetY + city.plan.wallHeight, "the prediction's wall: top " + (wallTop + 1));
            h.assertTrue(FarCity.plannedAt(city, level.getSeed(), city.cx + 4000, city.cz, yAt) == null, "the prediction leaves the land beyond the city");

            // a chunk loaded before anything collected (as the city's pregeneration holds them, never saving) reaches the
            // store as soon as collecting starts
            int x0 = (o.getX() & ~15) + 4, z0 = (o.getZ() & ~15) + 4, y0 = o.getY() + 2;
            for (int x = x0 - 2; x < x0 + 4; x++) {
                for (int z = z0 - 2; z < z0 + 4; z++) {
                    for (int y = y0; y <= level.getMaxY(); y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            for (int y = y0; y < y0 + 23; y++) {
                for (int k = 0; k < 4; k++) level.setBlock(new BlockPos(x0 + (k & 1), y, z0 + (k >> 1)), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            boolean was = FarStore.collecting;
            FarStore.collecting = false;
            int cell = 2 * FarChunk.SIDE + 2, cx = x0 >> 4, cz = z0 >> 4;
            h.runAfterDelay(3, () -> FarStore.collecting = true);
            h.succeedWhen(() -> {
                FarChunk seen = FarStore.of(level).get(cx, cz);
                h.assertTrue(seen != null && FarChunk.top(seen.cell()[cell]) == y0 + 23,
                        "the loaded chunk reached the store: " + (seen == null ? "not yet" : "top " + FarChunk.top(seen.cell()[cell])));
                FarStore.collecting = was;
            });
        });

        AwakenTests.registerIsolated("farsight_trees_basin", 20, h -> {
            // a dense far forest over a ring 3 patch that holds a city basin: no tree in the reserve, every other one on
            // its cell's ground (the shader then stands it on the mesh as drawn there)
            int ring = 3, s = Rings.cellSize(ring), pb = Rings.patchBlocks(ring), n = Rings.PATCH * Rings.PATCH;
            int px = 40, pz = -12, x0 = px * pb, z0 = pz * pb;
            CitySite before = CityBasin.current();
            CitySite site = new CitySite(x0 + pb / 2, z0 + pb / 2, 20, 120, CityBasin.STREET_Y, List.of(), 0, 0, true);
            CityBasin.set(site);
            try {
                int[] height = new int[n], colour = new int[n], info = new int[n], crown = new int[n];
                for (int k = 0; k < n; k++) {
                    int i = k % Rings.PATCH, j = k / Rings.PATCH;
                    height[k] = FarColumns.packHeight(90f + 0.2f * i * s);
                    info[k] = FarColumns.abgr(0, 32, 255, 128);
                    crown[k] = FarColumns.abgr(0x2E5A2E, FarPalette.CONIFER);
                }
                int[] out = new int[FarTrees.MAX * FarTrees.INTS];
                int planted = FarTrees.plant(ring, px, pz, height, colour, info, crown, 63, out);
                h.assertTrue(planted > 20, "a dense forest plants: " + planted);
                int outside = 0;
                for (int t = 0; t < planted; t++) {
                    float lx = (out[t * FarTrees.INTS] & 0xFFFF) / 16f, lz = (out[t * FarTrees.INTS] >>> 16) / 16f;
                    int wx = (int) Math.floor(x0 + lx), wz = (int) Math.floor(z0 + lz);
                    h.assertFalse(CityBasin.reserved(wx, wz), "no far tree in the city's reserve: " + wx + ", " + wz);
                    float ground = FarColumns.unpackHeight(out[t * FarTrees.INTS + 1] & 0xFFFF);
                    h.assertTrue(Math.abs(ground - (90f + 0.2f * lx)) < 1f, "a tree on its cell's ground: " + ground + " at " + lx);
                    outside++;
                }
                h.assertTrue(outside == planted, "every tree accounted for");
            } finally {
                CityBasin.set(before);
            }
            h.succeed();
        });
    }
}
