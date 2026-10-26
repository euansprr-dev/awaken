package dev.awaken.delve.cave;

import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * For other structures that want to open into the natural caves (W5: the Crystal Nest's tunnel, PLAN 8.2 "joined
 * lairs"): the nearest floor of natural cave air near a point, read from the generator's base columns before any chunk
 * exists. A feet cell {x, y, z}, or null when there is none within reach.
 */
public final class CaveJoints {
    private CaveJoints() {}

    public static int @Nullable [] air(Structure.GenerationContext ctx, int x, int y, int z, int reach) {
        return CaveSites.air(ctx, x, y, z, reach);
    }
}
