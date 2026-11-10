package dev.awaken.gates.realms.kit;

import dev.awaken.gates.realms.Land;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last pass of a land drawn from a sheet: small strays can't survive it (SPEC §50.6). A lantern with nothing above it,
 * a plant with nothing under it, a vine on no wall, a lily off the water and a lone block in the air are taken out as the
 * chunk is drawn, and counted, so whoever placed them fixes the cause. It judges by {@link Kinds}, the float check's own
 * table. Anything bigger than one block is still the float check's to find: this is not a licence to place carelessly.
 */
public final class Settle {
    private Settle() {}

    private static final ThreadLocal<byte[]> KIND = ThreadLocal.withInitial(() -> new byte[V.W * V.W * V.H]);
    private static final int DX = V.W * V.H, DZ = V.H;

    /** What was taken out of a land, by block and by the piece it was in, over every chunk drawn so far. */
    public static final class Tally {
        private final Land land;
        // a chunk drawn again (the float check and the preview both draw every chunk) replaces its own count
        private final ConcurrentHashMap<Long, Map<String, Integer>> byChunk = new ConcurrentHashMap<>();

        public Tally(Land land) {
            this.land = land;
        }

        void put(int x0, int z0, Map<String, Integer> counts) {
            long key = (long) x0 << 32 | z0 & 0xFFFFFFFFL;
            if (counts == null || counts.isEmpty()) byChunk.remove(key);
            else byChunk.put(key, Map.copyOf(counts));
        }

        /** "crypt_candle in nave" to how many, the most first. */
        public Map<String, Integer> counts() {
            Map<String, Integer> sum = new HashMap<>();
            for (Map<String, Integer> m : byChunk.values()) m.forEach((k, n) -> sum.merge(k, n, Integer::sum));
            List<Map.Entry<String, Integer>> order = new ArrayList<>(sum.entrySet());
            order.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : order) out.put(e.getKey(), e.getValue());
            return out;
        }

        public int total() {
            int n = 0;
            for (Map<String, Integer> m : byChunk.values()) for (int c : m.values()) n += c;
            return n;
        }

        public void clear() {
            byChunk.clear();
        }

        /** One line for a log: "settled 14: 9 crypt_candle in nave, 5 keep_vines in gate". */
        public String line() {
            Map<String, Integer> c = counts();
            int total = 0;
            for (int n : c.values()) total += n;
            StringBuilder s = new StringBuilder("settled ").append(total);
            int shown = 0;
            for (Map.Entry<String, Integer> e : c.entrySet()) {
                if (shown == 10) {
                    s.append(", and ").append(c.size() - shown).append(" more kinds");
                    break;
                }
                s.append(shown++ == 0 ? ": " : ", ").append(e.getValue()).append(' ').append(e.getKey());
            }
            return s.toString();
        }

