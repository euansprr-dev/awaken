package dev.awaken.anims;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The sounds of working a station by hand (SPEC §65.4). Every one is one of Minecraft's own recordings, named in
 * {@code src/main/merge/anims/assets/awaken/sounds.json} by its vanilla sound event with a volume and a pitch: the game
 * plays its own files and none of Mojang's audio is in the repo. First picks; the owner's ear decides.
 */
public final class AnimsSounds {
    private AnimsSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** A thing set down on a station: on wood, as metal, as stone or a gem, as something soft. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LAY = sound("station.lay"), LAY_METAL = sound("station.lay_metal"),
            LAY_STONE = sound("station.lay_stone"), LAY_SOFT = sound("station.lay_soft");
    /** A thing lifted off a station; a result coming together; a result taken. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT = sound("station.lift"), RESULT = sound("station.result"), TAKE = sound("station.take");
    /** Fuel onto the coals, and the flare that answers; kneeling before a furnace. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FUEL = sound("station.fuel"), FLARE = sound("station.flare"), KNEEL = sound("station.kneel");
    /** A palm on a stone, and both hands set to wood: touches vanilla gives no sound of their own. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PALM = sound("touch.palm"), SET = sound("touch.set");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
