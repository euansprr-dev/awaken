package dev.awaken.core;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.function.LongPredicate;

/**
 * The edge hold's math (SPEC §28.9, "The edge hold"): fast movement never carries the player into chunks the client
 * doesn't have yet. 26.3's client has no chunk gate, so a player faster than the server's world generation sends move
 * packets into land not made, and each one waits on the server thread for its chunk (the flight freeze of runs
 * 40715105903 and 40716396298). The client looks ahead along its horizontal velocity, about 1.25 s of travel; when a
 * chunk on that path is missing it eases the horizontal speed down so the player nears that edge as the land comes in:
 * a headwind, never a wall (the cap falls at most {@link #EASE} a tick) and never a dead stop (never below
 * {@link #FLOOR}). It only ever slows: when the land comes, the movers' own acceleration brings the speed back. Pure
 * math here (the GameTest drives it); the client's hook is {@code core.client.EdgeHoldClient}.
 */
public final class EdgeHold {
    private EdgeHold() {}

    /** How far ahead the path is read: this many ticks of travel at the speed now (1.25 s). */
    public static final double LOOK_TICKS = 25;
    /** The speed it never holds below, blocks a tick (2 blocks a second, a slow walk): thick air, never a stop. */
    public static final double FLOOR = 0.1;
    /** The most the cap falls in one tick (land missing close ahead after a turn is a quick ease, never a wall). */
    public static final double EASE = 0.12;
    /** Chunks a path reads at most (1.25 s at 100 blocks a second crosses fewer). */
    static final int MOST_CHUNKS = 24;

    /**
     * Blocks along the horizontal path from ({@code x}, {@code z}) in the direction ({@code vx}, {@code vz}) to the
     * first chunk {@code has} says is missing, looking at most {@code reach} blocks: 0 when the player's own chunk is
     * missing, {@link Double#POSITIVE_INFINITY} when every chunk on the path is there. One lookup a chunk the path crosses.
     */
    public static double edge(double x, double z, double vx, double vz, double reach, LongPredicate has) {
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len < 1.0E-9) return Double.POSITIVE_INFINITY;
        double dx = vx / len, dz = vz / len;
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4;
        if (!has.test(ChunkPos.pack(cx, cz))) return 0;
        int sx = dx > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        // the path's distance to the next chunk border across x and across z, and between borders (a grid walk)
        double nextX = dx == 0 ? Double.POSITIVE_INFINITY : ((dx > 0 ? (cx + 1) * 16 : cx * 16) - x) / dx;
        double nextZ = dz == 0 ? Double.POSITIVE_INFINITY : ((dz > 0 ? (cz + 1) * 16 : cz * 16) - z) / dz;
        double stepX = dx == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dx), stepZ = dz == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dz);
        for (int i = 0; i < MOST_CHUNKS; i++) {
            double t;
            if (nextX < nextZ) {
                t = nextX;
                cx += sx;
                nextX += stepX;
            } else {
                t = nextZ;
                cz += sz;
                nextZ += stepZ;
            }
            if (t > reach) return Double.POSITIVE_INFINITY;
            if (!has.test(ChunkPos.pack(cx, cz))) return t;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * The horizontal speed allowed this tick, blocks a tick, from the speed now, the path's edge ({@link #edge}) and the
     * cap last tick ({@link Double#POSITIVE_INFINITY} when nothing held): infinite when the edge is beyond 1.25 s of
     * travel (or the speed already a crawl), else the speed that would take the rest of that time to reach it, eased in.
     */
    public static double cap(double speed, double edge, double last) {
        if (speed <= FLOOR || edge >= speed * LOOK_TICKS) return Double.POSITIVE_INFINITY;
        double target = Math.max(FLOOR, edge / LOOK_TICKS);
        return Math.max(target, Math.min(last, speed) * (1 - EASE));
    }
}
