package dev.awaken.body;

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

/** The body's registrations: the saved memory, the live motion, its sounds and its dust. */
public final class BodyContent {
    private BodyContent() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

    /** Saved with the player and kept through death: which unlocks were announced. */
    public static final Supplier<AttachmentType<BodyMemory>> MEMORY = ATTACHMENTS.register("body_memory",
            () -> AttachmentType.builder(() -> BodyMemory.EMPTY).serialize(BodyMemory.MAP_CODEC, m -> !m.isEmpty()).copyOnDeath().build());

    /** Moves in progress (server only, never saved). */
    static final Supplier<AttachmentType<BodyMotion>> MOTION = ATTACHMENTS.register("body_motion",
            () -> AttachmentType.builder(BodyMotion::new).build());

    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL = sound("body.roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRINT = sound("body.sprint");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE = sound("body.slide");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANTLE = sound("body.mantle");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = sound("body.impact");
    // the Runner's Kit
    public static final DeferredHolder<SoundEvent, SoundEvent> WALLSTEP = sound("body.wallstep");
    public static final DeferredHolder<SoundEvent, SoundEvent> WALLJUMP = sound("body.walljump");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAB = sound("body.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIMMY = sound("body.shimmy");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLIMB = sound("body.climb");
    public static final DeferredHolder<SoundEvent, SoundEvent> DROP = sound("body.drop");

    /** Grit kicked up by the body (slide spray, a roll's ring, chips off a ledge), tinted by the ground it came from. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = PARTICLES.register("body_dust", () -> new SimpleParticleType(false));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
    }
}
