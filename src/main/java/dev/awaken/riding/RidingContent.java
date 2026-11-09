package dev.awaken.riding;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The thread's four sounds, each built from Minecraft's own recordings in sounds.json. */
final class RidingContent {
    private RidingContent() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    static final DeferredHolder<SoundEvent, SoundEvent> BOND = sound("riding.bond");
    static final DeferredHolder<SoundEvent, SoundEvent> LET_GO = sound("riding.let_go");
    static final DeferredHolder<SoundEvent, SoundEvent> FOLD = sound("riding.fold");
    static final DeferredHolder<SoundEvent, SoundEvent> SPRING = sound("riding.spring");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
