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

    /** Rising air, drawn into the land like any other block; never held. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<UpdraftBlock> UPDRAFT = dev.awaken.gates.GatesRegistry.BLOCKS.registerBlock("realm_updraft", UpdraftBlock::new,
            p -> p.noCollision().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F).replaceable()
                    .isValidSpawn((s, l, pos, t) -> false).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED));

    /** A honey river's surface: glows, looks like liquid, wades to the knee. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<HoneyFlowBlock> HONEY_FLOW = dev.awaken.gates.GatesRegistry.BLOCKS.registerBlock("realm_honey", HoneyFlowBlock::new,
            p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE).noLootTable().strength(-1.0F, 3600000.0F).lightLevel(s -> 9).noOcclusion()
                    .sound(net.minecraft.world.level.block.SoundType.HONEY_BLOCK).isValidSpawn((s, l, pos, t) -> false).pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, Awaken.MODID);

    public static void init(IEventBus modBus) {
        GENERATORS.register("realm", () -> RealmChunkGenerator.CODEC);
        SOURCES.register("realm", () -> RealmBiomeSource.CODEC);
        GENERATORS.register(modBus);
        SOURCES.register(modBus);
        NeoForge.EVENT_BUS.addListener(Blueprints::addReload);
        NeoForge.EVENT_BUS.addListener(RealmRun::knockBack);
        dev.awaken.core.api.Sources.neverHeld("a Realm's rising air, drawn into the land", "realm_updraft", "realm_honey");
        RealmTests.register();
    }
}
