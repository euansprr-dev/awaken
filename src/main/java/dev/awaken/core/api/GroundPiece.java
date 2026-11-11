package dev.awaken.core.api;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * A structure piece whose box is far bigger than the land it really changes (SPEC §12, §28.9 "Ground others stand on"): a
 * road leg's box is its whole raster, a settlement's its whole square. A thing that must keep off land a structure uses
 * (a secret, SPEC §21.11) asks such a piece instead of testing its box. The village module's road and settlement pieces
 * are ones.
 */
public interface GroundPiece {
    /** Whether this piece paints, clears or grades any land within {@code r} of (x, z). Worldgen threads, from the region's own view. */
    boolean usesGround(WorldGenLevel level, ChunkGenerator generator, int x, int z, int r);
}
