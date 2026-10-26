package dev.awaken.terrain.city;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Registers {@code awaken:city_basin} and chooses the city site when a server starts, before its levels (and their
 * generators' samplers) exist, so the very first chunk is already shaped by it. Cleared when the server stops, so the
 * next world in the same game never inherits it.
 */
public final class CityBasinSetup {
    private CityBasinSetup() {}

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Awaken.MODID);

    static {
        TYPES.register("city_basin", () -> CityBasinFunction.CODEC);
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener(CityBasinSetup::aboutToStart);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> CityBasin.set(null));
    }

    private static void aboutToStart(ServerAboutToStartEvent e) {
        MinecraftServer server = e.getServer();
        CityBasin.set(null);
        if (!CityBasin.wanted(server) && !devOverride(server)) return;
        LevelStem stem = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) {
            Awaken.LOG.warn("[terrain] city basin: the overworld has no noise generator, no site");
            return;
        }
        long seed = stem.seedOverride().orElse(server.getWorldGenSettings().options().seed());
        long t0 = System.currentTimeMillis();
        RandomState rs = RandomState.create(server.registryAccess().lookupOrThrow(Registries.NOISE), seed, gen.generatorSettings().value());
        boolean carved = carvable(server);
        CityBasin.softRim(softRim(server));
        CitySite site = SitePicker.pick(gen, rs, heights(stem), carved);
        CityBasin.set(site);
        Awaken.LOG.info("[terrain] city site at {} {} (street {}, basin {}) chosen in {} ms", site.x(), site.z(), site.streetY(),
                carved ? "carved" : "not carved: no Lithostitched or no Awakened Lands", System.currentTimeMillis() - t0);
    }

    /**
     * Whether this world's basin edge is the soft rise (SPEC §15): recorded once in {@code <world>/awaken/terrain_rim}.
     * A world with no overworld chunks yet is new and gets it; one with chunks keeps the cliff it was made with, so a
     * slope never meets an old cliff at a chunk's edge.
     */
    static boolean softRim(MinecraftServer server) {
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path marker = root.resolve("awaken").resolve("terrain_rim");
        try {
            if (Files.exists(marker)) return Files.readString(marker).trim().equals("soft");
            Path region = root.resolve("dimensions").resolve("minecraft").resolve("overworld").resolve("region");
            boolean fresh = !Files.isDirectory(region);
            if (!fresh) {
                try (var files = Files.list(region)) {
                    fresh = files.findAny().isEmpty();
                }
            }
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, fresh ? "soft\n" : "steep\n");
            return fresh;
        } catch (java.io.IOException ex) {
            Awaken.LOG.warn("[terrain] city basin: can't read or write {}: {}", marker, ex.toString());
            return false;
        }
    }

    /** The basin is carved by the lands pack's Lithostitched router wrap: both must be present in this world. */
    static boolean carvable(MinecraftServer server) {
        var lands = FeatureFlags.REGISTRY.fromNames(Set.of(Awaken.id("awakened_lands")), id -> {});
        return ModList.get().isLoaded("lithostitched") && lands.isSubsetOf(server.getWorldData().enabledFeatures());
    }

    /** Before the city exists: {@code config/awaken-terrain-dev.properties} with {@code city=on} asks for a site anyway. */
    static boolean devOverride(MinecraftServer server) {
        Path f = server.getServerDirectory().resolve("config/awaken-terrain-dev.properties");
        try {
            return Files.exists(f) && Files.readString(f).lines().anyMatch(l -> l.trim().equals("city=on"));
        } catch (java.io.IOException ex) {
            return false;
        }
    }

    /** The overworld's height range for sampling, from its dimension type (the level itself doesn't exist yet). */
    static net.minecraft.world.level.LevelHeightAccessor heights(LevelStem stem) {
        var type = stem.type().value();
        return net.minecraft.world.level.LevelHeightAccessor.create(type.minY(), type.height());
    }
}
