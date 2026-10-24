package dev.awaken.terrain.glass;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Glass Steppe (docs/modules/lands.md): its blocks and its feature types ({@code awaken:glass_shards},
 * {@code awaken:mirror_pool}; the features themselves are data in the lands pack, written by
 * tools/content/lands2_content.py). The ground's fused glass is the pack's material rule, the Gate Scar a landmark.
 */
public final class GlassLands {
    private GlassLands() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<GlassShardsFeature>> GLASS_SHARDS =
            FEATURES.register("glass_shards", () -> GlassShardsFeature.CODEC);
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<MirrorPoolFeature>> MIRROR_POOL =
            FEATURES.register("mirror_pool", () -> MirrorPoolFeature.CODEC);

    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    /** The biome's ambience bed (its {@code minecraft:audio/ambient_sounds} loop; tools/art/specs/audio_lands.py). */
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> GLASS_STEPPE_BED =
            SOUNDS.register("amb.glass_steppe", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Awaken.id("amb.glass_steppe")));

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        GlassBlocks.init(modBus);
        FEATURES.register(modBus);
    }
}
