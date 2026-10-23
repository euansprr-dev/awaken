package dev.awaken.core.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The GameTest server never creates datapack dimensions, so nothing else would notice a dimension that can't load or a
 * generator that throws. This decodes every {@code data/awaken/dimension/*.json} the way world loading does and builds
 * a few chunks of each with its own generator.
 */
final class WorldgenTests {
    private WorldgenTests() {}

    static void register() {
        AwakenTests.register("core_dimensions_load_and_generate", 200, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            Map<Identifier, ?> files = server.getResourceManager().listResources("dimension",
                    id -> id.getNamespace().equals("awaken") && id.getPath().endsWith(".json"));
            h.assertTrue(files.size() >= 16, "all custom dimensions are in the data, found " + files.size());
            var settings = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            RandomState random = RandomState.create(server.registryAccess().lookupOrThrow(Registries.NOISE), 1234L, settings);
            for (Identifier file : files.keySet()) {
                String name = file.getPath().substring("dimension/".length(), file.getPath().length() - ".json".length());
                LevelStem stem;
                try (var reader = server.getResourceManager().getResourceOrThrow(file).openAsReader()) {
                    var parsed = LevelStem.CODEC.parse(ops, JsonParser.parseReader(reader));
                    h.assertTrue(parsed.isSuccess(), name + " decodes: " + parsed.error().map(e -> e.message()).orElse(""));
                    stem = parsed.getOrThrow();
                } catch (IOException e) {
                    h.fail(name + " could not be read: " + e);
                    return;
                }
                var type = stem.type().value();
                var gen = stem.generator();
                h.assertFalse(gen.getBiomeSource().possibleBiomes().isEmpty(), name + " has biomes");
                LevelHeightAccessor heights = LevelHeightAccessor.create(type.minY(), type.height());
                for (ChunkPos pos : List.of(new ChunkPos(0, 0), new ChunkPos(3, -2), new ChunkPos(33, 17))) {
                    ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, heights, level.palettedContainerFactory(), null);
                    try {
                        gen.buildTerrain(chunk, Blender.empty(), random, level.structureManager(), level.getBiomeManager(), null,
                                gen.getBiomeSource().possibleBiomes()).join();
                        gen.getBaseHeight(pos.getMinBlockX(), pos.getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, heights, random);
                    } catch (RuntimeException e) {
                        h.fail(name + " fails to generate chunk " + pos + ": " + e);
                    }
                }
            }
            h.succeed();
        });
    }
}
