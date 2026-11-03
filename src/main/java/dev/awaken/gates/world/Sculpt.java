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

    /** {@code src}: who added it, kept only while {@link #TRACE} is on (the placement census, SPEC §38.16). */
    record Op(int x0, int y0, int z0, int x1, int y1, int z1, Voxel v, String src, boolean afloat) {}

    /** Tests only: ops remember who added them and a carve says whose block it's writing ({@link #source()}). */
    static volatile boolean TRACE;
    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();

    /** Tests only: the census asks of each block written whether it floats on purpose ({@link #floating()}). */
    static boolean CENSUS;
    private static final ThreadLocal<boolean[]> FLOATING = ThreadLocal.withInitial(() -> new boolean[1]);

    static void floating(boolean on) {
        FLOATING.get()[0] = on;
    }

    static boolean floating() {
        return FLOATING.get()[0];
    }

    static void source(String who) {
        SOURCE.set(who);
    }

    /** While tracing, who placed the block a carve is writing right now (read it in the sink). */
    static String source() {
        return SOURCE.get();
    }

    private final List<Op> ops = new ArrayList<>();
    private final Skin skin;
    final int yLo, yHi;
    /**
     * A version 7 floor (SPEC §38.14): a shell block under or over something thin (a plant, a slab, crumbs) is skinned as
     * floor or ceiling as if the air were open there, not as wall.
     */
    boolean thinOpen;
    /**
     * A version 9 floor (SPEC §38.16): before a chunk is written, anything placed whose spot doesn't hold it up (a plant
     * over air, roots under nothing, a vestment off its wall) is left out ({@link Holds#settle}).
     */
    boolean settle;
    /**
     * While set, what's added floats on purpose (SPEC §38.16: the Sleeper's furniture, the Endless Stair's landings, the far
     * isles): the settle keeps a lump with any of it in.
     */
    boolean afloat;
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
        ops.add(new Op(x0, y0, z0, x1, y1, z1, v, TRACE ? PlacementTests.caller() : null, afloat));
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
        if (buckets.get(key(sx, sz)) == null) return;
        Buf b = BUF.get();
        run(sx, sz, b, TRACE);
        if (settle) {
            b.crumbs(this);
            b.settle();
            if (b.chainDropped) {
                b.crumbs(this);
                b.settle();
            }
        }
        b.flush(skin, out, skipLo, skipHi, thinOpen);
    }

    /**
     * Tests: reports every block of chunk slot (sx, sz) the ops decided is open or set (not left unset, not shell), so the
     * placement census can tell the carved air of the places from the dark outside their shell.
     */
    void opened(int sx, int sz, Voxel to) {
        if (buckets.get(key(sx, sz)) == null) return;
        Buf b = BUF.get();
        run(sx, sz, b, false);
        for (int ly = 0; ly < b.h; ly++)
            for (int lz = 0; lz < 16; lz++)
                for (int lx = 0; lx < 16; lx++) {
                    short c = b.cells[(ly * 16 + lz) * 16 + lx];
                    if (c != UNSET && c != SOLID) to.at(b, b.x0 + lx, yLo + ly, b.z0 + lz);
                }
    }

    /** Runs every operation bucketed at chunk slot (sx, sz) into {@code b}, fresh (tracing who set what if asked). */
    private void run(int sx, int sz, Buf b, boolean trace) {
        b.reset(sx * 16, sz * 16, yLo, yHi);
        b.srcs = trace ? new String[256 * b.h] : null;
        int[] idx = buckets.get(key(sx, sz));
        if (idx == null) return;
        for (int i : idx) {
            Op o = ops.get(i);
            b.src = o.src;
            b.afloat = o.afloat;
            int ax = Math.max(o.x0, b.x0), bx = Math.min(o.x1, b.x0 + 15);
            int az = Math.max(o.z0, b.z0), bz = Math.min(o.z1, b.z0 + 15);
            for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int y = o.y0; y <= o.y1; y++) o.v.at(b, x, y, z);
        }
    }

    /** One chunk column of the floor's height: markers (unset, open, solid to skin) or a palette index. */
    static final class Buf {
        int x0, z0, yLo, h;
        short[] cells = new short[16 * 16 * 256];
        /** While tracing: who set each cell, and the op running now. */
        String[] srcs;
        String src;
        /** Cells set by an op that floats on purpose ({@link Sculpt#afloat}), and whether the running op does. */
        final java.util.BitSet floats = new java.util.BitSet();
        boolean afloat;
        private final List<BlockState> palette = new ArrayList<>();
        /** Per palette entry: thin (not a full block, not a fluid), for {@link Sculpt#thinOpen}. */
        private boolean[] thin = new boolean[64];
        /** Per palette entry: something a settle looks at ({@link Holds#settles}). */
        private boolean[] loose = new boolean[64];
        /** Per palette entry: solid or a chain, what holds rock up when it touches it ({@link #crumbs}). */
        private boolean[] hard = new boolean[64];
        /** Per palette entry: water or another fluid, which a lump touching it holds in (so it stays). */
        private boolean[] wet = new boolean[64];
        private final Map<BlockState, Short> index = new IdentityHashMap<>();

        void reset(int x0, int z0, int yLo, int yHi) {
            this.x0 = x0;
            this.z0 = z0;
            this.yLo = yLo;
            this.h = yHi - yLo + 1;
            int n = 256 * h;
            if (cells.length < n) cells = new short[n];
            java.util.Arrays.fill(cells, 0, n, UNSET);
            floats.clear();
            afloat = false;
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

        /** Rock, or something solid placed (what a chain hangs from, what holds a lump up). */
        boolean hard(int x, int y, int z) {
            short c = get(x, y, z);
            return c == SOLID || c > SOLID && hard[c];
        }

        void mark(int x, int y, int z, short m) {
            int i = i(x, y, z);
            if (i < 0) return;
            cells[i] = m;
            if (srcs != null) srcs[i] = src;
            floats.set(i, afloat);
        }

        void set(int x, int y, int z, BlockState s) {
            int i = i(x, y, z);
            if (i < 0) return;
            if (srcs != null) srcs[i] = src;
            floats.set(i, afloat);
            if (s.isAir()) {
                cells[i] = OPEN;
                return;
            }
            Short p = index.get(s);
            if (p == null) {
                p = (short) palette.size();
                palette.add(s);
                index.put(s, p);
                if (p >= thin.length) {
                    thin = java.util.Arrays.copyOf(thin, p * 2);
                    loose = java.util.Arrays.copyOf(loose, p * 2);
                    hard = java.util.Arrays.copyOf(hard, p * 2);
                    wet = java.util.Arrays.copyOf(wet, p * 2);
                }
                thin[p] = !s.isSolidRender() && s.getFluidState().isEmpty();
                loose[p] = Holds.settles(s);
                hard[p] = Holds.rule(s) == Holds.SOLID || Holds.rule(s) == Holds.CHAIN;
                wet[p] = !s.getFluidState().isEmpty();
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

        private static final BlockState ROCK = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                AIR = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        /** Whether the last {@link #settle} left out a chain (what hung on it may be a lump in the air now). */
        boolean chainDropped;

        /** Leaves out what nothing in this chunk holds up (what's past its sides it can't see, so that never counts against). */
        void settle() {
            chainDropped = false;
            int n = 0;
            int[] xs = SX.get(), ys = SY.get(), zs = SZ.get();
            for (int ly = 0; ly < h; ly++)
                for (int lz = 0; lz < 16; lz++)
                    for (int lx = 0; lx < 16; lx++) {
                        short c = cells[(ly * 16 + lz) * 16 + lx];
                        if (c <= SOLID || !loose[c]) continue;
                        if (n == xs.length) {
                            xs = java.util.Arrays.copyOf(xs, n * 2);
                            ys = java.util.Arrays.copyOf(ys, n * 2);
                            zs = java.util.Arrays.copyOf(zs, n * 2);
                            SX.set(xs);
                            SY.set(ys);
                            SZ.set(zs);
                        }
                        xs[n] = x0 + lx;
                        ys[n] = yLo + ly;
                        zs[n++] = z0 + lz;
                    }
            if (n == 0) return;
            Holds.settle((x, y, z) -> {
                int i = i(x, y, z);
                if (i < 0) return null;
                short c = cells[i];
                return c == SOLID ? ROCK : c <= OPEN ? AIR : palette.get(c);
            }, xs, ys, zs, n, (x, y, z) -> {
                int i = i(x, y, z);
                if (hard[cells[i]]) chainDropped = true;
                cells[i] = OPEN;
            });
        }

        /** A lump smaller than this touching nothing that holds it is left out (the census's ground is as big). */
        static final int CRUMB = 2048;
        private static final ThreadLocal<int[]> QUEUE = ThreadLocal.withInitial(() -> new int[4096]);
        private static final ThreadLocal<java.util.BitSet> SEEN = ThreadLocal.withInitial(java.util.BitSet::new);

        /** How many chunks round this one a crumb test carves to follow a lump over a side before it calls it held. */
        static final int NEARS = 8;
        /** Chunks carved by their operations alone, kept per thread for the floor's next chunks (its neighbours share them). */
        private static final ThreadLocal<Nears> NEAR = ThreadLocal.withInitial(Nears::new);

        private static final class Nears {
            static final int SIZE = 12;
            final Buf[] bufs = new Buf[SIZE];
            final long[] keys = new long[SIZE];
            /** The crumb test that last used each, and how many this one has used (at most {@link #NEARS}). */
            final int[] used = new int[SIZE];
            Sculpt owner;
            int n, next, test, inTest;
        }
        private static final ThreadLocal<long[]> OUTER = ThreadLocal.withInitial(() -> new long[256]);
        private static final ThreadLocal<java.util.Set<Long>> OUTER_SEEN = ThreadLocal.withInitial(java.util.HashSet::new);

        private boolean hardCell(int i) {
            short c = cells[i];
            return c == SOLID || c > SOLID && hard[c];
        }

        /**
         * What holds a lump up at a block of it, besides rock: the band's ends, floating on purpose, water it holds in or
         * stands in, or something placed under it that's flat on top.
         */
        private boolean holds(int i) {
            int ly = i >> 8;
            if (ly == 0 || ly == h - 1 || floats.get(i)) return true;
            return under(cells[i - 256]) || wet(x0 + (i & 15), yLo + ly, z0 + ((i >> 4) & 15));
        }

        private boolean fluid(int x, int y, int z) {
            short c = get(x, y, z);
            return c > SOLID && wet[c];
        }

        /** Whether water (or another fluid) touches (x, y, z) on a face, as far as this chunk shows. */
        private boolean wet(int x, int y, int z) {
            for (int d = 0; d < 6; d++) {
                int j = i(x + (d == 0 ? 1 : d == 1 ? -1 : 0), y + (d == 2 ? 1 : d == 3 ? -1 : 0), z + (d == 4 ? 1 : d == 5 ? -1 : 0));
                if (j >= 0 && cells[j] > SOLID && wet[cells[j]]) return true;
            }
            return false;
        }

        private boolean under(short b) {
            if (b <= SOLID) return false;
            BlockState s = palette.get(b);
            return s.getBlock() == net.minecraft.world.level.block.Blocks.WATER || !hard[b] && Holds.sturdy(s, net.minecraft.core.Direction.UP);
        }

        /**
         * The chunk holding (x, z), carved by its operations alone (as its own carve has it before its crumbs), kept for
         * the next tests; null when this test has used too many already.
         */
        private Buf near(Sculpt s, int x, int z) {
            Nears nr = NEAR.get();
            long k = key(x >> 4, z >> 4);
            for (int i = 0; i < nr.n; i++) {
                if (nr.keys[i] != k) continue;
                if (nr.used[i] != nr.test) {
                    if (nr.inTest == NEARS) return null;
                    nr.used[i] = nr.test;
                    nr.inTest++;
                }
                return nr.bufs[i];
            }
            if (nr.inTest == NEARS) return null;
            int slot = nr.n < Nears.SIZE ? nr.n++ : -1;
            // full: the next one round that this test isn't using
            for (int i = 0; slot < 0; i++) if (nr.used[(nr.next + i) % Nears.SIZE] != nr.test) slot = (nr.next + i) % Nears.SIZE;
            nr.next = (slot + 1) % Nears.SIZE;
            if (nr.bufs[slot] == null) nr.bufs[slot] = new Buf();
            s.run(x >> 4, z >> 4, nr.bufs[slot], false);
            nr.keys[slot] = k;
            nr.used[slot] = nr.test;
            nr.inTest++;
            return nr.bufs[slot];
        }

        /**
         * Leaves out the lumps (SPEC §38.16): rock or anything solid in this chunk's air (a sliver of shell where two
         * shapes meet, a piece put where its support isn't) that touches no water and nothing solid that does, unless it
         * floats on purpose. A lump that runs over a side is followed into the chunks next to it, carved by their own
         * operations, so every chunk it crosses reaches the same answer. What hangs on one goes with it in the settle after.
         */
        void crumbs(Sculpt s) {
            int n = 256 * h;
            java.util.BitSet seen = SEEN.get();
            seen.clear();
            int[] q = QUEUE.get();
            long[] oq = OUTER.get();
            java.util.Set<Long> oseen = OUTER_SEEN.get();
            Nears nr = NEAR.get();
            // another floor's chunks are no use (a sealed floor's ops never change, so its own stay good)
            if (nr.owner != s) {
                nr.owner = s;
                nr.n = 0;
                java.util.Arrays.fill(nr.used, 0);
            }
            nr.test++;
            nr.inTest = 0;
            for (int s0 = 0; s0 < n; s0++) {
                if (!hardCell(s0) || seen.get(s0)) continue;
                int head = 0, tail = 0, ohead = 0, otail = 0, scanned = 0;
                q[tail++] = s0;
                seen.set(s0);
                oseen.clear();
                boolean held = false;
                while (true) {
                    // in the chunk first, the whole of it: most lumps are held here and never look past a side
                    while (head < tail) {
                        int i = q[head++];
                        if (holds(i)) held = true;
                        int lx = i & 15, lz = (i >> 4) & 15, ly = i >> 8;
                        for (int d = 0; d < 6; d++) {
                            int j = switch (d) {
                                case 0 -> lx < 15 ? i + 1 : -1;
                                case 1 -> lx > 0 ? i - 1 : -1;
                                case 2 -> lz < 15 ? i + 16 : -1;
                                case 3 -> lz > 0 ? i - 16 : -1;
                                case 4 -> ly < h - 1 ? i + 256 : -1;
                                default -> ly > 0 ? i - 256 : -1;
                            };
                            if (j < 0 || seen.get(j) || !hardCell(j)) continue;
                            seen.set(j);
                            if (tail == q.length) QUEUE.set(q = java.util.Arrays.copyOf(q, q.length * 2));
                            q[tail++] = j;
                        }
                    }
                    if (held || tail + otail >= CRUMB) {
                        held = true;
                        break;
                    }
                    // past a side: the blocks next to it in the chunk beyond
                    for (; scanned < tail && !held; scanned++) {
                        int i = q[scanned];
                        int lx = i & 15, lz = (i >> 4) & 15;
                        if (lx > 0 && lx < 15 && lz > 0 && lz < 15) continue;
                        int wx = x0 + lx, wy = yLo + (i >> 8), wz = z0 + lz;
                        for (int d = 0; d < 4 && !held; d++) {
                            int nx = wx + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = wz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                            if (i(nx, wy, nz) >= 0) continue;
                            Buf o = near(s, nx, nz);
                            if (o == null || o.fluid(nx, wy, nz)) held = true;
                            else if (o.hard(nx, wy, nz) && oseen.add(pack(nx, wy, nz))) {
                                if (otail == oq.length) OUTER.set(oq = java.util.Arrays.copyOf(oq, oq.length * 2));
                                oq[otail++] = pack(nx, wy, nz);
                            }
                        }
                    }
                    if (held || ohead == otail) break;
                    // and on from those, back into this chunk where it comes back
                    while (ohead < otail && !held) {
                        long k = oq[ohead++];
                        int wx = (int) (k >> 40), wy = (int) (k << 24 >> 44), wz = (int) (k << 44 >> 44);
                        Buf o = near(s, wx, wz);
                        if (o == null || wy == yLo || wy == yLo + h - 1 || o.floats.get(o.i(wx, wy, wz)) || o.under(o.get(wx, wy - 1, wz))
                                || o.wet(wx, wy, wz)) held = true;
                        for (int d = 0; d < 6 && !held; d++) {
                            int nx = wx + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = wy + (d == 4 ? 1 : d == 5 ? -1 : 0), nz = wz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                            if (ny < yLo || ny >= yLo + h) continue;
                            int j = i(nx, ny, nz);
                            if (j >= 0) {
                                if (seen.get(j) || !hardCell(j)) continue;
                                seen.set(j);
                                if (tail == q.length) QUEUE.set(q = java.util.Arrays.copyOf(q, q.length * 2));
                                q[tail++] = j;
                                continue;
                            }
                            Buf o2 = near(s, nx, nz);
                            if (o2 == null || o2.fluid(nx, ny, nz)) held = true;
                            else if (o2.hard(nx, ny, nz) && oseen.add(pack(nx, ny, nz))) {
                                if (otail == oq.length) OUTER.set(oq = java.util.Arrays.copyOf(oq, oq.length * 2));
                                oq[otail++] = pack(nx, ny, nz);
                            }
                        }
                        if (tail + otail >= CRUMB) held = true;
                    }
                    if (held) break;
                }
                // a held lump still marks the rest of its blocks here, so no other start floods it again
                while (head < tail) {
                    int i = q[head++];
                    int lx = i & 15, lz = (i >> 4) & 15, ly = i >> 8;
                    for (int d = 0; d < 6; d++) {
                        int j = switch (d) {
                            case 0 -> lx < 15 ? i + 1 : -1;
                            case 1 -> lx > 0 ? i - 1 : -1;
                            case 2 -> lz < 15 ? i + 16 : -1;
                            case 3 -> lz > 0 ? i - 16 : -1;
                            case 4 -> ly < h - 1 ? i + 256 : -1;
                            default -> ly > 0 ? i - 256 : -1;
                        };
                        if (j < 0 || seen.get(j) || !hardCell(j)) continue;
                        seen.set(j);
                        if (tail == q.length) QUEUE.set(q = java.util.Arrays.copyOf(q, q.length * 2));
                        q[tail++] = j;
                    }
                }
                if (held) continue;
                for (int k = 0; k < tail; k++) cells[q[k]] = OPEN;
            }
        }

        private static long pack(int x, int y, int z) {
            return ((long) x << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
        }

        private static final ThreadLocal<int[]> SX = ThreadLocal.withInitial(() -> new int[256]), SY = ThreadLocal.withInitial(() -> new int[256]),
                SZ = ThreadLocal.withInitial(() -> new int[256]);

        /** Open air, or (thin open) something thin, for skinning the shell next to it. */
        private boolean airy(short c, boolean thinOpen) {
            return c == OPEN || thinOpen && c > SOLID && thin[c];
        }

        void flush(Skin skin, GatePainter.Sink out, int skipLo, int skipHi, boolean thinOpen) {
            for (int ly = 0; ly < h; ly++) {
                int y = yLo + ly;
                if (y >= skipLo && y <= skipHi) continue;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        short c = cells[(ly * 16 + lz) * 16 + lx];
                        if (c == UNSET || c == OPEN) continue;
                        BlockState s;
                        if (c == SOLID) {
                            boolean floor = ly + 1 < h && airy(cells[((ly + 1) * 16 + lz) * 16 + lx], thinOpen);
                            boolean ceiling = ly > 0 && airy(cells[((ly - 1) * 16 + lz) * 16 + lx], thinOpen);
                            s = skin.at(x0 + lx, y, z0 + lz, floor, ceiling);
                        } else s = palette.get(c);
                        if (s == null || s.isAir()) continue;
                        if (srcs != null) SOURCE.set(c == SOLID ? "shell<" + srcs[(ly * 16 + lz) * 16 + lx] : srcs[(ly * 16 + lz) * 16 + lx]);
                        if (CENSUS) floating(floats.get((ly * 16 + lz) * 16 + lx));
                        out.set(lx, y, lz, s);
                    }
                }
            }
            if (CENSUS) floating(false);
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
