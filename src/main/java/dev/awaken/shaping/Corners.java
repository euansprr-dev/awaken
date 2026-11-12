package dev.awaken.shaping;

import dev.awaken.core.api.Ready;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * How edge cuts join at corners (SPEC §52.2), by the rules vanilla's {@code StairBlock.getStairsShape} uses. Behind a cut,
 * the same cut turned a quarter makes it an outer corner; in front of it, an inner corner; left or right by which way the
 * neighbour turns; and not when the same cut facing the same way sits alongside, so a straight run stays straight. A
 * corner lives in the block state ({@link ShapedBlock#CORNER}), worked out from the neighbours' states alone, on the knock
 * and again whenever a neighbour changes.
 * <p>
 * Real stairs count as the cut they read as (a bottom stair is a notch, a top one an under), so cuts and stairs join. A
 * stair can't see a cut, though: a real stair whose corner needs a cut becomes a cut of itself (it still drops the stair),
 * and is the real stair again as soon as the stair alone would turn the same corner (§52.3).
 */
public final class Corners {
    private Corners() {}

    /** How many stairs one change may turn into cuts or back, so nothing can run along a row. */
    private static final int SETTLE_MAX = 8;

    /** A block in edge terms: its cut and the way its front faces. */
    private record Edge(Shape shape, Direction front) {}

    /** This block as an edge, or null. With {@code stairsOnly} only real stairs count: what vanilla would see. */
    private static @Nullable Edge edge(BlockState s, boolean stairsOnly) {
        if (StairBlock.isStairs(s)) {
            return new Edge(s.getValue(StairBlock.HALF) == Half.BOTTOM ? Shape.NOTCH : Shape.UNDER, s.getValue(StairBlock.FACING).getOpposite());
        }
        if (stairsOnly || !(s.getBlock() instanceof ShapedBlock)) return null;
        Shape shape = s.getValue(ShapedBlock.SHAPE);
        return shape.edge() ? new Edge(shape, s.getValue(ShapedBlock.FACING)) : null;
    }

    /** The corner a cut of this shape, facing this way, turns here: read from the neighbours alone. */
    public static StairsShape corner(Shape shape, Direction front, BlockGetter level, BlockPos pos) {
        return corner(shape, front, level, pos, false);
    }

    private static StairsShape corner(Shape shape, Direction front, BlockGetter level, BlockPos pos, boolean stairsOnly) {
        if (!shape.edge()) return StairsShape.STRAIGHT;
        Edge back = edge(level.getBlockState(pos.relative(front.getOpposite())), stairsOnly);
        if (back != null && back.shape == shape && back.front.getAxis() != front.getAxis()
                && free(shape, front, level, pos.relative(back.front), stairsOnly)) {
            return back.front == front.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
        }
        Edge ahead = edge(level.getBlockState(pos.relative(front)), stairsOnly);
        if (ahead != null && ahead.shape == shape && ahead.front.getAxis() != front.getAxis()
                && free(shape, front, level, pos.relative(ahead.front.getOpposite()), stairsOnly)) {
            return ahead.front == front.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
        }
        return StairsShape.STRAIGHT;
    }

    /** False when the block there is this cut facing this way: this one is then partway along a straight run. */
    private static boolean free(Shape shape, Direction front, BlockGetter level, BlockPos at, boolean stairsOnly) {
        Edge e = edge(level.getBlockState(at), stairsOnly);
        return e == null || e.shape != shape || e.front != front;
    }

    /** The corner the block here shows: a cut's own, or a real stair's. */
    public static StairsShape of(BlockState s) {
        if (s.getBlock() instanceof ShapedBlock) return s.getValue(ShapedBlock.CORNER);
        if (StairBlock.isStairs(s)) return s.getValue(StairBlock.SHAPE);
        return StairsShape.STRAIGHT;
    }

    /** The corner a real stair here would turn by itself, seeing only other stairs. */
    static StairsShape stairs(BlockState stair, BlockGetter level, BlockPos pos) {
        Edge e = edge(stair, true);
        return e == null ? StairsShape.STRAIGHT : corner(e.shape, e.front, level, pos, true);
    }

    /** Whether a real stair here would turn the corner the cuts' rule asks for, all by itself. */
    static boolean realFits(BlockState stair, BlockGetter level, BlockPos pos) {
        Edge e = edge(stair, true);
        return e != null && corner(e.shape, e.front, level, pos, false) == corner(e.shape, e.front, level, pos, true);
    }

    /**
     * After a change at {@code pos}: the stairs there and alongside that can't show their corner become cuts of
     * themselves, and cuts that are really stairs and now can are stairs again. Each change looks again at that block's
     * neighbours. With a player, only where they may use the Mallet; with none (a notch or under cut's tick after a change
     * alongside it), anywhere but the places kept whole and enforced wards.
     */
    static void settle(ServerLevel level, BlockPos pos, @Nullable ServerPlayer actor) {
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(pos.immutable());
        for (Direction d : Direction.Plane.HORIZONTAL) todo.add(pos.relative(d));
        int changes = 0;
        while (!todo.isEmpty() && changes < SETTLE_MAX) {
            BlockPos p = todo.poll();
            if (!Ready.loaded(level, p)) continue;
            if (actor != null ? !ShapingRules.mayChange(actor, level, p) : ShapingRules.kept(level, p)) continue;
            if (!settleOne(level, p)) continue;
            changes++;
            for (Direction d : Direction.Plane.HORIZONTAL) todo.add(p.relative(d));
        }
    }

    private static boolean settleOne(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        boolean stair = StairBlock.isStairs(s);
        if (!stair && !(s.getBlock() instanceof ShapedBlock && s.getValue(ShapedBlock.SHAPE).edge())) return false;
        RealShapes.Parts parts = RealShapes.read(level, p, s);
        if (parts == null) return false;
        if (stair) {
            if (realFits(s, level, p)) return false;
        } else {
            BlockState real = RealShapes.realForm(parts);
            if (real == null || !StairBlock.isStairs(real) || !realFits(real, level, p)) return false;
        }
        RealShapes.place(level, p, parts);
        return true;
    }
}
