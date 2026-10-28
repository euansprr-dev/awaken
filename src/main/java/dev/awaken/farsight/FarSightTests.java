package dev.awaken.farsight;

import dev.awaken.Awaken;
import dev.awaken.city.CityLayout;
import dev.awaken.city.CityPlan;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.farsight.voxel.VoxelMesh;
import dev.awaken.farsight.voxel.VoxelMesher;
import dev.awaken.farsight.voxel.VoxelSnapshot;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import dev.awaken.terrain.city.CityBasinFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

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

        // Round Carillon the far ground is the basin as the real terrain builds it (CityBasinFunction wraps the final
        // density): the street runs flat out to where the blend lets the hills stand, then a cliff, never the long rise
        // that blending heights drew there (Euan, 2026-10-26: a far wall that turned out flat). Against the wrapped final
        // density itself, column by column through the blend, on a site among vanilla's hills (seed 777).
        AwakenTests.register("farsight_columns_basin", 600, h -> {
            ServerLevel level = h.getLevel();
            var access = level.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), settings);
            LevelHeightAccessor heights = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
            CitySite before = CityBasin.current();
            try {
                // the site whose blend has the most ground standing well above the street
                RandomState plain = RandomState.create(access.lookupOrThrow(Registries.NOISE), 777L, settings.value());
                FarColumns natural = new FarColumns(gen, plain, access, heights, false, null);
                int bestX = 0, bestZ = 0, best = -1;
                for (int sz = -4000; sz <= 4000; sz += 1000) {
                    for (int sx = -4000; sx <= 4000; sx += 1000) {
                        int high = 0;
                        for (int a = 0; a < 24; a++) {
                            double t = a * Math.PI / 12;
                            if (natural.ground(sx + (int) (Math.cos(t) * 560), sz + (int) (Math.sin(t) * 560)) > CityBasin.STREET_Y + 20) high++;
                        }
                        if (high > best) {
                            best = high;
                            bestX = sx;
                            bestZ = sz;
                        }
                    }
                }
                CitySite site = new CitySite(bestX, bestZ, CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, CityBasin.STREET_Y, List.of(), 0, 0, true);
                CityBasin.set(site);
                // samplers compile with the basin set
                RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), 777L, settings.value());
                var real = rs.getSampler(new CityBasinFunction(settings.value().noiseRouter().finalDensity(),
                        CityBasinFunction.Mode.DENSITY, java.util.Optional.of(settings.value().noiseRouter().depth())));
                var ctx = SamplerContext.builder().enableCaches().build();
                FarColumns columns = new FarColumns(gen, rs, access, heights, false, site);
                var shape = new CityBasinFunction.Shape(site);
                List<Float> errors = new ArrayList<>(), old = new ArrayList<>();
                int above = 0, solved = 0;
                long nanos = 0, p0 = columns.probes;
                for (int a = 0; a < 24; a++) {
                    double t = a * Math.PI / 12;
                    for (int d = 340; d <= 720; d += 10) {
                        int x = bestX + (int) (Math.cos(t) * d), z = bestZ + (int) (Math.sin(t) * d);
                        int y = 260;
                        while (y > heights.getMinY() && real.sampleValue(ctx, x, y, z) <= 0f) y--;
                        long t0 = System.nanoTime();
                        float predicted = columns.ground(x, z);
                        nanos += System.nanoTime() - t0;
                        solved++;
                        float top = y + 1, blended = shape.ground(natural.ground(x, z), x, z);
                        if (top <= columns.seaLevel() && predicted <= columns.seaLevel()) continue;
                        errors.add(Math.abs(predicted - top));
                        old.add(Math.abs(blended - top));
                        if (predicted > top + 3) above++;
                    }
                }
                h.assertTrue(best >= 6 && errors.size() >= 300, "a hilly site and enough land: " + best + " high, " + errors.size() + " columns");
                java.util.Collections.sort(errors);
                java.util.Collections.sort(old);
                float median = errors.get(errors.size() / 2), p90 = errors.get(errors.size() * 9 / 10);
                Awaken.LOG.info("[farsight] the basin's blend on {} columns (site {} {}, {} of 24 high, {} us and {} probes a column): median {}, p90 {}, {} stand over 3 blocks too high; blending heights: median {}, p90 {}",
                        errors.size(), bestX, bestZ, best, String.format(java.util.Locale.ROOT, "%.1f", nanos / 1000.0 / solved),
                        String.format(java.util.Locale.ROOT, "%.1f", (columns.probes - p0) / (double) solved), median, p90, above,
                        old.get(old.size() / 2), old.get(old.size() * 9 / 10));
                h.assertTrue(median <= 1f && p90 <= 2f, "the far ground follows the basin: median " + median + ", p90 " + p90);
                h.assertTrue(above * 100 <= errors.size(), "no ground stands where it isn't: " + above + " of " + errors.size() + " over 3 blocks too high");
            } finally {
                CityBasin.set(before);
            }
            h.succeed();
        });

        // City Sight's mesher (SPEC §20) on a real chunk: a 4-block stone cube shows each face once (merged, whatever
        // the light), a slab its top half a block up, a one-block pillar its top; at level 1 (2-block cells) the cube
        // keeps its silhouette and the pillar, a quarter of its cells, thins out instead of swelling. A fence and a poppy
        // go in level 0's detail run only (perf build C): the fence as its boxes, the poppy as its cross.
        // The Soft Land's pebbles and twigs are a few pixels' worth of stone: City Sight draws them as nothing (as their bounds they were
        // grey boxes over every far meadow, Euan 2026-10-27), its slabs as the slabs they are, a poppy still as its cross
        AwakenTests.register("farsight_voxels_ground_cover", 20, h -> {
            for (var p : dev.awaken.ground.GroundCover.PEBBLES.get().getStateDefinition().getPossibleStates()) {
                byte k = dev.awaken.farsight.voxel.VoxelKinds.kind(Block.BLOCK_STATE_REGISTRY.getId(p));
                h.assertTrue(k == dev.awaken.farsight.voxel.VoxelKinds.SKIP, "pebbles draw nothing from afar: " + p + " " + k);
            }
            for (var t : dev.awaken.ground.GroundCover.TWIGS.get().getStateDefinition().getPossibleStates()) {
                byte k = dev.awaken.farsight.voxel.VoxelKinds.kind(Block.BLOCK_STATE_REGISTRY.getId(t));
                h.assertTrue(k == dev.awaken.farsight.voxel.VoxelKinds.SKIP, "twigs draw nothing from afar: " + t + " " + k);
            }
            byte slab = dev.awaken.farsight.voxel.VoxelKinds.kind(Block.BLOCK_STATE_REGISTRY.getId(dev.awaken.ground.SoftSlabs.TURF_SLAB.get().defaultBlockState()));
            h.assertTrue(slab == dev.awaken.farsight.voxel.VoxelKinds.BOX, "a turf slab is a box: " + slab);
            byte fence = dev.awaken.farsight.voxel.VoxelKinds.kind(Block.BLOCK_STATE_REGISTRY.getId(Blocks.OAK_FENCE.defaultBlockState()));
            h.assertTrue(fence == dev.awaken.farsight.voxel.VoxelKinds.THIN, "a fence is still a thin thing: " + fence);
            h.succeed();
        });

        // City Sight's water: its colour blends over a biome border, and every level lays the surface where the real one is
        AwakenTests.registerIsolated("farsight_voxels_water", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int bx = o.getX() & ~15, bz = o.getZ() & ~15, top = (o.getY() + 12) & ~3;
            // a lake four deep in a stone rim, its surface at a cell's lowest row on levels 1 and 2 (y a multiple of 4)
            for (int x = bx; x < bx + 16; x++) {
                for (int z = bz + 3; z < bz + 13; z++) {
                    for (int y = top - 4; y <= level.getMaxY(); y++) {
                        boolean rim = x == bx || x == bx + 15 || z <= bz + 4 || z >= bz + 11;
                        BlockState b = y == top - 4 || rim && y <= top ? Blocks.STONE.defaultBlockState()
                                : y <= top ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        level.setBlock(new BlockPos(x, y, z), b, Block.UPDATE_CLIENTS);
                    }
                }
            }
            // the chunk's west half one biome, its east half another, kept to put back
            LevelChunk chunk = level.getChunkAt(new BlockPos(bx, top, bz));
            var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> west = biomes.getOrThrow(Biomes.PLAINS), east = biomes.getOrThrow(Biomes.WARM_OCEAN);
            java.util.Map<Long, Holder<Biome>> was = new java.util.HashMap<>();
            for (int qy = QuartPos.fromBlock(level.getMinY()); qy <= QuartPos.fromBlock(level.getMaxY()); qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    for (int qx = 0; qx < 4; qx++) {
                        was.put(BlockPos.asLong(qx, qy, qz), chunk.getNoiseBiome(QuartPos.fromBlock(bx) + qx, qy, QuartPos.fromBlock(bz) + qz));
                    }
                }
            }
            chunk.fillBiomesFromNoise((qx, qy, qz) -> qx - QuartPos.fromBlock(bx) < 2 ? west : east);
            AwakenTests.awaitLight(h, new BlockPos(bx, top - 4, bz + 3), new BlockPos(bx + 15, top + 4, bz + 12));
            VoxelSnapshot snap = VoxelSnapshot.take(level, chunk);
            chunk.fillBiomesFromNoise((qx, qy, qz) -> was.get(BlockPos.asLong(qx - QuartPos.fromBlock(bx), qy, qz - QuartPos.fromBlock(bz))));
            h.assertTrue(snap != null, "the chunk has blocks");
            int water = Block.BLOCK_STATE_REGISTRY.getId(Blocks.WATER.defaultBlockState());
            VoxelMesh mesh = new VoxelMesher(new VoxelMesher.Looks() {
                @Override
                public int face(int id, int dir) {
                    return id == water ? id | 1 << 16 : id & 0xFFFF;
                }

                @Override
                public int tint(int id, int tintIndex, Holder<Biome> biome) {
                    return id == water && biome != null ? biome.value().getWaterColor() & 0xFFFFFF : 0xFFFFFF;
                }

                @Override
                public int window(int id, int dir) {
                    return id & 0xFFFF;
                }
            }).mesh(snap, 0);
            int surface = (top - level.getMinY()) * 16 + 14;
            for (int l = 0; l < 3; l++) {
                int[] q = waterTop(mesh, l, water, 8, 7);
                h.assertTrue(q != null, "level " + l + " has the lake's surface");
                h.assertTrue((q[1] & 16383) == surface, "level " + l + "'s surface where the real one is: "
                        + ((q[1] & 16383) - surface) + " sixteenths off");
            }
            int a = west.value().getWaterColor() & 0xFFFFFF, b = east.value().getWaterColor() & 0xFFFFFF;
            h.assertTrue((waterTop(mesh, 0, water, 1, 7)[3] & 0xFFFFFF) == a, "the west shore its own biome's water");
            h.assertTrue((waterTop(mesh, 0, water, 14, 7)[3] & 0xFFFFFF) == b, "the east shore its own biome's water");
            for (int x = 6; x <= 9; x++) {
                int t = waterTop(mesh, 0, water, x, 7)[3] & 0xFFFFFF;
                boolean between = true;
                for (int sh = 0; sh <= 16; sh += 8) {
                    int c = t >> sh & 255, ca = a >> sh & 255, cb = b >> sh & 255;
                    between &= c > Math.min(ca, cb) && c < Math.max(ca, cb) || ca == cb && c == ca;
                }
                h.assertTrue(between, "at the border (x " + x + ") the two waters blend: " + Integer.toHexString(t));
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("farsight_voxels_mesh", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int x0 = (o.getX() & ~15) + 4, z0 = (o.getZ() & ~15) + 4, y0 = (o.getY() + 2) & ~1;
            for (int x = x0 - 2; x < x0 + 11; x++) {
                for (int z = z0 - 2; z < z0 + 9; z++) {
                    for (int y = y0; y <= level.getMaxY(); y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
            for (int y = y0; y < y0 + 4; y++) {
                for (int z = z0; z < z0 + 4; z++) for (int x = x0; x < x0 + 4; x++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
            }
            for (int y = y0; y < y0 + 6; y++) level.setBlockAndUpdate(new BlockPos(x0 + 8, y, z0 + 1), Blocks.OAK_PLANKS.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x0 + 6, y0, z0 + 6), Blocks.STONE_SLAB.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x0 + 10, y0, z0 + 6), Blocks.OAK_FENCE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x0 + 10, y0, z0 + 2), Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x0 + 10, y0 + 1, z0 + 2), Blocks.POPPY.defaultBlockState());
            int poppy = Block.BLOCK_STATE_REGISTRY.getId(Blocks.POPPY.defaultBlockState());
            // faces carry the light in front of them (a dark room's aren't drawn): the light first
            AwakenTests.awaitLight(h, new BlockPos(x0 - 2, y0, z0 - 2), new BlockPos(x0 + 10, y0 + 8, z0 + 8));
            LevelChunk chunk = level.getChunkAt(new BlockPos(x0, y0, z0));
            VoxelSnapshot snap = VoxelSnapshot.take(level, chunk);
            h.assertTrue(snap != null, "the chunk has blocks");
            VoxelMesher mesher = new VoxelMesher(looks(poppy));
            long t0 = System.nanoTime();
            VoxelMesh mesh = mesher.mesh(snap, 7);
            double ms = (System.nanoTime() - t0) / 1e6;
            int lx = x0 & 15, lz = z0 & 15, base = (y0 - level.getMinY()) * 16;
            // areas in square sixteenths of the faces in a box (dir; plane; u and v ranges in sixteenths)
            h.assertTrue(area(mesh, 0, 1, base + 64, lx * 16, lx * 16 + 64, lz * 16, lz * 16 + 64) == 64 * 64,
                    "the cube's top, once: " + area(mesh, 0, 1, base + 64, lx * 16, lx * 16 + 64, lz * 16, lz * 16 + 64));
            h.assertTrue(area(mesh, 0, 5, (lx + 4) * 16, lz * 16, lz * 16 + 64, base, base + 64) == 64 * 64, "the cube's east side, once");
            h.assertTrue(area(mesh, 0, 1, base + 8, (lx + 6) * 16, (lx + 7) * 16, (lz + 6) * 16, (lz + 7) * 16) == 256, "the slab's top, half a block up");
            h.assertTrue(area(mesh, 0, 1, base + 96, (lx + 8) * 16, (lx + 9) * 16, (lz + 1) * 16, (lz + 2) * 16) == 256, "the pillar's top");
            int top1 = area(mesh, 1, 1, base + 64, lx * 16, lx * 16 + 64, lz * 16, lz * 16 + 64);
            h.assertTrue(top1 == 64 * 64, "level 1 keeps the cube's silhouette: " + top1);
            h.assertTrue(!any(mesh, 1, (lx + 8) * 16, (lz + 1) * 16, base + 16), "level 1 thins the pillar out");
            for (int l = 0; l < VoxelMesher.LEVELS; l++) {
                for (int q = 0; q < mesh.quadCount(l); q++) h.assertTrue((mesh.quads()[l][q * 4 + 2] >>> 16) == 7, "every quad carries its slot");
            }
            // the detail run: the poppy's two diagonals and the fence's post, nothing of either among the faces
            int[] st = mesh.starts()[0], q0 = mesh.quads()[0];
            int crosses = 0, posts = 0;
            for (int q = st[6]; q < st[7]; q++) {
                int dir = q0[q * 4] >> 18 & 7, qx = (q0[q * 4] & 511) >> 4, qz = (q0[q * 4] >> 9 & 511) >> 4;
                h.assertTrue((q0[q * 4 + 1] >>> 28 & 1) == 1, "a detail quad says so");
                if (dir >= 6 && qx == lx + 10) crosses++;
                else if (dir < 6 && qx == lx + 10 && qz == lz + 6) posts++;
            }
            h.assertTrue(crosses == 2, "the poppy as a cross: " + crosses);
            h.assertTrue(posts >= 5, "the fence's post, its sides and top: " + posts);
            for (int q = 0; q < st[6]; q++) {
                int qx = (q0[q * 4] & 511) >> 4, qz = (q0[q * 4] >> 9 & 511) >> 4, y16 = q0[q * 4 + 1] & 16383;
                h.assertTrue(!(qx == lx + 10 && qz == lz + 6 && y16 >= base && y16 < base + 16 && (q0[q * 4] >> 18 & 7) >= 2),
                        "the fence isn't among the faces");
            }
            // kept on disk and read back, the same blocks and light
            try {
                byte[] blob = dev.awaken.farsight.voxel.VoxelDisk.encode(snap);
                VoxelSnapshot back = dev.awaken.farsight.voxel.VoxelDisk.of(level).decode(snap.chunkX(), snap.chunkZ(), blob);
                h.assertTrue(back.hash() == snap.hash() && back.fromSection() == snap.fromSection(), "a kept snapshot reads back the same");
                Awaken.LOG.info("[farsight] voxel snapshot kept in {} bytes", blob.length);
            } catch (java.io.IOException e) {
                h.fail("a kept snapshot can't be read back: " + e);
            }
            Awaken.LOG.info("[farsight] voxel mesh: {} ms, quads by level {} {} {} {}", String.format(java.util.Locale.ROOT, "%.2f", ms),
                    mesh.quadCount(0), mesh.quadCount(1), mesh.quadCount(2), mesh.quadCount(3));
            h.succeed();
        });

        // A cave's mouth under an overhang (Euan, 2026-10-27: a white band at a river's cave mouths in a new world): a
        // tunnel at the foot of a 44-block rock, lit from the next chunk's cliff and dark by the time it crosses into
        // this one, lies far under this chunk's ground (the rock's top). City Sight's snapshot still reaches down to it,
        // since the chunk beside it has sky light there, and the mesh closes the tunnel's dark end with its curtain.
        AwakenTests.registerIsolated("farsight_voxels_overhang", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int ax = o.getX() & ~15, bx = ax + 16, z0 = o.getZ() & ~15, gy = level.getMinY() + 4, top = gy + 43;
            BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
            for (int x = ax; x < bx + 16; x++) {
                boolean cliff = x >= bx + 14;
                for (int z = z0; z < z0 + 16; z++) {
                    boolean tunnel = x >= ax + 6 && z >= z0 + 2 && z <= z0 + 4;
                    for (int y = gy - 1; y <= (cliff ? o.getY() + 26 : top); y++) {
                        boolean open = cliff && y >= gy || tunnel && y >= gy && y <= gy + 2;
                        level.setBlock(new BlockPos(x, y, z), open ? air : stone, Block.UPDATE_CLIENTS);
                    }
                }
            }
            AwakenTests.awaitLight(h, new BlockPos(ax - 1, gy, z0 - 1), new BlockPos(bx + 16, top + 1, z0 + 16));
            h.assertTrue(level.getBrightness(LightLayer.SKY, new BlockPos(bx, gy, z0 + 3)) > 0
                    && level.getBrightness(LightLayer.SKY, new BlockPos(ax + 15, gy, z0 + 3)) == 0, "the tunnel is lit in the cliff's chunk, dark here");
            VoxelSnapshot snap = VoxelSnapshot.take(level, level.getChunkAt(new BlockPos(ax, gy, z0)));
            h.assertTrue(snap != null && snap.minY() <= gy - 1, "the snapshot reaches the tunnel under the rock: from " + (snap == null ? "none" : snap.minY()));
            VoxelMesh mesh = new VoxelMesher(looks(-1)).mesh(snap, 1);
            int v0 = (gy - level.getMinY()) * 16, closed = area(mesh, 0, 5, 256, 2 * 16, 5 * 16, v0, v0 + 48);
            h.assertTrue(closed == 48 * 48, "the tunnel's dark end is closed where it leaves the chunk: " + closed);
            h.succeed();
        });

        // What floats isn't ground (Euan, 2026-10-27: a sky platform stood on "huge spiky pillars" in the far view): a deck
        // 30 blocks up with a chain and a lone block under it keeps the ground under it in its cells, an island's leaves over
        // open air aren't a tree's crown (a trunk up to it) while a tall tree keeps its own, and a sealed dark room under a
        // crust (a cave) keeps the crust.
        AwakenTests.registerIsolated("farsight_floating_ground", 100, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            int bx = o.getX() & ~15, bz = o.getZ() & ~15, y0 = o.getY() + 2;
            BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
            // the fixture owns its columns (it can land under real terrain): open to the sky over a stone floor, deep
            // enough to stand (the test world's ground is far under it)
            for (int x = bx + 2; x < bx + 16; x++) {
                for (int z = bz + 2; z < bz + 16; z++) {
                    if (x >= bx + 10 != z >= bz + 10) continue;
                    for (int y = y0 - FarChunk.FLOAT_RUN; y <= y0; y++) level.setBlock(new BlockPos(x, y, z), stone, Block.UPDATE_CLIENTS);
                    for (int y = y0 + 1; y <= level.getMaxY(); y++) level.setBlock(new BlockPos(x, y, z), air, Block.UPDATE_CLIENTS);
                }
            }
            // the deck: cells (2, 2) to (3, 3), two thick; a chain from one corner down to 3 over the floor, a block on it
            // 18 over the chain's foot
            for (int x = bx + 4; x < bx + 8; x++) {
                for (int z = bz + 4; z < bz + 8; z++) {
                    for (int y = y0 + 30; y < y0 + 32; y++) level.setBlock(new BlockPos(x, y, z), stone, Block.UPDATE_CLIENTS);
                }
            }
            BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();
            for (int y = y0 + 4; y < y0 + 30; y++) level.setBlock(new BlockPos(bx + 4, y, bz + 4), y == y0 + 22 ? stone : chain, Block.UPDATE_CLIENTS);
            // an island's leaves 45 over the floor in cell (1, 1), nothing under them; a tree 20 tall in cell (4, 4), its crown
            // spreading over the air beside its trunk
            for (int k = 0; k < 4; k++) level.setBlock(new BlockPos(bx + 2 + (k & 1), y0 + 45, bz + 2 + (k >> 1)),
                    Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
            for (int y = y0 + 1; y <= y0 + 20; y++) level.setBlock(new BlockPos(bx + 8, y, bz + 8), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int k = 0; k < 4; k++) level.setBlock(new BlockPos(bx + 8 + (k & 1), y0 + 21, bz + 8 + (k >> 1)), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
            // the cave: a 6x6 stone block standing on the floor, its 4x4 room (cells (6, 6) inside it) 21 high and sealed
            for (int x = bx + 10; x < bx + 16; x++) {
                for (int z = bz + 10; z < bz + 16; z++) {
                    boolean room = x > bx + 10 && x < bx + 15 && z > bz + 10 && z < bz + 15;
                    for (int y = y0 + 1; y <= y0 + 24; y++) level.setBlock(new BlockPos(x, y, z), room && y <= y0 + 21 ? air : stone, Block.UPDATE_CLIENTS);
                }
            }
            AwakenTests.awaitLight(h, new BlockPos(bx, y0, bz), new BlockPos(bx + 15, y0 + 40, bz + 15));
            FarChunk c = FarChunk.of(level.getChunkAt(new BlockPos(bx, y0, bz)));
            for (int k = 0; k < 4; k++) {
                int i = (2 + (k >> 1)) * FarChunk.SIDE + 2 + (k & 1);
                h.assertTrue(FarChunk.top(c.cell()[i]) == y0 + 1 && FarChunk.canopy(c.cell()[i]) == 0,
                        "the ground under the deck, not the deck: cell " + i + " top " + FarChunk.top(c.cell()[i]) + " canopy " + FarChunk.canopy(c.cell()[i]));
            }
            int island = FarChunk.SIDE + 1, tree = 4 * FarChunk.SIDE + 4;
            h.assertTrue(FarChunk.top(c.cell()[island]) == y0 + 1 && FarChunk.canopy(c.cell()[island]) == 0,
                    "an island's leaves over open air aren't a tree's crown: canopy " + FarChunk.canopy(c.cell()[island]));
            h.assertTrue(FarChunk.canopy(c.cell()[tree]) == 21, "a tall tree keeps its crown: canopy " + FarChunk.canopy(c.cell()[tree]));
            int cave = 6 * FarChunk.SIDE + 6;
            h.assertTrue(FarChunk.top(c.cell()[cave]) == y0 + 25, "a cave's crust stands: top " + FarChunk.top(c.cell()[cave]) + " not " + (y0 + 25));
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
            // the pillar: chunk cell (2, 2), 12 high; the pool: cell (4, 2), 3 deep on a stone floor; both on a foundation
            // (standing in the air over the test world's ground they would float, and far terrain keeps the ground under it)
            for (int x = x0; x < x0 + 6; x++) {
                for (int z = z0; z < z0 + 2; z++) {
                    for (int y = y0 - FarChunk.FLOAT_RUN; y < y0; y++) level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
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
            // the chunk is the test's own: nothing over it, and plains under it (snow on the pillar read one block high,
            // "top 31", where another test had filled in a snowy biome and left the rain on)
            AwakenTests.clearToSky(level, new BlockPos(x0 - 4, y0, z0 - 4), new BlockPos(x0 + 11, y0, z0 + 11));
            var cmd = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            level.getServer().getCommands().performPrefixedCommand(cmd, "fillbiome " + (x0 - 4) + " " + (y0 - 4) + " " + (z0 - 4) + " "
                    + (x0 + 11) + " " + (y0 + 40) + " " + (z0 + 11) + " minecraft:plains");
            // (on a foundation: standing in the air over the test world's ground it would float, and far terrain keeps the
            // ground under what floats)
            for (int y = y0 - FarChunk.FLOAT_RUN; y < y0 + 23; y++) {
                for (int k = 0; k < 4; k++) level.setBlock(new BlockPos(x0 + (k & 1), y, z0 + (k >> 1)),
                        (y < y0 ? Blocks.STONE : Blocks.STONE_BRICKS).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            boolean was = FarStore.collecting;
            FarStore.collecting = false;
            AwakenTests.onEnd(h, () -> FarStore.collecting = was); // left on, it sums every chunk loaded for the rest of the run
            int cell = 2 * FarChunk.SIDE + 2, cx = x0 >> 4, cz = z0 >> 4;
            h.runAfterDelay(3, () -> FarStore.collecting = true);
            h.succeedWhen(() -> {
                FarChunk seen = FarStore.of(level).get(cx, cz);
                h.assertTrue(seen != null && FarChunk.top(seen.cell()[cell]) == y0 + 23,
                        "the loaded chunk reached the store: " + (seen == null ? "not yet" : "top " + FarChunk.top(seen.cell()[cell])));
            });
        });

        AwakenTests.registerIsolated("farsight_trees_crowns", 20, h -> {
            // far trees take the real trees' proportions from their height: a tall broadleaf forest (the lumen forest's
            // trees stand 17 tall, a crown 6 deep and 11 wide) grows wide caps, never columns; an oak wood stays oaks
            int ring = 2, n = Rings.PATCH * Rings.PATCH;
            for (int tallTrees : new int[] {17, 6}) {
                int[] height = new int[n], colour = new int[n], info = new int[n], crown = new int[n];
                for (int k = 0; k < n; k++) {
                    height[k] = FarColumns.packHeight(80f);
                    info[k] = FarColumns.abgr(0, tallTrees * 4, 255, 128);
                    crown[k] = FarColumns.abgr(0x2E5A6E, FarPalette.BROADLEAF);
                }
                int[] out = new int[FarTrees.MAX * FarTrees.INTS];
                int planted = FarTrees.plant(ring, 3, 5, height, colour, info, crown, 63, out);
                h.assertTrue(planted > 20, "a dense forest plants: " + planted);
                for (int t = 0; t < planted; t++) {
                    float tall = (out[t * FarTrees.INTS + 1] >> 16 & 255) / 4f, width = (out[t * FarTrees.INTS + 2] >>> 24) / 8f;
                    float deep = tall - out[t * FarTrees.INTS + 3] / 4f;
                    if (tallTrees > 12) {
                        h.assertTrue(width > deep && width >= 0.45f * tall, "a tall tree's crown is a wide cap: " + width + " wide, "
                                + deep + " deep on a tree " + tall + " tall");
                    } else {
                        h.assertTrue(width >= 3f && width <= 7f && deep >= 2f && deep <= 0.7f * tall, "an oak's crown: " + width
                                + " wide, " + deep + " deep on a tree " + tall + " tall");
                    }
                }
            }
            h.succeed();
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

    /** Plain looks for the mesher: a face is its block's id, untinted; {@code cross} is drawn as crossed quads. */
    private static VoxelMesher.Looks looks(int cross) {
        return new VoxelMesher.Looks() {
            @Override
            public int face(int id, int dir) {
                return id & 0xFFFF;
            }

            @Override
            public int tint(int id, int tintIndex, Holder<Biome> biome) {
                return 0xFFFFFF;
            }

            @Override
            public int window(int id, int dir) {
                return id & 0xFFFF;
            }

            @Override
            public boolean cross(int id) {
                return id == cross;
            }
        };
    }

    /** Level l's water top over chunk column (x, z): its quad's four ints, or null. */
    private static int[] waterTop(VoxelMesh mesh, int l, int water, int x, int z) {
        int[] q = mesh.quads()[l];
        for (int i = 0; i + 3 < mesh.quadCount(l) * 4; i += 4) {
            int qx = q[i] & 511, qz = q[i] >> 9 & 511, d = q[i] >> 18 & 7, eu = q[i] >> 21 & 511, ev = q[i + 1] >> 14 & 16383;
            if (d == 1 && (q[i + 2] & 0xFFFF) == (water & 0xFFFF) && x * 16 >= qx && x * 16 < qx + eu && z * 16 >= qz && z * 16 < qz + ev) {
                return new int[] {q[i], q[i + 1], q[i + 2], q[i + 3]};
            }
        }
        return null;
    }

    /** The area (square sixteenths) of level l's faces toward dir lying on plane, within the face's u and v ranges. */
    private static int area(VoxelMesh mesh, int l, int dir, int plane, int u0, int u1, int v0, int v1) {
        int[] q = mesh.quads()[l];
        int total = 0;
        for (int i = 0; i < q.length; i += 4) {
            int x = q[i] & 511, z = q[i] >> 9 & 511, d = q[i] >> 18 & 7, eu = q[i] >> 21 & 511;
            int y = q[i + 1] & 16383, ev = q[i + 1] >> 14 & 16383;
            if (d != dir) continue;
            int p, u, v;
            if (dir < 2) { p = y; u = x; v = z; }
            else if (dir < 4) { p = z; u = x; v = y; }
            else { p = x; u = z; v = y; }
            if (p != plane) continue;
            int a = Math.max(0, Math.min(u + eu, u1) - Math.max(u, u0)), b = Math.max(0, Math.min(v + ev, v1) - Math.max(v, v0));
            total += a * b;
        }
        return total;
    }

    /** Whether any of level l's quads covers column (x16, z16) at or above y16. */
    private static boolean any(VoxelMesh mesh, int l, int x16, int z16, int y16) {
        int[] q = mesh.quads()[l];
        for (int i = 0; i < q.length; i += 4) {
            int x = q[i] & 511, z = q[i] >> 9 & 511, d = q[i] >> 18 & 7, eu = q[i] >> 21 & 511;
            int y = q[i + 1] & 16383, ev = q[i + 1] >> 14 & 16383;
            int xs = d < 4 ? eu : 0, zs = d < 2 ? ev : d >= 4 ? eu : 0, ys = d < 2 ? 0 : ev;
            if (x16 + 8 >= x && x16 + 8 <= x + xs && z16 + 8 >= z && z16 + 8 <= z + zs && y + ys > y16) return true;
        }
        return false;
    }
}
