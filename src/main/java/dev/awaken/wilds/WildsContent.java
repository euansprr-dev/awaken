package dev.awaken.wilds;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.wilds.arrival.ArrivedByMovement;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Wilds' registrations: the ward's particles and sounds, and the loot condition "arrived by movement". */
public final class WildsContent {
    private WildsContent() {}

    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends LootItemCondition>> CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Awaken.MODID);

    /** A grain of a crumbling block: falls, tinted like the block it came from. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WARD_GRAIN = PARTICLES.register("ward_grain", () -> new SimpleParticleType(false));
    /** A thread of the Signal's light lifting off a block the ward took note of, or gave back. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WARD_SPARK = PARTICLES.register("ward_spark", () -> new SimpleParticleType(false));

    /** The ward takes note of a placed block: a glassy tick. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_MARK = sound("wilds.ward_mark");
    /** The block comes apart: a pour of grains under a falling chime. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_CRUMBLE = sound("wilds.ward_crumble");
    /** A site's wall refuses the pick: a dull, deep resonance. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_HOLD = sound("wilds.ward_hold");

    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<ArrivedByMovement>> ARRIVED_BY_MOVEMENT =
            CONDITIONS.register("arrived_by_movement", () -> ArrivedByMovement.MAP_CODEC);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        CONDITIONS.register(modBus);
    }
}
