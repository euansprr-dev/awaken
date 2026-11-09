package dev.awaken.terrain.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import org.jspecify.annotations.Nullable;

/**
 * The hero trees for whoever else writes the land (SPEC §15, "hero trees"). A giant is built chunk by chunk and far taller
 * than anything that clears land reaches, so a road or a village that cuts one leaves its top and its limbs in the air.
 * They ask here and leave it standing.
 */
public final class HeroTrees {
    private HeroTrees() {}

    /** Whether a block position is part of a hero tree (its blueprint's cell, or its trunk or root column down to the ground). */
    @FunctionalInterface
    public interface Cells {
        boolean at(int x, int y, int z);
    }

    /** The hero trees reaching this chunk or those beside it, as a lookup; null where there are none (nearly everywhere). */
    public static @Nullable Cells near(StructureManager structures, ChunkPos chunk) {
        return dev.awaken.terrain.hero.HeroWorldgen.cells(structures, chunk);
    }

    /** Whether this column lies inside a hero tree's reserved box (a gate or the like goes somewhere else). */
    public static boolean standsOver(ServerLevel level, BlockPos pos) {
        return dev.awaken.terrain.hero.HeroWorldgen.boxOver(level, pos);
    }
}
