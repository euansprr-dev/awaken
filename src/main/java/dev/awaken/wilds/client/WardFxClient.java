package dev.awaken.wilds.client;

import dev.awaken.wilds.WildsContent;
import dev.awaken.wilds.ward.WardFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a ward event's particles on this client. Marked: a few threads of light lift off the block's top (the ward
 * has seen it). Crumbled: the block falls apart in grains of its own colour while light rises out of it. Held: light
 * sparks off the wall that refused the pick. Shed (a climb refused a block): the block's ghost, its edges traced in
 * grains of its own colour, falls away as a breath of light lifts off where it would have stood. Unsealed (a climb
 * cache's inner seal opened): a ring of light rises off the lid and closes over it. The Signal's light is the System's
 * edge blue (#7FC3FF).
 */
final class WardFxClient {
    private WardFxClient() {}

    static final int SIGNAL = 0x7FC3FF;

    static void play(WardFx.Event e) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockPos pos = e.pos();
        RandomSource r = level.getRandom();
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        switch (e.what()) {
            case MARK -> {
                for (int i = 0; i < 5; i++) {
                    double ex = x + edge(r), ez = z + edge(r);
                    spark(level, ex, y + 1.02, ez, (r.nextDouble() - 0.5) * 0.01, 0.025 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.01);
                }
            }
            case CRUMBLE -> {
                int tint = tint(level, Block.stateById(e.state()), pos);
                WardParticles.nextTint = tint;
                for (int i = 0; i < 28; i++) {
                    double gx = x + 0.1 + r.nextDouble() * 0.8, gy = y + 0.1 + r.nextDouble() * 0.8, gz = z + 0.1 + r.nextDouble() * 0.8;
                    level.addParticle(WildsContent.WARD_GRAIN.get(), gx, gy, gz, (gx - x - 0.5) * 0.12, r.nextDouble() * 0.08, (gz - z - 0.5) * 0.12);
                }
                for (int i = 0; i < 9; i++) {
                    spark(level, x + 0.25 + r.nextDouble() * 0.5, y + 0.3 + r.nextDouble() * 0.5, z + 0.25 + r.nextDouble() * 0.5,
                            (r.nextDouble() - 0.5) * 0.03, 0.04 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.03);
                }
            }
            case SHED -> {
                WardParticles.nextTint = tint(level, Block.stateById(e.state()), pos);
                // the ghost: its twelve edges in grains, each falling a little outward, so the shape reads as it goes
                for (int edge = 0; edge < 12; edge++) {
                    for (int k = 0; k < 4; k++) {
                        double t = (k + r.nextDouble()) / 4;
                        int axis = edge / 4, a = edge & 1, b = (edge >> 1) & 1;
                        // along x: (t, a, b); along y: (a, t, b); along z: (a, b, t)
                        double gx = axis == 0 ? t : a, gy = axis == 0 ? a : axis == 1 ? t : b, gz = axis == 2 ? t : b;
                        level.addParticle(WildsContent.WARD_GRAIN.get(), x + gx, y + gy, z + gz,
                                (gx - 0.5) * 0.05, 0.01 + r.nextDouble() * 0.03, (gz - 0.5) * 0.05);
                    }
                }
                for (int i = 0; i < 7; i++) {
                    spark(level, x + 0.2 + r.nextDouble() * 0.6, y + 0.15 + r.nextDouble() * 0.7, z + 0.2 + r.nextDouble() * 0.6,
                            (r.nextDouble() - 0.5) * 0.02, 0.05 + r.nextDouble() * 0.04, (r.nextDouble() - 0.5) * 0.02);
                }
            }
            case UNSEAL -> {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24, cx = x + 0.5 + Math.cos(a) * 0.62, cz = z + 0.5 + Math.sin(a) * 0.62;
                    spark(level, cx, y + 0.95, cz, -Math.cos(a) * 0.012, 0.06 + r.nextDouble() * 0.03, -Math.sin(a) * 0.012);
                }
                for (int i = 0; i < 6; i++) spark(level, x + 0.5, y + 1.0, z + 0.5, (r.nextDouble() - 0.5) * 0.01, 0.1 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.01);
            }
            case HOLD -> {
                for (int i = 0; i < 10; i++) {
                    // a point on one of the six faces, thrown a little outward from it
                    int face = r.nextInt(6);
                    double u = r.nextDouble(), v = r.nextDouble();
                    double fx = face == 0 ? 0 : face == 1 ? 1 : u, fy = face == 2 ? 0 : face == 3 ? 1 : face < 2 ? u : v, fz = face == 4 ? 0 : face == 5 ? 1 : v;
                    double ox = face == 0 ? -1 : face == 1 ? 1 : 0, oy = face == 2 ? -1 : face == 3 ? 1 : 0, oz = face == 4 ? -1 : face == 5 ? 1 : 0;
                    spark(level, x + fx + ox * 0.02, y + fy + oy * 0.02, z + fz + oz * 0.02, ox * 0.05, oy * 0.05 + 0.01, oz * 0.05);
                }
            }
        }
    }

    private static void spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        WardParticles.nextTint = SIGNAL;
        level.addParticle(WildsContent.WARD_SPARK.get(), x, y, z, vx, vy, vz);
    }

    /** Somewhere along one edge of a block's top face (0..1). */
    private static double edge(RandomSource r) {
        return r.nextBoolean() ? r.nextDouble() : (r.nextBoolean() ? 0.04 : 0.96);
    }

    /** The block's map colour, a touch dusted so its grains read as grit rather than paint. */
    private static int tint(ClientLevel level, BlockState state, BlockPos pos) {
        int col = state.isAir() ? 0 : state.getMapColor(level, pos).col;
        if (col == 0) col = 0x9A9387;
        int red = ((col >> 16) & 255) * 8 / 10 + 0x9A * 2 / 10;
        int green = ((col >> 8) & 255) * 8 / 10 + 0x93 * 2 / 10;
        int blue = (col & 255) * 8 / 10 + 0x87 * 2 / 10;
        return red << 16 | green << 8 | blue;
    }
}
