package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spotting through the binoculars (SPEC §39.10). A module that owns findable things (creatures, lairs, landmarks, gates,
 * wonders) registers a {@link Source} at init; the binoculars' server side asks each source what this player hasn't
 * found yet within reach, holds the centre of the view on one and then asks its source to find it, by the source's own
 * discovery path (the same reward as going there). Server side only. A source offers nothing the player has already
 * found, never loads a chunk, and re-checks its thing in {@link Source#find} (it may have gone since it was offered).
 */
public final class Spotting {
    private Spotting() {}

    /**
     * How many blocks of leaves or bushes a look can pass through and still see what's behind (a hedge, the edge of a
     * crown, bushes in the way). A stand of trees is more than this on any line across it, so a forest still hides things.
     */
    public static final int FOLIAGE = 5;

    /** Leaves and bushes: you can make out a creature through them, which you can't through stone. */
    public static boolean foliage(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.getBlock() instanceof VegetationBlock;
    }

    /**
     * Whether the eye can see from {@code from} to {@code to} through loaded blocks: by what you see (glass and water hide
     * nothing), with up to {@link #FOLIAGE} blocks of foliage on the way. An unloaded chunk hides it.
     */
    public static boolean clearLine(ServerLevel level, Vec3 from, Vec3 to) {
        int[] leaves = {0};
        return BlockGetter.traverseBlocks(from, to, level, (l, pos) -> {
            if (!dev.awaken.core.api.Ready.at(l, pos)) return Boolean.FALSE;
            BlockState state = l.getBlockState(pos);
            if (state.isAir()) return null;
            VoxelShape shape = state.getVisualShape(l, pos, CollisionContext.empty());
            if (shape.isEmpty() || shape.clip(from, to, pos) == null) return null;
            return foliage(state) && ++leaves[0] <= FOLIAGE ? null : Boolean.FALSE;
        }, l -> Boolean.TRUE);
    }

    public interface Source {
        /** Whether what it offers moves (creatures: asked at every look) or stands (asked about once a second). */
        boolean moves();

        /** Offers to {@code out} what this player hasn't found within {@code reach} blocks of their eyes. */
        void gather(ServerPlayer p, double reach, Sink out);

        /** The player held the glass on {@code ref}: find it now. Returns whether it was a new find. */
        boolean find(ServerPlayer p, Object ref);
    }

    public interface Sink {
        /** Something standing, by its bounds; {@code key} names it within its source, the same at every gather. */
        void place(long key, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Object ref);

        /** Something moving: its bounds are read from the entity at each look. */
        void entity(Entity e, Object ref);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source s) {
        SOURCES.add(s);
    }

    public static List<Source> sources() {
        return SOURCES;
    }
}
