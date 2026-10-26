package dev.awaken.hearth.settle;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Where people can walk round a home you built (SPEC §28.8): the people engine walks its records along a street graph
 * of straight lines between nodes, so a player's home gets one, found by a breadth-first walk over the real blocks from
 * the yard by the Anchor (feet and head clear, something to stand on that isn't farmland, a bed, a fence or fire; steps
 * of one up and two down; doors pass) to every place someone goes (a bedside, an anvil, a field's edge, a hatch), each
 * path pulled straight wherever a straight line keeps to walkable ground at one height. Nodes stand at every step up or
 * down and on both sides of a door, so a body never cuts a corner or walks a wall. Budgeted: block reads a tick, cached
 * per cell, at most {@link #MAX_CELLS} cells.
 */
final class HomeWalk {
    static final int MAX_CELLS = 30_000;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    final ServerLevel level;
    final BlockPos hub;
    private final int x0, z0, x1, z1, yMin, yMax;
    private final Long2ByteOpenHashMap standCache = new Long2ByteOpenHashMap();
    private final Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private int reads;
    private boolean done;

    /** The walk over the box {@code lo..hi} (clamped), from the yard cell {@code hub}. */
    HomeWalk(ServerLevel level, BlockPos hub, BlockPos lo, BlockPos hi) {
        this.level = level;
        this.hub = hub.immutable();
        this.x0 = lo.getX();
        this.z0 = lo.getZ();
        this.x1 = hi.getX();
        this.z1 = hi.getZ();
        this.yMin = lo.getY();
        this.yMax = hi.getY();
        standCache.defaultReturnValue((byte) 0);
        parent.defaultReturnValue(Long.MIN_VALUE);
        parent.put(hub.asLong(), hub.asLong());
        queue.enqueue(hub.asLong());
    }

    boolean done() {
        return done;
    }

    /** Walks on until about {@code budget} block reads are spent; true when the whole reachable ground is known. */
    boolean step(int budget) {
        int start = reads;
        while (!queue.isEmpty() && reads - start < budget) {
            long c = queue.dequeueLong();
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            for (int[] d : DIRS) {
                int nx = x + d[0], nz = z + d[1];
                if (nx < x0 || nx > x1 || nz < z0 || nz > z1) continue;
                boolean diagonal = d[0] != 0 && d[1] != 0;
                if (diagonal) {
                    // no corner cutting: both sides of a diagonal step clear at this height
                    if (!standable(BlockPos.asLong(x + d[0], y, z)) || !standable(BlockPos.asLong(x, y, z + d[1]))) continue;
                    long n = BlockPos.asLong(nx, y, nz);
                    if (standable(n)) visit(c, n);
                    continue;
                }
                // level, a step up (head room to jump), down one or two (open over the drop)
                long same = BlockPos.asLong(nx, y, nz);
                if (standable(same)) {
                    visit(c, same);
                    continue;
                }
                if (y + 1 <= yMax && passable(BlockPos.asLong(x, y + 2, z)) && standable(BlockPos.asLong(nx, y + 1, nz))) {
                    visit(c, BlockPos.asLong(nx, y + 1, nz));
                    continue;
                }
                if (!passable(BlockPos.asLong(nx, y + 1, nz)) || !passable(same)) continue;
                for (int dy = 1; dy <= 2 && y - dy >= yMin; dy++) {
                    long down = BlockPos.asLong(nx, y - dy, nz);
                    if (standable(down)) {
                        visit(c, down);
                        break;
                    }
                    if (!passable(down)) break;
                }
            }
            if (parent.size() > MAX_CELLS) {
                queue.clear();
                break;
            }
        }
        done = queue.isEmpty();
        return done;
    }

    private void visit(long from, long to) {
        if (parent.containsKey(to)) return;
        parent.put(to, from);
        queue.enqueue(to);
    }

    boolean reached(BlockPos cell) {
        return parent.containsKey(cell.asLong());
    }

    int cells() {
        return parent.size();
    }

    // ---- cells ----------------------------------------------------------------------------------------------------

    private boolean standable(long cell) {
        byte v = standCache.get(cell);
        if (v != 0) return v == 1;
        BlockPos p = BlockPos.of(cell);
        reads += 3;
        boolean ok = p.getY() >= yMin && p.getY() <= yMax && level.isLoaded(p) && stand(level, p);
        standCache.put(cell, ok ? (byte) 1 : (byte) 2);
        return ok;
    }

    private boolean passable(long cell) {
        BlockPos p = BlockPos.of(cell);
        if (!level.isLoaded(p)) return false;
        reads++;
        return passable(level, p, level.getBlockState(p));
    }

    /** Someone can stand here: feet and head clear, and something under the feet to stand on. */
    static boolean stand(ServerLevel level, BlockPos feet) {
        BlockState f = level.getBlockState(feet);
        if (!passable(level, feet, f)) return false;
        BlockPos head = feet.above();
        if (!passable(level, head, level.getBlockState(head))) return false;
        BlockPos under = feet.below();
        return support(level, under, level.getBlockState(under));
    }

    /** Clear to walk through: no collision (or a carpet's), no water or lava; a wooden door counts (it's opened). */
    static boolean passable(ServerLevel level, BlockPos p, BlockState s) {
        if (!s.getFluidState().isEmpty()) return false;
        Block b = s.getBlock();
        if (b instanceof DoorBlock) return !s.is(Blocks.IRON_DOOR);
        if (s.is(BlockTags.FIRE) || b instanceof CampfireBlock) return false;
        VoxelShape c = s.getCollisionShape(level, p);
        return c.isEmpty() || c.max(net.minecraft.core.Direction.Axis.Y) <= 0.2;
    }

    /** Good to stand on: solid to half a block at least and no more than a block, not farmland, a bed, fire or leaves. */
    static boolean support(ServerLevel level, BlockPos p, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof FarmlandBlock || b instanceof BedBlock || b instanceof CampfireBlock || s.is(BlockTags.LEAVES) || s.is(Blocks.MAGMA_BLOCK)
                || !s.getFluidState().isEmpty() && s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return false;
        VoxelShape c = s.getCollisionShape(level, p);
        if (c.isEmpty()) return false;
        double top = c.max(net.minecraft.core.Direction.Axis.Y);
        return top >= 0.5 && top <= 1.0;
    }

    static boolean door(ServerLevel level, long cell) {
        return level.getBlockState(BlockPos.of(cell)).getBlock() instanceof DoorBlock;
    }

    // ---- the graph --------------------------------------------------------------------------------------------------

    /** The cells from the hub to {@code target} (the hub first), or empty when it isn't reached. */
    List<BlockPos> path(BlockPos target) {
        long t = target.asLong();
        if (!parent.containsKey(t)) return List.of();
        List<Long> rev = new ArrayList<>();
        long c = t;
        int guard = 0;
        while (guard++ < MAX_CELLS) {
            rev.add(c);
            long p = parent.get(c);
            if (p == c || p == Long.MIN_VALUE) break;
            c = p;
        }
        List<BlockPos> out = new ArrayList<>(rev.size());
        for (int i = rev.size() - 1; i >= 0; i--) out.add(BlockPos.of(rev.get(i)));
        return out;
    }

    /**
     * The path's corners: the hub, every cell on each side of a height change or a door, and wherever the straight line
     * from the last corner would leave walkable ground at that height; the target last.
     */
    List<BlockPos> corners(List<BlockPos> path) {
        List<BlockPos> out = new ArrayList<>();
        if (path.isEmpty()) return out;
        out.add(path.getFirst());
        int a = 0;
        for (int i = 1; i < path.size(); i++) {
            BlockPos p = path.get(i), q = path.get(i - 1);
            boolean step = p.getY() != q.getY();
            boolean doorway = door(level, p.asLong()) || door(level, q.asLong());
            if (step || doorway) {
                if (!out.getLast().equals(q)) out.add(q);
                out.add(p);
                a = i;
                continue;
            }
            if (!straight(path.get(a), p)) {
                if (!out.getLast().equals(q)) out.add(q);
                a = i - 1;
            }
        }
        if (!out.getLast().equals(path.getLast())) out.add(path.getLast());
        return out;
    }

    /** A straight walk from a to b stays on walkable ground at a's height (sampled every third of a block, a body's width). */
    private boolean straight(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) return false;
        double ax = a.getX() + 0.5, az = a.getZ() + 0.5, bx = b.getX() + 0.5, bz = b.getZ() + 0.5;
        double len = Math.hypot(bx - ax, bz - az);
        if (len > 12) return false; // a node at least every dozen blocks: bodies rejoin the line
        int n = (int) Math.ceil(len * 3);
        double px = -(bz - az) / Math.max(1e-6, len) * 0.3, pz = (bx - ax) / Math.max(1e-6, len) * 0.3;
        for (int k = 0; k <= n; k++) {
            double t = n == 0 ? 0 : (double) k / n;
            double x = ax + (bx - ax) * t, z = az + (bz - az) * t;
            for (int side = -1; side <= 1; side++) {
                long c = BlockPos.asLong((int) Math.floor(x + px * side), a.getY(), (int) Math.floor(z + pz * side));
                if (!parent.containsKey(c)) return false;
            }
        }
        return true;
    }

    /** The node index each corner got (shared where two paths pass the same corner). */
    static final class Nodes {
        final Long2IntOpenHashMap at = new Long2IntOpenHashMap();

        Nodes() {
            at.defaultReturnValue(-1);
        }
    }
}
