package dev.awaken.story;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Ritual of the Bell's sounds (synthesised by tools/art/specs/ritual_sounds.py into sounds/ritual/, events and
 * subtitles in src/main/merge/story/). The client plays them from the ritual's cues; nothing here plays on its own.
 */
public final class RitualSounds {
    private RitualSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("story.ritual." + name, SoundEvent::createVariableRangeEvent);
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> BELL = sound("bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOORS = sound("doors");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAMES_OUT = sound("flames_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANDLE = sound("candle");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORDS = sound("words");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYES = sound("eyes");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAZE_CHARGE = sound("gaze_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAZE_FIRE = sound("gaze_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATUE_RISE = sound("statue_rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWING = sound("swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = sound("impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIT = sound("pit");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLE = sound("rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAIN = sound("drain");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = sound("close");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> TEAR = sound("tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID = sound("void");

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
