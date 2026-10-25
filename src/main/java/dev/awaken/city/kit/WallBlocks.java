package dev.awaken.city.kit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The wall kit (Milestone 4, the Walls step): what the ring wall, its towers and gatehouses are dressed in beyond plain
 * masonry. Arrow loops cut into the stone, ivy on the sheltered face, the gatehouses' portcullis, braziers on the
 * towers and the wall walk, drain spouts and the city's shields. Same rules as the rest of the kit: no block entities,
 * no loot; only the brazier is a light source (a few per chunk, inside the city's light budget). Wall pieces face away
 * from the wall they sit on; models are drawn for facing=north with the wall at z 16. Art: tools/art/specs/city_wall.py.
 */
public final class WallBlocks {
    private WallBlocks() {}

    /** The stone a loop is cut into, so it sits in its wall without a seam (the model uses that stone's own texture). */
    public enum Stone implements KitBlocks.Named { STONE_BRICKS, MOSSY, DEEPSLATE, CALCITE, BRICKS, TUFF }

    /**
     * An arrow loop: a full block of the wall's own stone with a narrow opening cut through it, dark behind. Two-block
     * loops stack a lower and an upper part. A solid cube for movement (a loop is never a hold).
     */
    public static final class ArrowLoop extends HorizontalDirectionalBlock {
        public enum Style implements KitBlocks.Named { SLIT, CROSS, OILLET, WINDOW }

        public enum Part implements KitBlocks.Named { SINGLE, LOWER, UPPER }

        public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
        public static final EnumProperty<Stone> STONE = EnumProperty.create("stone", Stone.class);
        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

        public ArrowLoop(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STYLE, Style.SLIT)
                    .setValue(STONE, Stone.STONE_BRICKS).setValue(PART, Part.SINGLE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, STYLE, STONE, PART);
        }
    }

    /**
     * Ivy grown flat over a wall: climbable (#minecraft:climbable, runner_climbs) like the trellis but with no frame.
     * Density 0 is a few runners, 2 a full mat; the tone varies the leaves (green, dark old growth, autumn).
     */
    public static final class WallIvy extends HorizontalDirectionalBlock {
        public enum Tone implements KitBlocks.Named { GREEN, DARK, AUTUMN }

        public static final IntegerProperty DENSITY = IntegerProperty.create("density", 0, 2);
        public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(0, 0, 14, 16, 16, 16));

        public WallIvy(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DENSITY, 1).setValue(TONE, Tone.GREEN));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, DENSITY, TONE);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE.get(s.getValue(FACING));
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

    /** The gatehouse's portcullis: an iron-shod oak grid on the gate's axis, spiked at its foot. A barrier where it hangs. */
    public static final class Portcullis extends Block {
        public enum Part implements KitBlocks.Named { TOP, MIDDLE, BOTTOM }

        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        private static final VoxelShape X = Block.box(0, 0, 7, 16, 16, 9), Z = Block.box(7, 0, 0, 9, 16, 16);

        public Portcullis(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(PART, Part.MIDDLE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS, PART);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(AXIS) == Direction.Axis.X ? X : Z;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /**
     * A brazier: an iron basket of coals with a living flame (emissive, animated) and the kit's one light source. On a
     * tripod on the floor, or in a bracket on a wall (facing away from it).
     */
    public static final class Brazier extends HorizontalDirectionalBlock {
        public enum Mount implements KitBlocks.Named { FLOOR, WALL }

        public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
        private static final VoxelShape FLOOR = Block.box(2, 0, 2, 14, 12, 14);
        private static final Map<Direction, VoxelShape> WALL = Shapes.rotateHorizontal(Block.box(3, 7, 4, 13, 14, 16));

        public Brazier(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.FLOOR));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, MOUNT);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(MOUNT) == Mount.FLOOR ? FLOOR : WALL.get(s.getValue(FACING));
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** A carved stone drain spout under the wall walk's parapet, thrown clear of the face (a grotesque at its mouth). */
    public static final class WallSpout extends HorizontalDirectionalBlock {
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(4, 3, 0, 12, 11, 16));

        public WallSpout(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
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

    /** A painted heater shield on a wall: a district's arms, the Association's, or the city's own bell. */
    public static final class WallShield extends HorizontalDirectionalBlock {
        public enum Arms implements KitBlocks.Named { CARILLON, BELLWATCH, QUAY, MARKET, TERRACE, WORKS, GARDEN, WALLRING, ASSOCIATION }

        public static final EnumProperty<Arms> ARMS = EnumProperty.create("arms", Arms.class);
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(1, 0, 14, 15, 16, 16));

        public WallShield(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ARMS, Arms.CARILLON));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, ARMS);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE.get(s.getValue(FACING));
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
}
