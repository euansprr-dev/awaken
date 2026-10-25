package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Windways (SPEC §17.12): streams of air between neighbouring sky platforms that carry a glide from one to the next.
 * Each pair of platforms has two lanes, one each way, {@link #LANE} to the right of the line between them; a lane runs
 * from {@link #OUT} out from one platform (past its islands), {@link #BELOW} under its deck so a glide off the deck's
 * edge falls into it, to {@link #IN} short of the other's centre, {@link #ABOVE} over its deck. Inside its tube (radius
 * {@link #RADIUS}) the air pushes along it, holds a glide up against its sink and draws you toward its heart; weaker
 * at the rim, rising over its first stretch, braking over its last so it sets you down on the deck. A glide that looks
 * roughly along a lane is held on its line ({@link #steer}); a look well away breaks out. Moving air through AirHooks,
 * the same on both sides: the server's lanes come from the platforms near its players, the client's are the ones
 * synced to it.
 */
public final class Windways {
    private Windways() {}

    /** A lane: from (ax, ay, az) to (bx, by, bz), the air flowing that way. */
    public record Way(double ax, double ay, double az, double bx, double by, double bz) {
        public double length() {
            return Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        }

        /** How far along its axis (x, y, z) lies (can be under 0 or past its length). */
        public double along(double x, double y, double z) {
            double l = length();
            return l < 1e-9 ? 0 : ((x - ax) * (bx - ax) + (y - ay) * (by - ay) + (z - az) * (bz - az)) / l;
        }

        /** The distance from (x, y, z) to its axis (the nearest point on the segment). */
        public double off(double x, double y, double z) {
            double l = length(), s = Mth.clamp(along(x, y, z), 0, l), f = l < 1e-9 ? 0 : s / l;
            double ox = x - (ax + (bx - ax) * f), oy = y - (ay + (by - ay) * f), oz = z - (az + (bz - az) * f);
            return Math.sqrt(ox * ox + oy * oy + oz * oz);
        }

        /** Horizontal distance from (x, z) to the nearest point of its axis. */
        public double reach(double x, double z) {
            double dx = bx - ax, dz = bz - az, l2 = dx * dx + dz * dz;
            double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / l2));
            double px = ax + dx * t - x, pz = az + dz * t - z;
            return Math.sqrt(px * px + pz * pz);
        }
    }

    /** The tube's radius; the push along it, the hold against a glide's sink and the pull to its heart (blocks a tick²). */
    public static final double RADIUS = 6, PUSH = 0.018, HOLD = 0.022, CENTRE = 0.008;
    /** The push rises over this much of its start; over this much of its end it brakes, by this much. */
    public static final double FADE = 40, BRAKE = 64, BRAKE_PUSH = 0.012;
    /** The line a held glide flies: toward its heart this far ahead, bent by how far off it you look (this share). */
    public static final double AHEAD = 16, KEEP = 0.35;
    /** A look this far (degrees) from the lane's way breaks out of its hold (the air still pushes). */
    public static final double HOLD_LOOK = 35;
    /**
     * How far out of the first platform's centre it starts (past its islands, 19 out) and short of the next one's it
     * ends (over its deck's rim, 11 out with the side step); how far under the first deck and over the next (a glide's
     * walk-on height, its islands rise 4 over the deck); how far right of the line between them it runs (clear of the
     * islands on either side, and of the other way's lane).
     */
    public static final int OUT = 34, IN = 5, BELOW = 6, ABOVE = 5, LANE = 10;
    /** Platforms this far apart (or nearer) are linked, each to its nearest. */
    public static final int MAX_LINK = 1100;

    private static volatile List<Way> server = List.of(), client = List.of();

    /** The two lanes between two platforms (their centres and deck heights): p to q on its right, q to p on its right. */
    public static List<Way> between(BlockPos p, int deckP, BlockPos q, int deckQ) {
        List<Way> out = new ArrayList<>(2);
        out.add(lane(p, deckP, q, deckQ));
        out.add(lane(q, deckQ, p, deckP));
        return out;
    }

    private static Way lane(BlockPos p, int deckP, BlockPos q, int deckQ) {
        double dx = q.getX() - p.getX(), dz = q.getZ() - p.getZ(), l = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / l, uz = dz / l, rx = -uz, rz = ux;
        return new Way(p.getX() + 0.5 + ux * OUT + rx * LANE, deckP - BELOW, p.getZ() + 0.5 + uz * OUT + rz * LANE,
                q.getX() + 0.5 - ux * IN + rx * LANE, deckQ + 1 + ABOVE, q.getZ() + 0.5 - uz * IN + rz * LANE);
    }

    /** Adds one lane's air at (x, y, z) to {@code out}: along (or braking), up against the sink, in toward its heart. */
    public static void airOf(Way w, double x, double y, double z, double[] out) {
        double len = w.length();
        if (len < 1) return;
        double t = w.along(x, y, z);
        if (t < 0 || t > len) return;
        double ux = (w.bx - w.ax) / len, uy = (w.by - w.ay) / len, uz = (w.bz - w.az) / len;
        double ox = x - (w.ax + ux * t), oy = y - (w.ay + uy * t), oz = z - (w.az + uz * t), d = Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (d > RADIUS) return;
        double f = (1 - 0.6 * (d / RADIUS) * (d / RADIUS)) * Math.min(1, t / FADE + 0.15);
        double push = len - t > BRAKE ? PUSH : -BRAKE_PUSH;
        out[0] += ux * push * f;
        out[1] += uy * push * f + HOLD * f;
        out[2] += uz * push * f;
        if (d > 0.3) {
            double c = CENTRE * Math.min(1, d / RADIUS) / d;
            out[0] -= ox * c;
            out[1] -= oy * c;
            out[2] -= oz * c;
        }
    }

    /**
     * A glide held on a lane's line: when the look (yaw, pitch) is within {@link #HOLD_LOOK} of the lane's way, the line
     * the glide turns toward instead, into {@code out} (yaw, pitch): its heart {@link #AHEAD} on, bent a little by the
     * look. False (out untouched) when the look breaks out.
     */
    public static boolean steer(Way w, double x, double y, double z, float yaw, float pitch, float[] out) {
        double len = w.length();
        if (len < 1) return false;
        double ux = (w.bx - w.ax) / len, uy = (w.by - w.ay) / len, uz = (w.bz - w.az) / len;
        double cp = Mth.cos(pitch * Mth.DEG_TO_RAD);
        double lx = -Mth.sin(yaw * Mth.DEG_TO_RAD) * cp, ly = -Mth.sin(pitch * Mth.DEG_TO_RAD), lz = Mth.cos(yaw * Mth.DEG_TO_RAD) * cp;
        if (lx * ux + ly * uy + lz * uz < Math.cos(Math.toRadians(HOLD_LOOK))) return false;
        double s = Math.min(len, Math.max(0, w.along(x, y, z)) + AHEAD);
        double px = w.ax + ux * s - x, py = w.ay + uy * s - y, pz = w.az + uz * s - z, pl = Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1) return false;
        double ex = px / pl + KEEP * (lx - ux), ey = py / pl + KEEP * (ly - uy), ez = pz / pl + KEEP * (lz - uz);
        out[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
        out[1] = (float) Math.toDegrees(-Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
        return true;
    }

    /** AirHooks source: the lanes of this side at the feet. */
    static void airAt(Level level, double x, double y, double z, double[] out) {
        if (level.dimension() != Level.OVERWORLD) return;
        for (Way w : level.isClientSide() ? client : server) airOf(w, x, y, z, out);
    }

    /** The lane whose tube holds (x, y, z) on this side, or null. */
    public static Way inside(Level level, double x, double y, double z) {
        if (level.dimension() != Level.OVERWORLD) return null;
        for (Way w : level.isClientSide() ? client : server) if (holds(w, x, y, z)) return w;
        return null;
    }

    /** Whether (x, y, z) is in this lane's tube. */
    public static boolean holds(Way w, double x, double y, double z) {
        double t = w.along(x, y, z);
        return t >= 0 && t <= w.length() && w.off(x, y, z) <= RADIUS;
    }

    static void setServer(List<Way> ways) {
        server = List.copyOf(ways);
    }

    public static List<Way> serverWays() {
        return server;
    }

    public static void setClient(List<Way> ways) {
        client = List.copyOf(ways);
    }

    public static List<Way> clientWays() {
        return client;
    }
}
