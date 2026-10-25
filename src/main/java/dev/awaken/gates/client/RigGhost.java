package dev.awaken.gates.client;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.entity.GauntletGhost;
import dev.awaken.gates.net.RigPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Your ghost on a Gauntlet's course (SPEC §17.5 "Gauntlet"): the server sends your best run of it as a run starts;
 * from that moment a translucent you in the course's gold runs it again beside you, sample to sample, its walk
 * animated by its own pace, a short trail of light behind it. It waits at the far landing a moment, then fades out;
 * it goes at once if the run is given up.
 */
final class RigGhost {
    private RigGhost() {}

    /** How long it waits at the end of its run before it goes, and how many trail points it keeps. */
    static final int LINGER = 30, TRAIL = 10;

    private static RigPayloads.Ghost pending;
    private static RigPayloads.Ghost playing;
    private static long start;
    private static GauntletGhost ghost;
    static final Deque<Vec3> TRAIL_POINTS = new ArrayDeque<>();
    private static int nextId = -0x6A0000;

    static void ghost(RigPayloads.Ghost g) {
        pending = g;
    }

    static void run(RigPayloads.Run r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (r.phase() == RigPayloads.Run.START) {
            remove();
            // the ghost sent with this start (none: no best yet)
            if (pending != null && r.ticks() > 0) {
                playing = pending;
                start = level.getGameTime();
                spawn(level);
            }
            pending = null;
        } else if (r.phase() == RigPayloads.Run.STOP) {
            remove();
        }
    }

    private static void spawn(ClientLevel level) {
        var me = Minecraft.getInstance().player;
        if (me == null) return;
        ghost = new GauntletGhost(GatesRegistry.GHOST.get(), level);
        ghost.setId(nextId--);
        ghost.route(me.getUUID(), List.of(), List.of());
        Vec3 at = sample(0);
        ghost.snapTo(at.x, at.y, at.z, yaw(0), 0);
        level.addEntity(ghost);
        TRAIL_POINTS.clear();
    }

    private static void remove() {
        if (ghost != null) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) level.removeEntity(ghost.getId(), Entity.RemovalReason.DISCARDED);
        }
        ghost = null;
        playing = null;
        TRAIL_POINTS.clear();
    }

    static void clear() {
        pending = null;
        remove();
    }

    static GauntletGhost current() {
        return ghost;
    }

    private static Vec3 sample(int i) {
        RigPayloads.Ghost g = playing;
        i = Mth.clamp(i, 0, g.xz().size() - 1);
        int xz = g.xz().get(i), yw = g.yw().get(i);
        BlockPos o = g.origin();
        return new Vec3(o.getX() + (xz >> 16) / 8.0, o.getY() + (yw >> 16) / 8.0, o.getZ() + (short) (xz & 0xFFFF) / 8.0);
    }

    private static float yaw(int i) {
        RigPayloads.Ghost g = playing;
        i = Mth.clamp(i, 0, g.yw().size() - 1);
        return (g.yw().get(i) & 0xFF) * 360F / 256F;
    }

    /** Client tick: on along its samples (between two, the straight line), its body turned the way it was going. */
    static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (ghost == null || playing == null || level == null) return;
        if (ghost.level() != level) {
            remove();
            return;
        }
        long age = level.getGameTime() - start;
        int n = playing.xz().size();
        double f = age / (double) playing.step();
        int i = (int) Math.floor(f);
        if (i >= n - 1 + LINGER / playing.step()) {
            remove();
            return;
        }
        Vec3 a = sample(i), b = sample(i + 1), at = a.lerp(b, Mth.clamp(f - i, 0, 1));
        float yaw = Mth.rotLerp((float) Mth.clamp(f - i, 0, 1), yaw(i), yaw(i + 1));
        // the renderer draws between xOld and now; the walk animation measures from xo
        ghost.xo = ghost.xOld = ghost.getX();
        ghost.yo = ghost.yOld = ghost.getY();
        ghost.zo = ghost.zOld = ghost.getZ();
        ghost.yRotO = ghost.getYRot();
        ghost.yBodyRotO = ghost.yBodyRot;
        ghost.yHeadRotO = ghost.yHeadRot;
        ghost.setPos(at.x, at.y, at.z);
        ghost.setYRot(yaw);
        ghost.yBodyRot = yaw;
        ghost.yHeadRot = yaw;
        ghost.calculateEntityAnimation(false);
        ghost.tickCount++;
        TRAIL_POINTS.addFirst(at.add(0, 1.0, 0));
        while (TRAIL_POINTS.size() > TRAIL) TRAIL_POINTS.removeLast();
    }
}
