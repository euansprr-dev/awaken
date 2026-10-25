package dev.awaken.city.kit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;
import java.util.Map;

/**
 * The Carillon kit's block classes (look bible §8). Wall pieces face away from the wall they hang on, like a ladder:
 * a flower box on a north wall faces north. Collision follows the climbing grid (bible §3.1): a hold's collision reaches
 * its block's top and its face sits at the outer edge of the cell, so every lip of a window stack is caught the same
 * way; decoration that is not a hold has no collision at all.
 */
public final class KitBlocks {
    private KitBlocks() {}

    /** A lower-case enum property value. */
    interface Named extends StringRepresentable {
        @Override
        default String getSerializedName() {
            return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A window box under a window (or an iron balconette, or a stone sill box): a hold in the grid's odd rows. The
     * model is a planter at the bottom of the cell with flowers above it; the collision is the whole cell, so its top is
     * the block's top and its face the cell's outer edge (a lip exactly like the courses above and below it).
     */
    public static final class FlowerBox extends HorizontalDirectionalBlock {
        public enum Style implements Named { WOOD, IRON, STONE }

        public enum Bloom implements Named { NONE, RED, YELLOW, BLUE, PINK, WHITE, HERBS }

        public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
        public static final EnumProperty<Bloom> BLOOM = EnumProperty.create("bloom", Bloom.class);
        private static final Map<Direction, VoxelShape> OUTLINE = Shapes.rotateHorizontal(Block.box(0, 0, 3, 16, 14, 16));

        public FlowerBox(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STYLE, Style.WOOD).setValue(BLOOM, Bloom.RED));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, STYLE, BLOOM);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return OUTLINE.get(s.getValue(FACING));
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.block();
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** A painted shutter that doesn't open, beside a window: the cue for "a window stack here". No collision. */
    public static final class Shutter extends HorizontalDirectionalBlock {
        public enum Colour implements Named { BLUE, SAGE, OXBLOOD, NATURAL, GREEN, CREAM }

        public static final EnumProperty<Colour> COLOUR = EnumProperty.create("colour", Colour.class);
        public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        private static final Map<Direction, VoxelShape> OUTLINE = Shapes.rotateHorizontal(Block.boxZ(16, 14, 16));

        public Shutter(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOUR, Colour.BLUE)
                    .setValue(HINGE, DoorHingeSide.LEFT).setValue(HALF, DoubleBlockHalf.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, COLOUR, HINGE, HALF);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return OUTLINE.get(s.getValue(FACING));
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /**
     * A lead downpipe on a wall: climbable (the kit adds it to {@code #minecraft:climbable}). The shoe kicks out at the
     * street, the head is the hopper under the eave. Collision is the pipe itself, so a climber stands in its cell.
     */
    public static final class Drainpipe extends HorizontalDirectionalBlock {
        public enum Part implements Named { PIPE, HEAD, SHOE }

        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        private static final Map<Direction, VoxelShape> PIPE = Shapes.rotateHorizontal(Block.box(5, 0, 10, 11, 16, 16));
        private static final Map<Direction, VoxelShape> HEAD = Shapes.rotateHorizontal(Block.box(3, 0, 8, 13, 16, 16));

        public Drainpipe(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.PIPE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PART);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return (s.getValue(PART) == Part.HEAD ? HEAD : PIPE).get(s.getValue(FACING));
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** A timber lattice on a wall, with or without ivy grown through it: climbable (no vines, which the sweep takes). */
    public static final class Trellis extends HorizontalDirectionalBlock {
        public static final BooleanProperty IVY = BooleanProperty.create("ivy");
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.boxZ(16, 14, 16));

        public Trellis(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(IVY, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, IVY);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE.get(s.getValue(FACING));
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /**
     * A window of a closed house: frame, glass and a glimpse of a room, in its district's style, as one opaque block in
     * the wall (the core behind stays solid). Lit, the room glows (emissive, no light: the night is lit by windows, not
     * by light sources). Two-storey windows are a lower and an upper half; dormers and attics are single.
     */
    public static final class LitWindow extends Block {
        public enum Style implements Named { LEADED, SASH, ARCHED, COTTAGE, WAREHOUSE, BARRED, WORKS, SHOP, DORMER, ROUND }

        public enum Half implements Named { LOWER, UPPER, SINGLE }

        public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
        public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public LitWindow(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(STYLE, Style.LEADED).setValue(HALF, Half.SINGLE).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(STYLE, HALF, LIT);
        }
    }

    /** A warehouse's two-leaf loading door (one quarter per block), set in the wall of the Quay's middle bay. */
    public static final class LoadingDoor extends HorizontalDirectionalBlock {
        public enum Part implements Named { LOWER_LEFT, LOWER_RIGHT, UPPER_LEFT, UPPER_RIGHT }

        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

        public LoadingDoor(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOWER_LEFT));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PART);
        }
    }

    /** Hemp rope hanging straight down (a hoist's fall): climbable, nothing to bump into. */
    public static final class Rope extends Block {
        private static final VoxelShape SHAPE = Block.column(4, 0, 16);

        public Rope(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** A warehouse's hoist beam: a timber out of the gable's top with a pulley at its end. A bar to hang and swing on. */
    public static final class HoistBeam extends HorizontalDirectionalBlock {
        public static final BooleanProperty PULLEY = BooleanProperty.create("pulley");
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(5, 8, 0, 11, 14, 16));

        public HoistBeam(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PULLEY, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PULLEY);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE.get(s.getValue(FACING));
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /**
     * The capped top course of a chimney stack (an oversailing band, soot at the flue). The pot on it stays a vanilla
     * flower pot: the citizens' chimney finder looks for a pot on bricks to puff smoke from (hence "bricks" in the id).
     */
    public static final class ChimneyBricks extends Block {
        public enum Material implements Named { BRICK, MUD, STONE }

        public static final EnumProperty<Material> MATERIAL = EnumProperty.create("material", Material.class);

        public ChimneyBricks(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(MATERIAL, Material.BRICK));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(MATERIAL);
        }
    }

    /**
     * Lime-washed plaster, the infill of the timber frames and the Market's pastel fronts. Its blockstate picks one of
     * several weighted textures per block (SPEC §16.0 decision 27), so a long wall never repeats.
     */
    public static final class Plaster extends Block {
        public enum Colour implements Named { WHITE, CREAM, OCHRE, ROSE, CLAY, MAUVE, OXBLOOD, SAGE }

        public static final EnumProperty<Colour> COLOUR = EnumProperty.create("colour", Colour.class);

        public Plaster(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(COLOUR, Colour.WHITE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(COLOUR);
        }
    }

    /** Hewn timber for the frames (posts, beams, beam ends): dark oak or a paler spruce, weighted variants per block. */
    public static final class Timber extends RotatedPillarBlock {
        public enum Tone implements Named { DARK, PALE }

        public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);

        public Timber(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y).setValue(TONE, Tone.DARK));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            super.createBlockStateDefinition(b);
            b.add(TONE);
        }
    }
}
