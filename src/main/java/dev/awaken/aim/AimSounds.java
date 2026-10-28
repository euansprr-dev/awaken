package dev.awaken.aim;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The lock's sound events (SPEC §40.4): synthesised in the System's glass voice by {@code tools/art/specs/aim_sounds.py},
 * events and subtitles in {@code src/main/merge/aim}. Only your own client plays them; registered on both sides like
 * every sound event.
 */
public final class AimSounds {
    private AimSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** The ring closes on the creature. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LOCK = reg("aim.lock");
    /** The lock moves to the next creature. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SWITCH = reg("aim.switch");
    /** The lock is let go. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RELEASE = reg("aim.release");
    /** The locked creature falls: the ring splinters. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK = reg("aim.break");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
