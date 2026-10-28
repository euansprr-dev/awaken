package dev.awaken.ground.api;

import dev.awaken.ground.wild.WildLife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Grass that lives (SPEC §41.2, the Wild Lands): what herds and hunters do to the wild's plants. Herds crop their grazing
 * lawns and press trails; the ground module grows both back on random ticks (lawns over a few days, trails over a day or
 * two), so only land near players changes and nothing is kept per tick. Server thread.
 *
 * <p>{@code pos} is the block a body stands in (its feet); the plant there, or the one on the ground block passed, is the
 * one touched. Every call is cheap and safe on any block: a block with nothing to crop or press is left alone.
 */
public final class WildPlants {
    private WildPlants() {}

    /** Trample strengths: one body (a person sprinting or riding, a pack), a herd moving, a stampede. */
    public static final int LIGHT = 1, HERD = 2, STAMPEDE = 3;

    /**
     * Grazed: long grass or rye (any of its blocks) cropped to meadow grass that remembers what it was and grows back over
     * a few days; vanilla tall grass and large fern cropped to short grass. True when something changed.
     */
    public static boolean crop(ServerLevel level, BlockPos pos) {
        return WildLife.crop(level, pos);
    }

    /**
     * Pressed flat along a way (a trail): meadow grass, long grass, rye and the game's own grass lie down as trampled grass
     * that springs back over a day or two to what stood there. {@code strength} 1..3 ({@link #LIGHT}, {@link #HERD},
     * {@link #STAMPEDE}): how flat and how long it stays down; pressing a trail again keeps it fresh. True when something
     * changed.
     */
    public static boolean trample(ServerLevel level, BlockPos pos, int strength) {
        return WildLife.trample(level, pos, strength);
    }

    /**
     * How hidden someone crouching at {@code pos} is, 0 (in the open) to 1 (gone): from the plants in their block and the
     * one above, and a little from the four beside (the edge of a swathe hides less than its middle). Rye hides a crouched
     * body completely, long grass and bracken almost; meadow grass a little. Either side; no allocation.
     */
    public static double coverAt(Level level, BlockPos pos) {
        return WildLife.coverAt(level, pos);
    }
}
