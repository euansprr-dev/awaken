package dev.awaken.city.kit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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

import java.util.Map;

/**
 * The street kit (Milestone 4 step B3a; look bible §7, §8): what the street-life layer is made of. Awnings, sign
 * brackets, lines and hay carts are the city's affordances (the runner_* tags), so their collision follows the climbing
 * grid's rules: a hold's top is where the hands go; cloth, signs and wares have none. No block entities, no light
 * sources (lanterns glow through emissive model layers, decision 26). Directional models are drawn facing north; a
 * wall piece faces away from the wall it hangs on (its north model has the wall at z 16).
 */
public final class StreetBlocks {
    private StreetBlocks() {}

    /** A facing block with one extra shape table (drawn for north). */
    abstract static class Facing extends HorizontalDirectionalBlock {
        Facing(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    // ---- awnings: the shopfront's canvas and the stall's canopy --------------------------------------------------------

    /**
     * Striped canvas at a shopfront's hood row (row 3) or over a stall. A hold where it meets the street (the hood's own
     * catch) and a soft landing: bounces like a bed and takes half the fall (bible §3.6, runner_soft).
     */
    public static final class Awning extends Facing {
        public enum Colour implements KitBlocks.Named { RED, BLUE, YELLOW, GREEN, ORANGE, PLUM }

        public enum Shape implements KitBlocks.Named { SLOPE, UPPER, LOWER, FLAT }

        public static final EnumProperty<Colour> COLOUR = EnumProperty.create("colour", Colour.class);
        public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
        private static final VoxelShape TOP = Block.box(0, 8, 0, 16, 16, 16), LOW = Block.box(0, 4, 0, 16, 12, 16);

        public Awning(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOUR, Colour.RED).setValue(SHAPE, Shape.SLOPE));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, COLOUR, SHAPE);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(SHAPE) == Shape.LOWER ? LOW : TOP;
        }
    }

    // ---- signs ---------------------------------------------------------------------------------------------------------

    /** A wrought-iron sign arm out over the street at row 5: a bar to catch and swing off (runner_swings). */
    public static final class SignBracket extends Facing {
        public enum Part implements KitBlocks.Named { ARM, END }

        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        private static final Map<Direction, VoxelShape> BAR = Shapes.rotateHorizontal(Block.box(6, 12, 0, 10, 15, 16));

        public SignBracket(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.ARM));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PART);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return BAR.get(s.getValue(FACING));
        }
    }

    /** A painted trade sign with an icon (no lettering, no block entity): hung under a bracket, or flat on a fascia. */
    public static final class Signboard extends Facing {
        public enum Trade implements KitBlocks.Named { BREAD, FISH, PRODUCE, CLOTH, POTTERY, CANDLES, FLOWERS, SPICE, TOOLS, CHEESE, TAVERN, BOOTS, BOOKS, KEYS }

        public enum Mount implements KitBlocks.Named { HANGING, FLAT }

        public static final EnumProperty<Trade> TRADE = EnumProperty.create("trade", Trade.class);
        public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
        private static final Map<Direction, VoxelShape> HANG = Shapes.rotateHorizontal(Block.box(7, 2, 2, 9, 16, 14));
        private static final Map<Direction, VoxelShape> FLAT = Shapes.rotateHorizontal(Block.box(0, 4, 15, 16, 12, 16));

        public Signboard(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TRADE, Trade.BREAD).setValue(MOUNT, Mount.HANGING));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, TRADE, MOUNT);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return (s.getValue(MOUNT) == Mount.FLAT ? FLAT : HANG).get(s.getValue(FACING));
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }
    }

    // ---- lines across the street ---------------------------------------------------------------------------------------

    /**
     * A rope across a lane at a course row (7, 9, 11...), bare or hung with laundry, bunting or lanterns (bible §3.7): a
     * thin bar with a collision top, like a chain, so a runner catches it and goes across hand over hand
     * (runner_lines); what hangs on it has no collision. {@code sag} droops the rope toward a line's middle.
     */
    public static final class HangLine extends Block {
        public enum Hang implements KitBlocks.Named { ROPE, LAUNDRY, BUNTING, LANTERNS }

        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final EnumProperty<Hang> HANG = EnumProperty.create("hang", Hang.class);
        public static final IntegerProperty SET = IntegerProperty.create("set", 0, 3);
        public static final IntegerProperty SAG = IntegerProperty.create("sag", 0, 2);
        private static final VoxelShape X = Block.box(0, 6.5, 6.5, 16, 9.5, 9.5), Z = Block.box(6.5, 6.5, 0, 9.5, 9.5, 16);

        public HangLine(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(HANG, Hang.ROPE).setValue(SET, 0).setValue(SAG, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS, HANG, SET, SAG);
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

    /** A timber pole a line ties to at the far kerb (bible: "plant a pole there"): climbable, nothing to bump into. */
    public static final class LinePole extends Block {
        public static final BooleanProperty TOP = BooleanProperty.create("top");
        private static final VoxelShape FULL = Block.column(4, 0, 16), CAP = Block.column(6, 0, 13);

        public LinePole(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(TOP, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(TOP);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(TOP) ? CAP : FULL;
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

    // ---- banners and flagpoles -----------------------------------------------------------------------------------------

    /** A long cloth banner (three blocks tall) in a district's colours: flat on a facade, or hung from a flagpole. */
    public static final class BannerCloth extends Facing {
        public enum Design implements KitBlocks.Named { BELLWATCH, QUAY, MARKET, TERRACE, WORKS, GARDEN, WALLRING, ASSOCIATION }

        public enum Part implements KitBlocks.Named { TOP, MIDDLE, BOTTOM }

        public enum Mount implements KitBlocks.Named { WALL, POLE }

        public static final EnumProperty<Design> DESIGN = EnumProperty.create("design", Design.class);
        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
        private static final Map<Direction, VoxelShape> WALL = Shapes.rotateHorizontal(Block.box(2, 0, 15, 14, 16, 16));
        private static final Map<Direction, VoxelShape> POLE = Shapes.rotateHorizontal(Block.box(7.5, 0, 2, 8.5, 16, 14));

        public BannerCloth(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DESIGN, Design.MARKET)
                    .setValue(PART, Part.MIDDLE).setValue(MOUNT, Mount.WALL));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, DESIGN, PART, MOUNT);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return (s.getValue(MOUNT) == Mount.POLE ? POLE : WALL).get(s.getValue(FACING));
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }
    }

    /** A flagpole out from an upper storey, angled up (bible §3.7): catch it and swing; a banner hangs from its tip. */
    public static final class Flagpole extends Facing {
        private static final Map<Direction, VoxelShape> BAR = Shapes.rotateHorizontal(Block.box(6, 8, 0, 10, 12, 16));

        public Flagpole(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return BAR.get(s.getValue(FACING));
        }
    }

    // ---- goods ---------------------------------------------------------------------------------------------------------

    /** A market crate showing what it holds: a step-up (bible §3.10). */
    public static final class Crate extends Block {
        public enum Content implements KitBlocks.Named { LID, APPLES, CABBAGES, FISH, STRAW, BOTTLES }

        public static final EnumProperty<Content> CONTENT = EnumProperty.create("content", Content.class);

        public Crate(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(CONTENT, Content.LID));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(CONTENT);
        }
    }

    /** One to three sacks of grain, flour or wool at a door. */
    public static final class Sack extends Block {
        public enum Fill implements KitBlocks.Named { GRAIN, FLOUR, WOOL }

        public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 3);
        public static final EnumProperty<Fill> FILL = EnumProperty.create("fill", Fill.class);
        private static final VoxelShape ONE = Block.box(3, 0, 3, 13, 10, 13), TWO = Block.box(1, 0, 2, 15, 10, 14), HEAP = Block.box(1, 0, 1, 15, 12, 15);

        public Sack(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(COUNT, 1).setValue(FILL, Fill.GRAIN));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(COUNT, FILL);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return switch (s.getValue(COUNT)) {
                case 1 -> ONE;
                case 2 -> TWO;
                default -> HEAP;
            };
        }
    }

    /** Goods laid out on a stall's counter (the kit's real wares, never vanilla stand-ins). No collision. */
    public static final class Wares extends Block {
        public enum Good implements KitBlocks.Named { BREAD, FISH, PRODUCE, CLOTH, POTTERY, CANDLES, FLOWERS, SPICE, TOOLS, CHEESE }

        public static final EnumProperty<Good> GOOD = EnumProperty.create("good", Good.class);
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 7, 15);

        public Wares(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(GOOD, Good.BREAD));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(GOOD);
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

    /** A stall's counter, facing its customers: open (the board down, wares on top) or shut (the board folded up). */
    public static final class StallCounter extends Facing {
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

        public StallCounter(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, OPEN);
        }
    }

    /** A cask (vanilla's barrel is a block entity, so not in the fabric: decision 26): upright, or lying along x or z. */
    public static final class Cask extends Block {
        public enum Orient implements KitBlocks.Named { UPRIGHT, X, Z }

        public static final EnumProperty<Orient> ORIENT = EnumProperty.create("orient", Orient.class);
        private static final VoxelShape UPRIGHT = Block.column(14, 0, 16);

        public Cask(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(ORIENT, Orient.UPRIGHT));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(ORIENT);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(ORIENT) == Orient.UPRIGHT ? UPRIGHT : Shapes.block();
        }
    }

    /**
     * A two-wheeled cart, two blocks long: the body (bed, wheels, load) and its shafts, facing the way the shafts point.
     * The hay cart is the leap of faith (bible §3.6): its body takes a fall as a hay bale does (×0.2, runner_soft).
     */
    public static class Cart extends Facing {
        public enum Part implements KitBlocks.Named { BODY, SHAFTS }

        public enum Load implements KitBlocks.Named { EMPTY, CRATES, SACKS, CASKS, PRODUCE }

        public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
        public static final EnumProperty<Load> LOAD = EnumProperty.create("load", Load.class);
        static final VoxelShape BODY = Block.box(0, 0, 0, 16, 14, 16), SHAFTS = Block.box(0, 0, 0, 16, 4, 16);

        public Cart(BlockBehaviour.Properties p) {
            super(p);
            BlockState d = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BODY);
            if (d.hasProperty(LOAD)) d = d.setValue(LOAD, Load.EMPTY);
            registerDefaultState(d);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PART, LOAD);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(PART) == Part.BODY ? BODY : SHAFTS;
        }
    }

    /** The hay cart: a cart with a heap of loose hay, soft to land in. */
    public static final class HayCart extends Cart {
        public HayCart(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, PART);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(PART) == Part.BODY ? Shapes.block() : SHAFTS;
        }

        @Override
        public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
            if (state.getValue(PART) == Part.BODY) entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall());
            else super.fallOn(level, state, pos, entity, fallDistance);
        }
    }

    // ---- furniture -----------------------------------------------------------------------------------------------------

    /** A street bench (a sitter looks the way it faces): a step-up, two places to sit. */
    public static final class Bench extends Facing {
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Shapes.or(Block.box(0, 0, 3, 16, 9, 13), Block.box(0, 9, 12, 16, 14, 14)));

        public Bench(BlockBehaviour.Properties p) {
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
    }

    /** A trestle table (taverns' tables outside, market tables). */
    public static final class MarketTable extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

        public MarketTable(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS);
        }
    }

    /** An open rack of goods against a shopfront, beside its door. */
    public static final class GoodsShelf extends Facing {
        public enum Goods implements KitBlocks.Named { JARS, CLOTH, BREAD, POTS, BOOKS, TOOLS }

        public static final EnumProperty<Goods> GOODS = EnumProperty.create("goods", Goods.class);
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(0, 0, 8, 16, 16, 16));

        public GoodsShelf(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GOODS, Goods.JARS));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, GOODS);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE.get(s.getValue(FACING));
        }
    }

    /** A low crate or basket of market produce on the ground. */
    public static final class Produce extends Block {
        public enum Kind implements KitBlocks.Named { APPLES, CABBAGES, CARROTS, PUMPKINS, POTATOES, FLOWERS }

        public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

        public Produce(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(KIND, Kind.APPLES));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(KIND);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    /** A street planter (wood or stone) with flowers or a clipped shrub. */
    public static final class Planter extends Block {
        public enum Style implements KitBlocks.Named { WOOD, STONE }

        public enum Bloom implements KitBlocks.Named { RED, YELLOW, BLUE, PINK, WHITE, HERBS, SHRUB }

        public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
        public static final EnumProperty<Bloom> BLOOM = EnumProperty.create("bloom", Bloom.class);
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

        public Planter(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(STYLE, Style.WOOD).setValue(BLOOM, Bloom.RED));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(STYLE, BLOOM);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** An iron lantern on a wall bracket by a door: its glass glows (emissive), it lights nothing (decision 26). */
    public static final class WallLantern extends Facing {
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(5, 3, 5, 11, 13, 16));

        public WallLantern(BlockBehaviour.Properties p) {
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
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }
    }

    /** A notice board on two posts (the Terrace, the squares). */
    public static final class NoticeBoard extends Facing {
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(0, 0, 6, 16, 16, 10));

        public NoticeBoard(BlockBehaviour.Properties p) {
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
    }

    /** A fishing net hung to dry on the Quay: no collision. */
    public static final class FishingNet extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape X = Block.box(0, 0, 7.5, 16, 16, 8.5), Z = Block.box(7.5, 0, 0, 8.5, 16, 16);

        public FishingNet(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(AXIS) == Direction.Axis.X ? X : Z;
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

    /** A well's winding drum with its crank, rope and bucket, over the water between the posts. */
    public static final class WellWindlass extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape X = Block.box(0, 2, 5, 16, 8, 11), Z = Block.box(5, 2, 0, 11, 8, 16);

        public WellWindlass(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AXIS);
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

    /** An iron mooring ring on the quay wall: a cue, no collision. */
    public static final class MooringRing extends Facing {
        private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(4, 3, 14, 12, 13, 16));

        public MooringRing(BlockBehaviour.Properties p) {
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
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }
    }
}
