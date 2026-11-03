package dev.awaken.lucidium.trial;

import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.block.WakingVeilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rooms, the Chase's road and the Duel's ring (SPEC §9.5), laid on the course kit after the doorway. A Room has one
 * dream rule and its layout ({@link Course.Room}) says where the rule's pieces stand: the plaza a moving goal keeps to,
 * the three wrong things, the fold's door, no door's walls. Pure: built off-thread with the rest of the dream.
 */
public final class RoomLayouts {
    private RoomLayouts() {}

    /** The plazas' reach each way from their middle. */
    public static final int PLAZA = 13, PLACE_PLAZA = 13;
    /** The fold: the hallway's length and the stretch it loops (a multiple of its lamps' spacing, so it looks the same). */
    public static final int FOLD_LEN = 30, FOLD_FROM = 6, FOLD_TO = 24, FOLD_DOOR = 15;

    static BlockState stone() {
        return LucidiumContent.SHIFTING_STONE.get().defaultBlockState();
    }

    static BlockState glass() {
        return LucidiumContent.DREAM_GLASS.get().defaultBlockState();
    }

    /**
     * Lays a Room (or the Chase's road, the Duel's ring) from the kit's cursor. {@code bell}: a bell is the goal (the
     * bells set it where the room says); {@code place}: a hidden place stands beyond dream glass at the end;
     * {@code today}: a place from today stands in the middle of what's wrong here (else a little courtyard).
     */
    public static void lay(CourseKit k, Trial t, boolean bell, boolean place, long seed, boolean today) {
        switch (t) {
            case LOOK_AWAY, HUSH -> plazaRoom(k, bell, place);
            case WRONG -> wrong(k, place, seed, today);
            case LONG_FALL -> longFall(k);
            case FOLD -> fold(k, place);
            case NO_DOOR -> noDoor(k, place, seed);
            case CHASE -> {
                k.road(6).ring().road(6).gap(2).road(5).up().road(5).ring().road(4).gap(2).road(6).up().road(4).ring().road(8);
                k.door();
            }
            case DUEL -> duel(k);
            default -> k.road(12).ring().road(12).door();
        }
    }

    // ---- look away, hush: a plaza the goal moves about -------------------------------------------------------------

    private static void plazaRoom(CourseKit k, boolean bell, boolean place) {
        k.road(4).ring();
        int z0 = k.z;
        BlockPos centre = plaza(k, PLAZA, true);
        BlockPos goal = new BlockPos(k.x, k.y, z0 + 2 * PLAZA - 3);
        if (!bell) door(k, goal);
        k.pathTo(Vec3.atBottomCenterOf(goal));
        BlockPos at = place ? window(k, z0 + 2 * PLAZA + 1, k.y) : null;
        k.at(k.y, z0 + 2 * PLAZA + 1);
        k.room = new Course.Room(centre, PLAZA, goal, List.of(), List.of(), -1, 0, 0, k.y, at);
    }

