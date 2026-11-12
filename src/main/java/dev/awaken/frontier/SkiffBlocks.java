package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.api.Sources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.Map;

/**
 * What the skiff is made of (SPEC §67.4): its own blocks, so it looks like nothing else in the world. Precursor plating
 * for the hull, a planked deck with the gold sheer line on its side, low gold-capped gunwales, thin spars, canvas for
 * the wings and the wind-wheel, brass, the helm with its wheel, and a caged lamp (blue on the skiff, amber on its
 * mooring). None has an item: the mooring's builder is the only way they come into the world, and nothing drops them.
 * Art and models: {@code tools/art/skiff_art.py}.
 */
public final class SkiffBlocks {
    private SkiffBlocks() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);

    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties p, MapColor colour) {
        return p.mapColor(colour).strength(3F, 12F).sound(SoundType.COPPER).noLootTable();
    }

    private static BlockBehaviour.Properties wood(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.WOOD).strength(2F, 6F).sound(SoundType.WOOD).noLootTable();
    }

    public static final DeferredBlock<Block> HULL = BLOCKS.registerSimpleBlock("skiff_hull", p -> metal(p, MapColor.COLOR_BLUE));
    public static final DeferredBlock<StairBlock> HULL_STAIRS = BLOCKS.registerBlock("skiff_hull_stairs",
            p -> new StairBlock(HULL.get().defaultBlockState(), p), p -> metal(p, MapColor.COLOR_BLUE));
    public static final DeferredBlock<RotatedPillarBlock> DECK = BLOCKS.registerBlock("skiff_deck", RotatedPillarBlock::new, SkiffBlocks::wood);
    public static final DeferredBlock<Block> BRASS = BLOCKS.registerSimpleBlock("skiff_brass", p -> metal(p, MapColor.GOLD));
    public static final DeferredBlock<Spar> SPAR = BLOCKS.registerBlock("skiff_spar", Spar::new, p -> wood(p).noOcclusion());
    public static final DeferredBlock<Sail> SAIL = BLOCKS.registerBlock("skiff_sail", Sail::new,
            p -> p.mapColor(MapColor.SAND).strength(0.8F, 2F).sound(SoundType.WOOL).noOcclusion().noCollision().noLootTable());
    public static final DeferredBlock<Gunwale> GUNWALE = BLOCKS.registerBlock("skiff_gunwale", Gunwale::new, p -> wood(p).noOcclusion());
    public static final DeferredBlock<Helm> HELM = BLOCKS.registerBlock("skiff_helm", Helm::new,
            p -> metal(p, MapColor.GOLD).noOcclusion().lightLevel(s -> 6));
    public static final DeferredBlock<Lamp> LAMP = BLOCKS.registerBlock("skiff_lamp", Lamp::new,
            p -> p.mapColor(MapColor.GOLD).strength(1.5F, 6F).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> 14).noLootTable());

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        Sources.declare("set by the skiff's mooring when it is built (frontier.Moorings, SPEC §67.4)",
                "skiff_hull", "skiff_hull_stairs", "skiff_deck", "skiff_brass", "skiff_spar", "skiff_sail", "skiff_gunwale", "skiff_helm", "skiff_lamp");
    }

    /** A thin pole: booms, the bowsprit, the wheel's arms, the mast's top. */
    public static final class Spar extends RotatedPillarBlock {
        private static final VoxelShape ALONG_X = Block.box(0, 6, 6, 16, 10, 10), UPRIGHT = Block.box(6, 0, 6, 10, 16, 10), ALONG_Z = Block.box(6, 6, 0, 10, 10, 16);

        public Spar(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return switch (s.getValue(AXIS)) {
                case X -> ALONG_X;
                case Z -> ALONG_Z;
                default -> UPRIGHT;
            };
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /**
     * Canvas, two texels thick, lying in one of three planes; the tip panels are dyed. Nothing to walk into. A side lashed
     * to a spar (north, east, south, west, up, down: set by the plan, tools/art/skiff_plan.py) runs the cloth on into the
     * pole beside it, since a spar is thin and in the middle of its block (SPEC §67.4).
     */
    public static final class Sail extends Block {
        /** Flat like a wing; across the skiff (the wind-wheel's blades); along it (a pennant). */
        public enum Plane implements StringRepresentable {
            FLAT, ATHWART, FORE_AFT;

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        public static final EnumProperty<Plane> PLANE = EnumProperty.create("plane", Plane.class);
        public static final BooleanProperty BAND = BooleanProperty.create("band");
        public static final BooleanProperty NORTH = BooleanProperty.create("north"), EAST = BooleanProperty.create("east"),
                SOUTH = BooleanProperty.create("south"), WEST = BooleanProperty.create("west"), UP = BooleanProperty.create("up"),
                DOWN = BooleanProperty.create("down");
        private static final VoxelShape LYING = Block.box(0, 7, 0, 16, 9, 16), ACROSS = Block.box(0, 0, 7, 16, 16, 9), ALONG = Block.box(7, 0, 0, 9, 16, 16);

        public Sail(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(PLANE, Plane.FLAT).setValue(BAND, false).setValue(NORTH, false)
                    .setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(PLANE, BAND, NORTH, EAST, SOUTH, WEST, UP, DOWN);
        }

        /** The flag for the side of canvas facing {@code d}: true when it is lashed to the spar there. */
        public static BooleanProperty side(Direction d) {
            return switch (d) {
                case NORTH -> NORTH;
                case EAST -> EAST;
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                case UP -> UP;
                case DOWN -> DOWN;
            };
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return switch (s.getValue(PLANE)) {
                case ATHWART -> ACROSS;
                case FORE_AFT -> ALONG;
                default -> LYING;
            };
        }

        @Override
        protected BlockState rotate(BlockState s, Rotation rot) {
            if (rot == Rotation.NONE) return s;
            // the lashed sides turn with the skiff, so each still names the side its spar is on
            BlockState out = s;
            for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(side(rot.rotate(d)), s.getValue(side(d)));
            if (rot == Rotation.CLOCKWISE_180) return out;
            return switch (s.getValue(PLANE)) {
                case ATHWART -> out.setValue(PLANE, Plane.FORE_AFT);
                case FORE_AFT -> out.setValue(PLANE, Plane.ATHWART);
                default -> out;
            };
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** The hull's low side above the deck, capped in gold, on whichever edges of its block it's given. */
    public static final class Gunwale extends Block {
        public static final BooleanProperty NORTH = BooleanProperty.create("north"), EAST = BooleanProperty.create("east"),
                SOUTH = BooleanProperty.create("south"), WEST = BooleanProperty.create("west");
        private static final Map<Direction, VoxelShape> SIDE = Shapes.rotateHorizontal(Block.box(0, 0, 0, 16, 12, 4));
        private static final VoxelShape[] SHAPES = new VoxelShape[16];

        public Gunwale(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(NORTH, EAST, SOUTH, WEST);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            int i = (s.getValue(NORTH) ? 1 : 0) | (s.getValue(EAST) ? 2 : 0) | (s.getValue(SOUTH) ? 4 : 0) | (s.getValue(WEST) ? 8 : 0);
            VoxelShape shape = SHAPES[i];
            if (shape == null) {
                shape = Shapes.empty();
                if ((i & 1) != 0) shape = Shapes.or(shape, SIDE.get(Direction.NORTH));
                if ((i & 2) != 0) shape = Shapes.or(shape, SIDE.get(Direction.EAST));
                if ((i & 4) != 0) shape = Shapes.or(shape, SIDE.get(Direction.SOUTH));
                if ((i & 8) != 0) shape = Shapes.or(shape, SIDE.get(Direction.WEST));
                SHAPES[i] = shape;
            }
            return shape;
        }

        @Override
        protected BlockState rotate(BlockState s, Rotation rot) {
            boolean n = s.getValue(NORTH), e = s.getValue(EAST), so = s.getValue(SOUTH), w = s.getValue(WEST);
            return switch (rot) {
                case CLOCKWISE_90 -> s.setValue(NORTH, w).setValue(EAST, n).setValue(SOUTH, e).setValue(WEST, so);
                case CLOCKWISE_180 -> s.setValue(NORTH, so).setValue(EAST, w).setValue(SOUTH, n).setValue(WEST, e);
                case COUNTERCLOCKWISE_90 -> s.setValue(NORTH, e).setValue(EAST, so).setValue(SOUTH, w).setValue(WEST, n);
                default -> s;
            };
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }

    /** The helm: a console on a pedestal with the wheel toward whoever stands behind it. It faces the bow. */
    public static final class Helm extends HorizontalDirectionalBlock {
        private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 9, 12), Block.box(3, 9, 3, 13, 15, 13));

        public Helm(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
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

    /** A caged lamp, standing or hung: the skiff's are Precursor blue, the mooring's amber. */
    public static final class Lamp extends Block {
        public enum Glass implements StringRepresentable {
            BLUE, AMBER;

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        public static final EnumProperty<Glass> GLASS = EnumProperty.create("glass", Glass.class);
        public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
        private static final VoxelShape STANDING = Block.box(4, 0, 4, 12, 12, 12), HUNG = Block.box(5, 3, 5, 11, 16, 11);

        public Lamp(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(GLASS, Glass.BLUE).setValue(HANGING, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(GLASS, HANGING);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(HANGING) ? HUNG : STANDING;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }
}
