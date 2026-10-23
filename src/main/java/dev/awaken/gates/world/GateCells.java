package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Theme;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The plan of every live cell, readable from worldgen threads. Written on the server thread before a cell's chunks are
 * requested (gate creation, server start), removed when the cell is released. Plans are immutable records.
 */
public final class GateCells {
    private GateCells() {}

    private static final ConcurrentHashMap<Integer, GatePlan> PLANS = new ConcurrentHashMap<>();

    public static void put(int cell, GatePlan plan) {
        PLANS.put(cell, plan);
    }

    public static void remove(int cell) {
        GatePlan p = PLANS.remove(cell);
        if (p != null) GateLayout.forget(p);
    }

    public static void clear() {
        PLANS.clear();
    }

    public static GatePlan plan(int cell) {
        return cell < 0 ? null : PLANS.get(cell);
    }

    /** Theme at a block column (annex half uses the annex theme), or null outside any live cell. */
    public static Theme themeAt(int blockX, int blockZ) {
        int cell = Cells.cellAt(blockX, blockZ);
        GatePlan p = plan(cell);
        if (p == null) return null;
        return Cells.localX(cell, blockX) >= Cells.ANNEX_SLOT_X * 16 ? p.annexTheme() : p.theme();
    }
}
