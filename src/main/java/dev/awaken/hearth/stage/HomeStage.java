package dev.awaken.hearth.stage;

import dev.awaken.city.kit.CityKit;
import dev.awaken.city.kit.KitBlocks;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.guide.Cottage;
import dev.awaken.hearth.guide.CottagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A home grown into a village, built in one go for tours and the studio (admin only: never in play). Round the Anchor's
 * paved yard: the hall (a Grand room: long tables, benches, a fire), Oren's forge, the bell tower, five cottages of the
 * Hearth's own design in three woods (the owner's a Sanctuary; four bedrooms, each its kitchen corner), Ilse's greenhouse,
 * the mine head over a real 22-block ladder shaft, Hettie's strip field and the pen, a road in from the south, lit posts,
 * and Carillon's climbable kit on the owner's cottage (a drainpipe down its corner, a hoist beam under its gable, window
 * boxes). Every room reads as the Hearth reads any build: nothing here is special to it. The spots and camera views are
 * named for the tour.
 */
public final class HomeStage {
    private HomeStage() {}

    /** A camera view: where the eye is and what it looks at (world). */
    public record View(Vec3 eye, Vec3 look) {}

    /** What was built: the Anchor, the named beds and places, the road's start, the views. */
    public record Stage(BlockPos anchor, Map<String, BlockPos> beds, Map<String, BlockPos> spots, BlockPos road, Map<String, View> views) {}

    private static @Nullable Stage last;

    public static @Nullable Stage last() {
        return last;
    }

    /** The flattened ground's reach round the Anchor and the height cleared above it. */
    static final int REACH = 50, CLEAR = 26;

    private static ServerLevel level;
    private static int ox, gy, oz;

