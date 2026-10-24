package dev.awaken.terrain.frost;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Frost Spires (docs/modules/lands.md): its blocks and its feature type ({@code awaken:frost_spires}; the features
 * themselves are data in the lands pack, written by tools/content/lands3_content.py). The hoarfrost ground is the
 * pack's material rule, the Aurora Spire a landmark.
 */
public final class FrostLands {
    private FrostLands() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<FrostSpiresFeature>> FROST_SPIRES =
            FEATURES.register("frost_spires", () -> FrostSpiresFeature.CODEC);

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    /** The biome's ambience bed (its {@code minecraft:audio/ambient_sounds} loop; tools/art/specs/audio_lands.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FROST_SPIRES_BED =
            SOUNDS.register("amb.frost_spires", () -> SoundEvent.createVariableRangeEvent(Awaken.id("amb.frost_spires")));

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        FrostBlocks.init(modBus);
        FEATURES.register(modBus);
    }
}
