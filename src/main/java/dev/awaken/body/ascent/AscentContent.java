package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** The Ascent's registrations: the saved record, the live motion, its sounds and its light. */
public final class AscentContent {
    private AscentContent() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

    /** Saved with the player and kept through death: the tiers held. */
    public static final Supplier<AttachmentType<AscentMemory>> MEMORY = ATTACHMENTS.register("ascent_memory",
            () -> AttachmentType.builder(() -> AscentMemory.EMPTY).serialize(AscentMemory.MAP_CODEC, m -> !m.isEmpty()).copyOnDeath().build());

    /** The state in progress (server only, never saved). */
    static final Supplier<AttachmentType<AscentMotion>> MOTION = ATTACHMENTS.register("ascent_motion",
            () -> AttachmentType.builder(AscentMotion::new).build());

    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_FIRE = sound("ascent.rope_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_BITE = sound("ascent.rope_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_STRAIN = sound("ascent.rope_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_RELEASE = sound("ascent.rope_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH = sound("ascent.launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("ascent.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINGS_OPEN = sound("ascent.wings_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINGS_FOLD = sound("ascent.wings_fold");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAP = sound("ascent.flap");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOM = sound("ascent.boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = sound("ascent.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> REMEMBER = sound("ascent.remember");

    /** Motes of the Signal's light: sparks where a rope bites, feathers off the wings, the shock ring's grit. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE = PARTICLES.register("ascent_mote", () -> new SimpleParticleType(false));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }
}
