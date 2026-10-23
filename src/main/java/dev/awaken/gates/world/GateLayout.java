package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The full interior of one gate: main floors (1..floors, the last one holds the Gate Lord) and, for Double Gate and
 * Gate within a Gate, two annex floors in the other half of the cell. A pure function of the {@link GatePlan}.
 */
public final class GateLayout {
    public final GatePlan plan;
    public final List<FloorLayout> main;
    public final List<FloorLayout> annex;

    GateLayout(GatePlan plan, List<FloorLayout> main, List<FloorLayout> annex) {
        this.plan = plan;
        this.main = List.copyOf(main);
        this.annex = List.copyOf(annex);
    }

    /** Floor f (1-based) of area 0 (main) or 1 (annex), or null. */
    public FloorLayout floor(int area, int f) {
        List<FloorLayout> list = area == 0 ? main : annex;
        return f >= 1 && f <= list.size() ? list.get(f - 1) : null;
    }

    /** Floor at cell-local coordinates (x inside the cell, absolute y). */
    public FloorLayout floorAt(int cellLocalX, int y) {
        int f = Cells.floorAtY(y);
        if (f == 0) return null;
        int area = cellLocalX >= Cells.ANNEX_SLOT_X * 16 ? 1 : 0;
        return floor(area, f);
    }

    // ---- cache ------------------------------------------------------------------------------------------------------

    private static final ConcurrentHashMap<Long, GateLayout> CACHE = new ConcurrentHashMap<>();

    /** Builds (or returns the cached) layout. Thread-safe; the same plan always yields the same layout. */
    public static GateLayout of(GatePlan plan) {
        long key = plan.seed() * 31 + plan.contentVersion();
        GateLayout l = CACHE.get(key);
        if (l != null && l.plan.equals(plan)) return l;
        l = FloorBuilder.build(plan);
        if (CACHE.size() > 256) CACHE.clear();
        CACHE.put(key, l);
        return l;
    }

    public static void forget(GatePlan plan) {
        CACHE.remove(plan.seed() * 31 + plan.contentVersion());
    }
}
