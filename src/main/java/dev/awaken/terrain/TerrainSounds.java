package dev.awaken.terrain;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The living land's sound events (sounds.json and subtitles in {@code src/main/merge/terrain}, files rendered from
 * {@code tools/art/specs/audio_lands.py} by {@code tools/art/audio_build.py}). Played by the client
 * ({@code terrain.client.LeafRustle}); registered here because every sound event is.
 */
public final class TerrainSounds {
    private TerrainSounds() {}

    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** Leaves rustling in the wind: the bed under trees. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAF_RUSTLE = reg("amb.lands_rustle");
    /** Deep wind high up and in the open. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_HIGH = reg("amb.lands_wind_high");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
