package dev.awaken.caves.life;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Where the underlife lives (SPEC §42.5), read from the blocks round a cell: a floor to walk, a wall or roof to crawl,
 * a roof high enough for a roost, a still pool, a light. Pure reads of a {@link BlockGetter}, so the client's director
 * and the GameTest ask the same questions.
 */
public final class UnderSites {
    private UnderSites() {}

    public static final int FLOOR = 1, WALL = 2, ROOF = 4, ROOST = 8, POOL = 16;
    /** Open cells a roost needs under its roof, and a light's least emission for moths to come to it. */
    public static final int ROOST_ROOM = 5, MOTH_LIGHT = 12;

    /** Rock (or any full block) a creature can stand on, crawl over or hang from. */
    public static boolean rock(BlockState s) {
        return s.isSolidRender();
    }

    /** Open to a creature: air, or something it passes through (lichen, pebbles, a puddle), never water. */
    public static boolean open(BlockState s) {
        return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty();
    }

    /** Still water a fish can swim in: a source. */
    public static boolean water(BlockState s) {
        FluidState f = s.getFluidState();
        return f.is(FluidTags.WATER) && f.isSource();
    }

    /** The sites at an open cell (or a pool's surface cell), as bits. */
    public static int classify(BlockGetter level, BlockPos.MutableBlockPos m, int x, int y, int z) {
        BlockState here = level.getBlockState(m.set(x, y, z));
        if (water(here)) return pool(level, m, x, y, z) ? POOL : 0;
        if (!open(here)) return 0;
        int bits = 0;
        if (rock(level.getBlockState(m.set(x, y - 1, z)))) bits |= FLOOR;
        if (rock(level.getBlockState(m.set(x, y + 1, z)))) {
            bits |= ROOF;
            if (room(level, m, x, y, z, ROOST_ROOM) >= ROOST_ROOM) bits |= ROOST;
        }
        if (wall(level, m, x, y, z) != null) bits |= WALL;
        return bits;
    }

    /** A pool's surface: a source with open air over it and water on at least two sides, so a fish has room to turn. */
    public static boolean pool(BlockGetter level, BlockPos.MutableBlockPos m, int x, int y, int z) {
        if (!water(level.getBlockState(m.set(x, y, z))) || !open(level.getBlockState(m.set(x, y + 1, z)))) return false;
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) if (water(level.getBlockState(m.set(x + d.getStepX(), y, z + d.getStepZ())))) n++;
        return n >= 2;
    }

    /** The first side with rock beside an open cell, or null. */
    public static Direction wall(BlockGetter level, BlockPos.MutableBlockPos m, int x, int y, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (rock(level.getBlockState(m.set(x + d.getStepX(), y, z + d.getStepZ())))) return d;
        return null;
    }

    /** How many open cells run down from (x, y, z), itself included, up to {@code max}. */
    public static int room(BlockGetter level, BlockPos.MutableBlockPos m, int x, int y, int z, int max) {
        int n = 0;
        while (n < max && open(level.getBlockState(m.set(x, y - n, z)))) n++;
        return n;
    }

    /** True when a crawler at a point on a surface stays on it: rock behind the point and open in front. */
    public static boolean onSurface(BlockGetter level, BlockPos.MutableBlockPos m, double x, double y, double z, Direction out) {
        double bx = x - out.getStepX() * 0.1, by = y - out.getStepY() * 0.1, bz = z - out.getStepZ() * 0.1;
        if (!rock(level.getBlockState(m.set(floor(bx), floor(by), floor(bz))))) return false;
        return open(level.getBlockState(m.set(floor(x + out.getStepX() * 0.1), floor(y + out.getStepY() * 0.1), floor(z + out.getStepZ() * 0.1))));
    }

    /**
     * True when a body lying flat on a surface, reaching {@code r} from its middle, lies wholly on it: rock behind its middle
     * and behind every corner of the square round it, open in front of each (SPEC §42.4 "Settled": no half a beetle over a
     * ledge's edge, none sunk into the floor where a wall meets it). A square under a block wide touches at most four cells,
     * each holding a corner, so five points are enough.
     */
    public static boolean bodyOnSurface(BlockGetter level, BlockPos.MutableBlockPos m, double x, double y, double z, Direction out, double r) {
        if (!onSurface(level, m, x, y, z, out)) return false;
        // the face's two axes: x and z on a floor or roof, the other flat one and y on a wall
        int ux = out.getAxis() == Direction.Axis.X ? 0 : 1, uz = out.getAxis() == Direction.Axis.X ? 1 : 0;
        int vy = out.getAxis() == Direction.Axis.Y ? 0 : 1, vz = out.getAxis() == Direction.Axis.Y ? 1 : 0;
        for (int c = 0; c < 4; c++) {
            double su = (c & 1) == 0 ? -r : r, sv = (c & 2) == 0 ? -r : r;
            if (!onSurface(level, m, x + ux * su, y + vy * sv, z + uz * su + vz * sv, out)) return false;
        }
        return true;
    }

    /**
     * True when a body hanging from a point just under a roof, reaching {@code r} from its middle, hangs wholly from it: rock
     * over its middle and over every corner of the square round it, open under each (a bat, a glow-worm curtain).
     */
    public static boolean hangs(BlockGetter level, BlockPos.MutableBlockPos m, double x, double y, double z, double r) {
        int roof = floor(y) + 1;
        for (int c = 0; c < 5; c++) {
            int cx = floor(c == 4 ? x : x + ((c & 1) == 0 ? -r : r)), cz = floor(c == 4 ? z : z + ((c & 2) == 0 ? -r : r));
            if (!rock(level.getBlockState(m.set(cx, roof, cz))) || !open(level.getBlockState(m.set(cx, roof - 1, cz)))) return false;
        }
        return true;
    }

    /** The light a held item gives (a torch's 14), 0 for none: any block item whose block gives light. */
    public static int heldLight(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState().getLightEmission() : 0;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
