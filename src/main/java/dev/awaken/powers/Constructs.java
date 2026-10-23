package dev.awaken.powers;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.powers.content.ConstructBlock;
import dev.awaken.powers.content.PowerContent;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Architect constructs: shape builders and placement. A construct only ever goes into air, never inside another
 * player's Hearth, and never inside a player. Each player's recent constructs are remembered (in memory) so Collapse
 * and Gravity + Architect can find them again.
 */
public final class Constructs {
    private Constructs() {}

    private record Placed(ResourceKey<Level> dim, BlockPos pos) {}

    private static final int MEMORY = 320;
    private static final Map<UUID, Deque<Placed>> OWNED = new HashMap<>();

    /** Hearth protection: powers never change blocks inside someone else's Hearth (48 blocks). */
    public static boolean mayChange(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return false;
        Optional<BlockPos> hearth = HearthHooks.nearest(level, pos, 48);
        if (hearth.isEmpty()) return true;
        Optional<BlockPos> mine = HearthHooks.hearthOf(player);
        return mine.isPresent() && mine.get().equals(hearth.get()) && level.dimension() == Level.OVERWORLD;
    }

    public static boolean canPlace(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!mayChange(player, pos)) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        return level.getEntitiesOfClass(Player.class, new AABB(pos)).isEmpty();
    }

    /** Places constructs at every free position. Returns how many went down. */
    public static int place(ServerPlayer player, Iterable<BlockPos> positions, Set<BlockPos> slabs, boolean dream, int lifetime) {
        ServerLevel level = player.level();
        ConstructBlock block = dream ? PowerContent.DREAM_CONSTRUCT.get() : PowerContent.CONSTRUCT.get();
        int placed = 0;
        Deque<Placed> mine = OWNED.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        for (BlockPos pos : positions) {
            if (!canPlace(player, pos)) continue;
            BlockState state = block.stateFor(slabs.contains(pos), lifetime);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            level.scheduleTick(pos, block, ConstructBlock.firstDelay(lifetime));
            if (placed % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.01);
            }
            if (!dream) {
                mine.addLast(new Placed(level.dimension(), pos.immutable()));
                while (mine.size() > MEMORY) mine.removeFirst();
            }
            placed++;
        }
        if (placed > 0 && !dream && Powers.inCombat(player)) {
            Progression.count(player, Counters.BLOCKS_PLACED_IN_COMBAT, placed);
        }
        return placed;
    }

    public static int place(ServerPlayer player, Iterable<BlockPos> positions, int lifetime) {
        return place(player, positions, Set.of(), false, lifetime);
    }

    /** The player's constructs still standing within radius of a point. */
    public static List<BlockPos> ownedNear(ServerPlayer player, Vec3 center, double radius) {
        List<BlockPos> out = new ArrayList<>();
        Deque<Placed> mine = OWNED.get(player.getUUID());
        if (mine == null) return out;
        ServerLevel level = player.level();
        double r2 = radius * radius;
        for (Iterator<Placed> it = mine.iterator(); it.hasNext(); ) {
            Placed p = it.next();
            if (p.dim() != level.dimension()) continue;
            if (!(level.getBlockState(p.pos()).getBlock() instanceof ConstructBlock)) {
                it.remove();
                continue;
            }
            if (Vec3.atCenterOf(p.pos()).distanceToSqr(center) <= r2) out.add(p.pos());
        }
        return out;
    }

    public static boolean isConstruct(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ConstructBlock;
    }

    /** All construct blocks connected to a start block (6-way), up to a cap. */
    public static List<BlockPos> connected(ServerLevel level, BlockPos start, int cap) {
        List<BlockPos> out = new ArrayList<>();
        if (!isConstruct(level, start)) return out;
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        while (!queue.isEmpty() && out.size() < cap) {
            BlockPos p = queue.removeFirst();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && isConstruct(level, n)) queue.addLast(n);
            }
        }
        return out;
    }

    /** Removes a construct silently (it is about to become a flying block). */
    public static BlockState lift(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return s;
    }

    static void forget(UUID player) {
        OWNED.remove(player);
    }

    // ---- shapes -----------------------------------------------------------------------------------------------------

    /** The horizontal direction the player faces, snapped to the grid. */
    public static Direction facing(Cast c) {
        return Direction.fromYRot(c.yRot());
    }

    /** Ground level under the player's feet (the block they stand in). */
    public static BlockPos feet(ServerPlayer p) {
        return BlockPos.containing(p.getX(), p.getY() + 0.01, p.getZ());
    }

    /** A wall of width x height, centred, {@code ahead} blocks in front, perpendicular to the facing. */
    public static List<BlockPos> wall(BlockPos feet, Direction facing, int ahead, int width, int height) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos base = feet.relative(facing, ahead);
        Direction side = facing.getClockWise();
        int half = width / 2;
        for (int w = -half; w <= half - (width % 2 == 0 ? 1 : 0); w++) {
            for (int h = 0; h < height; h++) out.add(base.relative(side, w).above(h));
        }
        return out;
    }

    /** A walkable ramp rising half a block per block ahead (slab, full, full+slab, ...). Fills slabs. */
    public static List<BlockPos> ramp(BlockPos feet, Direction facing, int length, int width, Set<BlockPos> slabs) {
        List<BlockPos> out = new ArrayList<>();
        Direction side = facing.getClockWise();
        int half = width / 2;
        for (int d = 1; d <= length; d++) {
            int halves = d; // top height in half blocks
            int full = halves / 2;
            boolean slab = halves % 2 == 1;
            for (int w = -half; w <= half; w++) {
                BlockPos col = feet.relative(facing, d).relative(side, w);
                for (int h = 0; h < full; h++) out.add(col.above(h));
                if (slab) {
                    BlockPos s = col.above(full);
                    out.add(s);
                    slabs.add(s);
                }
            }
        }
        return out;
    }

    /** A flat square platform at y (centre at the given column). */
    public static List<BlockPos> floor(BlockPos centre, int size) {
        List<BlockPos> out = new ArrayList<>();
        int half = size / 2;
        for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) out.add(centre.offset(x, 0, z));
        return out;
    }

    /** A stepped pyramid roof over a column (Fortnite cone): rings shrinking upward. */
    public static List<BlockPos> cone(BlockPos base, int size) {
        List<BlockPos> out = new ArrayList<>();
        int half = size / 2;
        for (int layer = 0; layer <= half; layer++) {
            int r = half - layer;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == r) out.add(base.offset(x, layer, z));
                }
            }
        }
        return out;
    }

    /** A square ring of walls around a centre with a one-block door in the facing direction and crenellations. */
    public static List<BlockPos> rampart(BlockPos centre, Direction facing, int radius, int height) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos door = centre.relative(facing, radius);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                BlockPos col = centre.offset(x, 0, z);
                for (int h = 0; h < height; h++) {
                    if (col.getX() == door.getX() && col.getZ() == door.getZ() && h < 2) continue;
                    out.add(col.above(h));
                }
                if ((x + z) % 2 == 0) out.add(col.above(height));
            }
        }
        return out;
    }

    /** A hollow dome shell of radius r with a few windows. */
    public static List<BlockPos> dome(BlockPos centre, int radius) {
        List<BlockPos> out = new ArrayList<>();
        for (int y = 0; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (Math.abs(d - radius) > 0.5) continue;
                    boolean window = y == 2 && (x == 0 || z == 0);
                    if (!window) out.add(centre.offset(x, y, z));
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        return out;
    }
}
