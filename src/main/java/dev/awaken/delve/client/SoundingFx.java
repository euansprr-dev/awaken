package dev.awaken.delve.client;

import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import dev.awaken.delve.TuningForkItem;
import dev.awaken.delve.forged.DelveForged;
import dev.awaken.delve.forged.Sounding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sounding, heard (SPEC §49.5): the answer comes back through the rock a beat after the tap, nearer caves first, from the
 * side they're on, and dust shakes off that part of the wall. Nothing is drawn through the rock: you learn it by turning
 * your head. A lode answers last, at its ore's own note, from where it lies.
 */
final class SoundingFx {
    private SoundingFx() {}

    /** Ticks a block the answer takes to come back, and the most directions you can tell apart at once. */
    private static final float SPEED = 1.5F;
    private static final int MOST = 5;
    private static final RandomSource R = RandomSource.create();
    private static final int DUST_TINT = 0xB9AE98;

    private record Answer(int due, double x, double y, double z, float volume, float pitch, int ore) {}

    /** The dust thrown off one part of the wall as its answer comes. */
    private record Shake(int due, Vec3 at, Vec3 side, Vec3 up, int grains) {}

    private static final List<Answer> ANSWERS = new ArrayList<>();
    private static final List<Shake> SHAKES = new ArrayList<>();
    private static int now;

    static void heard(DelveNet.PickSounding m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ANSWERS.clear();
        SHAKES.clear();
        BlockPos at = BlockPos.of(m.at());
        Direction face = Direction.from3DDataValue(Math.floorMod(m.face(), 6));
        Vec3 out = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        Vec3 centre = Vec3.atCenterOf(at).add(out.scale(0.52));
        // the tap's own puff, out of the block you struck
        for (int i = 0; i < 6; i++) {
            Vec3 d = out.scale(0.03).add(jitter(0.02));
            Vec3 p = centre.add(jitter(0.25).multiply(1 - Math.abs(out.x), 1 - Math.abs(out.y), 1 - Math.abs(out.z)));
            DelveParticles.spawn(mc.level, DelveContent.GRIT.get(), DUST_TINT, 0.9F, p.x, p.y, p.z, d.x, d.y, d.z);
        }
        // the nearest first; two rays that lie close are one cave, so only the nearer of them speaks
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < m.hollow().length && k < Sounding.DIRECTIONS; k++) if (m.hollow()[k] > 0) order.add(k);
        order.sort(Comparator.comparingInt(k -> m.hollow()[k]));
        List<Vec3> spoken = new ArrayList<>();
        int last = 0;
        Vec3 eye = mc.player.getEyePosition();
        for (int k : order) {
            int[] d = Sounding.direction(k);
            Vec3 dir = new Vec3(d[0], d[1], d[2]).normalize();
            boolean near = false;
            for (Vec3 v : spoken) near |= v.dot(dir) > 0.6;
            if (near) continue;
            if (spoken.size() == MOST) break;
            spoken.add(dir);
            int r = m.hollow()[k], due = 6 + Math.round(r * SPEED);
            last = Math.max(last, due);
            // said from a little way off in the true direction: from the true distance you'd not hear it at all
            Vec3 from = Vec3.atCenterOf(at).add(dir.scale(Math.min(r, 8)));
            float near01 = 1F - r / (float) (m.radius() + 4);
            // a nearer hollow is higher and clearer, a far one deep and dull
            ANSWERS.add(new Answer(now + due, from.x, from.y, from.z, 0.35F + 0.55F * near01, 0.72F + 0.42F * near01, -1));
            // the dust comes off the wall on that side of where you tapped
            Vec3 lateral = dir.subtract(out.scale(dir.dot(out)));
            lateral = lateral.lengthSqr() < 1e-4 ? Vec3.ZERO : lateral.normalize();
            Vec3 place = centre.add(lateral.scale(0.45));
            Vec3 up = lateral == Vec3.ZERO ? (Math.abs(out.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)) : lateral;
            Vec3 across = out.cross(up);
            SHAKES.add(new Shake(now + due, place, across, up, 3 + Math.round(4 * near01)));
        }
        if (m.ore() >= 0 && m.ore() < Ore.values().length) {
            BlockPos lode = BlockPos.of(m.lode());
            Vec3 c = Vec3.atCenterOf(lode);
            Vec3 v = c.subtract(eye);
            // close enough to hear, on the line to the lode
            if (v.length() > 8) c = eye.add(v.normalize().scale(8));
            ANSWERS.add(new Answer(now + last + 12, c.x, c.y, c.z, 0.7F, TuningForkItem.pitch(Ore.values()[m.ore()]) * 1.5F, m.ore()));
        }
    }

    private static Vec3 jitter(double r) {
        return new Vec3((R.nextDouble() - 0.5) * r * 2, (R.nextDouble() - 0.5) * r * 2, (R.nextDouble() - 0.5) * r * 2);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        now++;
        for (int i = ANSWERS.size() - 1; i >= 0; i--) {
            Answer a = ANSWERS.get(i);
            if (a.due() > now) continue;
            ANSWERS.remove(i);
            var event = a.ore() >= 0 ? DelveContent.FORK_RING.get() : DelveForged.SOUNDING_HOLLOW.get();
            mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, a.volume(), Math.min(2F, a.pitch()), R, a.x(), a.y(), a.z()));
            if (a.ore() >= 0) {
                int tint = Ore.values()[a.ore()].tint;
                Vec3 eye = mc.player.getEyePosition(), dir = new Vec3(a.x(), a.y(), a.z()).subtract(eye).normalize();
                Vec3 p = eye.add(dir.scale(1.1));
                // the only light in it: a few motes drifting off toward the ore, gone in a breath
                for (int k = 0; k < 5; k++) {
                    Vec3 d = dir.scale(0.03 + R.nextDouble() * 0.03).add(jitter(0.008));
                    Vec3 q = p.add(jitter(0.12));
                    DelveParticles.spawn(level, DelveContent.LIGHT_MOTE.get(), tint, 0.7F, q.x, q.y, q.z, d.x, d.y, d.z);
                }
            }
        }
        for (int i = SHAKES.size() - 1; i >= 0; i--) {
            Shake s = SHAKES.get(i);
            if (s.due() > now) continue;
            SHAKES.remove(i);
            for (int g = 0; g < s.grains(); g++) {
                // a line of it along the wall, falling
                double along = (R.nextDouble() - 0.5) * 0.9, lift = (R.nextDouble() - 0.3) * 0.5;
                Vec3 p = s.at().add(s.side().scale(along)).add(s.up().scale(lift));
                DelveParticles.spawn(level, DelveContent.DUST.get(), DUST_TINT, 0.8F + R.nextFloat() * 0.5F, p.x, p.y, p.z, 0, -0.015, 0);
            }
        }
    }

    static void reset() {
        ANSWERS.clear();
        SHAKES.clear();
    }
}
