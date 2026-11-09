package dev.awaken.gates.realms;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Realms, the gates that are whole places (SPEC §50.7). Everything here sits behind the server's gateRealms switch. */
public final class RealmsModule {
    private RealmsModule() {}

    public static final ResourceKey<Level> REALMS = ResourceKey.create(Registries.DIMENSION, Awaken.id("realms"));

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Awaken.MODID);

    public static void init(IEventBus modBus) {
        GENERATORS.register("realm", () -> RealmChunkGenerator.CODEC);
        SOURCES.register("realm", () -> RealmBiomeSource.CODEC);
        GENERATORS.register(modBus);
        SOURCES.register(modBus);
        NeoForge.EVENT_BUS.addListener(Blueprints::addReload);
        RealmTests.register();
    }
}
