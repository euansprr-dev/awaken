package dev.awaken.delve.client;

import dev.awaken.delve.api.Bands;
import dev.awaken.delve.cave.CaveContent;
import dev.awaken.delve.cave.CaveKind;
import dev.awaken.delve.cave.CaveNet;
import dev.awaken.delve.sense.SenseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The Deep breathes (SPEC §34.5): every 3 to 6 minutes under the rock a slow exhale runs through the tunnels toward the
 * nearest way out the server has named (a mouth's lip, a sinkhole's rim; with none, straight on): a low moan from
 * behind you that passes on ahead, dust streaming along the floor in that direction for four seconds. In the Deep it
 * is a pressure swell too: the hum rises with it. So it also tells you which way is out.
 */
final class Breath {
    private Breath() {}

    static final int MIN_GAP = 20 * 180, MAX_GAP = 20 * 360, LENGTH = 80;
    private static final RandomSource R = RandomSource.create();
    private static long next = -1, started = -1;
    private static double dx, dz;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        if (!SenseState.active || SenseState.under < 0.7F || SenseState.underwater) {
            if (next < 0 || now > next) next = now + MIN_GAP + R.nextInt(MAX_GAP - MIN_GAP);
            return;
        }
        if (next < 0) next = now + MIN_GAP + R.nextInt(MAX_GAP - MIN_GAP);
        if (now >= next && started < 0) start(mc, level, now);
        if (started >= 0) {
            long t = now - started;
            if (t > LENGTH) {
                started = -1;
                next = now + MIN_GAP + R.nextInt(MAX_GAP - MIN_GAP);
            } else dust(mc, level, t);
        }
    }

    private static void start(Minecraft mc, ClientLevel level, long now) {
        started = now;
        // toward the nearest way out the server named, else the way you face
        BlockPos at = mc.player.blockPosition();
        double best = Double.MAX_VALUE;
        dx = -Mth.sin(mc.player.getYRot() * Mth.DEG_TO_RAD);
        dz = Mth.cos(mc.player.getYRot() * Mth.DEG_TO_RAD);
        for (CaveNet.Site s : CaveFx.sites()) {
            if (s.kind() != CaveKind.MOUTH.ordinal() && s.kind() != CaveKind.SINKHOLE.ordinal()) continue;
            double d = s.at().distSqr(at);
            if (d < best && d > 9) {
                best = d;
                double ex = s.at().getX() - at.getX(), ez = s.at().getZ() - at.getZ(), l = Math.sqrt(ex * ex + ez * ez);
                if (l > 1e-3) {
                    dx = ex / l;
                    dz = ez / l;
                }
            }
        }
        double x = mc.player.getX() - dx * 10, y = mc.player.getY() + 1, z = mc.player.getZ() - dz * 10;
        level.playLocalSound(x, y, z, SenseModule.BREATH.get(), SoundSource.AMBIENT, 1.0F, 0.55F, false);
        level.playLocalSound(mc.player.getX() + dx * 14, y, mc.player.getZ() + dz * 14, SenseModule.BREATH.get(), SoundSource.AMBIENT, 0.6F, 0.48F, false);
        if (Bands.of(Mth.floor(SenseState.y)) == Bands.Band.DEEP) {
            level.playLocalSound(mc.player.getX(), y, mc.player.getZ(), SenseModule.DEEP_HUM.get(), SoundSource.AMBIENT, 0.9F, 0.6F, false);
        }
    }

    /** Dust along the floor round you, streaming toward the way out (more in the middle of the breath). */
    private static void dust(Minecraft mc, ClientLevel level, long t) {
        if (mc.options.particles().get() == net.minecraft.server.level.ParticleStatus.MINIMAL) return;
        float swell = Mth.sin((float) t / LENGTH * Mth.PI);
        int n = (int) (swell * 4 + R.nextFloat());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double x = mc.player.getX() + (R.nextDouble() - 0.5) * 16, z = mc.player.getZ() + (R.nextDouble() - 0.5) * 16;
            int y = Mth.floor(mc.player.getY()) + 2;
            m.set(x, y, z);
            // down to the floor
            for (int k = 0; k < 6 && level.getBlockState(m.below()).isAir(); k++) m.move(0, -1, 0);
            if (!level.getBlockState(m).isAir() || level.getBlockState(m.below()).isAir()) continue;
            double s = 0.12 + swell * 0.1;
            level.addParticle(CaveContent.DRAUGHT.get(), x, m.getY() + 0.1 + R.nextDouble() * 0.4, z, dx * s, 0.004, dz * s);
        }
    }

    static void reset() {
        next = -1;
        started = -1;
    }
}
