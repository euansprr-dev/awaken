package dev.awaken.caves.skin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * The skin's shaped blocks (SPEC §42.4). Their models come from tools/art/specs/deep_skin.py; the shapes here follow them.
 */
public final class SkinBlocks {
    private SkinBlocks() {}

    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);

    /**
     * Broken rock heaped where a floor meets a wall, its high side to the wall ({@link #FACING} points at it). A half
     * step underfoot: low enough to walk over, so a riser with rubble at its foot is climbed in two.
     */
    public static final class Rubble extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE = Block.box(0.5, 0, 0.5, 15.5, 5, 15.5);

        public Rubble(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, VARIANT);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                         BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected boolean isPathfindable(BlockState state, PathComputationType type) {
            return type == PathComputationType.LAND;
        }
    }

    /** Two or three loose stones of the rock: no collision, broken by a touch, replaced by whatever is placed over them. */
    public static final class Pebbles extends VegetationBlock {
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 2, 14);

        public Pebbles(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(VARIANT);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.isFaceSturdy(level, pos, Direction.UP);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /** A formation's thickness along its length, tip to base (pointed dripstone's four, without its merged tip). */
    public enum Thickness implements StringRepresentable {
        TIP, FRUSTUM, MIDDLE, BASE;

        public static final EnumProperty<Thickness> PROPERTY = EnumProperty.create("thickness", Thickness.class);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The thickness of the piece {@code fromTip} blocks back from the tip of a formation {@code length} long. */
        public static Thickness along(int fromTip, int length) {
            if (fromTip == 0) return TIP;
            if (fromTip == length - 1 && length >= 3) return BASE;
            return fromTip == 1 ? FRUSTUM : MIDDLE;
        }
    }

    /**
     * A stalagmite ({@code up}) or stalactite ({@code down}) of the rock: a tapered column of a few boxes, held up by
     * the rock (or the next piece) behind it; with nothing to hold it, it breaks. Decoration only: it doesn't grow, fall
     * or fill cauldrons (vanilla's dripstone keeps all that).
     */
    public static final class Formation extends Block {
        public static final EnumProperty<Direction> DIRECTION = BlockStateProperties.VERTICAL_DIRECTION;
        private static final VoxelShape TIP = Block.column(3, 0, 13), FRUSTUM = Block.column(6, 0, 16),
                MIDDLE = Block.column(8, 0, 16), BASE = Block.column(12, 0, 16);
        private static final VoxelShape TIP_DOWN = Block.column(3, 3, 16);

        public Formation(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(DIRECTION, Direction.UP).setValue(Thickness.PROPERTY, Thickness.TIP));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(DIRECTION, Thickness.PROPERTY);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return switch (state.getValue(Thickness.PROPERTY)) {
                case TIP -> state.getValue(DIRECTION) == Direction.UP ? TIP : TIP_DOWN;
                case FRUSTUM -> FRUSTUM;
                case MIDDLE -> MIDDLE;
                case BASE -> BASE;
            };
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            Direction toward = state.getValue(DIRECTION).getOpposite();      // the side it grows from
            BlockPos behind = pos.relative(toward);
            BlockState b = level.getBlockState(behind);
            return b.is(this) && b.getValue(DIRECTION) == state.getValue(DIRECTION) || b.isFaceSturdy(level, behind, toward.getOpposite());
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                         BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            return dir == state.getValue(DIRECTION).getOpposite() && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        protected boolean isPathfindable(BlockState state, PathComputationType type) {
            return false;
        }

        @Override
        public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, RandomSource random) {
            // a stalactite's tip drips now and then (SPEC §42.4): the drop forms, falls and splashes where it lands
            if (state.getValue(DIRECTION) == Direction.DOWN && state.getValue(Thickness.PROPERTY) == Thickness.TIP && random.nextInt(9) == 0) {
                Drips.drip(level, pos, random);
            }
        }
    }

    /** The Rootways' glowcaps (SPEC §42.7): a little cluster of teal-capped mushrooms on the floor, giving a faint light. */
    public static final class Glowcap extends VegetationBlock {
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 7, 13);

        public Glowcap(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(VARIANT);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.isFaceSturdy(level, pos, Direction.UP);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /**
     * The Rootways' root curtains (SPEC §42.7): old roots hanging through the roof from the land above, a strand or a
     * few blocks long, its last block the frayed tip. Held by the rock over it (or the root above); with nothing to hold
     * it, it drops away.
     */
    public static final class RootCurtain extends Block {
        public static final BooleanProperty TIP = BooleanProperty.create("tip");
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

        public RootCurtain(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(TIP, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(TIP);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            BlockState above = level.getBlockState(pos.above());
            return above.is(this) || above.isFaceSturdy(level, pos.above(), Direction.DOWN);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                         BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            if (dir == Direction.UP && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
            return dir == Direction.DOWN ? state.setValue(TIP, !neighbour.is(this)) : state;
        }
    }

    /** A skin of water on the floor under the drips: no collision, tinted by the cave's water colour. */
    public static final class Puddle extends Block {
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

        public Puddle(Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                         BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }
    }

    /**
     * A low drift on the floor (SPEC §42.8): the Pale Halls' rimstone, the Drowned Galleries' silt banks, the Black Vaults'
     * ash. A few pixels high, walked over like a carpet that's there; held by the floor under it.
     */
    public static final class Drift extends Block {
        private final VoxelShape shape;

        public Drift(Properties p, double height) {
            super(p);
            shape = Block.box(0, 0, 0, 16, height, 16);
            registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(VARIANT);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                         BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        protected boolean isPathfindable(BlockState state, PathComputationType type) {
            return type == PathComputationType.LAND;
        }
    }

    /** The Drowned Galleries' driftwood (SPEC §42.8): a bleached branch lying on the floor along its axis, a low step. */
    public static final class Driftwood extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape X = Block.box(0, 0, 4, 16, 6, 12), Z = Block.box(4, 0, 0, 12, 6, 16);

        public Driftwood(Properties p) {
            super(p);
            registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(AXIS) == Direction.Axis.X ? X : Z;
        }

        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                         BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return switch (rotation) {
                case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
                default -> state;
            };
        }

        @Override
        protected boolean isPathfindable(BlockState state, PathComputationType type) {
            return type == PathComputationType.LAND;
        }
    }

    /** The Drowned Galleries' pale cave weed (SPEC §42.8): seagrass's habits (it needs still water over a floor), no growing tall. */
    public static final class CaveWeed extends SeagrassBlock {
        public CaveWeed(Properties p) {
            super(p);
        }

        @Override
        public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
            return false;
        }

        @Override
        public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
            return false;
        }
    }
}
