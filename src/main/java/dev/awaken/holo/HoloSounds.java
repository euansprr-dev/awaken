package dev.awaken.holo;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The holo panels' sound events (docs/modules/holo.md "Sounds"): synthesised in the System's glass voice by
 * {@code tools/art/specs/audio_holo.py}, events and subtitles in {@code src/main/merge/holo}. Only the client plays
 * them (Interface slider); they are registered on both sides like every sound event.
 */
public final class HoloSounds {
    private HoloSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** The K menu unfolds out of the wrist. */
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = reg("holo.open");
    /** The inventory unfolds (the same voice, shorter: it opens in 180 ms and often). */
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN_QUICK = reg("holo.open_quick");
    /** A panel folds back into the band. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = reg("holo.close");
    /** The cursor comes onto something on a panel. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOVER = reg("holo.hover");
    /** A System card is projected in (the projected look's voice for {@code signal.open}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD = reg("holo.card");
    /** The band's pages (SPEC §30.4): a page turns, a stat point placed (pitched up per point), confirmed, taken back. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PAGE = reg("holo.page");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEND = reg("holo.spend");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONFIRM = reg("holo.confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNDO = reg("holo.undo");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
