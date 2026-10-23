package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Test hooks: build a layout without the cache, fill a proto chunk exactly as worldgen does. */
public final class GateTestAccess {
    private GateTestAccess() {}

    public static GateLayout build(GatePlan plan) {
        return FloorBuilder.build(plan);
    }

    public static void fill(ChunkAccess chunk, GatePlan plan, int cell) {
        GateChunkGenerator.fill(chunk, plan, cell, chunk.getPos().x(), chunk.getPos().z());
    }
}
