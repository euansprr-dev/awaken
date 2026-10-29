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

    /** The light a held item gives (a torch's 14), 0 for none: any block item whose block gives light. */
    public static int heldLight(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState().getLightEmission() : 0;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
