package dev.awaken.terrain;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeManager;

/** The wind is the same everywhere it is computed, stays in range, turns slowly and its gust fronts travel downwind. */
final class WindTests {
    private WindTests() {}

    static void register() {
        AwakenTests.register("terrain_wind_deterministic", 20, h -> {
            var level = h.getLevel();
            var anyBiome = level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().findFirst().orElseThrow();
            long salt = Wind.salt(level);
            // a client holds only the hashed seed the server sends at login: a biome manager built from it agrees
            long fromHash = Wind.saltOf(new BiomeManager((x, y, z) -> anyBiome, BiomeManager.obfuscateSeed(level.getSeed())), anyBiome);
            h.assertTrue(salt == fromHash, "salt from the level " + salt + " equals the salt from the hashed seed " + fromHash);
            long other = Wind.saltOf(new BiomeManager((x, y, z) -> anyBiome, BiomeManager.obfuscateSeed(level.getSeed() + 1)), anyBiome);
            h.assertTrue(other != salt, "another seed, another wind");
            h.assertTrue(Wind.salt(level) == salt, "cached salt is stable");

            Wind.Field a = new Wind.Field(), b = new Wind.Field();
            for (long t = 0; t < 48_000; t += 977) {
                a.set(salt, t, 0.37F, 0.2F, 0F);
                b.set(salt, t, 0.37F, 0.2F, 0F);
                h.assertTrue(a.dirX == b.dirX && a.dirZ == b.dirZ && a.steady == b.steady, "same inputs, same wind at " + t);
                h.assertTrue(a.gust(1234.5, -987.25) == b.gust(1234.5, -987.25), "same gust at " + t);
            }
            h.succeed();
        });

        AwakenTests.register("terrain_wind_ranges", 20, h -> {
            long salt = Wind.salt(h.getLevel());
            Wind.Field f = new Wind.Field(), prev = new Wind.Field();
            float minGust = 1, maxGust = 0, minSteady = 1, maxSteady = 0;
            double minAngle = Double.MAX_VALUE, maxAngle = -Double.MAX_VALUE, unwrapped = 0, last = Double.NaN;
            float maxStep = 0;
            // two in-game days, a tick at a time
            for (long t = 0; t < 48_000; t++) {
                prev.dirX = f.dirX;
                prev.dirZ = f.dirZ;
                f.set(salt, t, 0F, 0F, 0F);
                h.assertTrue(Math.abs(f.dirX * f.dirX + f.dirZ * f.dirZ - 1F) < 1e-4F, "direction is a unit vector");
                h.assertTrue(f.steady >= 0F && f.steady <= 1F, "steady in range: " + f.steady);
                float g = f.gust(t * 0.37, -t * 0.11);
                h.assertTrue(g >= 0F && g <= 1F, "gust in range: " + g);
                float s = f.strength(t * 0.37, -t * 0.11);
                h.assertTrue(s >= 0F && s <= 1F, "strength in range: " + s);
                minGust = Math.min(minGust, g);
                maxGust = Math.max(maxGust, g);
                minSteady = Math.min(minSteady, f.steady);
                maxSteady = Math.max(maxSteady, f.steady);
                double ang = Math.atan2(f.dirZ, f.dirX);
                if (!Double.isNaN(last)) {
                    double d = Math.IEEEremainder(ang - last, 2 * Math.PI);
                    unwrapped += d;
                    maxStep = Math.max(maxStep, (float) Math.abs(d));
                }
                last = ang;
                minAngle = Math.min(minAngle, unwrapped);
                maxAngle = Math.max(maxAngle, unwrapped);
            }
            h.assertTrue(minGust < 0.05F && maxGust > 0.6F, "it gusts and it calms: " + minGust + ".." + maxGust);
            h.assertTrue(maxSteady - minSteady > 0.2F, "the steady wind breathes: " + minSteady + ".." + maxSteady);
            h.assertTrue(maxAngle - minAngle > Math.toRadians(30), "the heading wanders over two days: " + Math.toDegrees(maxAngle - minAngle));
            h.assertTrue(maxStep < Math.toRadians(1.5), "and turns slowly: at most " + Math.toDegrees(maxStep) + " degrees a tick");

            // rain and thunder raise it
            f.set(salt, 5_000, 0F, 0F, 0F);
            float calm = f.steady;
            f.set(salt, 5_000, 0F, 1F, 1F);
            h.assertTrue(f.steady > calm + 0.3F || f.steady == 1F, "a storm blows harder: " + calm + " -> " + f.steady);

            // a front passing a place reaches the place 70 blocks downwind 10 s later (mid-segment: one heading)
            long t0 = 500;
            f.set(salt, t0, 0F, 0F, 0F);
            double x = 300, z = -200;
            float here = f.gust(x, z);
            Wind.Field later = new Wind.Field().set(salt, t0 + 200, 0F, 0F, 0F);
            float there = later.gust(x + f.dirX * Wind.FRONT_SPEED * 10, z + f.dirZ * Wind.FRONT_SPEED * 10);
            h.assertTrue(Math.abs(here - there) < 1e-3F, "the gust travels downwind: " + here + " then " + there);
            h.succeed();
        });
    }
}
