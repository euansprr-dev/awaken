package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Kinds;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Nothing may ever float (SPEC §50.6). Draws a Realm's land exactly as the game does and walks outward from the ground
 * through everything solid: whatever the walk can't reach is in the air, whether it's one fence post or a tower. A plant
 * must stand on something, a hanging thing must hang from something. Only a piece that says it is airborne is let off.
 * Touching at an edge counts as touching, because that is how it reads to the eye; touching at one corner does not.
 */
public final class FloatCheck {
    private FloatCheck() {}

    public record Floater(int x, int y, int z, int blocks, String what) {}

    public record Report(List<Floater> floaters, int blocks, int chunks, long millis) {
        public boolean clean() {
            return floaters.isEmpty();
        }

        public String summary() {
            StringBuilder s = new StringBuilder(floaters.size() + " floating of " + blocks + " blocks in " + chunks + " chunks");
            int shown = 0;
            for (Floater f : floaters) {
                if (shown++ == 12) break;
                s.append("\n  ").append(f.blocks).append(" of ").append(f.what).append(" at ").append(f.x).append(' ').append(f.y).append(' ').append(f.z);
            }
            return s.toString();
        }
    }

    private static final int AIR = Kinds.AIR, BODY = Kinds.BODY, ON_GROUND = Kinds.ON_GROUND, HANGS = Kinds.HANGS, BESIDE = Kinds.BESIDE, AFLOAT = Kinds.AFLOAT, WATER = Kinds.WATER, LEAF = Kinds.LEAF;

    /** What a block needs to stay where it is: the kit's one table ({@link Kinds}), so Settle and this check agree. */
    public static int kindOf(BlockState s) {
        return Kinds.of(s);
    }

    /**
     * One chunk's worth of kinds, three bits a block, and the body blocks no walk has reached yet as plain bits: the walk
     * visits every solid block's eighteen neighbours, so that test has to be one array read, not a map and four bit sets.
     */
    private static final class Chunk {
        final BitSet a = new BitSet(16 * 16 * V.H), b = new BitSet(16 * 16 * V.H), c = new BitSet(16 * 16 * V.H);
        /** Bodies whose top is lower than their cell's ({@link Kinds#low}): they carry bodies, not small things. */
        final BitSet low = new BitSet();
        final long[] unreached = new long[16 * 16 * V.H >> 6];

        void put(int i, int kind) {
            if ((kind & 1) != 0) a.set(i);
            if ((kind & 2) != 0) b.set(i);
            if ((kind & 4) != 0) c.set(i);
            if (kind == BODY) unreached[i >> 6] |= 1L << i;
        }

        int get(int i) {
            return (a.get(i) ? 1 : 0) | (b.get(i) ? 2 : 0) | (c.get(i) ? 4 : 0);
        }

        /** A body block a walk has reached. */
        boolean seen(int i) {
            return (unreached[i >> 6] & 1L << i) == 0 && get(i) == BODY;
        }

        /** Takes a body block for the walk; false when it isn't one or was taken before. */
        boolean take(int i) {
            long bit = 1L << i;
            if ((unreached[i >> 6] & bit) == 0) return false;
            unreached[i >> 6] &= ~bit;
            return true;
        }
    }

    /** Every chunk of one scan, found by position with no hashing. */
    private static final class Grid {
        final int cx0, cz0, w, d;
        final Chunk[] chunks;

        Grid(int cx0, int cz0, int cx1, int cz1) {
            this.cx0 = cx0;
            this.cz0 = cz0;
            w = cx1 - cx0 + 1;
            d = cz1 - cz0 + 1;
            chunks = new Chunk[w * d];
        }

        Chunk at(int cx, int cz) {
            int i = cx - cx0, j = cz - cz0;
            return i < 0 || j < 0 || i >= w || j >= d ? null : chunks[j * w + i];
        }
    }

    /** The walk's to-do list: packed positions, last in first out (the order doesn't change what is reached). */
    private static final class Stack {
        long[] at = new long[1 << 16];
        int n;

