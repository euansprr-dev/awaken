package dev.awaken.mobs;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Mob sounds. Each points at re-pitched vanilla files in src/main/merge/mobs/assets/awaken/sounds.json. */
public final class MobSounds {
    private MobSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** Light wind-up tell (bolts, small swings). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH = sound("mobs.telegraph");
    /** Heavy wind-up tell (slams, beams, boss signatures). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_HEAVY = sound("mobs.telegraph_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_INTRO = sound("mobs.boss_intro");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_PHASE = sound("mobs.boss_phase");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_TOLL = sound("mobs.boss_toll");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEFEATED = sound("mobs.boss_defeated");
    public static final DeferredHolder<SoundEvent, SoundEvent> RISE = sound("mobs.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORD_BITE = sound("mobs.cord_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_HEAR = sound("mobs.listener_hear");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
