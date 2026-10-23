package dev.awaken.world.anomaly;

import dev.awaken.Awaken;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * Where an anomaly may stand: 32–96 blocks from the player on dry, loaded, natural ground, with nothing built or placed
 * nearby (no block entities, nothing from {@code #awaken:anomaly_avoid}: planks, glass, bricks, beds, doors...), not in
 * water, never inside a Hearth's reach, clear of gates and other anomalies. A spot that fails is never forced: the
 * director tries others and otherwise skips quietly.
 */
public final class Spots {
    private Spots() {}

    public static final int MIN = 32, MAX = 96;
    /** The largest Hearth radius (tier V). A player inside it is at home. */
    public static final int HOME_RADIUS = 80;
    /** ...and an anomaly keeps a little further off, so nothing that spills out wanders in. */
    public static final int HEARTH_CLEARANCE = 96;
    public static final int GATE_CLEARANCE = 24;
    public static final int ANOMALY_CLEARANCE = 48;
    /** Not on a cliff far above or below the player. */
    public static final int MAX_RISE = 24;
    /** Half-width of the area scanned for builds and block entities. */
    public static final int BOX = 5;
    /** Half-width of the area that must be dry. */
    public static final int DRY = 3;
    public static final int ATTEMPTS = 16;

    /** Crafted building blocks an anomaly never stands near. Data: data/awaken/tags/block/anomaly_avoid.json. */
    public static final TagKey<Block> AVOID = TagKey.create(Registries.BLOCK, Awaken.id("anomaly_avoid"));

    @FunctionalInterface
    public interface HearthCheck {
        boolean near(ServerLevel level, BlockPos pos, int radius);
    }

    public static final HearthCheck REAL_HEARTH = (l, p, r) -> HearthHooks.nearest(l, p, r).isPresent();

    /** How far from the player, and which outside checks apply (tests swap the Hearth lookup). */
    public record Rules(int minDist, int maxDist, HearthCheck hearth, boolean checkGates) {
        public static final Rules NORMAL = new Rules(MIN, MAX, REAL_HEARTH, true);

        public Rules distance(int min, int max) {
            return new Rules(min, max, hearth, checkGates);
        }

        public Rules hearth(HearthCheck h) {
            return new Rules(minDist, maxDist, h, checkGates);
        }
    }

    /** Why {@code ground} can't hold an anomaly for a player standing at {@code from}, or null when it can. */
    public static @Nullable String check(ServerLevel level, BlockPos from, BlockPos ground, Rules rules, @Nullable AnomalyData data) {
        double d = horizontal(from, ground);
        if (d < rules.minDist()) return "too close";
        if (d > rules.maxDist()) return "too far";
        if (Math.abs(ground.getY() - from.getY()) > MAX_RISE) return "too steep";
        if (!level.hasChunksAt(ground.getX() - BOX, ground.getZ() - BOX, ground.getX() + BOX, ground.getZ() + BOX)
                || !level.isPositionEntityTicking(ground)) return "unloaded";
        if (rules.hearth().near(level, ground, HEARTH_CLEARANCE)) return "hearth";
        if (data != null) {
            for (Anomaly a : data.active()) {
                if (a.pos.distSqr(ground) < (double) ANOMALY_CLEARANCE * ANOMALY_CLEARANCE) return "another anomaly";
            }
        }
        if (rules.checkGates() && !GateHooks.near(level, ground, GATE_CLEARANCE).isEmpty()) return "gate";
        return checkGround(level, ground);
    }

    /** The block rules alone: natural, dry, open ground with nothing built or placed near it. Null when fine. */
    public static @Nullable String checkGround(Level level, BlockPos ground) {
        BlockState floor = level.getBlockState(ground);
        if (!floor.getFluidState().isEmpty()) return "water";
        if (floor.isAir() || floor.is(BlockTags.LEAVES) || !floor.isFaceSturdy(level, ground, Direction.UP)) return "no ground";
        if (floor.hasBlockEntity()) return "block entity";
        if (floor.is(AVOID)) return "builds";
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 1; y <= 3; y++) {
            BlockState s = level.getBlockState(m.setWithOffset(ground, 0, y, 0));
            if (!s.getFluidState().isEmpty()) return "water";
            if (s.is(BlockTags.BLOCKS_MOTION)) return "cramped";
        }
        for (int dy = -2; dy <= 5; dy++) {
            for (int dx = -BOX; dx <= BOX; dx++) {
                for (int dz = -BOX; dz <= BOX; dz++) {
                    BlockState s = level.getBlockState(m.set(ground.getX() + dx, ground.getY() + dy, ground.getZ() + dz));
                    if (s.isAir()) continue;
                    if (s.hasBlockEntity()) return "block entity";
                    if (s.is(AVOID)) return "builds";
                    if (!s.getFluidState().isEmpty() && Math.abs(dx) <= DRY && Math.abs(dz) <= DRY && dy >= -1 && dy <= 2) return "water";
                }
            }
        }
        return null;
    }

    /**
     * Tries up to {@link #ATTEMPTS} spots around {@code from}; {@code preferAngle} (radians, x = cos, z = sin; NaN for any
     * direction) narrows the search to a cone of {@code spread}. Each failure reason is tallied into {@code rejects}.
     */
    public static @Nullable BlockPos find(ServerLevel level, BlockPos from, RandomSource r, Rules rules, @Nullable AnomalyData data,
                                          double preferAngle, double spread, Map<String, Integer> rejects) {
        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = Double.isNaN(preferAngle) ? r.nextDouble() * Math.PI * 2 : preferAngle + (r.nextDouble() - 0.5) * spread;
            int dist = rules.minDist() + r.nextInt(Math.max(1, rules.maxDist() - rules.minDist() + 1));
            int x = from.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = from.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (!level.hasChunkAt(x, z)) {
                rejects.merge("unloaded", 1, Integer::sum);
                continue;
            }
            BlockPos ground = surface(level, x, z);
            String why = check(level, from, ground, rules, data);
            if (why == null) return ground;
            rejects.merge(why, 1, Integer::sum);
        }
        return null;
    }

    /** The top solid block at x/z ignoring leaves (water counts, so oceans fail as "water"). Only call on a loaded chunk. */
    public static BlockPos surface(Level level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    public static Map<String, Integer> tally() {
        return new TreeMap<>();
    }

    // ---- words --------------------------------------------------------------------------------------------------------

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public static double horizontal(BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Compass direction from {@code from} to {@code to} (north is -Z). */
    public static String direction(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        return DIRS[(int) Math.round(deg / 45.0) % 8];
    }

    /** "52 m NE". */
    public static String where(BlockPos from, BlockPos to) {
        return Math.round(horizontal(from, to)) + " m " + direction(from, to);
    }
}
