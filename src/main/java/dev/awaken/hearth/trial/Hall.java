package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A Warden's hall (SPEC §46.4, §46.6): a pure layout, built by {@link TrialChunkGenerator} in every hall cell at its own
 * spot, and put back as built after each run. Local x runs across it, z from its far end (north) to its door (back), dy
 * up from the trials floor.
 */
interface Hall {
    /** Its centre in a cell, from the cell's corner. */
    int atX();

    int atZ();

    /** Its extent: x from -wall to wall, z from north to back, dy from low to roof. */
    int wall();

    int north();

    int back();

    int low();

    int roof();

    /** The band a fight changes, put back after every run (the rest is put back only where it was made before the hall). */
    int bandLo();

    int bandHi();

    /** The block at a local spot, or null for air. */
    @Nullable BlockState block(int x, int dy, int z);

    /** Whether it stands as built (a few telling spots). */
    boolean standing(ServerLevel level, BlockPos centre);

    default boolean spans(int x, int z) {
        return Math.abs(x) <= wall() && z >= north() && z <= back();
    }

    default BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(atX(), 0, atZ());
    }

    default AABB box(BlockPos c) {
        return new AABB(c.getX() - wall(), c.getY() + low(), c.getZ() + north(), c.getX() + wall() + 1, c.getY() + roof(), c.getZ() + back() + 1);
    }

    /** Whether a spot holds what the layout says. */
    default boolean is(ServerLevel level, BlockPos c, int x, int dy, int z) {
        BlockState want = block(x, dy, z);
        return level.getBlockState(c.offset(x, dy, z)) == (want == null ? Blocks.AIR.defaultBlockState() : want);
    }

    /** Putting a hall back as built, a few rows of it a tick: the whole of it, or the band a fight changes. */
    final class Repair {
        final Hall hall;
        final ServerLevel level;
        final BlockPos centre;
        final int lo, hi;
        final boolean whole;
        private int z;
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

        Repair(Hall hall, ServerLevel level, BlockPos centre, boolean whole) {
            this.hall = hall;
            this.level = level;
            this.centre = centre;
            this.whole = whole;
            this.lo = whole ? hall.low() : hall.bandLo();
            this.hi = whole ? hall.roof() : hall.bandHi();
            this.z = hall.north();
        }

        /** Up to {@code rows} rows (north to south); true once the hall is done. */
        boolean step(int rows) {
            int w = hall.wall();
            for (int n = 0; n < rows && z <= hall.back(); n++, z++) {
                for (int x = -w; x <= w; x++) {
                    for (int dy = lo; dy <= hi; dy++) {
                        BlockState want = hall.block(x, dy, z);
                        if (want == null) want = Blocks.AIR.defaultBlockState();
                        at.set(centre.getX() + x, centre.getY() + dy, centre.getZ() + z);
                        if (level.getBlockState(at) != want) level.setBlock(at, want, Block.UPDATE_CLIENTS);
                    }
                }
            }
            return z > hall.back();
        }

        boolean done() {
            return z > hall.back();
        }

        /** All of it now (it waits for nobody). */
        void finish() {
            while (!step(64)) { /* the whole band at once */ }
        }
    }
}
