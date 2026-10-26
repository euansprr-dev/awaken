package dev.awaken.wilds;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.wilds.arrival.ArrivedByMovement;
import dev.awaken.wilds.arrival.Visit;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Wilds' registrations: the ward's particles and sounds, the loot condition "arrived by movement", a visit kept
 * over a relog and a climb cache's inner seal.
 */
public final class WildsContent {
    private WildsContent() {}

    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends LootItemCondition>> CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

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

    /** The ward visit a player logged out in (SPEC §17.29): saved, taken off at the next login. */
    public static final java.util.function.Supplier<AttachmentType<Visit>> VISIT = ATTACHMENTS.register("ward_visit",
            () -> AttachmentType.builder(() -> new Visit(1, "", false, false, 0)).serialize(Visit.MAP_CODEC).build());
    /** A climb cache's inner seal: who it has opened to (SPEC §17.29), on the chest's block entity. */
    public static final java.util.function.Supplier<AttachmentType<dev.awaken.wilds.ward.InnerSeal>> INNER_SEAL = ATTACHMENTS.register("inner_seal",
            () -> AttachmentType.builder(() -> dev.awaken.wilds.ward.InnerSeal.NONE).serialize(dev.awaken.wilds.ward.InnerSeal.MAP_CODEC).build());

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        CONDITIONS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
