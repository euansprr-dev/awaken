package dev.awaken.hearth.settle;

import dev.awaken.city.kit.StreetBlocks;
import dev.awaken.city.kit.StreetKit;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.room.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Wren's rope lines (PLAN 4.7, 9: "strings rope lines between your roofs: your home becomes a course"). Once a play-day she
 * strings a line between two of your buildings at their eaves: the city's own hang line ({@code awaken:hang_line}, in
 * {@code #awaken:runner_lines}: a runner catches it and goes across hand over hand), plain rope or bunting, sagging toward
 * its middle, across a gap of 3 to 14 blocks of open air at least three above the ground, wall to wall. The nearest such
 * gap to the Anchor that crosses no line already hung goes first; never through a room, never over anything but air.
 */
final class Ropes {
    private Ropes() {}

    static final int MIN = 3, MAX = 14;

    /** Strings the next line; where it starts, or null when the home has no gap for one. */
    static @Nullable BlockPos hang(ServerLevel level, HearthRecord h) {
        Line l = string(level, h);
        return l == null ? null : l.start;
    }

    /** Strings the next line: where it runs, or null when the home has no gap for one. */
    static @Nullable Line string(ServerLevel level, HearthRecord h) {
        Line best = null;
        for (Room r : h.rooms) {
            if (r.max().getX() - r.min().getX() < 2 || r.max().getZ() - r.min().getZ() < 2) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Line l = fromSide(level, h, r, d);
                if (l != null && (best == null || l.score < best.score)) best = l;
            }
        }
        if (best == null) return null;
        BlockState base = StreetKit.HANG_LINE.get().defaultBlockState().setValue(StreetBlocks.HangLine.AXIS, best.dir.getAxis())
                .setValue(StreetBlocks.HangLine.HANG, h.folk.ropes.size() % 2 == 1 ? StreetBlocks.HangLine.Hang.BUNTING : StreetBlocks.HangLine.Hang.ROPE)
                .setValue(StreetBlocks.HangLine.SET, Math.floorMod(best.start.hashCode(), 4));
        for (int i = 0; i < best.length; i++) {
            BlockPos p = best.start.relative(best.dir, i);
            int fromEnd = Math.min(i, best.length - 1 - i);
            int sag = best.length < 5 ? 0 : fromEnd >= 3 ? 2 : fromEnd >= 1 ? 1 : 0;
            level.setBlock(p, base.setValue(StreetBlocks.HangLine.SAG, sag), Block.UPDATE_ALL);
            if (i % 3 == 0) level.playSound(null, p, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.1f);
        }
        h.folk.ropes.add(best.start);
        return best;
    }

    record Line(BlockPos start, Direction dir, int length, double score) {
        BlockPos middle() {
            return start.relative(dir, length / 2);
        }
    }

    /** The best line out of one side of a room at its eaves (the top row inside), or null. */
    private static @Nullable Line fromSide(ServerLevel level, HearthRecord h, Room r, Direction d) {
        BlockPos a = r.min(), b = r.max();
        int y = b.getY();
        Line best = null;
        int span = d.getAxis() == Direction.Axis.X ? b.getZ() - a.getZ() : b.getX() - a.getX();
        for (int k = 1; k < span; k++) {
            BlockPos wall = switch (d) {
                case EAST -> new BlockPos(b.getX() + 1, y, a.getZ() + k);
                case WEST -> new BlockPos(a.getX() - 1, y, a.getZ() + k);
                case SOUTH -> new BlockPos(a.getX() + k, y, b.getZ() + 1);
                default -> new BlockPos(a.getX() + k, y, a.getZ() - 1);
            };
            if (!level.isLoaded(wall) || !level.getBlockState(wall).isFaceSturdy(level, wall, d)) continue;
            BlockPos start = wall.relative(d);
            int n = 0;
            BlockPos end = null;
            for (int i = 0; i <= MAX; i++) {
                BlockPos c = start.relative(d, i);
                if (!h.contains(c) || !level.isLoaded(c)) break;
                BlockState s = level.getBlockState(c);
                if (!s.isAir()) {
                    if (s.isFaceSturdy(level, c, d.getOpposite()) && !inside(r, c)) end = c;
                    break;
                }
                // open air under the line, three above whatever stands below
                if (!level.getBlockState(c.below()).isAir() || !level.getBlockState(c.below(2)).isAir()) break;
                n++;
            }
            if (end == null || n < MIN || n > MAX || crosses(h, start, d, n)) continue;
            BlockPos mid = start.relative(d, n / 2);
            double score = mid.distSqr(h.pos) + Math.abs(n - 7) * 4;
            if (best == null || score < best.score) best = new Line(start, d, n, score);
        }
        return best;
    }

    private static boolean inside(Room r, BlockPos p) {
        return p.getX() >= r.min().getX() - 1 && p.getX() <= r.max().getX() + 1 && p.getZ() >= r.min().getZ() - 1 && p.getZ() <= r.max().getZ() + 1
                && p.getY() >= r.min().getY() - 1 && p.getY() <= r.max().getY() + 1;
    }

    /** A line already hung within two of this one. */
    private static boolean crosses(HearthRecord h, BlockPos start, Direction d, int n) {
        for (BlockPos s : h.folk.ropes) {
            for (int i = 0; i < n; i++) if (start.relative(d, i).distManhattan(s) <= 2) return true;
        }
        return false;
    }
}
