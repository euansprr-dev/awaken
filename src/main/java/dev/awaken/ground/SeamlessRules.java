package dev.awaken.ground;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Set;

/**
 * Where the ground's seams are (docs/terrain-immersion/PLAN.md, P0): which sides of a grass block wrap green down a
 * one-block riser, and which edges of a path or a patch of dirt have grass creeping over them. Pure rules on a
 * {@link BlockGetter}, shared by the client's models and the tests; nothing here touches the renderer.
 *
 * <p>Sides and corners are bit masks in the order north, east, south, west (corners: north-east, south-east, south-west,
 * north-west).
 */
public final class SeamlessRules {
    private SeamlessRules() {}

    public static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Corner i lies between {@code SIDES[i]} and {@code SIDES[(i + 1) % 4]}. */
    public static final int[][] CORNER_STEP = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};

    /** Our own bottom slabs of ground (empty until the soft steps): a riser that ends on one is half a block, so it wraps. */
    public static final TagKey<Block> SOFT_SLABS = TagKey.create(Registries.BLOCK, Awaken.id("ground/soft_slabs"));

    /** The blocks whose sides wrap: the ground that carries a green or fringed top. */
    private static final Set<Block> WRAPPERS = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);
    /** The blocks grass creeps over, from a grass neighbour at the same height. */
    private static final Set<Block> CREEPED = Set.of(Blocks.DIRT_PATH, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.GRAVEL,
            Blocks.SAND, Blocks.RED_SAND);

    public static boolean wrapperBlock(Block b) {
        return WRAPPERS.contains(b);
    }

    public static boolean creepedBlock(Block b) {
        return CREEPED.contains(b);
    }

    /** A grass block with its top showing (snow on it, a model of its own). */
    public static boolean bareGrass(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) && !s.getValue(SnowyBlock.SNOWY);
    }

    /** The sides of {@code pos}'s block (a wrapper) that wrap: open beside it, with ground just under, or ending on a soft slab. */
    public static int wrapMask(BlockGetter level, BlockPos pos) {
        if (level.getBlockState(pos.above()).isSolidRender()) return 0;     // covered: not a lawn, its sides stay as they are
        int mask = 0;
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            Direction d = SIDES[i];
            n.setWithOffset(pos, d);
            BlockState beside = level.getBlockState(n);
            if (beside.isSolidRender()) continue;                     // the face is hidden anyway
            if (isSoftSlab(beside)) {
                mask |= 1 << i;
                continue;
            }
            n.move(Direction.DOWN);
            BlockState under = level.getBlockState(n);
            // a one-block riser: the ground beside, one lower, is solid. A taller riser is a cliff and keeps its dirt face.
            if (under.isFaceSturdy(level, n, Direction.UP)) mask |= 1 << i;
        }
        return mask;
    }

    /**
     * The edges of {@code pos}'s block (a creeped one) that have grass beside them at the same height, with the sky open
     * over both: bits 0-3 are sides, bits 4-7 corners where only the diagonal is grass (the sides' own creep covers the rest).
     */
    public static int creepMask(BlockGetter level, BlockPos pos) {
        if (level.getBlockState(pos.above()).isSolidRender()) return 0;      // no top showing
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        boolean[] side = new boolean[4];
        int mask = 0;
        for (int i = 0; i < 4; i++) {
            n.setWithOffset(pos, SIDES[i]);
            side[i] = grassBeside(level, n);
            if (side[i]) mask |= 1 << i;
        }
        for (int c = 0; c < 4; c++) {
            if (side[c] || side[(c + 1) % 4]) continue;
            n.set(pos.getX() + CORNER_STEP[c][0], pos.getY(), pos.getZ() + CORNER_STEP[c][1]);
            if (grassBeside(level, n)) mask |= 1 << (4 + c);
        }
        return mask;
    }

    private static boolean grassBeside(BlockGetter level, BlockPos at) {
        if (!bareGrass(level.getBlockState(at))) return false;
        return !level.getBlockState(at.above()).isSolidRender();
    }

    public static boolean isSoftSlab(BlockState s) {
        return s.is(SOFT_SLABS) && s.hasProperty(SlabBlock.TYPE) && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }
}
