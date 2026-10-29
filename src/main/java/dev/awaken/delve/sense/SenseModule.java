package dev.awaken.delve.sense;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The senses underground (SPEC §28.5; PLAN 8.4): the cave soundscape's sound events and the deep's hum (the client
 * half is {@code delve.client.SenseClient}: it plays them, placed on real blocks), and {@code /awaken delve senses goto}
 * for tours. Sounds: tools/art/specs/audio_caves.py, sounds.json and subtitles in src/main/merge/delve_sense/.
 */
public final class SenseModule {
    private SenseModule() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** A drop off a dripstone tip or a wet roof, landing on rock; into a pool; off a sulfur spike into acid. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP = sound("delve.cave.drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP_POOL = sound("delve.cave.drip_pool");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIP_ACID = sound("delve.cave.drip_acid");
    /** Stone settling under the weight above, on real rock round you (rare, deep). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SETTLE = sound("delve.cave.settle");
    /** Loops: an underground stream heard from a distance, toward real water; air breathing in from the way out. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FAR_WATER = sound("delve.cave.far_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH = sound("delve.cave.breath");
    /** Sulfur caves' own: gas hissing from potent sulfur, acid pools bubbling. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SULFUR_VENT = sound("delve.cave.sulfur_vent");
    public static final DeferredHolder<SoundEvent, SoundEvent> SULFUR_BUBBLE = sound("delve.cave.sulfur_bubble");
    /** Loops round the listener: the deep's hum below y 0, and the seep's second voice over it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DEEP_HUM = sound("delve.deep.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEEP_SEEP = sound("delve.deep.seep");
    /** Recorded (SPEC §34.5): the band's room tone round the listener (pitched by band), and the cave answering a heart. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BAND_ROOM = sound("delve.band.room");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAVE_ANSWERS = sound("delve.cave.answers");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        SenseCommands.register();
    }
}
