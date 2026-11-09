package dev.awaken.gates.realms;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Where Realms lie (SPEC §50.7). Cell i has its middle at (ORIGIN + column * PITCH, ORIGIN + row * PITCH), far past the
 * old gate grid, so no position in a Realm can ever read as a floor cell. Plans are immutable and readable from worldgen
 * threads; written on the server thread before a Realm's chunks are asked for.
 */
public final class RealmCells {
    private RealmCells() {}

    public static final int PITCH = 8192, PER_ROW = 64, ORIGIN = 131072 + PITCH / 2, HALF = PITCH / 2 - 64;

    private record Entry(RealmPlan plan, int gate) {}

    private static final ConcurrentHashMap<Integer, Entry> LIVE = new ConcurrentHashMap<>();

    public static int centerX(int cell) {
        return ORIGIN + Math.floorMod(cell, PER_ROW) * PITCH;
    }

    public static int centerZ(int cell) {
        return ORIGIN + Math.floorDiv(cell, PER_ROW) * PITCH;
    }

    /** The cell this column is in, or -1 when it's in none. */
    public static int cellAt(int x, int z) {
        int cx = Math.floorDiv(x - ORIGIN + PITCH / 2, PITCH), cz = Math.floorDiv(z - ORIGIN + PITCH / 2, PITCH);
        if (cx < 0 || cz < 0 || cx >= PER_ROW || cz >= PER_ROW) return -1;
        int cell = cx + cz * PER_ROW;
        return Math.abs(x - centerX(cell)) <= HALF && Math.abs(z - centerZ(cell)) <= HALF ? cell : -1;
    }

    public static void put(int cell, RealmPlan plan, int gateId) {
        LIVE.put(cell, new Entry(plan, gateId));
    }

    public static void remove(int cell) {
        LIVE.remove(cell);
    }

    public static void clear() {
        LIVE.clear();
    }

    public static RealmPlan plan(int cell) {
        Entry e = cell < 0 ? null : LIVE.get(cell);
        return e == null ? null : e.plan;
    }

    public static RealmPlan planAt(int x, int z) {
        return plan(cellAt(x, z));
    }

    /** The gate this column's Realm belongs to, or -1. */
    public static int gateAt(int x, int z) {
        int cell = cellAt(x, z);
        Entry e = cell < 0 ? null : LIVE.get(cell);
        return e == null ? -1 : e.gate;
    }
}
