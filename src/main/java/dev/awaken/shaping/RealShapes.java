package dev.awaken.shaping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Between real blocks and cut ones (SPEC §52.3). Real slabs and stairs read as cuts, and a cut writes back as the real
 * block whenever one exists, so a build stays ordinary blocks wherever it can. Nothing changes what a block is worth: a
 * slab knocked whole still drops a slab. A notch or an under is a real stair only where the stair would turn the same
 * corner by itself ({@link Corners}).
 */
public final class RealShapes {
    private RealShapes() {}

    /**
     * A block in the Mallet's terms: what it is, what it wears, how it's cut, and which way the cut faces. Never the corner,
     * which comes from the neighbours; and a real slab or stair is its base as placed, dry and straight, so a stair standing
     * as a cut of itself reads the same as the stair.
     */
    public record Parts(BlockState base, BlockState look, Shape shape, Direction facing) {}

    private record Family(Block full, @Nullable Block slab, @Nullable Block stairs) {}

    private static volatile @Nullable Map<Block, Family> families;

    private static Map<Block, Family> families() {
        Map<Block, Family> m = families;
        if (m != null) return m;
        Map<Block, Family> built = new HashMap<>();
        BlockFamilies.getAllFamilies().forEach(f -> {
            Family fam = new Family(f.getBaseBlock(), f.get(BlockFamily.Variant.SLAB), f.get(BlockFamily.Variant.STAIRS));
            if (fam.slab() == null && fam.stairs() == null) return;
            built.putIfAbsent(fam.full(), fam);
            if (fam.slab() != null) built.putIfAbsent(fam.slab(), fam);
            if (fam.stairs() != null) built.putIfAbsent(fam.stairs(), fam);
        });
        families = built;
        return built;
    }

    private static boolean whole(BlockState s) {
        return s.isSolidRender() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Why the Mallet won't touch this block (a language key), or null if it will. */
    public static @Nullable String refusal(BlockState s) {
        if (s.getBlock() instanceof ShapedBlock) return null;
        if (s.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0F) return "awaken.shaping.refuse.fixed";
        if (s.hasBlockEntity()) return "awaken.shaping.refuse.inside";
        if (s.is(Tags.BlockItems.ORES.block())) return "awaken.shaping.refuse.ore";
        Block b = s.getBlock();
        if (b instanceof SlabBlock || b instanceof StairBlock) return families().containsKey(b) ? null : "awaken.shaping.refuse.whole";
        return whole(s) ? null : "awaken.shaping.refuse.whole";
    }

    /** This block in the Mallet's terms, or null if it won't take the Mallet. */
    public static @Nullable Parts read(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ShapedBlock) {
            if (level.getBlockEntity(pos) instanceof ShapedBlockEntity be && be.look() != null) {
                return new Parts(be.base(), be.look(), state.getValue(ShapedBlock.SHAPE), state.getValue(ShapedBlock.FACING));
            }
            return null;
        }
        return real(state);
    }

    private static @Nullable Parts real(BlockState s) {
        if (refusal(s) != null) return null;
        Block b = s.getBlock();
        if (b instanceof SlabBlock) {
            BlockState look = families().get(b).full().defaultBlockState();
            BlockState dry = s.setValue(SlabBlock.WATERLOGGED, false);
            return switch (s.getValue(SlabBlock.TYPE)) {
                case BOTTOM -> new Parts(dry, look, Shape.HALF_BOTTOM, Direction.SOUTH);
                case TOP -> new Parts(dry, look, Shape.HALF_TOP, Direction.SOUTH);
                case DOUBLE -> new Parts(dry, look, Shape.FULL, Direction.SOUTH);
            };
        }
        if (b instanceof StairBlock) {
            BlockState look = families().get(b).full().defaultBlockState();
            BlockState plain = s.setValue(StairBlock.WATERLOGGED, false).setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);
            Direction front = s.getValue(StairBlock.FACING).getOpposite();
            return new Parts(plain, look, s.getValue(StairBlock.HALF) == Half.BOTTOM ? Shape.NOTCH : Shape.UNDER, front);
        }
        return new Parts(s, s, Shape.FULL, Direction.SOUTH);
    }

    /**
     * Puts these parts in the world: the real block if there is one, otherwise a cut block; then the stairs around it
     * settle their corners with the cuts ({@link Corners#settle}), only where this player may use the Mallet.
     */
    public static void write(ServerLevel level, BlockPos pos, Parts p, ServerPlayer actor) {
        place(level, pos, p);
        Corners.settle(level, pos, actor);
    }

    /** Puts these parts in the world, turning the corner its neighbours ask for; nothing around it is touched. */
    static void place(ServerLevel level, BlockPos pos, Parts p) {
        boolean water = p.shape() != Shape.FULL && level.getFluidState(pos).is(Fluids.WATER);
        BlockState real = realForm(p);
        // a stair only where it would turn this corner by itself: a stair can't see the cuts beside it
        if (real != null && real.getBlock() instanceof StairBlock) {
            real = Corners.realFits(real, level, pos) ? real.setValue(StairBlock.SHAPE, Corners.stairs(real, level, pos)) : null;
        }
        if (real != null) {
            if (real.hasProperty(BlockStateProperties.WATERLOGGED)) real = real.setValue(BlockStateProperties.WATERLOGGED, water);
            level.setBlock(pos, real, Block.UPDATE_ALL);
            return;
        }
        BlockState s = ShapingContent.SHAPED.get().defaultBlockState().setValue(ShapedBlock.SHAPE, p.shape())
                .setValue(ShapedBlock.FACING, p.facing()).setValue(ShapedBlock.WATERLOGGED, water)
                .setValue(ShapedBlock.GLOW, p.base().getLightEmission())
                .setValue(ShapedBlock.CORNER, Corners.corner(p.shape(), p.facing(), level, pos));
        level.setBlock(pos, s, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ShapedBlockEntity be) be.set(p.base(), p.look());
    }

    /** The real block these parts are, if there is one. */
    static @Nullable BlockState realForm(Parts p) {
        Parts own = real(p.base());
        if (own != null && own.shape() == p.shape() && own.look().is(p.look().getBlock())
                && (!ShapingRules.faces(p.shape()) || own.facing() == p.facing())) return p.base();
        if (!whole(p.base()) || !p.look().is(p.base().getBlock())) return null;
        Family fam = families().get(p.base().getBlock());
        if (fam == null) return null;
        return switch (p.shape()) {
            case HALF_BOTTOM -> slab(fam, SlabType.BOTTOM);
            case HALF_TOP -> slab(fam, SlabType.TOP);
            case NOTCH -> stairs(fam, p.facing(), Half.BOTTOM);
            case UNDER -> stairs(fam, p.facing(), Half.TOP);
            default -> null;
        };
    }

    private static @Nullable BlockState slab(Family fam, SlabType type) {
        return fam.slab() == null ? null : fam.slab().defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static @Nullable BlockState stairs(Family fam, Direction front, Half half) {
        return fam.stairs() == null ? null : fam.stairs().defaultBlockState().setValue(StairBlock.FACING, front.getOpposite()).setValue(StairBlock.HALF, half);
    }
}
