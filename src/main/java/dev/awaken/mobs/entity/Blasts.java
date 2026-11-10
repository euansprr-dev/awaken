package dev.awaken.mobs.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A creature's blast (SPEC §47.14): vanilla's explosion for its push, sound and smoke, but its damage a share of the
 * rank's fair health ({@link dev.awaken.core.api.FairShare}) instead of vanilla's fixed numbers, which were the same at E
 * as at S. It peaks at {@code peak} at the centre and falls along vanilla's curve to nothing at the reach (power x 2). It
 * breaks no blocks, and it is the body's, so a dodge answers it (Strikes: a blast with a cause is PERILOUS).
 */
final class Blasts {
    private Blasts() {}

    static void fire(ServerLevel level, AwakenMob body, Vec3 at, float power, float peak) {
        if (body.def().matter != null) {
            // a gate monster bursts as what it is made of (SPEC §60.8): vanilla's push, its damage curve and its sound,
            // none of its white smoke or black flecks (on film the Puffcap went off like a creeper)
            dev.awaken.mobs.MobParticles.Shot shape = dev.awaken.mobs.MobParticles.Shot.values()[body.def().matter.ordinal()];
            net.minecraft.core.particles.ParticleOptions bit = dev.awaken.mobs.MobParticles.shot(shape, body.def().matterRgb);
            level.explode(body, body.damageSources().explosion(body, body), new Fair(peak), at.x, at.y, at.z, power, false, Level.ExplosionInteraction.NONE,
                    bit, bit, net.minecraft.util.random.WeightedList.of(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE);
            Abilities.shed(level, body, at.add(0, 0.6, 0), 30 + (int) (power * 10), power * 0.45, 0.5, power * 0.45, 0.3);
            return;
        }
        level.explode(body, body.damageSources().explosion(body, body), new Fair(peak), at.x, at.y, at.z, power, false, Level.ExplosionInteraction.NONE);
    }

    /** Vanilla's falloff, scaled to {@code peak}: (p^2 + p) / 2 of it, p the closeness times how much of you it sees. */
    static float at(float peak, double dist, float reach, float exposure) {
        double p = Math.max(0.0, 1.0 - dist / reach) * exposure;
        return (float) (peak * (p * p + p) / 2.0);
    }

    private static final class Fair extends ExplosionDamageCalculator {
        private final float peak;

        Fair(float peak) {
            this.peak = peak;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
            return at(peak, Math.sqrt(entity.distanceToSqr(explosion.center())), explosion.radius() * 2.0F, exposure);
        }
    }
}
