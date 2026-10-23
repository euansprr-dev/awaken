package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;

/** Builds a layout without the cache (tests compare two independent builds). */
public final class GateTestAccess {
    private GateTestAccess() {}

    public static GateLayout build(GatePlan plan) {
        return FloorBuilder.build(plan);
    }
}
