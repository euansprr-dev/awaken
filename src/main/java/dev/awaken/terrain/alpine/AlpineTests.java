package dev.awaken.terrain.alpine;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.TilingMode;

/** Awakened Alpine (SPEC §15): erosion's shape and the snow and rock rule, as maths; the pack for new worlds only. */
final class AlpineTests {
    private AlpineTests() {}

    static void register() {
        AwakenTests.register("terrain_alpine_erosion", 40, h -> {
            // a mountainside rising toward +x at 0.6 a block, its ground near y 179 (offset 0.4 there), under the summits' knee
            DensityFunction slope = DensityFunctions.add(DensityFunctions.constant(0.4f),
                    DensityFunctions.mul(DensityFunctions.constant(0.6f / 128f), xCoordinate()));
            DensitySampler s = new ErodedFunction(slope).compileSampler(null);
            SamplerContext ctx = SamplerContext.EMPTY_UNCACHED;
            double along = 0, across = 0, most = 0;
            int n = 0;
            for (int z = 0; z < 512; z += 4) {
                for (int x = -64; x < 64; x += 4) {
                    float a = change(s, ctx, x, z), b = change(s, ctx, x + 1, z), c = change(s, ctx, x, z + 1);
                    along += Math.abs(b - a);
                    across += Math.abs(c - a);
                    most = Math.max(most, Math.abs(a));
                    n++;
                    h.assertTrue(Math.abs(b - a) < 3f && Math.abs(c - a) < 3f, "smooth from block to block at " + x + "," + z);
                }
            }
            h.assertTrue(most > 6 && most < ErodedFunction.MAX_CHANGE, "it cuts, within its bounds: up to " + most + " blocks");
            // gullies run downhill: the ground changes far more across the slope than along it
            h.assertTrue(across > along * 1.8, "stripes run down the slope: across " + across / n + " along " + along / n);
            h.assertTrue(change(s, ctx, 37, 211) == change(s, ctx, 37, 211), "deterministic");
            // flat ground and lowland are left alone
            DensitySampler flat = new ErodedFunction(DensityFunctions.constant(0.4f)).compileSampler(null);
            h.assertTrue(flat.sampleValue(ctx, 100, 0, 100) == 0.4f, "no downhill, no gullies");
            DensitySampler low = new ErodedFunction(DensityFunctions.add(DensityFunctions.constant(-0.5f),
                    DensityFunctions.mul(DensityFunctions.constant(0.6f / 128f), xCoordinate()))).compileSampler(null);
            h.assertTrue(Math.abs(low.sampleValue(ctx, 0, 0, 50) + 0.5f) < 1e-6f, "lowland keeps its shape");
            // summits round off toward the ceiling instead of flattening on it
            h.assertTrue(ErodedFunction.summit(ErodedFunction.KNEE) == ErodedFunction.KNEE && ErodedFunction.summit(150f) == 150f, "untouched below the knee");
            float a = ErodedFunction.summit(260f), b = ErodedFunction.summit(320f), c = ErodedFunction.summit(420f);
            h.assertTrue(a < b && b < c && c < ErodedFunction.TOP, "higher ground stays higher, under the top: " + a + " " + b + " " + c);
            // ridges keep to the room under the ceiling: all of them on low ground, none past the ceiling up high
            h.assertTrue(RidgeRoomFunction.room(0.5f, 0f) == 0.5f, "low mountains keep their ridges");
            float top = ErodedFunction.TOP / 128f - 1f;
            float j = RidgeRoomFunction.room(0.65f, top);
            h.assertTrue(128f * (1f + top + j * RidgeRoomFunction.RIDGE) <= RidgeRoomFunction.CEILING + 0.01f, "the highest ridge stays under the ceiling");
            h.succeed();
        });

        AwakenTests.register("terrain_alpine_snow_and_rock", 20, h -> {
            // the same height, a north face (rising toward +z) and a south face
            float north = AlpineSurface.snowLine(0, 0, 0, 0.6f), south = AlpineSurface.snowLine(0, 0, 0, -0.6f);
            h.assertTrue(north < south - 20, "snow lies lower on north faces: " + north + " vs " + south);
            h.assertTrue(AlpineSurface.snow(0, 260, 0, 0.2f, 0, 0.5f), "snow high on gentle ground");
            h.assertFalse(AlpineSurface.snow(0, 260, 0, 1.5f, 0, 0.5f), "no snow on a steep face");
            h.assertFalse(AlpineSurface.snow(0, 120, 0, 0.2f, 0, 0.5f), "no snow low down");
            h.assertTrue(AlpineSurface.stone(2f, 0, 0.5f) == 2 && AlpineSurface.stone(1.3f, 0, 0.5f) == 1 && AlpineSurface.stone(0.5f, 0, 0.5f) == 0,
                    "rock on cliffs, scree below them, soil on slopes");
            // the pack asks for a flag old worlds lack; this new test world loaded it
            var server = h.getLevel().getServer();
            var pack = server.getPackRepository().getPack(AlpineLands.PACK_ID);
            h.assertTrue(pack != null, "the Awakened Alpine pack is offered");
            h.assertFalse(pack.getRequestedFeatures().isSubsetOf(FeatureFlags.DEFAULT_FLAGS), "old worlds can't load it");
            h.assertTrue(server.getPackRepository().getSelectedIds().contains(pack.getId()), "a new world loads it");
            // mountain forests: spruce below the treeline, on soil-holding slopes, planted by the pack
            h.assertTrue(AlpineSurface.treeAllowed(0, 120, 0, 0.3f, 0) && !AlpineSurface.treeAllowed(0, 240, 0, 0.3f, 0)
                    && !AlpineSurface.treeAllowed(0, 120, 0, 1.5f, 0), "trees below the treeline, never on cliffs");
            h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                    .get(ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id("alpine/mountain_spruce"))).isPresent(), "the mountain spruce loads");
            h.succeed();
        });
    }

    private static float change(DensitySampler s, SamplerContext ctx, int x, int z) {
        return (s.sampleValue(ctx, x, 0, z) - (0.4f + 0.6f / 128f * x)) * 128f;
    }

    /** The block x coordinate as a density function (a gradient along x with slope 1). */
    private static DensityFunction xCoordinate() {
        return DensityFunctions.gradient(Direction.Axis.X, TilingMode.CLAMP_TO_EDGE, -100_000, 100_000, -100_000f, 100_000f);
    }
}
