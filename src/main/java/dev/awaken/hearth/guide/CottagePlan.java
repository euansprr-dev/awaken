package dev.awaken.hearth.guide;

import dev.awaken.hearth.HearthContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Oren's cottage, block by block. A 9 × 9 timber-framed cottage (7 × 7 inside, 3 high) on a stone plinth: log corner
 * and door posts, a stone lower course, plank walls with shuttered windows, a log wall plate, a plank ceiling (so the
 * room below is closed whatever the roof does), a steep gable roof with a one-block overhang and an attic window, a
 * chimney with a campfire on top, lanterns at the door and inside, a bed and a Meditation Mat (a Sanctuary), a rug,
 * a chest, a crafting table, a stove and books. Wood and stone come from the biome ({@link Palette}).
 *
 * <p>Local frame: x 0..8 left to right, z 0..8 front to back, the door in the front wall (z = 0) facing local north,
 * y 0 = the floor. {@link #place} turns and moves it. Pure: the build job, the site finder and the tests share it.
 */
public final class CottagePlan {
    private CottagePlan() {}

    /** Everything the build touches, local: x -1..10 (overhangs, chimney), z -3..9 (path, back eave), y up to 13. */
    public static final int MIN_X = -1, MAX_X = 10, MIN_Z = -3, MAX_Z = 9, TOP = 13;
    /** A cell inside the room (floor level, the middle), for "is this the cottage". */
    public static final BlockPos INSIDE = new BlockPos(4, 1, 3);
    /** The local column of the chimney. */
    static final int CHIMNEY_X = 9, CHIMNEY_Z = 6;

    /** Build order: the animation goes stage by stage, then layer by layer, row by row. */
    public static final int FOUNDATION = 0, WALLS = 1, PLATE = 2, ROOF = 3, CHIMNEY = 4, FURNISH = 5;

    /** One block of the plan, in local coordinates. */
    public record Put(int x, int y, int z, BlockState state, int stage) {}

    /** Materials by biome. {@code worn*} are the weathered swaps for the stone (null: none). */
    public record Palette(String id, Block planks, Block floor, Block log, Block base, @Nullable Block baseWorn, Block plinth,
                          @Nullable Block plinthWorn, Block roofStairs, Block roofSlab, Block ridge, Block door, Block trapdoor,
                          Block chimney, Block step, DyeColor bed, DyeColor rug) {}

    public static final Palette MEADOW = new Palette("meadow", Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.SPRUCE_STAIRS,
            Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.BRICKS, Blocks.STONE_BRICK_STAIRS, DyeColor.RED, DyeColor.BROWN);
    public static final Palette TAIGA = new Palette("taiga", Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_LOG,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.COBBLESTONE, Blocks.STONE_BRICK_STAIRS, DyeColor.BLUE, DyeColor.RED);
    public static final Palette BIRCH = new Palette("birch", Blocks.BIRCH_PLANKS, Blocks.OAK_PLANKS, Blocks.DARK_OAK_LOG,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.COBBLESTONE, Blocks.STONE_BRICK_STAIRS, DyeColor.LIME, DyeColor.GREEN);
    public static final Palette JUNGLE = new Palette("jungle", Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_LOG,
            Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.JUNGLE_DOOR, Blocks.JUNGLE_TRAPDOOR, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICK_STAIRS, DyeColor.ORANGE, DyeColor.LIME);
    public static final Palette SAVANNA = new Palette("savanna", Blocks.ACACIA_PLANKS, Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG,
            Blocks.TERRACOTTA, null, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.ACACIA_DOOR, Blocks.ACACIA_TRAPDOOR, Blocks.BRICKS, Blocks.COBBLESTONE_STAIRS, DyeColor.YELLOW, DyeColor.ORANGE);
    public static final Palette DESERT = new Palette("desert", Blocks.SMOOTH_SANDSTONE, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.SANDSTONE, null, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.ACACIA_STAIRS,
            Blocks.ACACIA_SLAB, Blocks.ACACIA_PLANKS, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_STAIRS,
            DyeColor.CYAN, DyeColor.RED);
    public static final Palette BADLANDS = new Palette("badlands", Blocks.TERRACOTTA, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_LOG,
            Blocks.RED_SANDSTONE, null, Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.BRICKS, Blocks.RED_SANDSTONE_STAIRS, DyeColor.RED, DyeColor.BROWN);
    public static final Palette SWAMP = new Palette("swamp", Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_LOG,
            Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICK_STAIRS, DyeColor.PURPLE, DyeColor.GREEN);
    public static final Palette MANGROVE = new Palette("mangrove", Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG,
            Blocks.MUD_BRICKS, null, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.MANGROVE_DOOR, Blocks.MANGROVE_TRAPDOOR, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS, DyeColor.LIME, DyeColor.BROWN);
    public static final Palette CHERRY = new Palette("cherry", Blocks.CHERRY_PLANKS, Blocks.CHERRY_PLANKS, Blocks.CHERRY_LOG,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.DARK_OAK_STAIRS,
            Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_PLANKS, Blocks.CHERRY_DOOR, Blocks.CHERRY_TRAPDOOR, Blocks.COBBLESTONE, Blocks.STONE_BRICK_STAIRS, DyeColor.PINK, DyeColor.MAGENTA);

    public static final List<Palette> PALETTES = List.of(MEADOW, TAIGA, BIRCH, JUNGLE, SAVANNA, DESERT, BADLANDS, SWAMP, MANGROVE, CHERRY);

    /** Walls stand 1..WALL (the room is 7 × 7 × 4 inside); the plate and the ceiling are one above. */
    static final int WALL = 4, PLATE_Y = WALL + 1;

    /** The roof's height over a front-to-back row (z -1..9): eaves on the plate's level, the ridge (z 4) four above. */
    static int roofY(int z) {
        return PLATE_Y + Math.min(z + 1, 9 - z);
    }

    /**
     * The whole plan. {@code ground} gives the natural ground's top under each local column relative to the floor
     * (0 = level, -2 = two below, so the plinth fills down to it; +1 = a bump the floor replaces). {@code salt} varies
     * the weathering.
     */
    public static List<Put> plan(Palette p, Ground ground, long salt) {
        List<Put> out = new ArrayList<>(720);
        // ---- foundation and floor: the plinth fills down to real ground ----------------------------------------------
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                int g = ground.top(x, z);
                for (int y = Math.max(-4, g + 1); y < 0; y++) out.add(new Put(x, y, z, stone(p.plinth, p.plinthWorn, x, y, z, salt), FOUNDATION));
                out.add(new Put(x, 0, z, edge ? stone(p.plinth, p.plinthWorn, x, 0, z, salt) : p.floor.defaultBlockState(), FOUNDATION));
            }
        }
        // ---- walls: posts, a stone course, planks, tall windows, the door with a light over it --------------------------
        for (int y = 1; y <= WALL; y++) {
            for (int z = 0; z <= 8; z++) {
                for (int x = 0; x <= 8; x++) {
                    if (!(x == 0 || x == 8 || z == 0 || z == 8)) continue;
                    BlockState s = wall(p, x, y, z, salt);
                    if (s != null) out.add(new Put(x, y, z, s, WALLS));
                }
            }
        }
        // the door, both halves together so neither ever stands alone
        BlockState door = p.door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        out.add(new Put(4, 1, 0, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), WALLS));
        out.add(new Put(4, 2, 0, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), WALLS));
        // ---- the wall plate and the ceiling (the room is closed here, whatever the roof does) -------------------------
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                BlockState s;
                if ((x == 0 || x == 8) && (z == 0 || z == 8)) s = log(p, Direction.Axis.Y);
                else if (z == 0 || z == 8) s = log(p, Direction.Axis.X);
                else if (edge) s = log(p, Direction.Axis.Z);
                else s = p.floor.defaultBlockState();
                out.add(new Put(x, PLATE_Y, z, s, PLATE));
            }
        }
        // ---- gables, the roof, its thick gable edges and the ridge beam ------------------------------------------------
        int ridgeY = roofY(4);
        for (int y = PLATE_Y; y <= ridgeY + 1; y++) {
            for (int z = -1; z <= 9; z++) {
                int ry = roofY(z);
                for (int x = -1; x <= 9; x++) {
                    boolean gable = x == 0 || x == 8, overhang = x == -1 || x == 9;
                    if (gable && z >= 1 && z <= 7 && y > PLATE_Y && y < Math.min(ry, ridgeY)) {
                        boolean window = z == 4 && y == PLATE_Y + 2;
                        out.add(new Put(x, y, z, window ? Blocks.GLASS_PANE.defaultBlockState() : p.planks.defaultBlockState(), ROOF));
                    }
                    if (z == 4) {
                        if (y == ridgeY) out.add(new Put(x, y, z, overhang || gable ? log(p, Direction.Axis.X) : p.ridge.defaultBlockState(), ROOF));
                        if (y == ridgeY + 1) out.add(new Put(x, y, z, p.roofSlab.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), ROOF));
                        if (overhang && y == ridgeY - 1) out.add(new Put(x, y, z, log(p, Direction.Axis.X), ROOF)); // the ridge beam's end
                        continue;
                    }
                    if (x == CHIMNEY_X && z == CHIMNEY_Z) continue;
                    Direction up = z < 4 ? Direction.SOUTH : Direction.NORTH; // stairs climb toward the ridge
                    if (y == ry) {
                        out.add(new Put(x, y, z, p.roofStairs.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, up)
                                .setValue(BlockStateProperties.HALF, Half.BOTTOM), ROOF));
                    } else if (overhang && y == ry - 1 && z >= 0 && z <= 8) {
                        // an upside-down stair under each gable-edge stair: the roof's edge reads thick, like a bargeboard
                        out.add(new Put(x, y, z, p.roofStairs.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, up)
                                .setValue(BlockStateProperties.HALF, Half.TOP), ROOF));
                    }
                }
            }
        }
        // ---- chimney beside the right gable, with a fire on top -------------------------------------------------------
        int cg = ground.top(CHIMNEY_X, CHIMNEY_Z);
        for (int y = Math.max(-4, cg + 1); y < TOP; y++) out.add(new Put(CHIMNEY_X, y, CHIMNEY_Z, p.chimney.defaultBlockState(), CHIMNEY));
        out.add(new Put(CHIMNEY_X, TOP, CHIMNEY_Z, Blocks.CAMPFIRE.defaultBlockState(), CHIMNEY));
        // ---- inside: a Sanctuary (bed + Meditation Mat), and a few things that make it a home -------------------------
        BlockState bed = Blocks.BED.pick(p.bed).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        out.add(new Put(2, 1, 6, bed.setValue(BedBlock.PART, BedPart.FOOT), FURNISH));
        out.add(new Put(2, 1, 7, bed.setValue(BedBlock.PART, BedPart.HEAD), FURNISH));
        out.add(new Put(1, 1, 7, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), FURNISH));
        out.add(new Put(1, 2, 7, Blocks.LANTERN.defaultBlockState(), FURNISH));
        out.add(new Put(5, 1, 6, HearthContent.MEDITATION_MAT.get().defaultBlockState(), FURNISH));
        out.add(new Put(6, 1, 7, Blocks.POTTED_FERN.defaultBlockState(), FURNISH));
        out.add(new Put(7, 1, 6, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), FURNISH));
        out.add(new Put(7, 1, 2, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), FURNISH));
        out.add(new Put(7, 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState(), FURNISH));
        for (int y = 1; y <= 3; y++) out.add(new Put(1, y, 1, Blocks.BOOKSHELF.defaultBlockState(), FURNISH));
        out.add(new Put(1, 1, 2, Blocks.POTTED_POPPY.defaultBlockState(), FURNISH));
        for (int z = 2; z <= 4; z++) for (int x = 3; x <= 5; x++) out.add(new Put(x, 1, z, Blocks.CARPET.pick(p.rug).defaultBlockState(), FURNISH));
        out.add(new Put(4, WALL, 4, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), FURNISH));
        // ---- outside: lanterns under the eave by the door, shutters, flower boxes, a path to the Anchor ----------------
        out.add(new Put(3, WALL, -1, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), FURNISH));
        out.add(new Put(5, WALL, -1, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), FURNISH));
        for (int[] w : WINDOWS) {
            int wx = w[0], wz = w[1];
            Direction out1 = wz == 0 ? Direction.NORTH : wz == 8 ? Direction.SOUTH : wx == 0 ? Direction.WEST : Direction.EAST;
            int ox = out1.getStepX(), oz = out1.getStepZ();
            int sx = oz != 0 ? 1 : 0, sz = ox != 0 ? 1 : 0; // along the wall
            for (int y = 2; y <= 3; y++) {
                BlockState shutter = p.trapdoor.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out1)
                        .setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, y == 2 ? Half.BOTTOM : Half.TOP);
                out.add(new Put(wx + ox - sx, y, wz + oz - sz, shutter, FURNISH));
                out.add(new Put(wx + ox + sx, y, wz + oz + sz, shutter, FURNISH));
            }
            if (out1 == Direction.NORTH) { // the front windows get a flower box
                out.add(new Put(wx, 1, wz - 1, p.trapdoor.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP), FURNISH));
                out.add(new Put(wx, 2, wz - 1, (wx < 4 ? Blocks.POTTED_DANDELION : Blocks.POTTED_POPPY).defaultBlockState(), FURNISH));
            }
        }
        int step = ground.top(4, -1);
        if (step < 0) { // the ground in front of the door is lower: a step up, standing on the plinth stone
            for (int y = Math.max(-4, step + 1); y < 0; y++) out.add(new Put(4, y, -1, p.plinth.defaultBlockState(), FURNISH));
            out.add(new Put(4, 0, -1, p.step.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), FURNISH));
        }
        for (int z = -1; z >= -3; z--) {
            if (ground.top(4, z) == 0 && ground.isGrass(4, z)) out.add(new Put(4, 0, z, Blocks.DIRT_PATH.defaultBlockState(), FURNISH));
        }
        // stage, layer, row, column; a door's upper half sorts right after its lower half so neither stands alone
        out.sort(Comparator.comparingInt(Put::stage).thenComparingInt(CottagePlan::layer).thenComparingInt(Put::z)
                .thenComparingInt(Put::x).thenComparingInt(Put::y));
        return out;
    }

    private static int layer(Put p) {
        BlockState s = p.state();
        return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                ? p.y() - 1 : p.y();
    }

    /** Windows (x, z), two tall (y 2..3): two at the front, two at the back, two on the left, one on the right (the stove). */
    static final int[][] WINDOWS = {{2, 0}, {6, 0}, {2, 8}, {6, 8}, {0, 2}, {0, 6}, {8, 2}};

    private static @Nullable BlockState wall(Palette p, int x, int y, int z, long salt) {
        boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
        boolean post = corner || (z == 0 && (x == 3 || x == 5)) || (z == 8 && x == 4) || ((x == 0 || x == 8) && z == 4);
        if (z == 0 && x == 4) {
            if (y <= 2) return null; // the door
            return y == 3 ? Blocks.GLASS_PANE.defaultBlockState() : log(p, Direction.Axis.X); // a light over the door, the lintel
        }
        if (post) return log(p, Direction.Axis.Y);
        for (int[] w : WINDOWS) if (w[0] == x && w[1] == z && (y == 2 || y == 3)) return Blocks.GLASS_PANE.defaultBlockState();
        if (x == 8 && z == 6 && y <= 2) return p.chimney.defaultBlockState(); // the stove's back
        return y == 1 ? stone(p.base, p.baseWorn, x, y, z, salt) : p.planks.defaultBlockState();
    }

    private static BlockState log(Palette p, Direction.Axis axis) {
        BlockState s = p.log.defaultBlockState();
        return s.hasProperty(BlockStateProperties.AXIS) ? s.setValue(BlockStateProperties.AXIS, axis) : s;
    }

    /** Weathering in patches (2-block cells of a hash), about one in five. */
    private static BlockState stone(Block block, @Nullable Block worn, int x, int y, int z, long salt) {
        if (worn == null) return block.defaultBlockState();
        long h = salt * 0x9E3779B97F4A7C15L + (x >> 1) * 0x632BE59BD9B4E019L + (y >> 1) * 0x85157AF5L + (z >> 1) * 0x2545F4914F6CDD1DL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFF) < 56 ? worn.defaultBlockState() : block.defaultBlockState();
    }

    /** The ground under the plan, local columns, relative to the floor. */
    public interface Ground {
        int top(int x, int z);

        default boolean isGrass(int x, int z) {
            return true;
        }

        Ground FLAT = (x, z) -> 0;
    }

    // ---- into the world ----------------------------------------------------------------------------------------------

    /** World position of a local cell for a cottage whose local origin is {@code origin}, turned by {@code rot}. */
    public static BlockPos at(BlockPos origin, Rotation rot, int x, int y, int z) {
        return origin.offset(new BlockPos(x, y, z).rotate(rot));
    }

    /** The turn that makes the door (local north) face {@code front}. */
    public static Rotation facing(Direction front) {
        return switch (front) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
