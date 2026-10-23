package dev.awaken.mobs.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side tells: rings where an area will land, lines where a charge or beam will go. Coloured with the
 * creature's theme glow so every theme reads differently, kept to a handful of particles per send.
 */
final class Telegraph {
    private Telegraph() {}

    static void ring(ServerLevel level, Vec3 centre, float radius, int points, int colour, boolean heavy) {
        DustParticleOptions dust = new DustParticleOptions(colour, heavy ? 1.6F : 1.1F);
        int n = Math.max(8, Math.min(48, points));
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            level.sendParticles(dust, centre.x + Mth.cos(a) * radius, centre.y + 0.15, centre.z + Mth.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        if (heavy) level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y + 0.2, centre.z, 4, radius * 0.4, 0.05, radius * 0.4, 0.05);
    }

    static void disc(ServerLevel level, Vec3 centre, float radius, int colour) {
        DustParticleOptions dust = new DustParticleOptions(colour, 1.0F);
        level.sendParticles(dust, centre.x, centre.y + 0.15, centre.z, Math.max(4, (int) (radius * radius * 2)), radius * 0.5, 0.02, radius * 0.5, 0);
    }

    static void line(ServerLevel level, Vec3 from, Vec3 to, int colour, boolean heavy) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.01) return;
        int steps = (int) Math.min(40, len * 1.5);
        DustParticleOptions dust = new DustParticleOptions(colour, heavy ? 1.4F : 0.9F);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    static void cone(ServerLevel level, Vec3 origin, Vec3 dir, float length, int colour) {
        DustParticleOptions dust = new DustParticleOptions(colour, 1.0F);
        Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
        for (int i = 1; i <= 6; i++) {
            float dist = length * i / 6.0F;
            for (int s = -2; s <= 2; s++) {
                double ang = s * 0.32;
                double cx = flat.x * Math.cos(ang) - flat.z * Math.sin(ang);
                double cz = flat.x * Math.sin(ang) + flat.z * Math.cos(ang);
                level.sendParticles(dust, origin.x + cx * dist, origin.y + 0.2, origin.z + cz * dist, 1, 0, 0, 0, 0);
            }
        }
    }

    static void gather(ServerLevel level, Vec3 at, int colour) {
        level.sendParticles(new DustParticleOptions(colour, 1.2F), at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.02);
    }
}
