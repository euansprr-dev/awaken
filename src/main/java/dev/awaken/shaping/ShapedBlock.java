package dev.awaken.shaping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A block the Mallet has cut (SPEC §52.2). The state holds the cut, which way it faces and the corner it turns; the block
 * entity holds what it really is (the base: its hardness, its tool, its drops) and what it looks like. It never has an
 * item of its own.
 */
public final class ShapedBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** The base's own light: light works from the state alone, and the state is read off the main thread. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 15);
    /**
     * The corner an edge cut turns, a stair's left and right, inner and outer (§52.2, {@link Corners}); always straight for
     * the cuts with no front. It comes from the neighbours, so a block saved before corners loads straight and turns its
     * corner the first time a neighbour changes.
     */
    public static final EnumProperty<StairsShape> CORNER = EnumProperty.create("corner", StairsShape.class);

    public ShapedBlock(Properties props) {
        super(props);
        registerDefaultState(this.stateDefinition.any().setValue(SHAPE, Shape.FULL).setValue(FACING, Direction.SOUTH)
                .setValue(WATERLOGGED, false).setValue(GLOW, 0).setValue(CORNER, StairsShape.STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(SHAPE, FACING, WATERLOGGED, GLOW, CORNER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShapedBlockEntity(pos, state);
    }

    private static BlockState base(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ShapedBlockEntity be ? be.base() : Blocks.STONE.defaultBlockState();
    }

    // ---- shape ---------------------------------------------------------------------------------------------------------------

    private static VoxelShape voxel(BlockState state) {
        return state.getValue(SHAPE).voxel(state.getValue(FACING), state.getValue(CORNER));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return voxel(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return voxel(state);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return voxel(state);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A mirror turns a left corner into a right one. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        StairsShape c = state.getValue(CORNER);
        StairsShape flipped = switch (c) {
            case OUTER_LEFT -> StairsShape.OUTER_RIGHT;
            case OUTER_RIGHT -> StairsShape.OUTER_LEFT;
            case INNER_LEFT -> StairsShape.INNER_RIGHT;
            case INNER_RIGHT -> StairsShape.INNER_LEFT;
            case STRAIGHT -> StairsShape.STRAIGHT;
        };
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(CORNER, flipped);
    }

    // ---- it is still what it was -------------------------------------------------------------------------------------------

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return base(level, pos).getDestroyProgress(player, level, pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return base(level, pos).canHarvestBlock(level, pos, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ShapedBlockEntity be) return be.base().getDrops(params);
        return List.of();
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return base(level, pos).getExplosionResistance(level, pos, explosion);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return new ItemStack(base(level, pos).getBlock());
    }

    /** It sounds like what it looks like. */
    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        if (level.getBlockEntity(pos) instanceof ShapedBlockEntity be && be.look() != null) return be.look().getSoundType();
        return super.getSoundType(state, level, pos, entity);
    }

    // ---- water: a cut block can stand in water, a whole one can't ------------------------------------------------------------

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
        return state.getValue(SHAPE) != Shape.FULL && SimpleWaterloggedBlock.super.canPlaceLiquid(user, level, pos, state, type);
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return state.getValue(SHAPE) != Shape.FULL && SimpleWaterloggedBlock.super.placeLiquid(level, pos, state, fluidState);
    }

    /** A neighbour alongside changed: the corner is worked out again, as a stair's is. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (direction.getAxis().isHorizontal()) {
            Shape shape = state.getValue(SHAPE);
            state = state.setValue(CORNER, Corners.corner(shape, state.getValue(FACING), level, pos));
            // a notch or an under can stand where a real stair would: settle the hand-off between stairs and cuts here
            if (shape == Shape.NOTCH || shape == Shape.UNDER) ticks.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Corners.settle(level, pos, null);
    }
}
