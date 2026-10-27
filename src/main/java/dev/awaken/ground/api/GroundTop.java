package dev.awaken.ground.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a body really stands over a column (SPEC §31, P1). A heightmap counts a slab as a whole block, so code that puts
 * a thing "on the surface" by the heightmap alone floats half a block over a soft step. Use this for anything placed on the
 * ground after world generation (camps, anomalies, pieces, gates).
 */
public final class GroundTop {
    private GroundTop() {}

    /** The y of the top of the highest block that blocks motion at (x, z), by its real collision shape (a bottom slab is half a block). */
    public static double standingY(LevelReader level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos p = new BlockPos(x, top, z);
        BlockState s = level.getBlockState(p);
        VoxelShape shape = s.getCollisionShape(level, p);
        return shape.isEmpty() ? top : top + shape.max(Direction.Axis.Y);
    }
}
