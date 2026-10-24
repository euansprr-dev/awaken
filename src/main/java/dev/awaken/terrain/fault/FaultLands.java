package dev.awaken.terrain.fault;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Shattered Highlands (docs/modules/lands.md): its rock and its feature types ({@code awaken:fault_lines},
 * {@code awaken:floating_shard}; the features themselves are data in the lands pack, written by
 * tools/content/lands2_content.py). The faults follow {@link FaultNoise}; the Shard Drift is a landmark.
 */
public final class FaultLands {
    private FaultLands() {}

    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<FaultLinesFeature>> FAULT_LINES =
            FEATURES.register("fault_lines", () -> FaultLinesFeature.CODEC);
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<FloatingShardFeature>> FLOATING_SHARD =
            FEATURES.register("floating_shard", () -> FloatingShardFeature.CODEC);

    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    /** The biome's ambience bed (its {@code minecraft:audio/ambient_sounds} loop; tools/art/specs/audio_lands.py). */
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> HIGHLANDS_BED =
            SOUNDS.register("amb.shattered_highlands", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Awaken.id("amb.shattered_highlands")));

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        FaultBlocks.init(modBus);
        FEATURES.register(modBus);
    }
}
