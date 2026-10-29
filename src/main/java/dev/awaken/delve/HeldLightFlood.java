package dev.awaken.delve;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a held light reaches (SPEC §28.3, "Held light"), found the way the game's own light spreads: a flood from the
 * flame's cell through every cell that isn't a full opaque block, within a sphere of the light's reach (round corners,
 * never through rock). Every face of a full block that a reached cell touches is a surface, each found once from the one
 * cell it faces, so a ledge gives its top and its side and a step its tread and its riser; a plant, slab or lantern in a
 * reached cell is taken whole. Nearest first by flood steps, walked a budget at a time so no frame pays for all of it.
 * Pure rules over a level, nothing allocated per walk; the client draws what it finds ({@code delve.client.TorchGlow}).
 */
public final class HeldLightFlood {
    /** A surface that is a whole block (a plant, a slab, a lantern), not one face. */
    public static final int WHOLE = 6;
    /** A flood step's worth of distance: light that had to go round a wall is dimmer than its straight line says. */
    public static final float STEP = 0.55F;
    /** No cap stopped the flood: it ran out of reach first. */
    public static final int NO_EDGE = 127;
    /** Points along the holder-to-flame line {@link #clear} tries, flame first: a hand's reach in about 3 cm steps. */
    private static final int PULL = 16;
    /** How far a flame pulled out of rock sits back from it along that line, in blocks. */
    private static final double OFF = 0.2;

    private static final Direction[] DIRS = Direction.values();

    private final long[] q;
    private final byte[] qs;
    private final LongOpenHashSet seen;
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), from = new BlockPos.MutableBlockPos();
    private Level level;
    private int head, tail, cap, ox, oy, oz;
    private float reach;

    /** The surfaces found so far: where, which face ({@link #WHOLE}: the whole block), flood steps, and the light already there (block << 4 | sky). */
    public long[] pos;
    public byte[] face, steps, lit;
    public int n;
    /** Where a cap stopped the flood, the steps of its last surface ({@link #NO_EDGE} when none did): the light fades out toward it. */
    public int edge = NO_EDGE;

    public HeldLightFlood(int queue, int surfaces) {
        q = new long[queue];
        qs = new byte[queue];
        seen = new LongOpenHashSet(queue);
        pos = new long[surfaces];
        face = new byte[surfaces];
        steps = new byte[surfaces];
        lit = new byte[surfaces];
    }

    /** Starts a new flood from a cell (open, not rock), keeping at most {@code cap} surfaces (no more than the arrays hold). */
    public void start(Level level, long origin, float reach, int cap) {
        this.level = level;
        this.reach = reach;
        this.cap = Math.min(cap, pos.length);
        ox = BlockPos.getX(origin);
        oy = BlockPos.getY(origin);
        oz = BlockPos.getZ(origin);
        seen.clear();
        seen.add(origin);
        q[0] = origin;
        qs[0] = 0;
        head = 0;
        tail = 1;
        n = 0;
        edge = NO_EDGE;
    }

    /**
     * Where a flame held against rock shines from: at a wall the hand's point is inside the block, and a flood from in
     * there finds nothing in front of it (the face looking at you stays black). The point on the line from where the
     * holder stands ({@code a}) to the flame ({@code f}) just before the line enters rock, then {@link #OFF} further back
     * (a flame has a size: shining from on the face, it lit the face only at a grazing angle), into {@code out}: the
     * flame itself when its cell is open. False when the holder's own cell is rock.
     */
    public static boolean clear(Level level, double ax, double ay, double az, double fx, double fy, double fz, double[] out, BlockPos.MutableBlockPos m) {
        double len = Math.max(1e-3, Math.sqrt((ax - fx) * (ax - fx) + (ay - fy) * (ay - fy) + (az - fz) * (az - fz)));
        for (int i = 0; i <= PULL; i++) {
            if (!along(level, ax, ay, az, fx, fy, fz, i, out, m)) continue;
            if (i > 0 && !along(level, ax, ay, az, fx, fy, fz, Math.min(PULL, i + (int) Math.ceil(OFF / len * PULL)), out, m)) {
                along(level, ax, ay, az, fx, fy, fz, i, out, m);
            }
            return true;
        }
        return false;
    }

    /** The point {@code i} steps back from the flame toward the holder, into {@code out}; true when its cell is open. */
    private static boolean along(Level level, double ax, double ay, double az, double fx, double fy, double fz, int i, double[] out,
                                 BlockPos.MutableBlockPos m) {
        double back = i / (double) PULL;
        out[0] = fx + (ax - fx) * back;
        out[1] = fy + (ay - fy) * back;
        out[2] = fz + (az - fz) * back;
        return !level.getBlockState(m.set(out[0], out[1], out[2])).isSolidRender();
    }

    /** True once the flood has walked everything in reach or kept its cap of surfaces. */
    public boolean done() {
        return head >= tail || n >= cap;
    }

    /** Walks up to {@code budget} cells; returns how many it walked (at least 1, so a caller's budget always falls). */
    public int step(int budget) {
        float r2 = (reach + 0.5F) * (reach + 0.5F);
        int maxSteps = Math.min(120, (int) (reach / STEP) + 2);
        int walked = 0;
        while (head < tail && walked < budget && n < cap) {
            long c = q[head];
            int s = qs[head];
            head++;
            walked++;
            for (Direction d : DIRS) {
                long nb = BlockPos.offset(c, d);
                int x = BlockPos.getX(nb), y = BlockPos.getY(nb), z = BlockPos.getZ(nb);
                int dx = x - ox, dy = y - oy, dz = z - oz;
                if (dx * dx + dy * dy + dz * dz > r2 || level.isOutsideBuildHeight(y) || seen.contains(nb)) continue;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                m.set(x, y, z);
                BlockState st = level.getBlockState(m);
                if (st.isSolidRender()) {
                    // rock: its face toward this cell. A block is never marked seen, so each cell that touches it gives
                    // the face it touches (a ledge's top and its side, from the cells above and beside it)
                    from.set(BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c));
                    record(nb, d.getOpposite().ordinal(), s + 1, from);
                    if (n >= cap) break;
                    continue;
                }
                seen.add(nb);
                int ns = s + 1 + st.getLightDampening();
                if (!st.isAir() && st.getFluidState().isEmpty()) {
                    record(nb, WHOLE, ns, m);       // a plant, a slab, a lantern: the whole of it
                    if (n >= cap) break;
                }
                if (ns < maxSteps && tail < q.length) {
                    q[tail] = nb;
                    qs[tail] = (byte) ns;
                    tail++;
                }
            }
        }
        if (n >= cap) edge = steps[n - 1];
        return Math.max(1, walked);
    }

    private void record(long p, int f, int s, BlockPos lightAt) {
        pos[n] = p;
        face[n] = (byte) f;
        steps[n] = (byte) Math.min(NO_EDGE, s);
        int b = level.getBrightness(LightLayer.BLOCK, lightAt), sky = level.getBrightness(LightLayer.SKY, lightAt);
        lit[n] = (byte) (b << 4 | sky);
        n++;
    }
}
