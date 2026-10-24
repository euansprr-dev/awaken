package dev.awaken.hearth.room;

import net.minecraft.core.BlockPos;

/**
 * Furniture inside a Hearth's radius that is not in a room, and why, from the scanner's own flood fill. {@code at} is
 * where to look: the cell that sees the sky, the gap the space leaks through, or the furniture itself (too big).
 */
public record Miss(Furniture furniture, BlockPos seed, Leak leak, BlockPos at) {
    public enum Leak {
        /** Open sky right above the furniture or the floor beside it: no roof. */
        SKY,
        /** A covered room with one column open to the sky inside it. */
        ROOF_HOLE,
        /** The covered space runs out through a gap in a wall (or into unloaded land). */
        OPENING,
        /** Closed, but bigger than a room can be. */
        TOO_BIG
    }

    /** True when {@code at} is a spot worth marking in the world (a gap or a hole), not just the furniture. */
    public boolean marksASpot() {
        return leak == Leak.OPENING || leak == Leak.ROOF_HOLE;
    }
}
