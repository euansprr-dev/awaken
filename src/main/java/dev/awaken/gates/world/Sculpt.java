package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world engines' chisel (SPEC §38.5): a floor's carve as an ordered list of operations, each inside its own box and
 * bucketed by chunk, so a chunk runs only the few that touch it. Open space is carved with a {@value #SHELL}-block shell
 * behind it (gate blocks can't be broken, so nothing further is ever seen); shell blocks are skinned last by what they
 * face (floor, ceiling or wall). Built once per floor at plan time; pure and thread-safe after that, each worldgen
 * thread painting through its own reused {@link Buf}.
 */
final class Sculpt {
    /** Solid kept behind open space, in blocks. */
    static final int SHELL = 3;
    static final short UNSET = 0, OPEN = 1, SOLID = 2;

    /** Signed distance in blocks at a block's centre: below 0 is open (or, for a fill, inside the shape). */
    interface Field {
        double d(double x, double y, double z);
    }

    /** What a skinned shell block becomes: floor (open above), ceiling (open below) or wall. */
    interface Skin {
        BlockState at(int x, int y, int z, boolean floor, boolean ceiling);
    }

    /** One block's work: x and z floor-local, y absolute. */
    interface Voxel {
        void at(Buf b, int x, int y, int z);
    }

    record Op(int x0, int y0, int z0, int x1, int y1, int z1, Voxel v) {}

    private final List<Op> ops = new ArrayList<>();
    private final Skin skin;
    final int yLo, yHi;
    private Map<Long, int[]> buckets;

    Sculpt(Skin skin, int yLo, int yHi) {
        this.skin = skin;
        this.yLo = yLo;
        this.yHi = yHi;
    }

    // ---- building the list (plan time) ------------------------------------------------------------------------------

    void op(int x0, int y0, int z0, int x1, int y1, int z1, Voxel v) {
        if (buckets != null) throw new IllegalStateException("sculpt already sealed");
        y0 = Math.max(y0, yLo);
        y1 = Math.min(y1, yHi);
        if (x1 < x0 || y1 < y0 || z1 < z0) return;
        ops.add(new Op(x0, y0, z0, x1, y1, z1, v));
    }

    /** Opens a shape's inside and keeps a shell of solid round it where nothing else has been decided. */
    void carve(int x0, int y0, int z0, int x1, int y1, int z1, Field f) {
        op(x0 - SHELL, y0 - SHELL, z0 - SHELL, x1 + SHELL, y1 + SHELL, z1 + SHELL, (b, x, y, z) -> {
            double d = f.d(x + 0.5, y + 0.5, z + 0.5);
            if (d < 0) b.mark(x, y, z, OPEN);
            else if (d < SHELL && b.get(x, y, z) == UNSET) b.mark(x, y, z, SOLID);
        });
    }

    /** Solid inside the shape, of the skin's material (columns, causeways: they're skinned like walls and floors). */
    void mass(int x0, int y0, int z0, int x1, int y1, int z1, Field f) {
        op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> {
            if (f.d(x + 0.5, y + 0.5, z + 0.5) < 0) b.mark(x, y, z, SOLID);
        });
    }

    /** A block inside the shape, whatever was there. */
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, Field f, BlockState s) {
        op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> {
            if (f.d(x + 0.5, y + 0.5, z + 0.5) < 0) b.set(x, y, z, s);
        });
    }

    /** A block inside the shape only where it's open (water in a carved floor, mist markers). */
    void fillOpen(int x0, int y0, int z0, int x1, int y1, int z1, Field f, BlockState s) {
        op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> {
            if (b.get(x, y, z) == OPEN && f.d(x + 0.5, y + 0.5, z + 0.5) < 0) b.set(x, y, z, s);
        });
    }

    void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> b.set(x, y, z, s));
    }

    void openBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> b.mark(x, y, z, OPEN));
    }

    void put(int x, int y, int z, BlockState s) {
        op(x, y, z, x, y, z, (b, xx, yy, zz) -> b.set(xx, yy, zz, s));
    }

    /** A block only where the place is open (a candle never replaces a wall). */
    void putOpen(int x, int y, int z, BlockState s) {
        op(x, y, z, x, y, z, (b, xx, yy, zz) -> {
            if (b.get(xx, yy, zz) == OPEN) b.set(xx, yy, zz, s);
        });
    }

    /**
     * A block where the place is open or only a plant stands there (a touchstone over the sprouts grown before it: the
     * plan-time probe can't see a plant that needs the floor under it).
     */
    void putOverGrowth(int x, int y, int z, BlockState s) {
        op(x, y, z, x, y, z, (b, xx, yy, zz) -> {
            BlockState there = b.state(xx, yy, zz);
            if (b.get(xx, yy, zz) == OPEN || (there != null && !there.isSolid() && !b.water(xx, yy, zz))) b.set(xx, yy, zz, s);
        });
    }

    /** A pre-made shape (a bell, a statue) at an origin: offsets and states side by side. */
    void stamp(int ox, int oy, int oz, Stamp st) {
        if (st.size() == 0) return;
        op(ox + st.x0, oy + st.y0, oz + st.z0, ox + st.x1, oy + st.y1, oz + st.z1, (b, x, y, z) -> {
            BlockState s = st.at(x - ox, y - oy, z - oz);
            if (s != null) b.set(x, y, z, s);
        });
    }

    private static final ThreadLocal<Buf> PROBE = ThreadLocal.withInitial(Buf::new);

    /**
     * One block as the operations so far leave it, worked out alone at plan time (an operation that reads a neighbour sees
     * it unset). Read the answer from the returned buffer at the same block before the next probe on this thread.
     */
    Buf probe(int x, int y, int z) {
        Buf b = PROBE.get();
        b.reset(Math.floorDiv(x, 16) * 16, Math.floorDiv(z, 16) * 16, yLo, yHi);
        for (Op o : ops) {
            if (x >= o.x0 && x <= o.x1 && y >= o.y0 && y <= o.y1 && z >= o.z0 && z <= o.z1) o.v.at(b, x, y, z);
        }
        return b;
    }

    /** Seals the list: buckets every operation by the chunk slots its box touches. */
    Sculpt seal() {
        Map<Long, List<Integer>> tmp = new java.util.HashMap<>();
        for (int i = 0; i < ops.size(); i++) {
            Op o = ops.get(i);
            for (int sx = o.x0 >> 4; sx <= o.x1 >> 4; sx++) {
                for (int sz = o.z0 >> 4; sz <= o.z1 >> 4; sz++) tmp.computeIfAbsent(key(sx, sz), k -> new ArrayList<>()).add(i);
            }
        }
        Map<Long, int[]> out = new java.util.HashMap<>();
        tmp.forEach((k, v) -> out.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        buckets = out;
        return this;
    }

    int size() {
        return ops.size();
    }

    private static long key(int sx, int sz) {
        return ((long) sx << 32) ^ (sz & 0xFFFFFFFFL);
    }

    // ---- carving a chunk (worldgen threads) ---------------------------------------------------------------------------

    private static final ThreadLocal<Buf> BUF = ThreadLocal.withInitial(Buf::new);

    /**
     * Carves floor-local chunk slot (sx, sz) into the sink. Heights from {@code skipLo} to {@code skipHi} are left to
     * a built room painted there (the Lord's arena, a Rig room); pass skipLo &gt; skipHi to skip nothing.
     */
    void carve(int sx, int sz, GatePainter.Sink out, int skipLo, int skipHi) {
        int[] idx = buckets.get(key(sx, sz));
        if (idx == null) return;
        Buf b = BUF.get();
        b.reset(sx * 16, sz * 16, yLo, yHi);
        for (int i : idx) {
            Op o = ops.get(i);
            int ax = Math.max(o.x0, b.x0), bx = Math.min(o.x1, b.x0 + 15);
            int az = Math.max(o.z0, b.z0), bz = Math.min(o.z1, b.z0 + 15);
            for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int y = o.y0; y <= o.y1; y++) o.v.at(b, x, y, z);
        }
        b.flush(skin, out, skipLo, skipHi);
    }

    /** One chunk column of the floor's height: markers (unset, open, solid to skin) or a palette index. */
    static final class Buf {
        int x0, z0, yLo, h;
        short[] cells = new short[16 * 16 * 256];
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Short> index = new IdentityHashMap<>();

        void reset(int x0, int z0, int yLo, int yHi) {
            this.x0 = x0;
            this.z0 = z0;
            this.yLo = yLo;
            this.h = yHi - yLo + 1;
            int n = 256 * h;
            if (cells.length < n) cells = new short[n];
            java.util.Arrays.fill(cells, 0, n, UNSET);
            palette.clear();
            index.clear();
            palette.add(null);
            palette.add(null);
            palette.add(null);
        }

        private int i(int x, int y, int z) {
            int lx = x - x0, lz = z - z0, ly = y - yLo;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || ly < 0 || ly >= h) return -1;
            return (ly * 16 + lz) * 16 + lx;
        }

        short get(int x, int y, int z) {
            int i = i(x, y, z);
            return i < 0 ? UNSET : cells[i];
        }

        boolean open(int x, int y, int z) {
            return get(x, y, z) == OPEN;
        }

        void mark(int x, int y, int z, short m) {
            int i = i(x, y, z);
            if (i >= 0) cells[i] = m;
        }

        void set(int x, int y, int z, BlockState s) {
            int i = i(x, y, z);
            if (i < 0) return;
            if (s.isAir()) {
                cells[i] = OPEN;
                return;
            }
            Short p = index.get(s);
            if (p == null) {
                p = (short) palette.size();
                palette.add(s);
                index.put(s, p);
            }
            cells[i] = p;
        }

        boolean water(int x, int y, int z) {
            BlockState s = state(x, y, z);
            return s != null && s.getBlock() == net.minecraft.world.level.block.Blocks.WATER;
        }

        BlockState state(int x, int y, int z) {
            int i = i(x, y, z);
            return i < 0 || cells[i] <= SOLID ? null : palette.get(cells[i]);
        }

        void flush(Skin skin, GatePainter.Sink out, int skipLo, int skipHi) {
            for (int ly = 0; ly < h; ly++) {
                int y = yLo + ly;
                if (y >= skipLo && y <= skipHi) continue;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        short c = cells[(ly * 16 + lz) * 16 + lx];
                        if (c == UNSET || c == OPEN) continue;
                        BlockState s;
                        if (c == SOLID) {
                            boolean floor = ly + 1 < h && cells[((ly + 1) * 16 + lz) * 16 + lx] == OPEN;
                            boolean ceiling = ly > 0 && cells[((ly - 1) * 16 + lz) * 16 + lx] == OPEN;
                            s = skin.at(x0 + lx, y, z0 + lz, floor, ceiling);
                        } else s = palette.get(c);
                        if (s != null && !s.isAir()) out.set(lx, y, lz, s);
                    }
                }
            }
        }
    }

    /** A small voxel shape made once and placed many times: a dense box of states (null = leave alone). */
    static final class Stamp {
        final int x0, y0, z0, x1, y1, z1;
        private final BlockState[] cells;

        Stamp(int x0, int y0, int z0, int x1, int y1, int z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.cells = new BlockState[(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1)];
        }

        private int i(int x, int y, int z) {
            if (x < x0 || x > x1 || y < y0 || y > y1 || z < z0 || z > z1) return -1;
            return ((y - y0) * (z1 - z0 + 1) + (z - z0)) * (x1 - x0 + 1) + (x - x0);
        }

        Stamp set(int x, int y, int z, BlockState s) {
            int i = i(x, y, z);
            if (i >= 0) cells[i] = s;
            return this;
        }

        BlockState at(int x, int y, int z) {
            int i = i(x, y, z);
            return i < 0 ? null : cells[i];
        }

        int size() {
            int n = 0;
            for (BlockState s : cells) if (s != null) n++;
            return n;
        }
    }

    // ---- shapes --------------------------------------------------------------------------------------------------------

    /** Distance to an axis-aligned box (negative inside). */
    static double box(double x, double y, double z, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = Math.max(x0 - x, x - x1), dy = Math.max(y0 - y, y - y1), dz = Math.max(z0 - z, z - z1);
        double ox = Math.max(dx, 0), oy = Math.max(dy, 0), oz = Math.max(dz, 0);
        return Math.sqrt(ox * ox + oy * oy + oz * oz) + Math.min(Math.max(dx, Math.max(dy, dz)), 0);
    }

    /** Distance to a vertical cylinder from y0 to y1. */
    static double cylY(double x, double y, double z, double cx, double cz, double r, double y0, double y1) {
        double dr = Math.hypot(x - cx, z - cz) - r;
        double dy = Math.max(y0 - y, y - y1);
        double or = Math.max(dr, 0), oy = Math.max(dy, 0);
        return Math.sqrt(or * or + oy * oy) + Math.min(Math.max(dr, dy), 0);
    }

    /** Distance to a capsule between two points. */
    static double capsule(double x, double y, double z, double ax, double ay, double az, double bx, double by, double bz, double r) {
        double px = x - ax, py = y - ay, pz = z - az, vx = bx - ax, vy = by - ay, vz = bz - az;
        double vv = vx * vx + vy * vy + vz * vz;
        double t = vv == 0 ? 0 : Math.max(0, Math.min(1, (px * vx + py * vy + pz * vz) / vv));
        double qx = px - vx * t, qy = py - vy * t, qz = pz - vz * t;
        return Math.sqrt(qx * qx + qy * qy + qz * qz) - r;
    }

    /**
     * The height of a pointed (two-centred) arch of half-width {@code half} springing at {@code spring} with a crown
     * {@code rise} above it, at lateral offset {@code a} from its axis: NaN outside the span.
     */
    static double pointedArch(double a, double half, double spring, double rise) {
        double aa = Math.abs(a);
        if (aa > half) return Double.NaN;
        // each side's arc is centred c past the axis on the far side, through its own springing point and the crown
        double c = (rise * rise - half * half) / (2 * half), r = c + half;
        double inner = r * r - (aa + c) * (aa + c);
        return spring + (inner <= 0 ? 0 : Math.sqrt(inner));
    }
}
