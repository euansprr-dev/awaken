package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The ore showing through the rock round a lode's heart (SPEC §42.11): a thin vein in the ore's colour on the face of a neighbouring
 * block, joined edge to edge back to the heart and thinning as it goes. It has no shape at all: a pick aims and strikes the rock
 * through it, so nothing about mining changes, and it goes when the rock behind it does. {@link #FACING} points at the rock; the
 * four connections are the edges the vein leaves by, as seen looking at that face ({@link #side}).
 */
public final class LodeTraceBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN,
            LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right");
    /** 0 beside the heart, thick and lit; 1 further out, thin and broken. */
    public static final IntegerProperty REACH = IntegerProperty.create("reach", 0, 1);
    public static final EnumProperty<Ore> ORE = EnumProperty.create("ore", Ore.class);
    /** The connections in {@link #side}'s order. */
    private static final BooleanProperty[] EDGES = {UP, DOWN, LEFT, RIGHT};

    public LodeTraceBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(UP, false).setValue(DOWN, false)
                .setValue(LEFT, false).setValue(RIGHT, false).setValue(REACH, 0).setValue(ORE, Ore.IRON));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, UP, DOWN, LEFT, RIGHT, REACH, ORE);
    }

    /** The world direction of an edge (0 up, 1 down, 2 left, 3 right) on a face toward {@code facing}: the way the models turn. */
    public static Direction side(Direction facing, int edge) {
        return switch (facing) {
            case DOWN -> new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}[edge];
            case UP -> new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST}[edge];
            default -> switch (edge) {
                case 0 -> Direction.UP;
                case 1 -> Direction.DOWN;
                case 2 -> facing.getCounterClockWise();
                default -> facing.getClockWise();
            };
        };
    }

    /** The state with a mask of connections (bit n is {@link #side}'s edge n). */
    public BlockState with(Direction facing, int mask, int reach, Ore ore) {
        BlockState s = defaultBlockState().setValue(FACING, facing).setValue(REACH, reach).setValue(ORE, ore);
        for (int e = 0; e < 4; e++) s = s.setValue(EDGES[e], (mask >> e & 1) != 0);
        return s;
    }

    /** Whether the vein leaves this block by that edge. */
    public static boolean joined(BlockState s, int edge) {
        return s.getValue(EDGES[edge]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction f = state.getValue(FACING);
        return level.getBlockState(pos.relative(f)).isFaceSturdy(level, pos.relative(f), f.getOpposite());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return dir == state.getValue(FACING) && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }
}
