package dev.awaken.body.client;

import dev.awaken.body.BodyContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The body's dust and its own sounds on this client. Every puff is the ground's colour (a slide over sand throws sand,
 * over grass a green-brown grit); nothing here is a vanilla particle.
 */
final class BodyFx {
    private BodyFx() {}

    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    /** The colour of the ground under a point, softened toward a dusty grey so it reads as grit, not paint. */
    static int ground(ClientLevel level, double x, double y, double z) {
        int col = 0;
        for (int dy = 0; dy <= 2 && col == 0; dy++) {
            POS.set(Mth.floor(x), Mth.floor(y - 0.05) - dy, Mth.floor(z));
            BlockState s = level.getBlockState(POS);
            if (!s.isAir()) col = s.getMapColor(level, POS).col;
        }
        if (col == 0) col = 0x8A8378;
        int r = ((col >> 16) & 255) * 7 / 10 + 0x8E * 3 / 10;
        int g = ((col >> 8) & 255) * 7 / 10 + 0x88 * 3 / 10;
        int b = (col & 255) * 7 / 10 + 0x7C * 3 / 10;
        return r << 16 | g << 8 | b;
    }

    static void puff(ClientLevel level, int tint, float size, double x, double y, double z, double vx, double vy, double vz) {
        BodyDustParticle.nextTint = tint;
        BodyDustParticle.nextSize = size;
        level.addParticle(BodyContent.DUST.get(), x, y, z, vx, vy, vz);
    }

    /** Grit sprayed forward and aside from the feet, every tick of a slide. */
    static void slideSpray(Entity e, double dirX, double dirZ, float strength) {
        if (!(e.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        int tint = ground(level, e.getX(), e.getY(), e.getZ());
        double sideX = -dirZ, sideZ = dirX;
        for (int i = 0; i < 3; i++) {
            double side = (r.nextDouble() - 0.5) * 2;
            double fx = e.getX() + dirX * 0.45 + sideX * side * 0.3, fz = e.getZ() + dirZ * 0.45 + sideZ * side * 0.3;
            puff(level, tint, 0.8F + strength * 0.4F, fx, e.getY() + 0.05, fz,
                    dirX * 0.09 * strength + sideX * side * 0.07, 0.05 + r.nextDouble() * 0.07 * strength, dirZ * 0.09 * strength + sideZ * side * 0.07);
        }
    }

    /** A low ring thrown out where a roll lands. */
    static void rollRing(Entity e) {
        if (!(e.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        int tint = ground(level, e.getX(), e.getY(), e.getZ());
        for (int i = 0; i < 14; i++) {
            double a = i / 14.0 * Mth.TWO_PI + r.nextDouble() * 0.3;
            double cx = Math.cos(a), cz = Math.sin(a);
            puff(level, tint, 1.2F, e.getX() + cx * 0.35, e.getY() + 0.05, e.getZ() + cz * 0.35, cx * 0.13, 0.03 + r.nextDouble() * 0.04, cz * 0.13);
        }
    }

    /** A kick-off puff behind the feet (tactical sprint, the slide-cancel's jump). */
    static void kick(Entity e, double dirX, double dirZ) {
        if (!(e.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        int tint = ground(level, e.getX(), e.getY(), e.getZ());
        for (int i = 0; i < 6; i++) {
            double side = (r.nextDouble() - 0.5) * 0.5;
            puff(level, tint, 0.9F, e.getX() - dirX * 0.3 - dirZ * side, e.getY() + 0.05, e.getZ() - dirZ * 0.3 + dirX * side,
                    -dirX * 0.08 + (r.nextDouble() - 0.5) * 0.05, 0.04 + r.nextDouble() * 0.05, -dirZ * 0.08 + (r.nextDouble() - 0.5) * 0.05);
        }
    }

    /** Chips and dust off the lip a hand grabbed. */
    static void lip(ClientLevel level, double x, double y, double z, double dirX, double dirZ) {
        RandomSource r = level.getRandom();
        int tint = ground(level, x + dirX * 0.2, y + 0.1, z + dirZ * 0.2);
        for (int i = 0; i < 7; i++) {
            double side = (r.nextDouble() - 0.5) * 0.9;
            puff(level, tint, 0.7F, x - dirZ * side, y + 0.02, z + dirX * side,
                    -dirX * 0.04 + (r.nextDouble() - 0.5) * 0.03, 0.02 + r.nextDouble() * 0.03, -dirZ * 0.04 + (r.nextDouble() - 0.5) * 0.03);
        }
    }

    /** A burst at a body that took a moving hit or was tripped: dust off its feet, thrown along the blow. */
    static void hitBurst(Entity e, double dirX, double dirZ, boolean trip) {
        if (!(e.level() instanceof ClientLevel level)) return;
        RandomSource r = level.getRandom();
        int tint = ground(level, e.getX(), e.getY(), e.getZ());
        int n = trip ? 10 : 7;
        double y = trip ? e.getY() + 0.05 : e.getY() + 0.1;
        for (int i = 0; i < n; i++) {
            double a = (r.nextDouble() - 0.5) * 2.2;
            double cx = dirX * Math.cos(a) - dirZ * Math.sin(a), cz = dirZ * Math.cos(a) + dirX * Math.sin(a);
            puff(level, tint, trip ? 1.1F : 0.9F, e.getX(), y, e.getZ(), cx * 0.14, 0.05 + r.nextDouble() * 0.08, cz * 0.14);
        }
    }

    /** One of the body's sounds at an entity, heard on this client only (the server plays it for everyone else). */
    static void sound(Entity e, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(e.getX(), e.getY(), e.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
