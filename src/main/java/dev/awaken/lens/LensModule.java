package dev.awaken.lens;

import dev.awaken.Awaken;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Lens's common side: only the particle types of Lens 2's ambient particles (SPEC §22), which the client spawns around
 * the camera on Cinematic and Ultra (dev.awaken.lens.client.LensParticles). Registered on both sides so the registries
 * agree; nothing on the server ever sends them.
 */
public final class LensModule {
    private LensModule() {}

    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    /** Dust in sunlight, a gate's drifting specks. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE = PARTICLES.register("lens_mote", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = PARTICLES.register("lens_ember", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FROST = PARTICLES.register("lens_frost", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPORE = PARTICLES.register("lens_spore", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP = PARTICLES.register("lens_wisp", () -> new SimpleParticleType(false));
    /** The player's breath in the cold. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BREATH = PARTICLES.register("lens_breath", () -> new SimpleParticleType(false));

    public static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
