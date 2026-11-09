package dev.awaken.delve.client;

import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The picks' gifts, seen (SPEC §34.4, §49): the wake's burst, the embers' sparks (their light is {@link TorchGlow}'s) and
 * the Call's ore flying home. Sounding has its own, {@link SoundingFx}.
 */
final class PickFx {
    private PickFx() {}

    private static final int ORE = 0xFFD27A;
    private static final RandomSource R = RandomSource.create();

    // ---- the wake, embers' sparks, the Call -------------------------------------------------------------------------

    /** A verse wakes: light gathers round the pick in your hand. */
    static void woke(DelveNet.PickWoke m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < 40; i++) {
            double a = R.nextDouble() * Math.PI * 2, r = 1.2 + R.nextDouble() * 0.8, h = (R.nextDouble() - 0.4) * 1.6;
            double x = eye.x + Math.cos(a) * r, y = eye.y - 0.4 + h, z = eye.z + Math.sin(a) * r;
            DelveParticles.spawn(mc.level, DelveContent.LIGHT_MOTE.get(), 0xFFE2B0, 1.4F, x, y, z, (eye.x - x) * 0.04, (eye.y - 0.4 - y) * 0.04, (eye.z - z) * 0.04);
        }
    }

    private record Call(int player, int ore, long[] cells, int ticks, long start) {}

    private static final List<Call> CALLS = new ArrayList<>();

    static void call(DelveNet.CallAnswer m) {
        var level = Minecraft.getInstance().level;
        if (level != null) CALLS.add(new Call(m.player(), m.ore(), m.cells(), m.ticks(), level.getGameTime()));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        sparks(level);
        for (var it = CALLS.iterator(); it.hasNext(); ) {
            Call c = it.next();
            long t = level.getGameTime() - c.start();
            if (t > c.ticks() + 20) {
                it.remove();
                continue;
            }
            Entity who = level.getEntity(c.player());
            if (who == null) continue;
            int tint = c.ore() >= 0 && c.ore() < Ore.values().length ? Ore.values()[c.ore()].tint : ORE;
            Vec3 to = who.getBoundingBox().getCenter();
            // each block, in its turn, sends a streak of its ore home
            int i = (int) Math.min(c.cells().length - 1, t * c.cells().length / Math.max(1, c.ticks()));
            BlockPos b = BlockPos.of(c.cells()[i]);
            for (int k = 0; k < 4; k++) {
                double x = b.getX() + 0.5 + (R.nextDouble() - 0.5) * 0.6, y = b.getY() + 0.5 + (R.nextDouble() - 0.5) * 0.6, z = b.getZ() + 0.5 + (R.nextDouble() - 0.5) * 0.6;
                DelveParticles.spawn(level, DelveContent.LIGHT_MOTE.get(), tint, 1.2F, x, y, z, (to.x - x) * 0.09, (to.y - y) * 0.09, (to.z - z) * 0.09);
            }
        }
    }

    /** The embers burning near you: a spark now and then off each. */
    private static void sparks(ClientLevel level) {
        long[] at = TorchGlow.emberAt;
        byte[] face = TorchGlow.emberFace;
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < at.length; i++) {
            if (R.nextInt(6) != 0) continue;
            var d = net.minecraft.core.Direction.from3DDataValue(face[i]);
            double x = BlockPos.getX(at[i]) + 0.5 + d.getStepX() * 0.42, y = BlockPos.getY(at[i]) + 0.5 + d.getStepY() * 0.42, z = BlockPos.getZ(at[i]) + 0.5 + d.getStepZ() * 0.42;
            if (mc.player.distanceToSqr(x, y, z) > 48 * 48) continue;
            level.addParticle(R.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.ELECTRIC_SPARK, x, y, z, -d.getStepX() * 0.01, 0.01, -d.getStepZ() * 0.01);
        }
    }

    static void embers(DelveNet.Embers m) {
        TorchGlow.emberAt = m.at();
        TorchGlow.emberFace = m.face();
    }

    static void reset() {
        CALLS.clear();
        TorchGlow.emberAt = new long[0];
        TorchGlow.emberFace = new byte[0];
    }
}