        void push(int x, int y, int z) {
            if (n == at.length) at = java.util.Arrays.copyOf(at, n * 2);
            at[n++] = (long) (x + (1 << 24)) << 34 | (long) (z + (1 << 24)) << 9 | y;
        }

        long pop() {
            return at[--n];
        }

        static int x(long p) {
            return (int) (p >>> 34) - (1 << 24);
        }

        static int z(long p) {
            return (int) (p >>> 9 & 0x1FFFFFF) - (1 << 24);
        }

        static int y(long p) {
            return (int) (p & 0x1FF);
        }
    }

    /** Walks from everything on the stack through every body block it touches; returns how many it took, and whether it met a leaf. */
    private static int walk(Grid grid, Stack q, boolean[] metLeaf) {
        int n = 0;
        while (q.n > 0) {
            long p = q.pop();
            int px = Stack.x(p), py = Stack.y(p), pz = Stack.z(p);
            n++;
            for (int[] d : STEP) {
                int x = px + d[0], y = py + d[1], z = pz + d[2];
                if (y < 0 || y >= V.H) continue;
                Chunk c = grid.at(x >> 4, z >> 4);
                if (c == null) continue;
                int i = idx(x, y, z);
                if (c.take(i)) q.push(x, y, z);
                else if (metLeaf != null && !metLeaf[0] && c.get(i) == LEAF) metLeaf[0] = true;
            }
        }
        return n;
    }

    private static int idx(int x, int y, int z) {
        return ((x & 15) * 16 + (z & 15)) * V.H + y;
    }

    private static final int[][] STEP;

