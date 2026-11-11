package dev.awaken.mobs.boss;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Where a kit move's blow is, as both sides work it out (SPEC §68.2): the server strikes with a move's capsules
 * ({@code FightRunner.strike}), and a client asks the same rule which tick of the blow will reach where it stands, so
 * the ring it draws closes on the tick the blow lands (`mobs.client.TellClock`). Pure maths, no state.
 */
public final class KitReach {
    private KitReach() {}

    /** A slam strikes everyone within this of where it lands, on its first active tick (the rest of its reach is a wave). */
    public static final float SLAM_CORE = 1.5F;
    /** A kit charge: the ticks it runs at most, how far past you it carries, and how near its body has to come to strike. */
    public static final int CHARGE_TICKS = 14;
    public static final double CHARGE_PAST = 1.5, CHARGE_GRAZE = 0.6;

    /** Blocks a tick a charge carries its body. */
    public static double chargeSpeed(boolean flies) {
        return flies ? 1.0 : 0.85;
    }

    /**
     * A point of the body's space (blocks: +x left, +y up, +z forward) in the world, for a body at (bx, by, bz) facing
     * {@code yaw} and leaning {@code lean} degrees into its blow about a hip {@code hip} blocks up.
     */
    public static Vec3 point(double bx, double by, double bz, float yaw, float lean, float hip, float x, float y, float z) {
        if (lean != 0) { // the body leans into the blow about its hips
            float cl = Mth.cos(lean * Mth.DEG_TO_RAD), sl = Mth.sin(lean * Mth.DEG_TO_RAD), up = y - hip;
            float zw = up * sl + z * cl;
            y = hip + up * cl - z * sl;
            z = zw;
        }
        float r = yaw * Mth.DEG_TO_RAD, c = Mth.cos(r), s = Mth.sin(r);
        // facing yaw: forward is (-sin, cos); the body's left is (cos, sin)
        return new Vec3(bx + x * c - z * s, by + y, bz + x * s + z * c);
    }

    /** Whether the capsule from a to b of radius r touches the box (the segment sampled every half block). */
    public static boolean touches(Vec3 a, Vec3 b, float r, AABB box) {
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) Math.ceil(len / 0.5));
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double x = a.x + (b.x - a.x) * f, y = a.y + (b.y - a.y) * f, z = a.z + (b.z - a.z) * f;
            double dx = Math.max(Math.max(box.minX - x, 0), x - box.maxX);
            double dy = Math.max(Math.max(box.minY - y, 0), y - box.maxY);
            double dz = Math.max(Math.max(box.minZ - z, 0), z - box.maxZ);
            if (dx * dx + dy * dy + dz * dz <= r * r) return true;
        }
        return false;
    }

    /** The first of a move's active ticks that has a capsule at all (0-based), or -1 for a move that strikes with none. */
    public static int firstCapsule(KitMoves.Move m) {
        for (int k = 0; k < m.hits().size(); k++) if (!m.hits().get(k).isEmpty()) return k;
        return -1;
    }

    /**
     * The first active tick (0-based) whose capsules touch {@code box}, for a body that stands at (bx, by, bz) as its
     * active window opens and then, as the server moves it before each tick's strike, goes {@code advance / active}
     * blocks forward and {@code rise / active} up a tick; -1 when no tick's capsules reach the box.
     */
    public static int firstTouch(KitMoves.Move m, int active, double bx, double by, double bz, float yaw, float lean, float hip,
                                 float advance, float rise, AABB box) {
        float r = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(r), fz = Mth.cos(r), stepF = advance / Math.max(1, active), stepUp = rise / Math.max(1, active);
        List<List<KitMoves.Capsule>> hits = m.hits();
        for (int k = 0; k < hits.size() && k < active; k++) {
            double x = bx + fx * stepF * (k + 1), y = by + stepUp * (k + 1), z = bz + fz * stepF * (k + 1);
            for (KitMoves.Capsule c : hits.get(k)) {
                if (touches(point(x, y, z, yaw, lean, hip, c.ax(), c.ay(), c.az()), point(x, y, z, yaw, lean, hip, c.bx(), c.by(), c.bz()), c.r(), box)) return k;
            }
        }
        return -1;
    }
}
