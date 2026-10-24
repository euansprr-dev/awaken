package dev.awaken.gates.world;

/**
 * The Mirror Hall's light (SPEC §17 "Gate Rig"): one horizontal beam traced cell by cell from its lens, turned by
 * one-sided mirrors and stopped by anything solid. Pure and allocation-free, so the server (what it burns, whether it
 * reaches the seal), the client (what it draws) and the solvability check (on the room's geometry) trace the same light.
 *
 * <p>Directions are the doors' sides: 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x). A mirror's turn {@code o}
 * points its silvered face at a diagonal, NE, SE, SW, NW for 0..3, a quarter turn clockwise each; light arriving at its
 * back stops there.
 */
public final class RigBeam {
    private RigBeam() {}

    public static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    /** The longest beam (cells): far more than a room is wide. */
    public static final int MAX_STEPS = 96;

    /** What the light meets in a cell. */
    public enum Hit { EMPTY, MIRROR, SOLID, RECEIVER }

    /** The cells a beam can see: the room's interior plus whatever sits in its walls. */
    public interface Grid {
        Hit at(int x, int z);

        /** A mirror's turn (0..3); only asked where {@link #at} said MIRROR. */
        int turn(int x, int z);
    }

    /** Receives each straight piece of the beam: from (x0, z0) to (x1, z1), cell centres, in order. */
    public interface Segments {
        void segment(int x0, int z0, int x1, int z1, int dir);
    }

    /** Where a beam arriving in {@code dir} goes off a mirror of turn {@code o}, or -1 when it meets the mirror's back. */
    public static int reflect(int o, int dir) {
        // o = 0 (face NE): west-going light turns north, south-going turns east; the rest rotate with the mirror
        int in = Math.floorMod(dir - o, 4);
        return switch (in) {
            case 3 -> Math.floorMod(o, 4);
            case 2 -> Math.floorMod(1 + o, 4);
            default -> -1;
        };
    }

    /** The turn that sends light arriving in {@code in} out in {@code out} (a quarter turn apart), or -1. */
    public static int turnFor(int in, int out) {
        for (int o = 0; o < 4; o++) if (reflect(o, in) == out) return o;
        return -1;
    }

    /**
     * Traces from a lens in cell (x, z) shining in {@code dir} (the first cell lit is the next one). Returns the receiver's packed cell
     * ({@code x << 16 | z & 0xFFFF}) if the light ends on it, else Long.MIN_VALUE. {@code out} may be null.
     */
    public static long trace(Grid grid, int x, int z, int dir, Segments out) {
        int sx = x, sz = z;
        int cx = x, cz = z;
        for (int step = 0; step < MAX_STEPS; step++) {
            int nx = cx + DX[dir], nz = cz + DZ[dir];
            Hit h = grid.at(nx, nz);
            if (h == Hit.EMPTY) {
                cx = nx;
                cz = nz;
                continue;
            }
            if (h == Hit.MIRROR) {
                int nd = reflect(grid.turn(nx, nz), dir);
                if (out != null) out.segment(sx, sz, nx, nz, dir);
                if (nd < 0) return Long.MIN_VALUE;
                dir = nd;
                sx = cx = nx;
                sz = cz = nz;
                continue;
            }
            if (out != null) out.segment(sx, sz, nx, nz, dir);
            return h == Hit.RECEIVER ? pack(nx, nz) : Long.MIN_VALUE;
        }
        if (out != null) out.segment(sx, sz, cx, cz, dir);
        return Long.MIN_VALUE;
    }

    public static long pack(int x, int z) {
        return ((long) x << 16) | (z & 0xFFFF);
    }
}
