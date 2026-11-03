package dev.awaken.lucidium.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A trial compiled for one dream (SPEC §9.3): where you arrive, the doorway that opens when you take control, the rings
 * a fall brings you back to, the goal, the true path H shows, and the blocks the dream lays as you come ("reveals").
 * Built off-thread with the blueprint; read by the server while the dream runs.
 *
 * @param par     the solver's time from the doorway to the goal in ticks (0 for a Room)
 * @param floorY  the lowest floor of the course: a fall this far below it is caught
 * @param reveals blocks the dream places ahead of you as you come (a road laying itself, walls sliding in), in course order
 */
public record Course(Trial trial, Vec3 start, float startYaw, List<BlockPos> doorway, List<Ring> rings, AABB goal,
                     @Nullable BlockPos bell, int floorY, int par, List<Vec3> path, List<Reveal> reveals, int seconds,
                     @Nullable Room room) {

    /** A checkpoint: pass through {@code box} and a fall brings you back to {@code at}, facing {@code yaw}. */
    public record Ring(int index, Vec3 at, float yaw, AABB box) {}

    /**
     * A Room's layout (SPEC §9.5), read by its rule while the dream runs.
     *
     * @param centre  the middle of the room's floor (feet height)
     * @param half    how far the floor reaches each way from the centre: a moving goal keeps to it
     * @param goal    where the goal first stands, or null while the rule hides it (the long fall's, what's wrong here's)
     * @param things  the three wrong things (what's wrong here), the fold's side door, no door's way in
     * @param walls   no door's three walls (west, east, north), each a box to look at
     * @param right   the wall that opens (no door)
     * @param loopFrom the fold: walk past {@code loopTo} and you're back {@code loopTo - loopFrom} blocks
     * @param edgeY   the long fall: the road's edge (feet), the fall measured from it
     * @param place   where the hidden place stands beyond dream glass, or null
     */
    public record Room(BlockPos centre, int half, @Nullable BlockPos goal, List<BlockPos> things, List<AABB> walls, int right,
                       int loopFrom, int loopTo, int edgeY, @Nullable BlockPos place) {}

    /** A block the dream lays when someone is within reach of {@code along} (the course's +z). */
    public record Reveal(BlockPos pos, BlockState state, int along) {}

    /** How far ahead of the furthest dreamer the dream lays its reveals. */
    public static final int AHEAD = 14;

    public Ring ring(int i) {
        return rings.get(Math.max(0, Math.min(rings.size() - 1, i)));
    }
}