    /**
     * Builds the whole homestead with its Anchor at {@code at} (the ground under it is levelled to {@code at}'s height).
     * The Anchor stands on the yard's paving; returns what it built.
     */
    public static Stage build(ServerLevel lvl, BlockPos at) {
        level = lvl;
        ox = at.getX();
        oz = at.getZ();
        gy = at.getY() - 1; // the ground's top block: floors replace it, walls rise from gy + 1
        ground();
        Map<String, BlockPos> beds = new LinkedHashMap<>(), spots = new LinkedHashMap<>();
        Map<String, View> views = new LinkedHashMap<>();
        yard();
        road();
        hall(spots);
        forge(spots);
        bellTower();
        beds.put("owner", cottage(-22, -11, Direction.EAST, CottagePlan.MEADOW, true));
        beds.put("ilse", cottage(14, -8, Direction.WEST, CottagePlan.BIRCH, false));
        beds.put("oren", cottage(-22, 3, Direction.EAST, CottagePlan.TAIGA, false));
        beds.put("hettie", cottage(-22, 17, Direction.EAST, CottagePlan.MEADOW, false));
        beds.put("miner", cottage(14, 17, Direction.WEST, CottagePlan.TAIGA, false));
        greenhouse(spots);
        mineHead(spots);
        field(spots);
        pen(spots);
        kit();
        lamps();
        BlockPos anchor = w(0, 1, 0);
        level.setBlock(anchor, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
        BlockPos road = w(0, 1, 46);
        // the views the tour takes (eye, look), in the village's own frame
        views.put("road", view(1.5, 2.9, 40.5, 0.5, 2.0, 12.5));
        views.put("ilse_door", view(7.5, 2.6, 2.5, 14.5, 1.9, -3.5));
        views.put("ilse_room", view(15.6, 2.5, -6.6, 20.5, 1.3, -1.8));
        views.put("forge", view(13.4, 2.5, -14.3, 15.5, 1.3, -17.5));
        views.put("field", view(-8.5, 2.8, 27.5, -17.5, 1.3, 33.5));
        views.put("minehead", view(6.4, 2.5, 17.4, 8.5, 1.0, 15.5));
        views.put("anchor", view(3.4, 2.4, 3.6, 0.5, 1.7, 0.5));
        views.put("kit", view(-8.5, 4.4, -17.5, -16.0, 4.0, -10.5));
        views.put("overview", view(30.5, 28, 44.5, -2.5, 2, -4.5));
        views.put("hall", view(0.5, 2.8, -15.5, 0.5, 1.6, -20.5));
        last = new Stage(anchor, beds, spots, road, views);
        return last;
    }

    private static View view(double ex, double ey, double ez, double lx, double ly, double lz) {
        return new View(new Vec3(ox + ex, gy + ey, oz + ez), new Vec3(ox + lx, gy + ly, oz + lz));
    }

    // ---- the ground ---------------------------------------------------------------------------------------------------

    private static BlockPos w(int x, int y, int z) {
        return new BlockPos(ox + x, gy + y, oz + z);
    }

    private static void set(int x, int y, int z, BlockState s) {
        level.setBlock(w(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static void set(int x, int y, int z, Block b) {
        set(x, y, z, b.defaultBlockState());
    }

    /** Levels the land: grass at the ground's height over dirt, air above (only where it isn't air already). */
    private static void ground() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        for (int dz = -REACH; dz <= REACH; dz++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dy = -4; dy <= CLEAR; dy++) {
                    m.set(ox + dx, gy + dy, oz + dz);
                    BlockState want = dy > 0 ? air : dy == 0 ? grass : dirt;
                    BlockState have = level.getBlockState(m);
                    if (have == want || dy > 0 && have.isAir()) continue;
                    if (dy < 0 && (have.is(Blocks.STONE) || have.is(Blocks.DEEPSLATE) || have.is(Blocks.DIRT))) continue;
                    level.setBlock(m, want, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The yard round the Anchor: a paved disc of mixed stone, a ring of river stones round the fire. */
    private static void yard() {
        for (int dz = -5; dz <= 5; dz++) for (int dx = -5; dx <= 5; dx++) {
            double d = Math.hypot(dx, dz);
            if (d > 5.4) continue;
            long hsh = (dx * 31L + dz) * 0x9E3779B97F4A7C15L;
            Block b = d < 1.5 ? Blocks.POLISHED_ANDESITE : (hsh >>> 60) < 5 ? Blocks.MOSSY_COBBLESTONE : (hsh >>> 60) < 10 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS;
            set(dx, 0, dz, b);
        }
        // benches round the fire, facing it (people sit here of an evening)
        int[][] benches = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] bn : benches) {
            Direction face = Math.abs(bn[0]) > 0 ? (bn[0] > 0 ? Direction.EAST : Direction.WEST) : Direction.NORTH;
            set(bn[0], 1, bn[1], Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face));
        }
    }

    /** The road in from the south: a worn path with gravel verges, to the edge of the land. */
    private static void road() {
        for (int z = 5; z <= 48; z++) {
            for (int x = -2; x <= 2; x++) {
                long hsh = (x * 131L + z * 7L) * 0x9E3779B97F4A7C15L;
                Block b = Math.abs(x) == 2 ? ((hsh >>> 61) < 3 ? Blocks.GRAVEL : Blocks.COARSE_DIRT) : Blocks.DIRT_PATH;
                set(x, 0, z, b);
            }
        }
    }

    /** Paths from each door to the yard: a line of path blocks, straight then across. */
    private static void path(int x0, int z0, int x1, int z1) {
        int x = x0, z = z0;
        int guard = 0;
        while ((x != x1 || z != z1) && guard++ < 80) {
            if (level.getBlockState(w(x, 0, z)).is(Blocks.GRASS_BLOCK)) set(x, 0, z, Blocks.DIRT_PATH);
            if (Math.abs(x1 - x) > Math.abs(z1 - z)) x += Integer.signum(x1 - x);
            else z += Integer.signum(z1 - z);
        }
    }

    // ---- a building's shell -------------------------------------------------------------------------------------------

    /** Materials for a building: the plinth course, the walls, the corner posts, the floor, the roof, the gables. */
    private record Mat(Block plinth, Block wall, Block post, Block floor, Block ceiling, Block roofStairs, Block roofSlab, Block door, Block shutter) {}

    private static final Mat STONE = new Mat(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_PLANKS,
            Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR);
    private static final Mat TIMBER = new Mat(Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.OAK_PLANKS,
            Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR);

    /**
     * Walls of a box x0..x1, z0..z1 (outer), {@code h} high from gy+1, the floor at gy, a closed ceiling at gy+h+1 and a
     * gable roof along its long side; a door in the middle of side {@code door}; windows every third block along the walls.
     */
    private static void shell(int x0, int z0, int x1, int z1, int h, Mat m, Direction door, boolean windows) {
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            set(x, 0, z, edge ? m.plinth : m.floor);
            for (int y = 1; y <= h + 3; y++) set(x, y, z, Blocks.AIR); // clear the inside and the roof's space
            if (!edge) continue;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int y = 1; y <= h; y++) {
                Block b = corner ? m.post : y == 1 ? m.plinth : m.wall;
                set(x, y, z, b);
            }
        }
        // the ceiling closes the room whatever the roof does
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) set(x, h + 1, z, (x == x0 || x == x1 || z == z0 || z == z1) ? m.post : m.ceiling);
        roof(x0, z0, x1, z1, h + 2, m);
        if (windows) {
            for (int x = x0 + 2; x < x1 - 1; x += 3) {
                window(x, z0, Direction.NORTH, h, m);
                window(x, z1, Direction.SOUTH, h, m);
            }
            for (int z = z0 + 2; z < z1 - 1; z += 3) {
                window(x0, z, Direction.WEST, h, m);
                window(x1, z, Direction.EAST, h, m);
            }
        }
        BlockPos d = doorAt(x0, z0, x1, z1, door);
        int dx = d.getX(), dz = d.getZ();
        BlockState ds = m.door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, door);
        set(dx, 1, dz, ds.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        set(dx, 2, dz, ds.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        // a step of path out of the door, and a lantern over it
        set(dx + door.getStepX(), 0, dz + door.getStepZ(), Blocks.DIRT_PATH);
        if (h >= 3) set(dx, 3, dz, m.wall);
    }

    /** Where the door goes on a side (its middle), local x/z in the wall. */
    private static BlockPos doorAt(int x0, int z0, int x1, int z1, Direction door) {
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        return switch (door) {
            case NORTH -> new BlockPos(mx, 0, z0);
            case SOUTH -> new BlockPos(mx, 0, z1);
            case WEST -> new BlockPos(x0, 0, mz);
            default -> new BlockPos(x1, 0, mz);
        };
    }

    /** A two-high window of panes in a wall, open shutters either side. */
    private static void window(int x, int z, Direction out, int h, Mat m) {
        if (h < 3) {
            set(x, 2, z, Blocks.GLASS_PANE);
            return;
        }
        set(x, 2, z, Blocks.GLASS_PANE);
        set(x, 3, z, Blocks.GLASS_PANE);
        int sx = out.getStepZ() != 0 ? 1 : 0, sz = out.getStepX() != 0 ? 1 : 0;
        for (int y = 2; y <= 3; y++) {
            BlockState shutter = m.shutter.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out)
                    .setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HALF, y == 2 ? Half.BOTTOM : Half.TOP);
            set(x + out.getStepX() - sx, y, z + out.getStepZ() - sz, shutter);
            set(x + out.getStepX() + sx, y, z + out.getStepZ() + sz, shutter);
        }
    }

    /** A gable roof of stairs over the box, along its longer side, a slab ridge, the gable ends filled. */
    private static void roof(int x0, int z0, int x1, int z1, int y, Mat m) {
        boolean alongX = x1 - x0 >= z1 - z0;
        int a0 = alongX ? z0 : x0, a1 = alongX ? z1 : x1; // across the ridge
        int b0 = alongX ? x0 : z0, b1 = alongX ? x1 : z1; // along the ridge
        int half = (a1 - a0 + 1) / 2;
        for (int k = 0; k <= half; k++) {
            int lo = a0 - 1 + k, hi = a1 + 1 - k;
            int ry = y + k;
            for (int b = b0 - 1; b <= b1 + 1; b++) {
                if (lo < hi) {
                    put(alongX, b, ry, lo, m.roofStairs.defaultBlockState().setValue(StairBlock.FACING, alongX ? Direction.SOUTH : Direction.EAST));
                    put(alongX, b, ry, hi, m.roofStairs.defaultBlockState().setValue(StairBlock.FACING, alongX ? Direction.NORTH : Direction.WEST));
                } else {
                    put(alongX, b, ry, lo, m.roofSlab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                }
                // the gable ends: walls under the slope, filled
                if ((b == b0 || b == b1) && lo < hi) for (int c = lo + 1; c < hi; c++) put(alongX, b, ry, c, m.wall.defaultBlockState());
            }
        }
    }

    private static void put(boolean alongX, int b, int y, int a, BlockState s) {
        if (alongX) set(b, y, a, s);
        else set(a, y, b, s);
    }

    private static void lantern(int x, int y, int z, boolean hanging) {
        set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging));
    }

    // ---- the buildings ---------------------------------------------------------------------------------------------

    /** The hall: long tables and benches, a fire at the end, banners and shelves: a Grand room, where meals are eaten. */
    private static void hall(Map<String, BlockPos> spots) {
        int x0 = -7, x1 = 7, z0 = -24, z1 = -14, h = 4;
        shell(x0, z0, x1, z1, h, STONE, Direction.SOUTH, true);
        // stripped-log pillars along the walls, inside, and a beamed ceiling
        for (int x = x0 + 3; x < x1; x += 4) {
            for (int y = 1; y <= h; y++) {
                set(x, y, z0 + 1, Blocks.STRIPPED_SPRUCE_LOG);
                set(x, y, z1 - 1, Blocks.STRIPPED_SPRUCE_LOG);
            }
            for (int z = z0 + 1; z <= z1 - 1; z++) set(x, h + 1, z, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        }
        // the long table down the middle (posts topped with slabs) and benches each side (stairs facing in)
        for (int x = -4; x <= 4; x++) {
            set(x, 1, -19, Blocks.SPRUCE_FENCE);
            set(x, 2, -19, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            if (x % 2 == 0) {
                set(x, 1, -20, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                set(x, 1, -18, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
            }
        }
        for (int x = -4; x <= 4; x += 4) set(x, 3, -19, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        // the fire at the east end, in a brick hearth
        set(x1 - 1, 1, -19, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true).setValue(BlockStateProperties.SIGNAL_FIRE, false));
        for (int y = 1; y <= h; y++) {
            set(x1 - 1, y, -20, Blocks.BRICKS);
            set(x1 - 1, y, -18, Blocks.BRICKS);
        }
        set(x1 - 1, 2, -19, Blocks.BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        for (int y = 3; y <= h; y++) set(x1 - 1, y, -19, Blocks.BRICKS);
        // shelves, a rug, banners, pots, light
        for (int z = -22; z <= -16; z += 6) for (int y = 1; y <= 2; y++) set(x0 + 1, y, z, Blocks.BOOKSHELF);
        for (int x = -3; x <= 3; x++) set(x, 1, -16, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
        set(x0 + 1, 1, -19, Blocks.POTTED_FERN);
        set(x0 + 1, 1, -21, Blocks.BARREL);
        set(-2, 2, z0 + 1, Blocks.WALL_BANNER.pick(DyeColor.RED).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        set(2, 2, z0 + 1, Blocks.WALL_BANNER.pick(DyeColor.YELLOW).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        for (int x = -4; x <= 4; x += 4) {
            lantern(x, h, -21, true);
            lantern(x, h, -17, true);
        }
        lantern(x0 + 1, 1, -16, false);
        lantern(x1 - 2, 1, -22, false);
        lantern(0, 3, z1 + 1, true);
        path(0, z1 + 2, 0, -6);
        spots.put("hall_door", w(0, 1, z1 + 1));
    }

    /** Oren's forge: stone, a Forge in the back wall, the anvil before it, a stand for armour, a lit blast furnace. */
    private static void forge(Map<String, BlockPos> spots) {
        int x0 = 12, x1 = 18, z0 = -20, z1 = -13, h = 3;
        shell(x0, z0, x1, z1, h, STONE, Direction.SOUTH, true);
        set(15, 1, z0, HearthContent.FORGE.get());
        set(14, 1, z0, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.LIT, true));
        set(15, 1, -17, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        set(x0 + 1, 1, z0 + 1, Blocks.BARREL);
        set(x1 - 1, 1, z0 + 1, Blocks.SMITHING_TABLE);
        set(x0 + 1, 1, z1 - 1, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        set(x1 - 1, 1, z1 - 1, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        set(15, h, -17, Blocks.IRON_CHAIN);
        lantern(15, h - 1, -17, true);
        lantern(x0 + 1, 2, z0 + 1, false);
        lantern(x1 - 1, 2, z1 - 1, false);
        lantern(x0 + 1, h, z1 - 1, true);
        ArmorStand stand = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        if (stand != null) {
            BlockPos p = w(x1 - 1, 1, -16);
            stand.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 270f, 0f);
            stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            level.addFreshEntity(stand);
        }
        path(15, z1 + 2, 4, -3);
        spots.put("anvil", w(15, 1, -17));
    }

    /** The bell tower: a stone shaft, its belfry closed with panes on every side, the bell hung from its roof. */
    private static void bellTower() {
        int x0 = -20, x1 = -16, z0 = -24, z1 = -20, top = 13;
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            set(x, 0, z, Blocks.COBBLESTONE);
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int y = 1; y <= 9; y++) set(x, y, z, y == 1 ? Blocks.COBBLESTONE : corner ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
            for (int y = 10; y <= 12; y++) {
                boolean mid = (x == (x0 + x1) / 2 || z == (z0 + z1) / 2) && edge && !corner;
                set(x, y, z, !edge ? Blocks.AIR : corner ? Blocks.STRIPPED_SPRUCE_LOG : mid && y <= 11 ? Blocks.GLASS_PANE : Blocks.STONE_BRICKS);
            }
            set(x, top, z, Blocks.SPRUCE_PLANKS);
        }
        // the slit windows down the shaft, the belfry floor of planks
        for (int y = 3; y <= 7; y += 4) {
            set((x0 + x1) / 2, y, z1, Blocks.GLASS_PANE);
            set(x1, y, (z0 + z1) / 2, Blocks.GLASS_PANE);
        }
        for (int z = z0 + 1; z < z1; z++) for (int x = x0 + 1; x < x1; x++) set(x, 9, z, Blocks.SPRUCE_PLANKS);
        set((x0 + x1) / 2, 12, (z0 + z1) / 2, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)
                .setValue(BellBlock.FACING, Direction.SOUTH));
        lantern(x0 + 1, 10, z0 + 1, false);
        // a pyramid roof of dark stairs, its point a lightning rod
        for (int k = 0; k <= 2; k++) {
            int a0 = x0 - 1 + k, a1 = x1 + 1 - k, b0 = z0 - 1 + k, b1 = z1 + 1 - k, y = top + 1 + k;
            for (int x = a0; x <= a1; x++) {
                set(x, y, b0, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                set(x, y, b1, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
            for (int z = b0 + 1; z < b1; z++) {
                set(a0, y, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
                set(a1, y, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            }
        }
        set((x0 + x1) / 2, top + 4, (z0 + z1) / 2, Blocks.DARK_OAK_PLANKS);
        set((x0 + x1) / 2, top + 5, (z0 + z1) / 2, Blocks.LIGHTNING_ROD.waxed().unaffected());
    }

    /**
     * One of the Hearth's own cottages (Oren's design) with its corner at x0, z0 and its door facing {@code door}; a
     * Sanctuary when {@code mat}, else a bedroom (a rug where the mat would be). Returns its bed's head.
     */
    private static BlockPos cottage(int x0, int z0, Direction door, CottagePlan.Palette p, boolean mat) {
        Rotation rot = CottagePlan.facing(door);
        // the rotated plan's 9 x 9 box: find the origin that puts its corner at (x0, z0)
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int[] c : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            BlockPos r = new BlockPos(c[0], 0, c[1]).rotate(rot);
            minX = Math.min(minX, r.getX());
            minZ = Math.min(minZ, r.getZ());
        }
        BlockPos origin = w(x0 - minX, 0, z0 - minZ);
        List<CottagePlan.Put> plan = new ArrayList<>(CottagePlan.plan(p, CottagePlan.Ground.FLAT, origin.asLong()));
        if (!mat) {
            for (int i = 0; i < plan.size(); i++) {
                CottagePlan.Put put = plan.get(i);
                if (put.state().is(HearthContent.MEDITATION_MAT.get())) {
                    plan.set(i, new CottagePlan.Put(put.x(), put.y(), put.z(), Blocks.CARPET.pick(p.rug()).defaultBlockState(), put.stage()));
                }
            }
        }
        // clear the space first (the plan only writes where it builds)
        for (int z = -3; z <= 9; z++) for (int x = -1; x <= 10; x++) for (int y = 1; y <= CottagePlan.TOP + 1; y++) {
            level.setBlock(CottagePlan.at(origin, rot, x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (CottagePlan.Put put : plan) {
            BlockPos at = CottagePlan.at(origin, rot, put.x(), put.y(), put.z());
            level.setBlock(at, put.state().rotate(rot), Block.UPDATE_CLIENTS);
        }
        for (CottagePlan.Put put : plan) {
            if (!put.state().is(Blocks.GLASS_PANE) && !put.state().is(net.minecraft.tags.BlockTags.STAIRS)) continue;
            BlockPos at = CottagePlan.at(origin, rot, put.x(), put.y(), put.z());
            BlockState cur = level.getBlockState(at), up = Block.updateFromNeighbourShapes(cur, level, at);
            if (up != cur) level.setBlock(at, up, Block.UPDATE_CLIENTS);
        }
        BlockPos doorAt = CottagePlan.at(origin, rot, 4, 0, -3);
        path(doorAt.getX() - ox, doorAt.getZ() - oz, 0, 0);
        // the bed's head (the plan puts it at local 2, 1, 7)
        return CottagePlan.at(origin, rot, 2, 1, 7);
    }

    /** Ilse's greenhouse: a stone course under glass walls and a glass roof, beds of soil with herbs and crops, a well. */
    private static void greenhouse(Map<String, BlockPos> spots) {
        int x0 = 14, x1 = 22, z0 = 4, z1 = 11, h = 3;
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            set(x, 0, z, edge ? Blocks.STONE_BRICKS : Blocks.DIRT);
            for (int y = 1; y <= h + 3; y++) set(x, y, z, Blocks.AIR);
            if (edge) for (int y = 1; y <= h; y++) set(x, y, z, corner ? Blocks.STRIPPED_OAK_LOG : y == 1 ? Blocks.STONE_BRICKS : Blocks.GLASS);
            set(x, h + 1, z, edge ? Blocks.STRIPPED_OAK_LOG : Blocks.GLASS);
        }
        // a ridge of glass over it
        for (int x = x0; x <= x1; x++) for (int z = z0 + 1; z <= z1 - 1; z++) if (z == (z0 + z1) / 2 || z == (z0 + z1) / 2 + 1) set(x, h + 2, z, Blocks.GLASS);
        // soil beds: farmland in two rows with water between, carrots, potatoes, flowers for herbs
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            for (int z : new int[]{z0 + 2, z1 - 2}) {
                set(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
                CropBlock crop = (CropBlock) ((x & 1) == 0 ? Blocks.CARROTS : Blocks.POTATOES);
                set(x, 1, z, crop.getStateForAge(crop.getMaxAge()));
            }
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 3) set(x, 0, (z0 + z1) / 2, Blocks.WATER);
        set(x0 + 1, 1, z0 + 1, Blocks.POTTED_FERN);
        set(x1 - 1, 1, z0 + 1, Blocks.POTTED_BLUE_ORCHID);
        set(x1 - 1, 1, z1 - 1, Blocks.POTTED_ALLIUM);
        set(x0 + 1, 1, z1 - 1, Blocks.COMPOSTER);
        set(x1 - 1, 1, (z0 + z1) / 2, Blocks.BARREL);
        set(x1 - 1, 1, (z0 + z1) / 2 + 1, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        lantern(x0 + 2, h, (z0 + z1) / 2, true);
        lantern(x1 - 2, h, (z0 + z1) / 2, true);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        int dz = (z0 + z1) / 2;
        set(x0, 1, dz, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        set(x0, 2, dz, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        set(x0 + 1, 0, dz, Blocks.SPRUCE_PLANKS);
        path(x0 - 1, dz, 4, 2);
        spots.put("greenhouse", w(x0 + 3, 1, dz));
    }

    /** The mine head: a timber shed, a hatch in its floor over a ladder shaft 22 deep to a small chamber, a chest by it. */
    private static void mineHead(Map<String, BlockPos> spots) {
        int x0 = 5, x1 = 10, z0 = 13, z1 = 18, h = 3;
        shell(x0, z0, x1, z1, h, TIMBER, Direction.WEST, false);
        set(x0, 2, 16, Blocks.GLASS_PANE);
        set(x1, 2, 15, Blocks.GLASS_PANE);
        int hx = 8, hz = 15, depth = 22;
        set(hx, 0, hz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        for (int k = 1; k <= depth; k++) {
            int y = -k;
            // the shaft's walls of its own rock (a cave it cut is walled off), the ladder on its north side, timber every fourth
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                set(hx + dx, y, hz + dz, k % 4 == 0 && dz != -1 ? Blocks.STRIPPED_SPRUCE_LOG : y < -8 ? Blocks.DEEPSLATE : Blocks.STONE);
            }
            set(hx, y, hz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        // the chamber at the foot of the shaft, timbered
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) for (int y = -depth - 3; y <= -depth - 1; y++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == -depth - 3;
            set(hx + dx, y, hz + dz, edge ? (Math.abs(dx) == 2 && Math.abs(dz) == 2 ? Blocks.SPRUCE_LOG : Blocks.STONE) : Blocks.AIR);
        }
        set(hx, -depth - 1, hz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        set(hx, -depth - 2, hz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        set(hx, -depth - 1, hz - 1, Blocks.STONE);
        set(hx, -depth - 2, hz - 1, Blocks.STONE);
        lantern(hx + 1, -depth - 2, hz + 1, false);
        // the shed's things: a chest for the haul, a lantern, picks on the wall, a rail cart
        set(x1 - 1, 1, z0 + 1, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        set(x1 - 1, 1, z1 - 1, Blocks.BARREL);
        lantern(x0 + 1, h, z0 + 1, true);
        lantern(x1 - 1, 2, z1 - 1, false);
        set(x0 + 1, 1, z1 - 1, Blocks.RAIL);
        set(hx, h, hz, Blocks.IRON_CHAIN);
        path(x0 - 1, (z0 + z1) / 2, 2, (z0 + z1) / 2);
        spots.put("hatch", w(hx, 0, hz));
    }

    /** Hettie's strip field: farmland in strips with a channel down the middle, wheat ripe in half of it. */
    private static void field(Map<String, BlockPos> spots) {
        int x0 = -24, x1 = -12, z0 = 30, z1 = 40;
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            if (x == (x0 + x1) / 2) {
                set(x, 0, z, Blocks.WATER);
                continue;
            }
            set(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
            CropBlock crop = (CropBlock) (x < (x0 + x1) / 2 ? Blocks.WHEAT : z % 2 == 0 ? Blocks.CARROTS : Blocks.WHEAT);
            set(x, 1, z, crop.getStateForAge(z < (z0 + z1) / 2 ? crop.getMaxAge() : crop.getMaxAge() - 3));
        }
        // a hay stook and a scarecrow at the field's head
        set(x0 - 1, 1, z0, Blocks.HAY_BLOCK);
        set(x1 + 1, 1, z0, Blocks.COMPOSTER);
        spots.put("field", w(x1 + 1, 1, (z0 + z1) / 2));
        path(x1 + 1, z0 - 1, -2, 24);
    }

    /** The pen: a fence round a patch of grass, a gate toward the yard, sheep and cows. */
    private static void pen(Map<String, BlockPos> spots) {
        int x0 = -9, x1 = -3, z0 = 14, z1 = 21;
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (edge) set(x, 1, z, Blocks.SPRUCE_FENCE);
        }
        set((x0 + x1) / 2, 1, z0, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            BlockPos p = w(x, 1, z);
            BlockState s = level.getBlockState(p), up = Block.updateFromNeighbourShapes(s, level, p);
            if (up != s) level.setBlock(p, up, Block.UPDATE_CLIENTS);
        }
        set(x1 - 1, 1, z1 - 1, Blocks.HAY_BLOCK);
        spawn(net.minecraft.world.entity.EntityTypes.SHEEP, x0 + 2, z0 + 3);
        spawn(net.minecraft.world.entity.EntityTypes.SHEEP, x0 + 3, z0 + 5);
        spawn(net.minecraft.world.entity.EntityTypes.SHEEP, x0 + 5, z0 + 2);
        spawn(net.minecraft.world.entity.EntityTypes.COW, x0 + 4, z0 + 4);
        spawn(net.minecraft.world.entity.EntityTypes.COW, x0 + 2, z0 + 6);
        spots.put("pen_gate", w((x0 + x1) / 2, 1, z0 - 1));
    }

    private static void spawn(EntityType<? extends Mob> type, int x, int z) {
        Mob m = type.create(level, EntitySpawnReason.COMMAND);
        if (m == null) return;
        BlockPos p = w(x, 1, z);
        m.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        m.setPersistenceRequired();
        level.addFreshEntity(m);
    }

    /**
     * Carillon's climbable kit, crafted, on the owner's cottage: a drainpipe down its front corner to the ground, a hoist
     * beam under its gable with a rope from its pulley, window boxes under its side windows.
     */
    private static void kit() {
        // the owner's cottage: corner (-22, -11), door east; its east (front) face is x = -14
        int fx = -13, zc = -11;
        BlockState pipe = CityKit.DRAINPIPE.get().defaultBlockState().setValue(KitBlocks.Drainpipe.FACING, Direction.EAST);
        for (int y = 1; y <= 5; y++) {
            KitBlocks.Drainpipe.Part part = y == 1 ? KitBlocks.Drainpipe.Part.SHOE : y == 5 ? KitBlocks.Drainpipe.Part.HEAD : KitBlocks.Drainpipe.Part.PIPE;
            set(fx, y, zc, pipe.setValue(KitBlocks.Drainpipe.PART, part));
        }
        // the hoist beam under the north gable (z = -12), two long, its pulley at the tip, a rope down from it
        BlockState beam = CityKit.HOIST_BEAM.get().defaultBlockState().setValue(KitBlocks.HoistBeam.FACING, Direction.NORTH);
        set(-18, 7, -12, beam.setValue(KitBlocks.HoistBeam.PULLEY, false));
        set(-18, 7, -13, beam.setValue(KitBlocks.HoistBeam.PULLEY, true));
        for (int y = 3; y <= 6; y++) set(-18, y, -13, CityKit.ROPE.get().defaultBlockState());
        // window boxes under the side windows
        BlockState box = CityKit.FLOWER_BOX.get().defaultBlockState().setValue(KitBlocks.FlowerBox.FACING, Direction.NORTH);
        set(-20, 1, -12, box.setValue(KitBlocks.FlowerBox.BLOOM, KitBlocks.FlowerBox.Bloom.RED));
        set(-16, 1, -12, box.setValue(KitBlocks.FlowerBox.BLOOM, KitBlocks.FlowerBox.Bloom.YELLOW));
    }

    /** Lamps on posts round the yard and down the road. */
    private static void lamps() {
        int[][] posts = {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {3, 14}, {-3, 24}, {3, 34}, {-3, 44}};
        for (int[] p : posts) {
            set(p[0], 1, p[1], Blocks.SPRUCE_FENCE);
            set(p[0], 2, p[1], Blocks.SPRUCE_FENCE);
            set(p[0], 3, p[1], Blocks.LANTERN.defaultBlockState());
        }
    }

    /** Clears the bed's foot of the kit and so on: nothing; kept for the tour's "beds" listing. */
    static BlockPos headOf(BlockPos head) {
        BlockState s = level.getBlockState(head);
        return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.FOOT ? head.relative(s.getValue(BedBlock.FACING)) : head;
    }
}
