package dev.awaken.mobs;

import dev.awaken.Awaken;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The creatures' own particles (sprites from tools/art/specs/fauna3.py; client providers in mobs.client.SeepParticles).
 * Sent from the server with a count of 0, their speed carries their numbers (the client reads them, then holds still).
 */
public final class MobParticles {
    private MobParticles() {}

    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

    /** A mote of the Deep's violet light, drifting (the seep heart's beat, its shards' trail, its wake). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEEP_MOTE = register("seep_mote");
    /** A mote drawn in: flies straight along its speed for 16 ticks and is gone as it arrives (a death's drain, a pulse's gathering). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEEP_DRAIN = register("seep_drain");
    /** A sliver of Deepglass, tumbling and falling. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEEP_SHARD = register("seep_shard");
    /** Cracks of violet light opening flat on the floor where roots will burst. Speed x: radius, y: ticks it lasts. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEEP_CRACK = register("seep_crack");
    /** A ring of light flat on the floor. Speed x: radius, y: ticks; z: 0 it rushes out from the middle, 1 it holds (a tell). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SEEP_WAVE = register("seep_wave");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
