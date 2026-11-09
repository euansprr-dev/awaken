package dev.awaken.powers;

import dev.awaken.powers.fx.FxKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * A Railgun's tunnel (SPEC §50.6): at level 5 a full shot goes through rock. Everything it carves goes in the {@link TerrainLedger}
 * and comes back from the far end towards you a few seconds later, so nothing is ever lost and nothing drops. What may not be
 * changed (a chest, bedrock, somebody's Hearth) stops the shot; soft things that are not a whole block (sand, fences) it passes
 * through and leaves alone.
 */
public final class Bore {
    private Bore() {}

    private static final int HOLD_TICKS = 90;
    private static final int RECHECK = 20;
    private static final double STEP = 0.3;

    /** Carves from the first wall along the line and returns where the shot really ends. */
    public static Vec3 through(Cast c, Vec3 from, Vec3 dir, Vec3 wall, double maxDist, int depth) {
        double d = from.distanceTo(wall) + 0.05;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos last = null;
        Vec3 end = wall;
        java.util.List<BlockPos> line = new java.util.ArrayList<>();
        boolean stopped = false;
        while (d < maxDist) {
            Vec3 at = from.add(dir.scale(d));
            d += STEP;
            end = at;
            p.set(at.x, at.y, at.z);
            if (p.equals(last)) continue;
            last = p.immutable();
            BlockState s = c.level.getBlockState(p);
            if (!WaveSurface.stands(c.level, p, s) || !s.getFluidState().isEmpty()) continue;
            if (TerrainLedger.canLift(c.level, c.player, p)) {
                if (line.size() >= depth) {
                    stopped = true;
                    break;
                }
                line.add(last);
            } else if (!passes(c, p, s)) {
                stopped = true;
                break;
            }
        }
        if (line.isEmpty()) return stopped ? end : from.add(dir.scale(maxDist));
        Axis cross = Axis.of(dir);
        Set<BlockPos> holes = new HashSet<>();
        for (int i = 0; i < line.size(); i++) {
            int ticks = HOLD_TICKS + (line.size() - 1 - i) * 2; // the far end first, closing back towards you
            BlockPos b = line.get(i);
            if (TerrainLedger.lift(c.level, c.player, b, ticks, true) != null) holes.add(b);
            for (Direction side : cross.sides) {
                BlockPos beside = b.relative(side);
                if (TerrainLedger.lift(c.level, c.player, beside, ticks, true) != null) holes.add(beside);
            }
        }
        seal(c, holes, Vec3.atCenterOf(line.get(0)), Vec3.atCenterOf(line.get(line.size() - 1)), line.size() * 2, HOLD_TICKS - 1);
        return end;
    }

    /** One tick before the far end closes: never on somebody. It waits for every player to walk out, however long that takes. */
    private static void seal(Cast c, Set<BlockPos> holes, Vec3 first, Vec3 deep, int ticks, int wait) {
        Scheduler.after(wait, () -> {
            if (playerInside(c.level, holes)) {
                TerrainLedger.postpone(c.level, holes, RECHECK);
                seal(c, holes, first, deep, ticks, RECHECK);
                return;
            }
            if (c.player.isRemoved()) return;
            Fx.at(c, FxKind.BORE_SEAL, first).to(deep).ticks(ticks).noAnim().send();
            c.sound(first.add(deep).scale(0.5), PowerSounds.RAIL_FILL, 1f, 1f);
        });
    }

    private static boolean playerInside(ServerLevel level, Set<BlockPos> holes) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            AABB body = p.getBoundingBox();
            for (BlockPos b : holes) if (new AABB(b).intersects(body)) return true;
        }
        return false;
    }

    private static boolean passes(Cast c, BlockPos p, BlockState s) {
        if (s.hasBlockEntity() || !Constructs.mayChange(c.player, p)) return false;
        float hardness = s.getDestroySpeed(c.level, p);
        return hardness >= 0 && hardness <= 5;
    }

    /** The blocks round the line that widen it: a tunnel you can see down, not a slit. */
    private enum Axis {
        X(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH),
        Y(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH),
        Z(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST);

        final Direction[] sides;

        Axis(Direction... sides) {
            this.sides = sides;
        }

        static Axis of(Vec3 d) {
            double x = Math.abs(d.x), y = Math.abs(d.y), z = Math.abs(d.z);
            return x >= y && x >= z ? X : y >= z ? Y : Z;
        }
    }
}
