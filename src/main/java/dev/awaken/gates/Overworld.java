package dev.awaken.gates;

import dev.awaken.gates.block.GateBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The surface gate: a dark frame (posts and lintel) around a 3x5 animated core, on a small platform, at the gate's
 * position. {@code pos} is the ground block under the centre of the core; {@code axis} 0 means the gate faces ±Z.
 */
public final class Overworld {
    private Overworld() {}

    public record Placed(BlockPos pos, BlockState state) {}

    /** Every block of the structure (platform, frame, core). */
    public static List<Placed> blocks(BlockPos pos, int axis, int rank, boolean broken) {
        List<Placed> out = new ArrayList<>();
        BlockState frame = (broken ? GatesRegistry.FRAME_CRACKED : GatesRegistry.FRAME).get().defaultBlockState();
        BlockState core = GatesRegistry.CORE.get().defaultBlockState().setValue(GateBlocks.Core.RANK, Math.max(0, Math.min(6, rank)))
                .setValue(GateBlocks.Core.BROKEN, broken);
        for (int a = -3; a <= 3; a++) {
            for (int b = -2; b <= 2; b++) {
                if (Math.abs(a) == 3 && Math.abs(b) == 2) continue;
                out.add(new Placed(at(pos, axis, a, 0, b), frame));
            }
        }
        for (int y = 1; y <= 5; y++) {
            out.add(new Placed(at(pos, axis, -2, y, 0), frame));
            out.add(new Placed(at(pos, axis, 2, y, 0), frame));
            for (int a = -1; a <= 1; a++) out.add(new Placed(at(pos, axis, a, y, 0), core));
        }
        for (int a = -2; a <= 2; a++) out.add(new Placed(at(pos, axis, a, 6, 0), frame));
        out.add(new Placed(at(pos, axis, 0, 7, 0), frame));
        return out;
    }

    /** a = along the gate's face, b = through it. */
    static BlockPos at(BlockPos pos, int axis, int a, int y, int b) {
        return axis == 0 ? pos.offset(a, y, b) : pos.offset(b, y, a);
    }

    /** Clears space and places the structure (the chunk must be loaded). */
    public static void place(ServerLevel level, GateRecord g, boolean broken) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        // clear a little air in front of and behind the gate so it is walkable
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                for (int y = 1; y <= 7; y++) {
                    BlockPos p = at(g.pos, g.axis, a, y, b);
                    if (!level.getBlockState(p).isAir() && level.getBlockState(p).getDestroySpeed(level, p) >= 0) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
                    }
                }
            }
        }
        for (Placed p : blocks(g.pos, g.axis, g.plan.rank().ordinal(), broken)) level.setBlock(p.pos(), p.state(), flags);
    }

    /** Removes the structure; leaves a scorched patch where it stood. */
    public static void remove(ServerLevel level, GateRecord g) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (Placed p : blocks(g.pos, g.axis, g.plan.rank().ordinal(), false)) {
            BlockState here = level.getBlockState(p.pos());
            if (!here.is(GatesRegistry.FRAME.get()) && !here.is(GatesRegistry.FRAME_CRACKED.get()) && !here.is(GatesRegistry.CORE.get())) continue;
            BlockState to = p.pos().getY() == g.pos.getY() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(p.pos(), to, flags);
        }
    }

    /** Ground under a column (top solid, non-liquid block). */
    public static int groundY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * Nobody built here: no block entities (chests, furnaces, beds' owners), no crafted building blocks, and not
     * inside anyone's Hearth. A gate never carves into a base or a village.
     */
    public static boolean untouched(ServerLevel level, BlockPos pos, int axis) {
        if (dev.awaken.core.api.HearthHooks.nearest(level, pos, 64).isPresent()) return false;
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                for (int y = 0; y <= 7; y++) {
                    BlockPos p = at(pos, axis, a, y, b);
                    if (level.getBlockEntity(p) != null) return false;
                    BlockState s = level.getBlockState(p);
                    if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS) || s.is(BlockTags.DOORS)
                            || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES)
                            || s.is(BlockTags.WALLS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.WOOL_CARPETS)
                            || s.is(net.neoforged.neoforge.common.Tags.BlockItems.GLASS_BLOCKS.block()) || s.is(net.minecraft.world.level.block.Blocks.BRICKS)
                            || s.is(net.minecraft.world.level.block.Blocks.TORCH) || s.is(net.minecraft.world.level.block.Blocks.WALL_TORCH)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The nearest spot within 24 blocks of {@code center} (same axis) that is good dry ground nobody built on. */
    public static java.util.Optional<BlockPos> clearSpotNear(ServerLevel level, BlockPos center, int axis) {
        for (int r = 0; r <= 24; r += 8) {
            for (int i = 0; i < (r == 0 ? 1 : 8); i++) {
                double ang = Math.PI / 4 * i;
                int x = center.getX() + (int) Math.round(Math.cos(ang) * r), z = center.getZ() + (int) Math.round(Math.sin(ang) * r);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos ground = new BlockPos(x, groundY(level, x, z), z);
                if (goodGround(level, ground) && untouched(level, ground, axis)) return java.util.Optional.of(ground);
            }
        }
        return java.util.Optional.empty();
    }

    /** A good spot: dry, solid, not a tree top. */
    public static boolean goodGround(ServerLevel level, BlockPos ground) {
        BlockState s = level.getBlockState(ground);
        if (!s.getFluidState().isEmpty() || s.isAir()) return false;
        if (!level.getBlockState(ground.above()).getFluidState().isEmpty()) return false;
        return s.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP);
    }
}
