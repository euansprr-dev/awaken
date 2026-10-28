package dev.awaken.gates.world;

import java.util.Arrays;

/**
 * Which place (room) holds a floor-local spot, at 4-block resolution (SPEC §38, "Places and a zone map"). Replaces the
 * 16-block slot grid for world floors, whose places are any shape: a grotto, a gallery over an aisle. Every cell holds a
 * ground place and optionally a second place above a split height (a gallery over a nave), so "which room is this?"
 * stays one array read. Built once per layout, read-only afterwards (shared by worldgen and server threads).
 */
public final class PlaceMap {
    public static final int CELL = 4;
    /** Size in cells; floor-local block x = cx * CELL. */
    public final int w, d;
    private final short[] ground, upper;
    /** Height above the floor's base where the upper place starts (only read where {@code upper} is set). */
    private final short[] split;

    public PlaceMap(int blocksW, int blocksD) {
        this.w = Math.ceilDiv(blocksW, CELL);
        this.d = Math.ceilDiv(blocksD, CELL);
        this.ground = new short[w * d];
        this.upper = new short[w * d];
        this.split = new short[w * d];
        Arrays.fill(split, Short.MAX_VALUE);
    }

    /** The room id at a floor-local spot ({@code yOff} above the floor's base), or -1 for none. */
    public int at(int fx, int yOff, int fz) {
        int cx = Math.floorDiv(fx, CELL), cz = Math.floorDiv(fz, CELL);
        if (cx < 0 || cz < 0 || cx >= w || cz >= d) return -1;
        int i = cx + cz * w;
        if (upper[i] != 0 && yOff >= split[i]) return upper[i] - 1;
        return ground[i] - 1;
    }

    /** The ground place at a floor-local column, or -1. */
    public int groundAt(int fx, int fz) {
        int cx = Math.floorDiv(fx, CELL), cz = Math.floorDiv(fz, CELL);
        if (cx < 0 || cz < 0 || cx >= w || cz >= d) return -1;
        return ground[cx + cz * w] - 1;
    }

    /** Marks the cells covering floor-local blocks [x0, x1) x [z0, z1) as the room's ground. */
    public void ground(int x0, int z0, int x1, int z1, int room) {
        forCells(x0, z0, x1, z1, i -> ground[i] = (short) (room + 1));
    }

    /** Marks a room standing over the ground from {@code yOff} up (galleries, bridges, ledges above a hall). */
    public void upper(int x0, int z0, int x1, int z1, int yOff, int room) {
        forCells(x0, z0, x1, z1, i -> {
            upper[i] = (short) (room + 1);
            split[i] = (short) yOff;
        });
    }

    /** Ground cells inside a circle of floor-local blocks (centre, radius). */
    public void groundDisc(int cx, int cz, int r, int room) {
        for (int z = Math.floorDiv(cz - r, CELL); z <= Math.floorDiv(cz + r, CELL); z++) {
            for (int x = Math.floorDiv(cx - r, CELL); x <= Math.floorDiv(cx + r, CELL); x++) {
                if (x < 0 || z < 0 || x >= w || z >= d) continue;
                int mx = x * CELL + CELL / 2 - cx, mz = z * CELL + CELL / 2 - cz;
                if (mx * mx + mz * mz <= r * r) ground[x + z * w] = (short) (room + 1);
            }
        }
    }

    /** How many cells name this room (ground or upper), for tests and budgets. */
    public int cells(int room) {
        int n = 0;
        for (int i = 0; i < ground.length; i++) {
            if (ground[i] == room + 1) n++;
            if (upper[i] == room + 1) n++;
        }
        return n;
    }

    /** The slot grid of a rooms floor as a place map (same answer as {@link FloorLayout#roomAtSlot}, cell for cell). */
    static PlaceMap ofSlots(FloorLayout f) {
        PlaceMap m = new PlaceMap(f.w * 16, f.h * 16);
        for (Room r : f.rooms) m.ground(r.gx * 16, r.gz * 16, (r.gx + r.gw) * 16, (r.gz + r.gh) * 16, r.id);
        return m;
    }

    private interface Cell {
        void set(int i);
    }

    private void forCells(int x0, int z0, int x1, int z1, Cell c) {
        int a = Math.max(0, Math.floorDiv(x0, CELL)), b = Math.max(0, Math.floorDiv(z0, CELL));
        int e = Math.min(w, Math.ceilDiv(x1, CELL)), f = Math.min(d, Math.ceilDiv(z1, CELL));
        for (int z = b; z < f; z++) for (int x = a; x < e; x++) c.set(x + z * w);
    }
}
