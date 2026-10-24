package dev.awaken.terrain;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashSet;

/** The terrain studio reads the real overworld generator at a fixed seed (vanilla here: GameTests never load the pack). */
final class TerrainTests {
    private TerrainTests() {}

    static void register() {
        AwakenTests.register("terrain_lands_pack_new_worlds_only", 20, h -> {
            var server = h.getLevel().getServer();
            var pack = server.getPackRepository().getPack("mod/awaken:datapacks/awakened_lands");
            h.assertTrue(pack != null, "the Awakened Lands pack is offered");
            var wants = pack.getRequestedFeatures();
            // a world made before the flag existed has only vanilla's flags: the pack can never load there
            h.assertFalse(wants.isSubsetOf(FeatureFlags.DEFAULT_FLAGS), "the pack needs a flag old worlds lack");
            // the GameTest world is new, so it turned the flag on and loaded the pack
            h.assertTrue(server.getPackRepository().getSelectedIds().contains(pack.getId()), "a new world loads it");
            h.assertTrue(wants.isSubsetOf(h.getLevel().enabledFeatures()), "a new world records the flag");
            h.assertTrue(h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME)
                    .get(ResourceKey.create(Registries.BIOME, Awaken.id("elderwood"))).isPresent(), "its biomes exist");
            h.succeed();
        });

        AwakenTests.register("terrain_survey_reads_the_generator", 400, h -> {
            var level = h.getLevel();
            var access = level.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), settings);
            long seed = 777L;
            RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());

            Survey.Grid g = Survey.sample("test", seed, gen, rs, level, 0, 0, 512, 32);
            h.assertTrue(g.size() == 33 && g.floor().length == 33 * 33, "grid size " + g.size());
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int f : g.floor()) {
                lo = Math.min(lo, f);
                hi = Math.max(hi, f);
            }
            h.assertTrue(lo >= g.minY() && hi <= g.maxY() && hi > lo, "heights in the world: " + lo + ".." + hi);
            h.assertTrue(g.palette().size() >= 2, "several biomes near spawn: " + g.palette());
            var ids = new HashSet<>(g.palette());
            h.assertTrue(ids.stream().noneMatch(id -> id.contains("caves") || id.equals("minecraft:deep_dark")), "surface biomes only: " + ids);
            // the same answer twice (deterministic)
            Survey.Grid again = Survey.sample("test", seed, gen, rs, level, 0, 0, 512, 32);
            h.assertTrue(java.util.Arrays.equals(g.floor(), again.floor()) && g.palette().equals(again.palette()), "deterministic");

            // visit finds a plains heart and it really is plains at the surface
            Survey.Spot heart = Survey.findHeart(gen, rs, level, b -> b.is(Biomes.PLAINS), 0, 0, 4096);
            h.assertTrue(heart != null, "plains within 4096 blocks");
            var resolver = gen.getBiomeSource().createCachingResolver(rs);
            Holder<Biome> at = Survey.spot(gen, rs, level, resolver, heart.x(), heart.z()).biome();
            h.assertTrue(at.is(Biomes.PLAINS), "heart is plains: " + Survey.id(at));
            Survey.Spot top = Survey.lookout(gen, rs, level, 0, 0, 256);
            h.assertTrue(top != null && top.floor() >= gen.getSeaLevel(), "a lookout on dry ground");
            h.succeed();
        });
    }
}
