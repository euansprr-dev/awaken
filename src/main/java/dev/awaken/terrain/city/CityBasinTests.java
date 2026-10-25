package dev.awaken.terrain.city;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import dev.awaken.terrain.hero.HeroStructure;
import dev.awaken.terrain.hero.HeroTree;
import dev.awaken.terrain.landmark.Landmark;
import dev.awaken.terrain.landmark.LandmarkStructure;
import dev.awaken.terrain.landmark.LandmarkWorldgen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.Noise;

/** The city basin's shape, the reserve and the site choice (docs/SPEC.md §15 "City basin"). */
public final class CityBasinTests {
    private CityBasinTests() {}

    /** The basin samplers never ask the context for noise: a context that refuses keeps the test honest. */
    private static final DensityFunction.CompileContext NO_NOISE = new DensityFunction.CompileContext() {
        @Override
        public Noise createNoiseSampler(Holder<NormalNoise> parameters) {
            throw new IllegalStateException("no noise in this test");
        }

        @Override
        public RandomSource createRandom(Identifier seed) {
            return RandomSource.create(1);
        }

        @Override
        public RandomSource createEndIslandRandom() {
            return RandomSource.create(1);
        }
    };

    public static void register() {
        AwakenTests.register("terrain_city_basin_shape", 20, h -> {
            CitySite before = CityBasin.current();
            try {
                CitySite site = SitePicker.site(1000, -2000, true);
                CityBasin.set(site);
                // natural ground: all air (−1) above y 100, all rock (+1) below, so the basin's work is visible
                DensityFunction natural = DensityFunctions.yClampedGradient(99, 101, 1.0F, -1.0F);
                DensitySampler s = new CityBasinFunction(natural, CityBasinFunction.Mode.DENSITY).compileSampler(NO_NOISE);
                SamplerContext ctx = SamplerContext.EMPTY_UNCACHED;
                int cx = site.x(), cz = site.z();
                // a street block on the east side, away from the river: solid to 70, air above, ground 20 deep, nature below
                h.assertTrue(s.sampleValue(ctx, cx + 200, 70, cz) > 0 && s.sampleValue(ctx, cx + 200, 71, cz) < 0, "flat at street level");
                h.assertTrue(s.sampleValue(ctx, cx + 200, 52, cz) > 0, "a solid crust under the street");
                h.assertTrue(s.sampleValue(ctx, cx + 200, 85, cz) < 0, "the hill above street level is gone");
                // the river: open down to its floor at the centre line, solid below
                int[] p = CityBasin.RIVER.get(5);
                int rx = cx + p[0], rz = cz + p[1];
                h.assertTrue(s.sampleValue(ctx, rx, 60, rz) < 0 && s.sampleValue(ctx, rx, 57, rz) > 0, "the channel is open to y 57");
                // far away: untouched
                h.assertTrue(s.sampleValue(ctx, cx + 900, 85, cz) > 0 && s.sampleValue(ctx, cx + 900, 105, cz) < 0, "nature beyond the blend");
                // halfway through the blend, between the two
                float mid = s.sampleValue(ctx, cx + 515, 85, cz);
                h.assertTrue(mid > -1 && mid < 1, "blended at 515: " + mid);
                // the surface estimate follows
                DensitySampler surf = new CityBasinFunction(DensityFunctions.constant(100), CityBasinFunction.Mode.SURFACE).compileSampler(NO_NOISE);
                h.assertTrue(Math.abs(surf.sampleValue(ctx, cx, 0, cz) - 70) < 0.01 && Math.abs(surf.sampleValue(ctx, cx + 900, 0, cz) - 100) < 0.01,
                        "surface level 70 in the core, natural outside");
                h.assertTrue(surf.sampleValue(ctx, rx, 0, rz) + 8 < 63,
                        "the surface estimate dips under the river, so aquifers fill it");
                // the reserve
                h.assertTrue(CityBasin.reserved(cx, cz) && CityBasin.reserved(cx + 370, cz) && !CityBasin.reserved(cx + 390, cz), "reserve = core + 48");
                CityBasin.set(null);
                h.assertTrue(!CityBasin.reserved(cx, cz), "no site, no reserve");
                DensitySampler none = new CityBasinFunction(natural, CityBasinFunction.Mode.DENSITY).compileSampler(NO_NOISE);
                h.assertTrue(none.sampleValue(ctx, cx + 200, 85, cz) > 0, "no site: the input unchanged");
            } finally {
                CityBasin.set(before);
            }
            h.succeed();
        });

        // No hero tree or landmark may stand where any part of it reaches the reserve, in every direction (the old check
        // looked at four points on the crown's edge and missed the reserve between them), and the reach each declares
        // covers the blocks it really builds.
        AwakenTests.register("terrain_city_structures_keep_out", 200, h -> {
            CitySite before = CityBasin.current();
            try {
                CitySite site = SitePicker.site(1000, -2000, true);
                CityBasin.set(site);
                int reserve = site.coreRadius() + CityBasin.MARGIN;
                java.util.Map<String, Object[]> cases = new java.util.LinkedHashMap<>();
                for (HeroTree t : HeroTree.values()) {
                    double real = 0;
                    for (long seed = 1; seed <= 8; seed++) real = Math.max(real, t.build(seed * 1_000_003L).reach());
                    cases.put(t.getSerializedName(), new Object[]{t.reach(), real, (java.util.function.BiPredicate<Integer, Integer>) (x, z) -> HeroStructure.cityNear(x, z, t)});
                }
                for (Landmark l : Landmark.values()) {
                    double real = 0;
                    for (long seed = 1; seed <= 8; seed++) real = Math.max(real, LandmarkWorldgen.reachOf(l, seed * 1_000_003L));
                    cases.put(l.getSerializedName(), new Object[]{l.reach(), real, (java.util.function.BiPredicate<Integer, Integer>) (x, z) -> LandmarkStructure.cityNear(x, z, l)});
                }
                for (var e : cases.entrySet()) {
                    int reach = (Integer) e.getValue()[0];
                    double real = (Double) e.getValue()[1];
                    @SuppressWarnings("unchecked")
                    java.util.function.BiPredicate<Integer, Integer> near = (java.util.function.BiPredicate<Integer, Integer>) e.getValue()[2];
                    h.assertTrue(real <= reach, e.getKey() + " builds " + real + " blocks from its centre but declares a reach of " + reach);
                    for (int deg = 0; deg < 360; deg += 5) {
                        double a = Math.toRadians(deg);
                        int[] in = {site.x() + (int) Math.round(Math.cos(a) * (reserve + reach - 2)), site.z() + (int) Math.round(Math.sin(a) * (reserve + reach - 2))};
                        int[] out = {site.x() + (int) Math.round(Math.cos(a) * (reserve + reach + 2)), site.z() + (int) Math.round(Math.sin(a) * (reserve + reach + 2))};
                        h.assertTrue(near.test(in[0], in[1]), e.getKey() + " standing at " + deg + " degrees with its edge 2 blocks inside the reserve is refused");
                        h.assertTrue(!near.test(out[0], out[1]), e.getKey() + " standing at " + deg + " degrees with its edge 2 blocks clear of the reserve is allowed");
                    }
                }
                CityBasin.set(null);
                h.assertTrue(!HeroStructure.cityNear(site.x(), site.z(), HeroTree.ELDER_COLOSSUS), "no city, no reserve");
            } finally {
                CityBasin.set(before);
            }
            h.succeed();
        });

        AwakenTests.register("terrain_city_site_deterministic", 400, h -> {
            var access = h.getLevel().registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), settings);
            RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), 777L, settings.value());
            CitySite a = SitePicker.pick(gen, rs, h.getLevel(), true);
            CitySite b = SitePicker.pick(gen, rs, h.getLevel(), true);
            h.assertTrue(a.x() == b.x() && a.z() == b.z(), "the same seed picks the same site");
            h.assertTrue(Math.hypot(a.x(), a.z()) <= 1500, "near the origin: " + a.x() + ", " + a.z());
            int land = 0, n = 0;
            for (int dx = -300; dx <= 300; dx += 60) {
                for (int dz = -300; dz <= 300; dz += 60) {
                    if (dx * dx + dz * dz > 300 * 300) continue;
                    n++;
                    if (SitePicker.floor(gen, rs, h.getLevel(), a.x() + dx, a.z() + dz) >= gen.getSeaLevel() + 1) land++;
                }
            }
            h.assertTrue(land >= n * 0.75, "the core is mostly land: " + land + "/" + n);
            h.assertTrue(a.river().size() > CityBasin.RIVER.size(), "the river runs on past the core");
            h.succeed();
        });
    }
}
