package dev.awaken.caves.skin;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The caves' drips (SPEC §42.4): a drop gathers at a stalactite's tip, falls, and splashes where it lands (sprites from
 * tools/art/specs/deep_skin.py; the client's {@code CaveParticles} draws them). Called from a block's animateTick, so
 * only ever on the client.
 */
public final class Drips {
    private Drips() {}

    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DRIP = PARTICLES.register("cave_drip", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPLASH = PARTICLES.register("cave_splash", () -> new SimpleParticleType(false));
    /** The regions' air (SPEC §42.7): the Rootways' spores and pollen, the Ochre Deep's warm dust (the client spawns them). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPORE = PARTICLES.register("cave_spore", () -> new SimpleParticleType(false)),
            POLLEN = PARTICLES.register("cave_pollen", () -> new SimpleParticleType(false)),
            WARM_DUST = PARTICLES.register("cave_warm_dust", () -> new SimpleParticleType(false));
    /** The other six's (SPEC §42.8): mist low over water, the Windcleft's mica glints, glittering dust, ash, rising seep. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = PARTICLES.register("cave_mist", () -> new SimpleParticleType(false)),
            MICA = PARTICLES.register("cave_mica", () -> new SimpleParticleType(false)),
            GLITTER = PARTICLES.register("cave_glitter", () -> new SimpleParticleType(false)),
            ASH = PARTICLES.register("cave_ash", () -> new SimpleParticleType(false)),
            SEEP = PARTICLES.register("cave_seep", () -> new SimpleParticleType(false));

    /** The client's {@code cave_particles} setting (caves.client.CavesClientConfig sets it; a plain flag here). */
    public static volatile boolean enabled = true;

    static void drip(Level level, BlockPos pos, RandomSource random) {
        if (!enabled) return;
        // the point of the tip's model (deep_skin.py FORMATION "tip": 12 of 16 down from the top), a hair off centre
        level.addParticle(DRIP.get(), pos.getX() + 0.5 + (random.nextFloat() - 0.5) * 0.06, pos.getY() + 0.22,
                pos.getZ() + 0.5 + (random.nextFloat() - 0.5) * 0.06, 0, 0, 0);
    }

    public static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
