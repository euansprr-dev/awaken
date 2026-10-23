package dev.awaken.powers;

import dev.awaken.core.AwakenNet;
import dev.awaken.powers.net.PowerPayloads;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Server-side visual effects: particle shapes and the feel/beam payloads. Particle counts are kept small and sent
 * only to players within normal range (32 blocks); clients thin them further with their particle setting.
 */
public final class Fx {
    private Fx() {}

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(rgb, scale);
    }

    /** A flat ring of particles. */
    public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0.02, 0, speed);
        }
    }

    /** A ring whose particles fly outward (shockwaves). */
    public static void burstRing(ServerLevel level, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            // count 0 means "use the offsets as a direction"
            level.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
        }
    }

    /** A sphere shell (domes, novas). */
    public static void sphere(ServerLevel level, Vec3 c, double radius, ParticleOptions p, int points) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double t = golden * i;
            level.sendParticles(p, c.x + Math.cos(t) * r * radius, c.y + y * radius, c.z + Math.sin(t) * r * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Particles along a line, one every {@code step} blocks. */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 u = d.scale(1 / len);
        int n = Math.min(96, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(u.scale(i * step));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Streaks that fly from a to b (trail particles): cheap beams for bolts and lightning. */
    public static void streak(ServerLevel level, Vec3 a, Vec3 b, int argb, int count, int duration) {
        TrailParticleOption trail = new TrailParticleOption(b, argb, duration);
        level.sendParticles(trail, a.x, a.y, a.z, count, 0.08, 0.08, 0.08, 0);
    }

    /** A jagged lightning line from a to b. */
    public static void lightning(ServerLevel level, Vec3 a, Vec3 b, int segments) {
        Vec3 prev = a;
        var rnd = level.getRandom();
        for (int i = 1; i <= segments; i++) {
            Vec3 t = a.lerp(b, i / (double) segments);
            if (i < segments) t = t.add((rnd.nextDouble() - 0.5) * 0.8, (rnd.nextDouble() - 0.5) * 0.8, (rnd.nextDouble() - 0.5) * 0.8);
            line(level, prev, t, ParticleTypes.ELECTRIC_SPARK, 0.35);
            prev = t;
        }
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFDDEEFF), b.x, b.y, b.z, 1, 0, 0, 0, 0);
    }

    public static void impact(ServerLevel level, Vec3 at, int argb) {
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.4);
        level.sendParticles(dust(argb & 0xFFFFFF, 1.4f), at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.05);
    }

    /** Hit-stop on the struck entity and a camera kick for the player who struck it. */
    public static void feel(ServerPlayer player, @Nullable LivingEntity hit, int hitstopMs, float shake) {
        AwakenNet.send(player, new PowerPayloads.Feel(hit == null ? -1 : hit.getId(), hitstopMs, shake));
    }

    /** Camera kick only, for every player near a big impact. */
    public static void quake(ServerLevel level, Vec3 at, double radius, float shake) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(at);
            if (d < radius) AwakenNet.send(p, new PowerPayloads.Feel(-1, 0, (float) (shake * (1 - d / radius))));
        }
    }

    /** Beam visuals to the caster and everyone tracking them. */
    public static void beam(ServerPlayer caster, Entity from, Vec3 end, int argb, int ticks, float width, float range, boolean follow) {
        PowerPayloads.Beam msg = new PowerPayloads.Beam(from.getId(), end.x, end.y, end.z, argb, ticks, width, range, follow);
        ServerLevel level = caster.level();
        List<ServerPlayer> players = level.players();
        for (ServerPlayer p : players) {
            if (p.distanceToSqr(from) < 96 * 96) AwakenNet.send(p, msg);
        }
    }
}