    static {
        List<int[]> s = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            int n = Math.abs(x) + Math.abs(y) + Math.abs(z);
            if (n == 1 || n == 2) s.add(new int[]{x, y, z});
        }
        STEP = s.toArray(new int[0][]);
    }

    /** Checks every chunk that has any part of the square from (minX, minZ) to (maxX, maxZ) in the Realm's coordinates. */
    public static Report scan(Land land, int minX, int minZ, int maxX, int maxZ) {
        long t0 = System.currentTimeMillis();
        int cx0 = minX >> 4, cz0 = minZ >> 4, cx1 = maxX >> 4, cz1 = maxZ >> 4;
        Grid grid = new Grid(cx0, cz0, cx1, cz1);
        // drawing is most of the work and a land may be drawn from any thread (worldgen does): every core draws chunks
        java.util.stream.IntStream.range(0, grid.chunks.length).parallel().forEach(n -> {
            int cx = cx0 + n % grid.w, cz = cz0 + n / grid.w;
            Chunk c = new Chunk();
            V v = new V(cx * 16, cz * 16);
            land.paint(v);
            int ox = cx * 16, oz = cz * 16;
            // a column is mostly runs of one block: ask the table only when the block changes
            BlockState last = null;
            int lastKind = AIR;
            boolean lastLow = false;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < V.H; y++) {
                BlockState s = v.get(ox + x, y, oz + z);
                if (s == null) continue;
                if (s != last) {
                    last = s;
                    lastKind = Kinds.of(s);
                    lastLow = lastKind == BODY && Kinds.low(s);
                }
                if (lastKind != AIR) c.put(idx(x, y, z), lastKind);
                if (lastLow) c.low.set(idx(x, y, z));
            }
            grid.chunks[n] = c;
        });
        Stack q = new Stack();
        // the walk starts at the world's floor and at the edge of what was drawn (land past the edge carries on)
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            Chunk c = grid.at(cx, cz);
            boolean rim = cx == cx0 || cx == cx1 || cz == cz0 || cz == cz1;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                boolean edge = rim && ((cx == cx0 && x == 0) || (cx == cx1 && x == 15) || (cz == cz0 && z == 0) || (cz == cz1 && z == 15));
                for (int y = 0; y < (edge ? V.H : 2); y++) if (c.take(idx(x, y, z))) q.push(cx * 16 + x, y, cz * 16 + z);
            }
        }
        int blocks = walk(grid, q, null);
        // what's left floats; things that lean on others are checked against the walk
        List<Piece<?>> airborne = new ArrayList<>();
        for (Piece<?> p : land.pieces()) if (p.airborne) airborne.add(p);
        List<Floater> out = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            Chunk c = grid.at(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < V.H; y++) {
                int i = idx(x, y, z), k = c.get(i);
                if (k == AIR) continue;
                int wx = cx * 16 + x, wz = cz * 16 + z;
                if (cx == cx0 && x == 0 || cx == cx1 && x == 15 || cz == cz0 && z == 0 || cz == cz1 && z == 15) continue;
                if (k == BODY) {
                    if (c.seen(i)) continue;
                    if (airborne.stream().anyMatch(p -> p.covers(wx, wz))) continue;
                    Floater fl = floater(grid, q, wx, y, wz);
                    if (fl != null) out.add(fl);
                } else if (k == ON_GROUND) {
                    if (y > 0 && c.low.get(idx(x, y - 1, z))) {
                        out.add(new Floater(wx, y, wz, 1, "a plant or small thing half a block over a slab laid low (" + named(land, wx, y, wz) + ")"));
                        continue;
                    }
                    if (held(grid, wx, y - 1, wz) || below(grid, wx, y - 1, wz, ON_GROUND)) continue;
                    out.add(new Floater(wx, y, wz, 1, "a plant or small thing with nothing under it (" + named(land, wx, y, wz) + ")"));
                } else if (k == AFLOAT) {
                    if (below(grid, wx, y - 1, wz, WATER)) continue;
                    out.add(new Floater(wx, y, wz, 1, "something meant to float with no water under it"));
                } else if (k == HANGS) {
                    if (held(grid, wx, y + 1, wz) || below(grid, wx, y + 1, wz, HANGS)) continue;
                    out.add(new Floater(wx, y, wz, 1, "a hanging thing with nothing above it"));
                } else if (k == BESIDE) {
                    if (held(grid, wx + 1, y, wz) || held(grid, wx - 1, y, wz) || held(grid, wx, y, wz + 1) || held(grid, wx, y, wz - 1) || held(grid, wx, y + 1, wz)) continue;
                    out.add(new Floater(wx, y, wz, 1, "a vine on nothing"));
                }
            }
        }
        return new Report(out, blocks, grid.chunks.length, System.currentTimeMillis() - t0);
    }

    /** What a stray is and what it's standing on, read again from the land: a failure should say which block to go and fix. */
    private static String named(Land land, int x, int y, int z) {
        V v = new V(x >> 4 << 4, z >> 4 << 4);
        land.paint(v);
        return v.get(x, y, z) + " on " + v.get(x, y - 1, z);
    }

    private static boolean held(Grid grid, int x, int y, int z) {
        if (y < 0 || y >= V.H) return false;
        Chunk c = grid.at(x >> 4, z >> 4);
        if (c == null) return true; // past the drawn edge: can't tell, so it's not a finding
        int i = idx(x, y, z);
        return c.get(i) == LEAF || c.seen(i);
    }

    private static boolean below(Grid grid, int x, int y, int z, int kind) {
        if (y < 0 || y >= V.H) return false;
        Chunk c = grid.at(x >> 4, z >> 4);
        return c != null && c.get(idx(x, y, z)) == kind;
    }

    /** Marks a whole floating cluster seen, so one tower is one finding. */
    private static final int MAX_TWIG = 12;

    private static Floater floater(Grid grid, Stack q, int x0, int y0, int z0) {
        grid.at(x0 >> 4, z0 >> 4).take(idx(x0, y0, z0));
        q.push(x0, y0, z0);
        boolean[] inLeaves = {false};
        int n = walk(grid, q, inLeaves);
        if (n <= MAX_TWIG && inLeaves[0]) return null; // a branch end inside a crown is held up by the leaves round it
        return new Floater(x0, y0, z0, n, "a floating thing");
    }
}