    /**
     * A round floor of road over the cloud sea, from the cursor along +z, {@code half} each way; pillars of shifting stone
     * crowned with pearl light to walk round. Returns its middle (feet).
     */
    private static BlockPos plaza(CourseKit k, int half, boolean pillars) {
        int x0 = k.x, y = k.y, z0 = k.z;
        BlockPos centre = new BlockPos(x0, y, z0 + half);
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > half + 0.5) continue;
                k.ground(x0 + dx, y - 1, centre.getZ() + dz, k.road);
                if (r > half - 1.5 && Math.floorMod(dx * 7 + dz * 3, 4) == 0) k.block(x0 + dx, y - 2, centre.getZ() + dz, k.cloud);
            }
        if (pillars)
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + 0.5;
                int px = x0 + (int) Math.round(Math.cos(a) * half * 0.55), pz = centre.getZ() + (int) Math.round(Math.sin(a) * half * 0.55);
                for (int h = 0; h < 4; h++) k.block(px, y + h, pz, stone());
                k.block(px, y + 4, pz, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            }
        return centre;
    }

    /** A small Waking Door standing on its own: two posts of pale light, a lintel, a veil to walk into (across x). */
    public static void door(CourseKit k, BlockPos at) {
        for (BlockPos p : doorBlocks(at)) k.block(p.getX(), p.getY(), p.getZ(), doorState(at, p));
    }

    /** The small door's blocks (the dream moves it by clearing these and laying them again). */
    public static List<BlockPos> doorBlocks(BlockPos at) {
        List<BlockPos> out = new ArrayList<>();
        for (int h = 0; h <= 3; h++) {
            out.add(at.offset(-1, h, 0));
            out.add(at.offset(1, h, 0));
        }
        out.add(at.offset(0, 3, 0));
        for (int h = 0; h <= 2; h++) out.add(at.offset(0, h, 0));
        return out;
    }

    public static BlockState doorState(BlockPos at, BlockPos p) {
        if (p.getX() == at.getX() && p.getY() < at.getY() + 3)
            return LucidiumContent.WAKING_VEIL.get().defaultBlockState().setValue(WakingVeilBlock.AXIS, Direction.Axis.X);
        return LucidiumContent.WAKING_DOOR.get().defaultBlockState();
    }

    /** Where the small door at {@code at} is touched. */
    public static AABB doorBox(BlockPos at) {
        return new AABB(at.getX() - 0.8, at.getY(), at.getZ() - 0.4, at.getX() + 1.8, at.getY() + 3, at.getZ() + 1.4);
    }

    /**
     * A window of dream glass across the room's far side and, beyond it, the ground the hidden place stands on (the
     * compiler sets the place itself there). Returns the place's middle (feet).
     */
    private static BlockPos window(CourseKit k, int z, int y) {
        for (int dx = -6; dx <= 6; dx++)
            for (int h = 0; h <= 4; h++) {
                boolean frame = Math.abs(dx) == 6 || h == 4;
                k.block(k.x + dx, y + h, z, frame ? stone() : glass());
            }
        for (int dx = -6; dx <= 6; dx++) k.ground(k.x + dx, y - 1, z, k.road);
        return new BlockPos(k.x, y, z + 14);
    }

    // ---- what's wrong here --------------------------------------------------------------------------------------------

    /** The three wrong things, by what they are: a door on its own a block off the ground, a block floating, a lantern hanging from nothing. */
    public static final int DOOR = 0, FLOATING = 1, LANTERN = 2;

    private static void wrong(CourseKit k, boolean place, long seed, boolean today) {
        k.road(4).ring();
        int z0 = k.z;
        BlockPos centre = plaza(k, PLACE_PLAZA, false);
        if (!today) courtyard(k, centre);
        // three of seven spots round the place, clear of it (it's 16 across), never straight ahead of the way in
        int[][] spots = {{-10, 0}, {10, 0}, {0, 10}, {-9, 9}, {9, 9}, {-9, -9}, {9, -9}};
        List<BlockPos> things = new ArrayList<>();
        int first = (int) Math.floorMod(seed, spots.length);
        for (int i = 0; i < 3; i++) {
            int[] s = spots[(first + i * 2) % spots.length];
            BlockPos base = centre.offset(s[0], 0, s[1]);
            things.add(wrongThing(k, i, base, centre));
            k.pathTo(Vec3.atCenterOf(things.getLast()));
        }
        BlockPos at = place ? window(k, z0 + 2 * PLACE_PLAZA + 1, k.y) : null;
        k.at(k.y, z0 + 2 * PLACE_PLAZA + 1);
        k.room = new Course.Room(centre, PLACE_PLAZA, null, List.copyOf(things), List.of(), -1, 0, 0, k.y, at);
    }

    /** Lays one wrong thing near {@code base}; returns the block to look at. */
    private static BlockPos wrongThing(CourseKit k, int kind, BlockPos base, BlockPos centre) {
        switch (kind) {
            case DOOR -> {
                // a door standing on its own with no wall, its foot a block above the ground
                Direction facing = Math.abs(base.getX() - centre.getX()) > Math.abs(base.getZ() - centre.getZ())
                        ? (base.getX() > centre.getX() ? Direction.WEST : Direction.EAST) : Direction.NORTH;
                BlockState d = Blocks.PALE_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
                BlockPos foot = base.above();
                k.block(foot.getX(), foot.getY(), foot.getZ(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                k.block(foot.getX(), foot.getY() + 1, foot.getZ(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                return foot;
            }
            case FLOATING -> {
                // a block of grass three up, nothing under it
                BlockPos at = base.above(2);
                k.block(at.getX(), at.getY(), at.getZ(), Blocks.GRASS_BLOCK.defaultBlockState());
                return at;
            }
            default -> {
                // a lantern hanging from a chain that ends in the air
                BlockPos at = base.above(2);
                k.block(at.getX(), at.getY(), at.getZ(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                k.block(at.getX(), at.getY() + 1, at.getZ(), Blocks.IRON_CHAIN.defaultBlockState());
                k.block(at.getX(), at.getY() + 2, at.getZ(), Blocks.IRON_CHAIN.defaultBlockState());
                return at;
            }
        }
    }

    /** No place from today: a little courtyard of pale wood and pearl light for the wrong things to be wrong in. */
    private static void courtyard(CourseKit k, BlockPos c) {
        BlockState post = Blocks.PALE_OAK_LOG.defaultBlockState(), plank = Blocks.PALE_OAK_PLANKS.defaultBlockState();
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int h = 0; h < 3; h++) k.block(c.getX() + sx * 3, c.getY() + h, c.getZ() + sz * 3, post);
                k.block(c.getX() + sx * 3, c.getY() + 3, c.getZ() + sz * 3, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            }
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) == 3 || Math.abs(dz) == 3) k.block(c.getX() + dx, c.getY() + 4, c.getZ() + dz, plank);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) k.block(c.getX() + dx, c.getY() - 1, c.getZ() + dz, Blocks.MOSS_BLOCK.defaultBlockState());
    }

    // ---- the long fall ------------------------------------------------------------------------------------------------

    private static void longFall(CourseKit k) {
        k.road(6).ring().road(5);
        int edge = k.z, y = k.y;
        // nothing below: a cloud to land on would end the fall without the rule (clouds are solid)
        k.pathTo(new Vec3(k.x + 0.5, y + 0.2, edge + 2.5));
        k.room = new Course.Room(new BlockPos(k.x, y, edge + 3), 6, null, List.of(), List.of(), -1, 0, 0, y, null);
    }

    // ---- the fold -----------------------------------------------------------------------------------------------------

    private static void fold(CourseKit k, boolean place) {
        k.road(3).ring();
        int x0 = k.x, y = k.y, z0 = k.z;
        BlockState light = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
        for (int dz = 0; dz < FOLD_LEN; dz++) {
            int z = z0 + dz;
            for (int dx = -1; dx <= 1; dx++) {
                k.ground(x0 + dx, y - 1, z, k.road);
                for (int h = 0; h <= 3; h++) k.clear(x0 + dx, y + h, z);
                k.block(x0 + dx, y + 4, z, dx == 0 && dz % 6 == 3 && dz < FOLD_TO + 3 ? light : stone());
            }
            for (int h = -1; h <= 4; h++) {
                k.block(x0 - 2, y + h, z, stone());
                k.block(x0 + 2, y + h, z, stone());
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int h = -1; h <= 4; h++) k.block(x0 + dx, y + h, z0 + FOLD_LEN, stone());
        // the door you keep passing: a pane of dream glass in a frame of pale wood, in the east wall
        int dzDoor = z0 + FOLD_DOOR;
        BlockState post = Blocks.PALE_OAK_LOG.defaultBlockState();
        List<BlockPos> door = List.of(new BlockPos(x0 + 2, y, dzDoor), new BlockPos(x0 + 2, y + 1, dzDoor));
        for (BlockPos p : door) k.block(p.getX(), p.getY(), p.getZ(), glass());
        for (int h = 0; h <= 2; h++) {
            k.block(x0 + 2, y + h, dzDoor - 1, post);
            k.block(x0 + 2, y + h, dzDoor + 1, post);
        }
        k.block(x0 + 2, y + 2, dzDoor, post);
        // beyond it: a short way east under a roof, and the Waking Door
        for (int dx = 3; dx <= 8; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                k.ground(x0 + dx, y - 1, dzDoor + dz, k.road);
                for (int h = 0; h <= 2; h++) k.clear(x0 + dx, y + h, dzDoor + dz);
                k.block(x0 + dx, y + 3, dzDoor + dz, stone());
            }
            k.block(x0 + dx, y, dzDoor - 2, stone());
            k.block(x0 + dx, y + 1, dzDoor - 2, stone());
            k.block(x0 + dx, y + 2, dzDoor - 2, stone());
            k.block(x0 + dx, y, dzDoor + 2, stone());
            k.block(x0 + dx, y + 1, dzDoor + 2, stone());
            k.block(x0 + dx, y + 2, dzDoor + 2, stone());
        }
        int doorX = x0 + 9;
        sideDoor(k, doorX, y, dzDoor);
        k.goal(new AABB(doorX + 0.2, y, dzDoor - 1, doorX + 0.8, y + 4, dzDoor + 2));
        k.pathTo(new Vec3(x0 + 0.5, y + 0.2, dzDoor + 0.5));
        k.pathTo(new Vec3(x0 + 5.5, y + 0.2, dzDoor + 0.5));
        BlockPos at = place ? new BlockPos(doorX + 15, y, dzDoor) : null;
        if (place) for (int dz = -2; dz <= 2; dz++) for (int dx = 10; dx <= 12; dx++) k.ground(x0 + dx, y - 1, dzDoor + dz, k.road);
        k.at(y, z0 + FOLD_LEN + 1);
        k.room = new Course.Room(new BlockPos(x0, y, z0 + FOLD_LEN / 2), 2, null, door, List.of(), -1, z0 + FOLD_FROM, z0 + FOLD_TO, y, at);
    }

    /** A Waking Door across x (you walk east into it): posts at z-2 and z+2, lintel, veil. */
    private static void sideDoor(CourseKit k, int x, int y, int z) {
        BlockState light = k.doorLight;
        for (int h = 0; h <= 4; h++) {
            k.block(x, y + h, z - 2, light);
            k.block(x, y + h, z + 2, light);
        }
        for (int dz = -2; dz <= 2; dz++) k.block(x, y + 4, z + dz, light);
        BlockState veil = LucidiumContent.WAKING_VEIL.get().defaultBlockState().setValue(WakingVeilBlock.AXIS, Direction.Axis.X);
        for (int dz = -1; dz <= 1; dz++) {
            k.ground(x, y - 1, z + dz, k.road);
            for (int h = 0; h <= 3; h++) k.block(x, y + h, z + dz, veil);
        }
    }

    // ---- no door ------------------------------------------------------------------------------------------------------

    /** The sealed room's walls, in the order of {@link Course.Room#walls}. */
    public static final int WEST = 0, EAST = 1, NORTH = 2;

    private static void noDoor(CourseKit k, boolean place, long seed) {
        k.road(4).ring();
        int x0 = k.x, y = k.y, z0 = k.z;
        int right = (int) Math.floorMod(seed >> 5, 3);
        // the room: inside x0-4..x0+4, z0+1..z0+9, five high; walls a block thick, the wrong walls backed with stone
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = 0; dz <= 10; dz++) {
                boolean wall = Math.abs(dx) == 5 || dz == 0 || dz == 10;
                k.ground(x0 + dx, y - 1, z0 + dz, wall ? stone() : k.road);
                for (int h = 0; h <= 4; h++) {
                    if (wall) k.block(x0 + dx, y + h, z0 + dz, stone());
                    else k.clear(x0 + dx, y + h, z0 + dz);
                }
                k.block(x0 + dx, y + 5, z0 + dz, Math.abs(dx) == 2 && (dz == 3 || dz == 7) ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : stone());
            }
        // the way in (it closes once you're inside)
        List<BlockPos> way = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int h = 0; h <= 2; h++) {
                k.clear(x0 + dx, y + h, z0);
                way.add(new BlockPos(x0 + dx, y + h, z0));
            }
        List<AABB> walls = List.of(new AABB(x0 - 5, y, z0 + 1, x0 - 4, y + 5, z0 + 10), new AABB(x0 + 5, y, z0 + 1, x0 + 6, y + 5, z0 + 10),
                new AABB(x0 - 4, y, z0 + 10, x0 + 5, y + 5, z0 + 11));
        for (int w = 0; w < 3; w++) {
            if (w == right) continue;
            // behind a wrong wall there's only more wall
            for (int d = 1; d <= 3; d++)
                for (int s = -4; s <= 4; s++)
                    for (int h = 0; h <= 4; h++) {
                        switch (w) {
                            case WEST -> k.block(x0 - 5 - d, y + h, z0 + 5 + s, stone());
                            case EAST -> k.block(x0 + 5 + d, y + h, z0 + 5 + s, stone());
                            default -> k.block(x0 + s, y + h, z0 + 10 + d, stone());
                        }
                    }
        }
        // behind the right wall: a landing and the Waking Door
        BlockPos at = null;
        switch (right) {
            case NORTH -> {
                for (int dz = 11; dz <= 15; dz++) for (int dx = -1; dx <= 1; dx++) k.ground(x0 + dx, y - 1, z0 + dz, k.road);
                int dz = z0 + 16;
                for (int h = 0; h <= 4; h++) {
                    k.block(x0 - 2, y + h, dz, k.doorLight);
                    k.block(x0 + 2, y + h, dz, k.doorLight);
                }
                for (int dx = -2; dx <= 2; dx++) k.block(x0 + dx, y + 4, dz, k.doorLight);
                BlockState veil = LucidiumContent.WAKING_VEIL.get().defaultBlockState().setValue(WakingVeilBlock.AXIS, Direction.Axis.Z);
                for (int dx = -1; dx <= 1; dx++) {
                    k.ground(x0 + dx, y - 1, dz, k.road);
                    for (int h = 0; h <= 3; h++) k.block(x0 + dx, y + h, dz, veil);
                }
                k.goal(new AABB(x0 - 1, y, dz + 0.3, x0 + 2, y + 4, dz + 0.7));
                k.pathTo(new Vec3(x0 + 0.5, y + 0.2, z0 + 13.5));
                if (place) at = new BlockPos(x0, y, dz + 14);
            }
            default -> {
                int side = right == WEST ? -1 : 1;
                for (int d = 6; d <= 10; d++) for (int dz = -1; dz <= 1; dz++) k.ground(x0 + side * d, y - 1, z0 + 5 + dz, k.road);
                int dx = x0 + side * 11;
                sideDoor(k, dx, y, z0 + 5);
                k.goal(new AABB(dx + 0.2, y, z0 + 4, dx + 0.8, y + 4, z0 + 7));
                k.pathTo(new Vec3(x0 + side * 8 + 0.5, y + 0.2, z0 + 5.5));
                if (place) at = new BlockPos(dx + side * 14, y, z0 + 5);
            }
        }
        k.at(y, z0 + 11);
        k.room = new Course.Room(new BlockPos(x0, y, z0 + 5), 4, null, List.copyOf(way), walls, right, 0, 0, y, at);
    }

    /** No door: the opening a made door cuts in a wall (3 wide, 3 high in its middle), and its frame of pale light. */
    public static List<BlockPos> opening(AABB wall) {
        List<BlockPos> out = new ArrayList<>();
        boolean alongZ = wall.getXsize() < wall.getZsize();
        int cx = (int) Math.floor(wall.minX + (alongZ ? 0 : wall.getXsize() / 2)), cz = (int) Math.floor(wall.minZ + (alongZ ? wall.getZsize() / 2 : 0));
        int y = (int) Math.floor(wall.minY);
        for (int s = -1; s <= 1; s++) for (int h = 0; h <= 2; h++) out.add(alongZ ? new BlockPos(cx, y + h, cz + s) : new BlockPos(cx + s, y + h, cz));
        return out;
    }

    public static List<BlockPos> frame(AABB wall) {
        List<BlockPos> out = new ArrayList<>();
        boolean alongZ = wall.getXsize() < wall.getZsize();
        int cx = (int) Math.floor(wall.minX + (alongZ ? 0 : wall.getXsize() / 2)), cz = (int) Math.floor(wall.minZ + (alongZ ? wall.getZsize() / 2 : 0));
        int y = (int) Math.floor(wall.minY);
        for (int h = 0; h <= 3; h++) {
            out.add(alongZ ? new BlockPos(cx, y + h, cz - 2) : new BlockPos(cx - 2, y + h, cz));
            out.add(alongZ ? new BlockPos(cx, y + h, cz + 2) : new BlockPos(cx + 2, y + h, cz));
        }
        for (int s = -1; s <= 1; s++) out.add(alongZ ? new BlockPos(cx, y + 3, cz + s) : new BlockPos(cx + s, y + 3, cz));
        return out;
    }

    // ---- the duel -----------------------------------------------------------------------------------------------------

    private static void duel(CourseKit k) {
        k.road(4).ring();
        int z0 = k.z;
        BlockPos centre = plaza(k, 9, false);
        // a ring of dream glass round it, open where you come in
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI * 2 / 72;
            int dx = (int) Math.round(Math.cos(a) * 10), dz = (int) Math.round(Math.sin(a) * 10);
            if (dz < -8 && Math.abs(dx) <= 1) continue;
            for (int h = 0; h <= 1; h++) k.block(centre.getX() + dx, centre.getY() + h, centre.getZ() + dz, glass());
            k.block(centre.getX() + dx, centre.getY() - 1, centre.getZ() + dz, k.road);
        }
        k.pathTo(Vec3.atBottomCenterOf(centre));
        k.at(k.y, z0 + 21);
        k.room = new Course.Room(centre, 9, null, List.of(), List.of(), -1, 0, 0, k.y, null);
    }

    /** The far end of the plaza a goal can move to: a spot on the floor this far from {@code from}, or null. */
    public static @Nullable BlockPos onFloor(Course.Room r, double x, double z, int near, int far, java.util.function.IntUnaryOperator random) {
        for (int i = 0; i < 16; i++) {
            double a = random.applyAsInt(3600) / 3600.0 * Math.PI * 2, d = near + random.applyAsInt(far - near + 1);
            int gx = (int) Math.floor(x + Math.cos(a) * d), gz = (int) Math.floor(z + Math.sin(a) * d);
            double ox = gx + 0.5 - r.centre().getX() - 0.5, oz = gz + 0.5 - r.centre().getZ() - 0.5;
            if (ox * ox + oz * oz > (r.half() - 2) * (r.half() - 2)) continue;
            return new BlockPos(gx, r.centre().getY(), gz);
        }
        return null;
    }
}
