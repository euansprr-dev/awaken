package dev.awaken.binoculars;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The binoculars' sounds (SPEC §39.5), our own, synthesised by {@code tools/art/specs/binoculars_sounds.py}; events and
 * subtitles in {@code src/main/merge/binoculars}. Only your own client plays them, quietly and near the ear.
 */
public final class BinocularsSounds {
    private BinocularsSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** Fabric and strap as the hands bring them up. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAISE = reg("binoculars.raise");
    /** The eyecups meet the eyes: soft rubber and a breath of glass. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EYECUP = reg("binoculars.eyecup");
    /** One detent of the focus wheel per step. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = reg("binoculars.step");
    /** Taken from the eyes and let down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LOWER = reg("binoculars.lower");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
