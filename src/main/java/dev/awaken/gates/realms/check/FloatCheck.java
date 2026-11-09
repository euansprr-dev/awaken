package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    private static final int AIR = 0, BODY = 1, ON_GROUND = 2, HANGS = 3, BESIDE = 4;

    private static final Map<BlockState, Integer> KIND = java.util.Collections.synchronizedMap(new IdentityHashMap<>());

    private static int kind(BlockState s) {
        if (s == null) return AIR;
        return KIND.computeIfAbsent(s, FloatCheck::classify);
    }

    private static int classify(BlockState s) {
        Block b = s.getBlock();
        if (s.isAir() || !s.getFluidState().isEmpty() && b == Blocks.WATER || b == Blocks.LIGHT || b == Blocks.BARRIER) return AIR;
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (b instanceof LanternBlock) return s.getValue(BlockStateProperties.HANGING) ? HANGS : ON_GROUND;
        if (b instanceof HangingRootsBlock || id.endsWith("_drip") || id.endsWith("_vines") || id.endsWith("_vines_plant")) return HANGS;
        if (b instanceof VineBlock) return BESIDE;
        if (b instanceof BushBlock || b instanceof DoublePlantBlock || b instanceof CarpetBlock || b instanceof net.minecraft.world.level.block.CropBlock
                || id.endsWith("_crumbs") || id.endsWith("_petals") || id.endsWith("_candle") || id.endsWith("_drift")) return ON_GROUND;
        return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() ? ON_GROUND : BODY;
    }

    /** One chunk's worth of kinds, three bits a block. */
    private static final class Chunk {
        final BitSet a = new BitSet(16 * 16 * V.H), b = new BitSet(16 * 16 * V.H), c = new BitSet(16 * 16 * V.H), seen = new BitSet(16 * 16 * V.H);

        void put(int i, int kind) {
            if ((kind & 1) != 0) a.set(i);
            if ((kind & 2) != 0) b.set(i);
            if ((kind & 4) != 0) c.set(i);
        }

        int get(int i) {
            return (a.get(i) ? 1 : 0) | (b.get(i) ? 2 : 0) | (c.get(i) ? 4 : 0);
        }
    }

    private static int idx(int x, int y, int z) {
        return ((x & 15) * 16 + (z & 15)) * V.H + y;
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
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
        Map<Long, Chunk> map = new HashMap<>();
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            Chunk c = new Chunk();
            V v = new V(cx * 16, cz * 16);
            land.paint(v);
            int ox = cx * 16, oz = cz * 16;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < V.H; y++) {
                int k = kind(v.get(ox + x, y, oz + z));
                if (k != AIR) c.put(idx(x, y, z), k);
            }
            map.put(key(cx, cz), c);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        // the walk starts at the world's floor and at the edge of what was drawn (land past the edge carries on)
        for (var e : map.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            Chunk c = e.getValue();
            boolean rim = cx == cx0 || cx == cx1 || cz == cz0 || cz == cz1;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                boolean edge = rim && ((cx == cx0 && x == 0) || (cx == cx1 && x == 15) || (cz == cz0 && z == 0) || (cz == cz1 && z == 15));
                for (int y = 0; y < (edge ? V.H : 2); y++) {
                    int i = idx(x, y, z);
                    if (c.get(i) == BODY && !c.seen.get(i)) {
                        c.seen.set(i);
                        q.add(new int[]{cx * 16 + x, y, cz * 16 + z});
                    }
                }
            }
        }
        int blocks = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            blocks++;
            for (int[] d : STEP) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                if (y < 0 || y >= V.H) continue;
                Chunk c = map.get(key(x >> 4, z >> 4));
                if (c == null) continue;
                int i = idx(x, y, z);
                if (c.seen.get(i) || c.get(i) != BODY) continue;
                c.seen.set(i);
                q.add(new int[]{x, y, z});
            }
        }
        // what's left floats; things that lean on others are checked against the walk
        List<Piece<?>> airborne = new ArrayList<>();
        for (Piece<?> p : land.pieces()) if (p.airborne) airborne.add(p);
        List<Floater> out = new ArrayList<>();
        for (var e : map.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            Chunk c = e.getValue();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < V.H; y++) {
                int i = idx(x, y, z), k = c.get(i);
                if (k == AIR) continue;
                int wx = cx * 16 + x, wz = cz * 16 + z;
                if (cx == cx0 && x == 0 || cx == cx1 && x == 15 || cz == cz0 && z == 0 || cz == cz1 && z == 15) continue;
                if (k == BODY) {
                    if (c.seen.get(i)) continue;
                    if (airborne.stream().anyMatch(p -> p.covers(wx, wz))) continue;
                    out.add(floater(map, wx, y, wz));
                } else if (k == ON_GROUND) {
                    if (held(map, wx, y - 1, wz) || below(map, wx, y - 1, wz, ON_GROUND)) continue;
                    out.add(new Floater(wx, y, wz, 1, "a plant or small thing with nothing under it"));
                } else if (k == HANGS) {
                    if (held(map, wx, y + 1, wz) || below(map, wx, y + 1, wz, HANGS)) continue;
                    out.add(new Floater(wx, y, wz, 1, "a hanging thing with nothing above it"));
                } else if (k == BESIDE) {
                    if (held(map, wx + 1, y, wz) || held(map, wx - 1, y, wz) || held(map, wx, y, wz + 1) || held(map, wx, y, wz - 1) || held(map, wx, y + 1, wz)) continue;
                    out.add(new Floater(wx, y, wz, 1, "a vine on nothing"));
                }
            }
        }
        return new Report(out, blocks, map.size(), System.currentTimeMillis() - t0);
    }

    private static boolean held(Map<Long, Chunk> map, int x, int y, int z) {
        if (y < 0 || y >= V.H) return false;
        Chunk c = map.get(key(x >> 4, z >> 4));
        if (c == null) return true; // past the drawn edge: can't tell, so it's not a finding
        int i = idx(x, y, z);
        return c.get(i) == BODY && c.seen.get(i);
    }

    private static boolean below(Map<Long, Chunk> map, int x, int y, int z, int kind) {
        if (y < 0 || y >= V.H) return false;
        Chunk c = map.get(key(x >> 4, z >> 4));
        return c != null && c.get(idx(x, y, z)) == kind;
    }

    /** Marks a whole floating cluster seen, so one tower is one finding. */
    private static Floater floater(Map<Long, Chunk> map, int x0, int y0, int z0) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x0, y0, z0});
        map.get(key(x0 >> 4, z0 >> 4)).seen.set(idx(x0, y0, z0));
        int n = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            n++;
            for (int[] d : STEP) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                if (y < 0 || y >= V.H) continue;
                Chunk c = map.get(key(x >> 4, z >> 4));
                if (c == null) continue;
                int i = idx(x, y, z);
                if (c.seen.get(i) || c.get(i) != BODY) continue;
                c.seen.set(i);
                q.add(new int[]{x, y, z});
            }
        }
        return new Floater(x0, y0, z0, n, "a floating thing");
    }
}