        @Override
        public String toString() {
            return line();
        }
    }

    /** What settling has taken out of this land so far, or null for a land that isn't settled (one that keeps its own paint). */
    public static Tally lastTally(Land land) {
        return land instanceof SheetLand<?> s ? s.tally : null;
    }

    /**
     * Settles the 16 x 16 columns of the chunk this volume covers, reading its one-block margin, and replaces the chunk's
     * count in the tally. Returns how many blocks it took out.
     */
    public static int run(V v, Tally t) {
        BlockState[] b = v.b;
        byte[] k = KIND.get();
        boolean small = false;
        BlockState last = null;
        int lastKind = Kinds.AIR;
        for (int i = 0; i < b.length; i++) {
            BlockState s = b[i];
            if (s == null) {
                k[i] = Kinds.AIR;
                continue;
            }
            if (s != last) {
                last = s;
                lastKind = Kinds.of(s);
            }
            k[i] = (byte) lastKind;
            if (lastKind >= Kinds.ON_GROUND && lastKind <= Kinds.AFLOAT) small = true;
        }
        Pass p = new Pass(v, k, t);
        if (small) p.small();
        // a lone block gone can leave what stood on it or hung from it with nothing: look once more (it can't make new lone blocks)
        if (p.specks() > 0 && small) p.small();
        t.put(v.x0, v.z0, p.counts);
        return p.removed;
    }

    private static final class Pass {
        final V v;
        final byte[] k;
        final Tally t;
        final int water;
        Map<String, Integer> counts;
        int removed;

        Pass(V v, byte[] k, Tally t) {
            this.v = v;
            this.k = k;
            this.t = t;
            water = t.land.water();
        }

        private static boolean holds(int kind) {
            return kind == Kinds.BODY || kind == Kinds.LEAF;
        }

        /** Everything that leans on something else, in the order that lets a chain of them be judged in one go. */
        void small() {
            // hanging things, top down: what it hangs from has already been judged
            for (int a = 1; a <= 16; a++) for (int c = 1; c <= 16; c++) {
                int base = (a * V.W + c) * V.H;
                for (int y = V.H - 1; y >= 0; y--) {
                    if (k[base + y] != Kinds.HANGS) continue;
                    int above = y + 1 < V.H ? k[base + y + 1] : Kinds.AIR;
                    if (!holds(above) && above != Kinds.HANGS) drop(a, y, c);
                }
            }
            // things that stand, bottom up: the top of a tall plant stands on its foot
            for (int a = 1; a <= 16; a++) for (int c = 1; c <= 16; c++) {
                int base = (a * V.W + c) * V.H;
                for (int y = 0; y < V.H; y++) {
                    if (k[base + y] != Kinds.ON_GROUND) continue;
                    int below = y > 0 ? k[base + y - 1] : Kinds.AIR;
                    if (!holds(below) && below != Kinds.ON_GROUND) drop(a, y, c);
                }
            }
            for (int a = 1; a <= 16; a++) for (int c = 1; c <= 16; c++) {
                int base = (a * V.W + c) * V.H;
                for (int y = 0; y < V.H; y++) {
                    int i = base + y, kind = k[i];
                    if (kind == Kinds.BESIDE) {
                        // a wall at its side, or (as the float check allows) a ceiling it hangs under
                        if (holds(k[i + DX]) || holds(k[i - DX]) || holds(k[i + DZ]) || holds(k[i - DZ]) || y + 1 < V.H && holds(k[i + 1])) continue;
                        drop(a, y, c);
                    } else if (kind == Kinds.AFLOAT) {
                        if (y == 0 || k[i - 1] != Kinds.WATER) drop(a, y, c);
                    }
                }
            }
        }

        /** Lone blocks: a body with no body or leaf anywhere round it. Returns how many went. */
        int specks() {
            int n = 0;
            for (int a = 1; a <= 16; a++) for (int c = 1; c <= 16; c++) {
                int base = (a * V.W + c) * V.H;
                // the float check's walk starts from the two lowest layers, so nothing there is ever a stray
                for (int y = 2; y < V.H; y++) {
                    int i = base + y;
                    if (k[i] != Kinds.BODY || holds(k[i - 1]) || touched(i, y)) continue;
                    int x = v.x0 + a - 1, z = v.z0 + c - 1;
                    boolean meant = false;
                    for (Piece<?> p : t.land.pieces()) if (p.airborne && p.covers(x, z)) meant = true;
                    if (meant) continue;
                    drop(a, y, c);
                    n++;
                }
            }
            return n;
        }

        private boolean touched(int i, int y) {
            for (int dx = -DX; dx <= DX; dx += DX) for (int dz = -DZ; dz <= DZ; dz += DZ) for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dz == 0 && dy == 0 || y + dy >= V.H) continue;
                if (holds(k[i + dx + dz + dy])) return true;
            }
            return false;
        }

        private void drop(int a, int y, int c) {
            int i = (a * V.W + c) * V.H + y;
            BlockState was = v.b[i];
            // under the waterline and touching water, the hole fills; anywhere else it's air
            boolean wet = y <= water && (y + 1 < V.H && k[i + 1] == Kinds.WATER || k[i + DX] == Kinds.WATER || k[i - DX] == Kinds.WATER || k[i + DZ] == Kinds.WATER || k[i - DZ] == Kinds.WATER);
            v.b[i] = wet ? Blocks.WATER.defaultBlockState() : null;
            k[i] = (byte) (wet ? Kinds.WATER : Kinds.AIR);
            removed++;
            int x = v.x0 + a - 1, z = v.z0 + c - 1;
            String where = "the open";
            for (Piece<?> p : t.land.pieces()) if (p.covers(x, z)) {
                where = p.label;
                break;
            }
            if (counts == null) counts = new HashMap<>();
            counts.merge(BuiltInRegistries.BLOCK.getKey(was.getBlock()).getPath() + " in " + where, 1, Integer::sum);
        }
    }
}
