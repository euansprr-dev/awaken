package dev.awaken.terrain.mire;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mirror Marsh (docs/modules/lands.md): its blocks and its ambience. Everything it grows is data in the lands pack
 * (tools/content/lands3_content.py): the weepwoods on vanilla's tree feature with Awaken's placers, the moonblooms as
 * a plain block feature, the peat floor as the pack's material rule; the Weeping Matriarch is a hero tree.
 */
public final class MireLands {
    private MireLands() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    /** The biome's ambience bed (its {@code minecraft:audio/ambient_sounds} loop; tools/art/specs/audio_lands.py). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_MARSH_BED =
            SOUNDS.register("amb.mirror_marsh", () -> SoundEvent.createVariableRangeEvent(Awaken.id("amb.mirror_marsh")));

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        MireBlocks.init(modBus);
    }
}
