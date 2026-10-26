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

    /**
     * Along 36 rays from (cx, cz), every 8 blocks from 330 to 600 out (the blend, before the land takes over): how many
     * steps rise more than 8 blocks, the largest rise, how many columns have an air gap of 3 or more under their top
     * (within 24 blocks), and the steps counted.
     */
    private static int[] rim(DensitySampler density, int cx, int cz) {
        SamplerContext ctx = SamplerContext.builder().enableCaches().build();
        int cliffs = 0, maxRise = 0, undercut = 0, steps = 0;
        for (int a = 0; a < 36; a++) {
            double t = a * Math.PI / 18;
            int last = Integer.MIN_VALUE;
            for (int d = 330; d <= 600; d += 8) {
                int x = cx + (int) Math.round(Math.cos(t) * d), z = cz + (int) Math.round(Math.sin(t) * d);
                int y = 260;
                while (y > -64 && density.sampleValue(ctx, x, y, z) <= 0f) y--;
                // an air gap under the top: solid, then 3 or more air, then solid again
                int run = 0;
                boolean gap = false;
                for (int k = y - 1; k >= y - 24; k--) {
                    if (density.sampleValue(ctx, x, k, z) <= 0f) run++;
                    else {
                        if (run >= 3) gap = true;
                        run = 0;
                    }
                }
                if (gap) undercut++;
                if (last != Integer.MIN_VALUE) {
                    int rise = Math.abs(y - last);
                    maxRise = Math.max(maxRise, rise);
                    if (rise > 8) cliffs++;
                    steps++;
                }
                last = y;
            }
        }
        return new int[]{cliffs, maxRise, undercut, steps};
    }

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

        // The basin's edge is a slope (2026-10-26, Euan: a sheer cliff ringing the city, undercut into a great cave): all round
        // a site among vanilla's hills (seed 777), through the blend, the ground as the wrapped final density builds it
        // rises no steeper than a block a block over 8 blocks bar a few steps, never a wall, and stands on itself (no air
        // under its top).
        // The original aim at the street (no depth) logged beside it for the record.
        AwakenTests.register("terrain_city_basin_rim", 600, h -> {
            CitySite before = CityBasin.current();
            boolean softBefore = CityBasin.softRim();
            try {
                var access = h.getLevel().registryAccess();
                Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
                var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
                NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), settings);
                var heights = net.minecraft.world.level.LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
                RandomState plain = RandomState.create(access.lookupOrThrow(Registries.NOISE), 777L, settings.value());
                // the site whose blend has the most land well above the street
                int bestX = 0, bestZ = 0, best = -1;
                for (int sz = -4000; sz <= 4000; sz += 1000) {
                    for (int sx = -4000; sx <= 4000; sx += 1000) {
                        int high = 0;
                        for (int a = 0; a < 12; a++) {
                            double t = a * Math.PI / 6;
                            int y = gen.getBaseHeight(sx + (int) (Math.cos(t) * 560), sz + (int) (Math.sin(t) * 560),
                                    net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, heights, plain);
                            if (y > CityBasin.STREET_Y + 25) high++;
                        }
                        if (high > best) {
                            best = high;
                            bestX = sx;
                            bestZ = sz;
                        }
                    }
                }
                CitySite site = new CitySite(bestX, bestZ, CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, CityBasin.STREET_Y,
                        java.util.List.of(), 0, 0, true);
                CityBasin.set(site);
                CityBasin.softRim(true);
                var router = settings.value().noiseRouter();
                RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), 777L, settings.value());
                DensitySampler soft = rs.getSampler(new CityBasinFunction(router.finalDensity(), CityBasinFunction.Mode.DENSITY,
                        java.util.Optional.of(router.depth())));
                DensitySampler steep = rs.getSampler(new CityBasinFunction(router.finalDensity(), CityBasinFunction.Mode.DENSITY));
                int[] softRim = rim(soft, bestX, bestZ), steepRim = rim(steep, bestX, bestZ);
                dev.awaken.Awaken.LOG.info("[terrain] the basin's rim at {} {} ({} of 12 high), {} steps: soft {} cliff steps (max rise {} over 8), "
                                + "{} undercut columns; the street aim {} cliff steps (max {}), {} undercut",
                        bestX, bestZ, best, softRim[3], softRim[0], softRim[1], softRim[2], steepRim[0], steepRim[1], steepRim[2]);
                h.assertTrue(best >= 6, "a hilly site: " + best + " of 12 high");
                h.assertTrue(softRim[0] * 100 <= softRim[3] * 2, "the rim is a slope: " + softRim[0] + " of " + softRim[3] + " steps rise over 8 in 8");
                h.assertTrue(softRim[2] * 100 <= softRim[3] * 2, "the rim stands on itself: " + softRim[2] + " undercut columns");
                // no wall anywhere round it (the street aim's rim rose 72 blocks in 8)
                h.assertTrue(softRim[1] <= 24, "no cliff in the rim: the largest rise " + softRim[1] + " blocks in 8");
            } finally {
                CityBasin.set(before);
                CityBasin.softRim(softBefore);
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
