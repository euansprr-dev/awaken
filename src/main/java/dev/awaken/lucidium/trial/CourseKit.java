package dev.awaken.lucidium.trial;

import dev.awaken.body.RunnerRules;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.block.WakingVeilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a trial's course along +z, piece by piece, and proves it (SPEC §9.3, like the Gauntlet's solver): every piece
 * records what a runner does there, and {@link #solve} walks them in order with the body's own probes
 * ({@link RunnerRules#findWall}, {@link RunnerRules#findEdge}) over the course's blocks, so a wall you're meant to run
 * on really is runnable, and an edge you're meant to hang from really holds, by the same code the body moves with.
 * The time it takes is the course's par.
 *
 * <p>The cursor is the middle lane's x, the feet's y and the next z. The road is three wide (x-1 to x+1).
 */
public final class CourseKit {
    /** The runner's pace on the road and on a wall (blocks a tick), as the Gauntlet's solver has them. */
    static final double RUN = 0.26, WALLRUN = 0.32;
    /** A sprint jump's flat gap at most, and a wall-run's span. */
    static final int MAX_GAP = 3, MAX_RUN = 7;

    private final Map<Long, BlockState> blocks = new LinkedHashMap<>();
    private final List<Course.Reveal> reveals = new ArrayList<>();
    private final List<Course.Ring> rings = new ArrayList<>();
    private final List<Vec3> path = new ArrayList<>();
    private final List<Step> steps = new ArrayList<>();
    final int x;
    int y, z;
    private int minFloor;
    /** The road lays itself as you come (the first dream's twist) instead of being there from the start. */
    boolean layRoad;
    /** Walls slide in from the clouds as you come (the wall's twist). */
    boolean slideWalls;
    private @Nullable AABB goal;
    private @Nullable BlockPos bell;
    private String failure = "";

    final BlockState road = LucidiumContent.DREAM_ROAD.get().defaultBlockState();
    final BlockState wall = LucidiumContent.DREAM_WALL.get().defaultBlockState();
    final BlockState ledge = LucidiumContent.DREAM_LEDGE.get().defaultBlockState();
    final BlockState cloud = LucidiumContent.DREAM_CLOUD.get().defaultBlockState();
    final BlockState doorLight = LucidiumContent.WAKING_DOOR.get().defaultBlockState();

    public CourseKit(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.minFloor = y;
    }

    // ---- what a runner does on each piece -------------------------------------------------------------------------

    enum Kind { ROAD, GAP, UP, DOWN, WALLRUN, CHAIN, HANG, SHIMMY, RAIL, BEAM }

    /** One piece as the solver sees it: its kind, its length and where it starts (feet). */
    record Step(Kind kind, int len, int x, int y, int z, int side) {}

    // ---- pieces -------------------------------------------------------------------------------------------------------

    /** The road, three wide, with soft cloud under its edges now and then. */
    public CourseKit road(int len) {
        for (int k = 0; k < len; k++) {
            for (int w = -1; w <= 1; w++) floor(x + w, y - 1, z + k);
            if (Math.floorMod(z + k, 5) == 0) {
                put(x - 2, y - 2, z + k, cloud, false);
                put(x + 2, y - 2, z + k, cloud, false);
            }
            if (k % 2 == 0) path.add(new Vec3(x + 0.5, y + 0.2, z + k + 0.5));
        }
        steps.add(new Step(Kind.ROAD, len, x, y, z, 0));
        z += len;
        return this;
    }

    /** A road one lane wide on {@code side} (-1 west, +1 east): the way narrows toward a wall to run. */
    public CourseKit lane(int len, int side) {
        for (int k = 0; k < len; k++) {
            floor(x + side, y - 1, z + k);
            if (k % 2 == 0) path.add(new Vec3(x + side + 0.5, y + 0.2, z + k + 0.5));
        }
        steps.add(new Step(Kind.ROAD, len, x, y, z, side));
        z += len;
        return this;
    }

    /** Open air to jump. */
    public CourseKit gap(int len) {
        steps.add(new Step(Kind.GAP, len, x, y, z, 0));
        path.add(new Vec3(x + 0.5, y + 1.1, z + len / 2.0));
        z += len;
        return this;
    }

    /** The road goes up a block (the first block of the new height is the step). */
    public CourseKit up() {
        steps.add(new Step(Kind.UP, 1, x, y, z, 0));
        y += 1;
        return this;
    }

    /** The road drops {@code h} blocks (nothing hurts in a dream; a drop is just quicker). */
    public CourseKit down(int h) {
        steps.add(new Step(Kind.DOWN, h, x, y, z, 0));
        y -= h;
        minFloor = Math.min(minFloor, y);
        return this;
    }

    /** A ring over the next road block: a fall brings you back here. */
    public CourseKit ring() {
        int i = rings.size();
        rings.add(new Course.Ring(i, new Vec3(x + 0.5, y, z + 0.5), 0f, new AABB(x - 1.5, y - 1, z, x + 2.5, y + 4, z + 1)));
        return this;
    }

    /**
     * A wall-run: {@code len} of open air with a wall on {@code side} from a block before it to a block past it, five
     * high so a body running at a jump's height has wall beside its feet and its head all the way.
     */
    public CourseKit wallRun(int len, int side) {
        int wx = x + side * 2;
        for (int k = -2; k <= len; k++)
            for (int h = -2; h <= 3; h++) put(wx, y + h, z + k, wall, slideWalls);
        steps.add(new Step(Kind.WALLRUN, len, x, y, z, side));
        for (int k = 0; k < len; k += 2) path.add(new Vec3(x + side * 0.9 + 0.5, y + 1.4, z + k + 0.5));
        z += len;
        return this;
    }

    /**
     * Walls on alternate sides across one long gap: run the first, jump off it to the second, and so on ({@code links}
     * walls of {@code each} blocks, starting on {@code side}).
     */
    public CourseKit wallChain(int links, int each, int side) {
        int s = side;
        int z0 = z;
        for (int l = 0; l < links; l++) {
            int wx = x + s * 2;
            for (int k = (l == 0 ? -2 : -1); k <= each; k++)
                for (int h = -2; h <= 3; h++) put(wx, y + h, z0 + l * each + k, wall, slideWalls);
            for (int k = 0; k < each; k += 2) path.add(new Vec3(x + s * 0.9 + 0.5, y + 1.4, z0 + l * each + k + 0.5));
            s = -s;
        }
        steps.add(new Step(Kind.CHAIN, links * each, x, y, z, side * (1000 + links * 10 + each))); // the side's sign, the links and each wall's span
        z += links * each;
        return this;
    }

    /**
     * A face across the way, {@code h} high (its top a glowing lip): jump at it to hang, then climb out on top. The
     * road carries on up there.
     */
    public CourseKit hangUp(int h, int depth) {
        for (int k = 0; k < depth; k++)
            for (int w = -1; w <= 1; w++) {
                for (int yy = y - 1; yy < y + h - 1; yy++) put(x + w, yy, z + k, wall, false);
                put(x + w, y + h - 1, z + k, ledge, false);
            }
        steps.add(new Step(Kind.HANG, h, x, y, z, 0));
        path.add(new Vec3(x + 0.5, y + h - 1.4, z - 0.3));
        y += h;
        z += depth;
        return this;
    }

    /**
     * A gap {@code len} long you cross hanging: a lip on {@code side} at hand height (a shelf out of a wall set back
     * behind it), a ceiling a block over it so you can't stand on it, and too far to jump. Hang on it from the road,
     * move along, and drop onto the road beyond.
     */
    public CourseKit shimmy(int len, int side) {
        int lx = x + side * 2;
        for (int k = -1; k <= len; k++) {
            put(lx, y + 1, z + k, ledge, false);            // the lip, its top two blocks over the feet
            put(lx, y + 3, z + k, wall, false);             // the ceiling: one block of room over the lip
            for (int h = -1; h <= 3; h++) put(lx + side, y + h, z + k, wall, false);   // the wall behind
        }
        steps.add(new Step(Kind.SHIMMY, len, x, y, z, side));
        for (int k = 0; k < len; k += 2) path.add(new Vec3(x + side * 0.9 + 0.5, y + 0.5, z + k + 0.5));
        z += len;
        return this;
    }

    /** A rail across the road (a fence, or a wall on {@code onBlock}): run at it to vault it. */
    public CourseKit rail(boolean wallRail) {
        BlockState s = wallRail ? Blocks.DIORITE_WALL.defaultBlockState() : Blocks.PALE_OAK_FENCE.defaultBlockState();
        for (int w = -1; w <= 1; w++) {
            floor(x + w, y - 1, z);
            put(x + w, y, z, s, false);
        }
        steps.add(new Step(Kind.RAIL, 1, x, y, z, 0));
        path.add(new Vec3(x + 0.5, y + 1.3, z + 0.5));
        z += 1;
        return this;
    }

    /** A low beam across the road, a block over it: slide under. */
    public CourseKit beam() {
        for (int w = -2; w <= 2; w++) {
            if (Math.abs(w) <= 1) floor(x + w, y - 1, z);
            put(x + w, y + 1, z, wall, false);
            if (Math.abs(w) == 2) put(x + w, y, z, wall, false);
        }
        steps.add(new Step(Kind.BEAM, 1, x, y, z, 0));
        path.add(new Vec3(x + 0.5, y + 0.3, z + 0.5));
        z += 1;
        return this;
    }

    /** The Waking Door at the end of the road: a frame of pale light, its opening a sheet you walk into. */
    public CourseKit door() {
        road(3);
        int dz = z;
        for (int w = -1; w <= 1; w++) floor(x + w, y - 1, dz);
        for (int h = 0; h <= 4; h++) {
            put(x - 2, y + h, dz, doorLight, false);
            put(x + 2, y + h, dz, doorLight, false);
        }
        for (int w = -2; w <= 2; w++) put(x + w, y + 4, dz, doorLight, false);
        BlockState veil = LucidiumContent.WAKING_VEIL.get().defaultBlockState().setValue(WakingVeilBlock.AXIS, Direction.Axis.Z);
        for (int w = -1; w <= 1; w++) for (int h = 0; h <= 3; h++) put(x + w, y + h, dz, veil, false);
        goal = new AABB(x - 1, y, dz + 0.3, x + 2, y + 4, dz + 0.7);
        path.add(new Vec3(x + 0.5, y + 1, dz + 0.5));
        z = dz + 1;
        // a little landing behind it, so the frame reads as standing on something
        for (int w = -2; w <= 2; w++) floor(x + w, y - 1, z);
        return this;
    }

    /** A pearl dais at the end of the road with a bell's place on it, the Waking Door behind. Returns the bell's spot. */
    public BlockPos dais() {
        road(2);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = 0; dz < 5; dz++) floor(x + dx, y - 1, z + dz);
        BlockPos at = new BlockPos(x, y, z + 2);
        bell = at;
        path.add(Vec3.atBottomCenterOf(at));
        z += 5;
        door();
        return at;
    }

    // ---- blocks ------------------------------------------------------------------------------------------------------

    private void floor(int bx, int by, int bz) {
        put(bx, by, bz, road, layRoad);
        minFloor = Math.min(minFloor, by + 1);
    }

    private void put(int bx, int by, int bz, BlockState s, boolean reveal) {
        long key = BlockPos.asLong(bx, by, bz);
        if (reveal) reveals.add(new Course.Reveal(new BlockPos(bx, by, bz), s, bz));
        else blocks.put(key, s);
    }

    /** Clears a block in the course (a doorway cut through a remembered room). */
    void clear(int bx, int by, int bz) {
        blocks.put(BlockPos.asLong(bx, by, bz), Blocks.AIR.defaultBlockState());
    }

    void block(int bx, int by, int bz, BlockState s) {
        blocks.put(BlockPos.asLong(bx, by, bz), s);
    }

    public Map<Long, BlockState> blocks() {
        return blocks;
    }

    public String failure() {
        return failure;
    }

    // ---- the solver --------------------------------------------------------------------------------------------------

    /** The course's blocks (reveals included, as everything is laid by the time you get there) as a level to probe. */
    final class View implements BlockGetter {
        private final Map<Long, BlockState> all = new HashMap<>(blocks);

        View() {
            for (Course.Reveal r : reveals) all.put(r.pos().asLong(), r.state());
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState s = all.get(pos.asLong());
            return s == null ? Blocks.AIR.defaultBlockState() : s;
        }

        @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 256; }
        @Override public int getMinY() { return 0; }
    }

    /**
     * Runs every piece with the body's probes. Returns the par in ticks (the runner's time from the start to the goal),
     * or -1 with {@link #failure} saying where and why.
     */
    public int solve() {
        View v = new View();
        CollisionContext ctx = CollisionContext.empty();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double t = 0;
        for (Step s : steps) {
            switch (s.kind) {
                case ROAD -> t += s.len / RUN;
                case GAP -> {
                    if (s.len > MAX_GAP) return fail(s, "a gap of " + s.len + " is past a sprint jump");
                    if (!solid(v, s.x, s.y - 1, s.z - 1) || !solid(v, s.x, s.y - 1, s.z + s.len)) return fail(s, "no road either side");
                    t += (s.len + 1) / RUN;
                }
                case UP -> {
                    if (!solid(v, s.x, s.y, s.z)) return fail(s, "no step");
                    t += 3;
                }
                case DOWN -> t += 6;
                case WALLRUN -> {
                    if (s.len > MAX_RUN) return fail(s, "a wall-run of " + s.len + " is too long");
                    if (!wallAlong(v, ctx, m, s.x, s.y, s.z, s.len, s.side)) return fail(s, "the wall doesn't hold a run");
                    if (!solid(v, s.x + s.side, s.y - 1, s.z + s.len)) return fail(s, "no landing");
                    t += s.len / WALLRUN + 6;
                }
                case CHAIN -> {
                    int side = Integer.signum(s.side), links = (Math.abs(s.side) / 10) % 10, each = Math.abs(s.side) % 10;
                    if (links > RunnerRules.LINKS) return fail(s, links + " walls is past a chain");
                    int sd = side;
                    for (int l = 0; l < links; l++) {
                        if (each > RunnerRules.runLength(l) * RunnerRules.RUN_SPEED_MIN) return fail(s, "wall " + l + " is longer than its run");
                        if (!wallAlong(v, ctx, m, s.x, s.y, s.z + l * each, each, sd)) return fail(s, "wall " + l + " doesn't hold a run");
                        sd = -sd;
                    }
                    t += s.len / WALLRUN + 8 * links;
                }
                case HANG -> {
                    // from a jump's height in front of the face
                    double px = s.x + 0.5, pz = s.z - 0.3 - 0.02;
                    RunnerRules.Edge e = null;
                    for (double up = 0.0; up <= 1.2 && e == null; up += 0.2)
                        e = RunnerRules.findEdge(v, ctx, m, px, s.y + up, pz, 2, 1, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
                    if (e == null) return fail(s, "no edge to hang from at " + s.len + " high");
                    if (RunnerRules.climbOut(v, ctx, m, e) == null) return fail(s, "no climbing out on top");
                    t += 24;
                }
                case SHIMMY -> {
                    if (s.len <= MAX_GAP + 1) return fail(s, "a shimmy gap of " + s.len + " could be jumped");
                    int axis = 0, sign = s.side;
                    double px = s.x + 0.5 + s.side * (1.0 - 0.3 - 0.02 + 0.5);
                    for (int k = 0; k <= s.len; k++) {
                        RunnerRules.Edge e = RunnerRules.findEdge(v, ctx, m, px, s.y, s.z + k - 0.5 + 0.01, axis, sign, RunnerRules.HANG_REACH,
                                RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
                        if (e == null) return fail(s, "the lip doesn't hold at " + k);
                        if (k > 0 && k < s.len && RunnerRules.climbOut(v, ctx, m, e) != null) return fail(s, "you could stand on the lip at " + k);
                    }
                    if (!solid(v, s.x + s.side, s.y - 1, s.z + s.len)) return fail(s, "no road under the end of the lip");
                    t += s.len / RunnerRules.SHIMMY + 16;
                }
                case RAIL -> t += 8;
                case BEAM -> {
                    if (!v.getBlockState(new BlockPos(s.x, s.y, s.z)).isAir() || v.getBlockState(new BlockPos(s.x, s.y + 1, s.z)).isAir())
                        return fail(s, "the beam isn't a slide");
                    t += 6;
                }
            }
        }
        if (goal == null) {
            failure = "no goal";
            return -1;
        }
        return (int) Math.ceil(t);
    }

    private int fail(Step s, String why) {
        failure = s.kind.name().toLowerCase() + " at z " + s.z + ": " + why;
        return -1;
    }

    private static boolean solid(BlockGetter v, int bx, int by, int bz) {
        BlockPos p = new BlockPos(bx, by, bz);
        return !v.getBlockState(p).getCollisionShape(v, p).isEmpty();
    }

    /**
     * A run along the wall on {@code side}: the probe finds it beside a body at a jump's height every half block, with no
     * ground under it (the body clear of the take-off and the landing at either end).
     */
    private static boolean wallAlong(BlockGetter v, CollisionContext ctx, BlockPos.MutableBlockPos m, int cx, int cy, int cz, int len, int side) {
        // the lane beside the wall, the body's side just off the face
        double face = side > 0 ? cx + 2 : cx - 1;
        double px = face - side * (0.3 + 0.05);
        for (double k = 0.75; k <= len - 0.75; k += 0.5) {
            RunnerRules.Wall w = RunnerRules.findWall(v, ctx, m, px, cy + 1.2, cz + k, 0f, RunnerRules.RUN_REACH);
            if (w == null || w.side() != side) return false;
            if (RunnerRules.groundWithin(v, ctx, m, px, cy + 1.2, cz + k, RunnerRules.RUN_MIN_HEIGHT)) return false;
        }
        return true;
    }

    // ---- result ------------------------------------------------------------------------------------------------------

    /**
     * The finished course. {@code start} is where you arrive (in your room), {@code doorway} the blocks that clear when
     * you take control; the first ring stands at the start so a fall before any other ring brings you back there.
     */
    public Course build(Trial trial, Vec3 start, float startYaw, List<BlockPos> doorway, int par, int seconds) {
        List<Course.Ring> all = new ArrayList<>();
        all.add(new Course.Ring(0, start, startYaw, new AABB(start, start)));
        for (Course.Ring r : rings) all.add(new Course.Ring(all.size(), r.at(), r.yaw(), r.box()));
        List<Course.Reveal> sorted = new ArrayList<>(reveals);
        sorted.sort((a, b) -> Integer.compare(a.along(), b.along()));
        return new Course(trial, start, startYaw, List.copyOf(doorway), List.copyOf(all), goal == null ? new AABB(0, -64, 0, 0, -64, 0) : goal,
                bell, minFloor, par, List.copyOf(path), List.copyOf(sorted), seconds);
    }
}
