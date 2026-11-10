package dev.awaken.finds.kit;

import dev.awaken.finds.Scene;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.function.IntFunction;

/**
 * The building kit for places (SPEC §59.5). Everything is in the kind's own coordinates with heights relative to the
 * heart's ground: after {@code Scene.level} the ground's top is 0 and things stand from 1. Nothing here knows about a
 * particular place; a place is these calls in an order. Rules it keeps: a wall has a foot course and two stones and,
 * when ruined, a ragged top; a roof has an overhang; randomness is a hash of the site's seed and the position, so a
 * place is the same from every chunk that writes a part of it.
 */
public final class B {
    private B() {}

    public static final BlockState AIR = Blocks.AIR.defaultBlockState(), WATER = Blocks.WATER.defaultBlockState();
    /** North, east, south, west. */
    public static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    public static final Direction[] DIR = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // ---- chance that is the same from every chunk -------------------------------------------------------------------

    /** 0..255 for a position at this site. */
    public static int h(Scene s, int x, int y, int z) {
        long v = s.site.seed() * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0xD6E8FEB86659FD93L + z * 0xA0761D6478BD642FL;
        v ^= v >>> 29;
        v *= 0xBF58476D1CE4E5B9L;
        v ^= v >>> 32;
        return (int) (v & 255);
    }

    /** Smooth 0..1 across the ground, one hump about every {@code scale} blocks. */
    public static double n(Scene s, double x, double z, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = h(s, ix, 977, iz), b = h(s, ix + 1, 977, iz), c = h(s, ix, 977, iz + 1), d = h(s, ix + 1, 977, iz + 1);
        return ((a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz) / 255.0;
    }

    // ---- states -----------------------------------------------------------------------------------------------------

    public static BlockState stair(BlockState st, Direction up) {
        return st.hasProperty(StairBlock.FACING) ? st.setValue(StairBlock.FACING, up) : st;
    }

    /** A stair hung upside down, its full side toward {@code back}: a corbel, an eave's underside, a seat's back. */
    public static BlockState stairT(BlockState st, Direction back) {
        return st.hasProperty(StairBlock.FACING) ? st.setValue(StairBlock.FACING, back).setValue(StairBlock.HALF, Half.TOP) : st;
    }

    public static BlockState top(BlockState slab) {
        return slab.hasProperty(SlabBlock.TYPE) ? slab.setValue(SlabBlock.TYPE, SlabType.TOP) : slab;
    }

    public static BlockState axis(BlockState log, Direction.Axis a) {
        return log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, a) : log;
    }

    public static BlockState leaf(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    /** A pane, bar or fence joined along one line (it joins the rest itself once the chunk is finished). */
    public static BlockState along(BlockState st, boolean x) {
        if (!(st.getBlock() instanceof CrossCollisionBlock)) return st;
        return x ? st.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true)
                : st.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.SOUTH, true);
    }

    public static BlockState lantern(boolean hanging, boolean soul) {
        return (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    public static BlockState candles(int n, boolean lit) {
        return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n))).setValue(CandleBlock.LIT, lit);
    }

    /** Light with nothing to see: only to lift an interior to where it can be read. */
    public static BlockState light(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    public static BlockState ladder(Direction away) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, away);
    }

    public static BlockState st(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    // ---- solids -----------------------------------------------------------------------------------------------------

    public static void fill(Scene s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.put(x, y, z, st);
    }

    public static void air(Scene s, int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(s, x0, y0, z0, x1, y1, z1, AIR);
    }

    /** One or the other by the position's hash: {@code pct} in a hundred are {@code b}. */
    public static void mix(Scene s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState a, BlockState b, int pct) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.put(x, y, z, h(s, x, y, z) * 100 < pct * 256 ? b : a);
    }

    /**
     * A straight wall from one corner to another: a foot course at {@code y0}, a body of two stones, and with
     * {@code ruin} above 0 a top that falls away by up to that many blocks, finished with a broken slab here and there.
     */
    public static void wall(Scene s, int x0, int z0, int x1, int z1, int y0, int y1, Pal p, int ruin) {
        int dx = Integer.signum(x1 - x0), dz = Integer.signum(z1 - z0), n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + dx * i, z = z0 + dz * i, top = y1;
            if (ruin > 0) top = Math.max(y0 + 1, Math.min(y1, y1 - (int) Math.round(n(s, x + 31, z - 17, 3.5) * ruin * 1.7 - ruin * 0.35)));
            for (int y = y0; y <= top; y++) s.put(x, y, z, y == y0 ? p.base() : h(s, x, y, z) < 64 ? p.wallB() : p.wall());
            if (ruin > 0 && top < y1 && h(s, x, top + 1, z) < 120) s.put(x, top + 1, z, p.slab());
        }
    }

    /** Four walls round a rectangle. */
    public static void walls(Scene s, int x0, int z0, int x1, int z1, int y0, int y1, Pal p, int ruin) {
        wall(s, x0, z0, x1, z0, y0, y1, p, ruin);
        wall(s, x0, z1, x1, z1, y0, y1, p, ruin);
        wall(s, x0, z0, x0, z1, y0, y1, p, ruin);
        wall(s, x1, z0, x1, z1, y0, y1, p, ruin);
    }

    /** A floor of two materials. */
    public static void floor(Scene s, int x0, int z0, int x1, int z1, int y, BlockState a, BlockState b, int pct) {
        mix(s, x0, y, z0, x1, y, z1, a, b, pct);
    }

    /**
     * A pitched roof over walls whose top course is at {@code y}, overhanging by {@code over} (0 or 1: the eave hangs
     * beside the top course and the next row sits on it, so every row touches the last). The ridge runs along x
     * when {@code alongX}. The gable ends are walled in the palette's wall. {@code holes} in a hundred of it has fallen in.
     */
    public static void gable(Scene s, int x0, int z0, int x1, int z1, int y, Pal p, boolean alongX, int over, int holes) {
        int a0 = (alongX ? z0 : x0) - over, a1 = (alongX ? z1 : x1) + over, b0 = (alongX ? x0 : z0) - over, b1 = (alongX ? x1 : z1) + over;
        for (int i = 0; a0 + i <= a1 - i; i++) {
            int lo = a0 + i, hi = a1 - i, yy = y + 1 - over + i;
            for (int b = b0; b <= b1; b++) {
                // a roof that has fallen in keeps its eaves, its gable ends and its ridge beam: nothing left hanging from nothing
                boolean gone = holes > 0 && i > 1, gone2 = gone;
                if (lo == hi) {                                   // the ridge: a beam with a slab cap
                    put(s, alongX, b, yy - 1, lo, axis(p.log(), alongX ? Direction.Axis.X : Direction.Axis.Z));
                    if (!gone) put(s, alongX, b, yy, lo, p.slab());
                    continue;
                }
                if (!gone) put(s, alongX, b, yy, lo, stair(p.stair(), alongX ? Direction.SOUTH : Direction.EAST));
                if (!gone2) put(s, alongX, b, yy, hi, stair(p.stair(), alongX ? Direction.NORTH : Direction.WEST));
                if (gone && hi - lo == 1) put(s, alongX, b, yy - 1, lo, axis(p.log(), alongX ? Direction.Axis.X : Direction.Axis.Z));
            }
            // the gable ends, walled under every stair so each row stands on the wall and not only on its neighbour's corner
            if (i > 0) for (int a = Math.max(lo, alongX ? z0 : x0); a <= Math.min(hi, alongX ? z1 : x1); a++) {
                put(s, alongX, alongX ? x0 : z0, yy - 1, a, h(s, a, yy, 3) < 64 ? p.wallB() : p.wall());
                put(s, alongX, alongX ? x1 : z1, yy - 1, a, h(s, a, yy, 7) < 64 ? p.wallB() : p.wall());
            }
        }
    }

    private static void put(Scene s, boolean alongX, int b, int y, int a, BlockState st) {
        if (alongX) s.put(b, y, a, st);
        else s.put(a, y, b, st);
    }

    public static void disc(Scene s, int cx, int cz, double r, int y, BlockState st) {
        int ir = (int) Math.ceil(r);
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++) if (x * x + z * z <= r * r + r * 0.6) s.put(cx + x, y, cz + z, st);
    }

    /** A ring one block thick. */
    public static void ring(Scene s, int cx, int cz, double r, int y, BlockState st) {
        int ir = (int) Math.ceil(r) + 1;
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= r - 0.71 && d < r + 0.71) s.put(cx + x, y, cz + z, st);
            }
    }

    /** A round wall, built like {@link #wall}. */
    public static void cyl(Scene s, int cx, int cz, double r, int y0, int y1, Pal p, int ruin) {
        int ir = (int) Math.ceil(r) + 1;
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < r - 0.5 || d >= r + 0.5) continue;
                int top = y1;
                if (ruin > 0) top = Math.max(y0 + 1, Math.min(y1, y1 - (int) Math.round(n(s, cx + x + 13, cz + z + 7, 3.0) * ruin * 1.7 - ruin * 0.35)));
                for (int y = y0; y <= top; y++) s.put(cx + x, y, cz + z, y == y0 ? p.base() : h(s, cx + x, y, cz + z) < 64 ? p.wallB() : p.wall());
            }
    }

    /** A stepped cone: a round tower's cap. Each step has a slab lip. */
    public static void cone(Scene s, int cx, int cz, int r, int y, BlockState block, BlockState slab) {
        for (int k = 0; k <= r; k++) {
            ring(s, cx, cz, r - k + 1, y + k, slab);
            disc(s, cx, cz, r - k, y + k, block);
        }
    }

    /** Half a hollow ball. */
    public static void dome(Scene s, int cx, int cz, double r, int y, BlockState st) {
        int ir = (int) Math.ceil(r) + 1;
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++)
                for (int k = 0; k <= ir; k++) {
                    double d = Math.sqrt(x * x + z * z + k * k);
                    if (d >= r - 0.95 && d < r + 0.5) s.put(cx + x, y + k, cz + z, st);
                }
    }

    public static void pillar(Scene s, int x, int z, int y0, int y1, BlockState shaft, BlockState cap) {
        for (int y = y0; y <= y1; y++) s.put(x, y, z, y == y0 || y == y1 ? cap : shaft);
    }

    /** An arch across x at one z (or across z at one x): two piers, a lintel, and a corbel under each end of it. */
    public static void arch(Scene s, int a0, int a1, int c, int y0, int y1, Pal p, boolean acrossX) {
        for (int y = y0; y <= y1; y++) {
            put(s, !acrossX, c, y, a0, y == y0 ? p.base() : p.wall());
            put(s, !acrossX, c, y, a1, y == y0 ? p.base() : p.wall());
        }
        for (int a = a0; a <= a1; a++) put(s, !acrossX, c, y1 + 1, a, a == (a0 + a1) / 2 ? p.trim() : p.wall());
        if (a1 - a0 >= 3) {
            put(s, !acrossX, c, y1, a0 + 1, stairT(p.stair(), acrossX ? Direction.WEST : Direction.NORTH));
            put(s, !acrossX, c, y1, a1 - 1, stairT(p.stair(), acrossX ? Direction.EAST : Direction.SOUTH));
        }
    }

    /** A flight of steps {@code width} wide climbing one block a step in direction {@code d} (0 north, 1 east, 2 south, 3 west), filled beneath. */
    public static void steps(Scene s, int x, int y, int z, int d, int n, int width, BlockState stairSt, BlockState under) {
        int px = DZ[d] == 0 ? 0 : 1, pz = DX[d] == 0 ? 0 : 1;
        for (int i = 0; i < n; i++)
            for (int w = 0; w < width; w++) {
                int sx = x + DX[d] * i + px * w, sz = z + DZ[d] * i + pz * w;
                s.put(sx, y + i, sz, stair(stairSt, DIR[d]));
                if (under != null) for (int k = y; k < y + i; k++) s.put(sx, k, sz, under);
            }
    }

    /** A lump of something with a rough skin: a boulder, a mound, a crag. {@code pick} gets the position's hash. */
    public static void blob(Scene s, int cx, int cy, int cz, double rx, double ry, double rz, IntFunction<BlockState> pick) {
        int ax = (int) rx + 2, ay = (int) ry + 2, az = (int) rz + 2, wx = 2 * ax + 1, wy = 2 * ay + 1, wz = 2 * az + 1;
        boolean[] in = new boolean[wx * wy * wz], ok = new boolean[wx * wy * wz];
        for (int x = -ax; x <= ax; x++)
            for (int y = -ay; y <= ay; y++)
                for (int z = -az; z <= az; z++) {
                    double f = x * x / (rx * rx) + y * y / (ry * ry) + z * z / (rz * rz);
                    in[((x + ax) * wy + y + ay) * wz + z + az] = f <= 0.78 + n(s, cx + x + y * 0.7, cz + z - y * 0.7, 3.0) * 0.44;
                }
        // a lump is one piece: only what joins its middle by a face is kept, so no speck hangs off its skin
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[] {0, 0, 0});
        ok[(ax * wy + ay) * wz + az] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            s.put(cx + c[0], cy + c[1], cz + c[2], pick.apply(h(s, cx + c[0], cy + c[1], cz + c[2])));
            for (int d = 0; d < 6; d++) {
                int x = c[0] + (d == 0 ? 1 : d == 1 ? -1 : 0), y = c[1] + (d == 2 ? 1 : d == 3 ? -1 : 0), z = c[2] + (d == 4 ? 1 : d == 5 ? -1 : 0);
                if (Math.abs(x) > ax || Math.abs(y) > ay || Math.abs(z) > az) continue;
                int i = ((x + ax) * wy + y + ay) * wz + z + az;
                if (in[i] && !ok[i]) { ok[i] = true; q.add(new int[] {x, y, z}); }
            }
        }
    }

    /**
     * A hollow cut out of the ground with a rough wall, lined one block deep with {@code lining} so the cave's skin is
     * the place's own and not whatever the land happened to be.
     */
    public static void carve(Scene s, int cx, int cy, int cz, double rx, double ry, double rz, IntFunction<BlockState> lining) {
        for (int x = (int) -rx - 2; x <= rx + 2; x++)
            for (int y = (int) -ry - 2; y <= ry + 2; y++)
                for (int z = (int) -rz - 2; z <= rz + 2; z++) {
                    double f = x * x / (rx * rx) + y * y / (ry * ry) + z * z / (rz * rz), edge = 0.8 + n(s, cx + x + y * 0.6, cz + z - y * 0.6, 3.4) * 0.4;
                    if (f <= edge) s.put(cx + x, cy + y, cz + z, AIR);
                    else if (lining != null && f <= edge + 0.55 && cy + y <= s.g(cx + x, cz + z) && !s.taken(cx + x, cy + y, cz + z)) s.put(cx + x, cy + y, cz + z, lining.apply(h(s, cx + x, cy + y, cz + z)));
                }
    }

    /** Still water in a hollow of its own: surface at {@code y}, deepest in the middle, on a bed. Keep it inside levelled ground with {@code y} at 0 or below. */
    public static void pool(Scene s, int cx, int cz, double rx, double rz, int y, int depth, BlockState bed, BlockState bedB) {
        for (int x = (int) -rx - 1; x <= rx + 1; x++)
            for (int z = (int) -rz - 1; z <= rz + 1; z++) {
                double f = x * x / (rx * rx) + z * z / (rz * rz);
                if (f > 1) continue;
                int d = Math.max(1, (int) Math.round(depth * (1 - f * f * 0.75)));
                for (int k = 0; k < d; k++) s.put(cx + x, y - k, cz + z, WATER);
                s.put(cx + x, y - d, cz + z, h(s, cx + x, y, cz + z) < 90 ? bedB : bed);
            }
    }

    /** Swaps the ground itself over a disc: {@code pick} gets a hash and returns the block, or null to leave the land's own. */
    public static void ground(Scene s, int cx, int cz, double r, IntFunction<BlockState> pick) {
        int ir = (int) Math.ceil(r);
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++) {
                if (x * x + z * z > r * r * (0.75 + n(s, cx + x, cz + z, 4.0) * 0.4)) continue;
                BlockState st = pick.apply(h(s, cx + x, -5, cz + z));
                if (st != null) s.put(cx + x, s.g(cx + x, cz + z), cz + z, st);
            }
    }

    /** A trodden way between two points, following the ground. */
    public static void path(Scene s, int x0, int z0, int x1, int z1, int half, BlockState a, BlockState b) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            int x = (int) Math.round(x0 + (x1 - x0) * t + (n(s, i, 3, 6.0) - 0.5) * 3), z = (int) Math.round(z0 + (z1 - z0) * t + (n(s, 9, i, 6.0) - 0.5) * 3);
            for (int ox = -half; ox <= half; ox++)
                for (int oz = -half; oz <= half; oz++) {
                    if (h(s, x + ox, 1, z + oz) < 70) continue;
                    int g = s.g(x + ox, z + oz);
                    if (!s.taken(x + ox, g + 1, z + oz)) s.put(x + ox, g, z + oz, h(s, x + ox, 2, z + oz) < 100 ? b : a);
                }
        }
    }

    // ---- what grows -------------------------------------------------------------------------------------------------

    public static void leaves(Scene s, int cx, int cy, int cz, double r, BlockState leaf) {
        int ir = (int) Math.ceil(r);
        for (int x = -ir; x <= ir; x++)
            for (int y = -ir; y <= ir; y++)
                for (int z = -ir; z <= ir; z++) {
                    double f = (x * x + y * y * 1.7 + z * z) / (r * r);
                    if (f > 1.1 || s.taken(cx + x, cy + y, cz + z)) continue;
                    // a solid middle, and loose leaves outside it only where one face touches the middle
                    boolean core = f <= 0.72, touch = false;
                    if (!core) for (int d = 0; d < 6 && !touch; d++) {
                        int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0), nz = z + (d == 4 ? 1 : d == 5 ? -1 : 0);
                        touch = (nx * nx + ny * ny * 1.7 + nz * nz) / (r * r) <= 0.72;
                    }
                    if (core || touch && h(s, cx + x, cy + y, cz + z) < 150) s.put(cx + x, cy + y, cz + z, leaf);
                }
    }

    /** A line of blocks from one point to another in which every block touches the last by a face: a branch, a root, a rope. */
    public static void limb(Scene s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
        int x = x0, y = y0, z = z0, m = Math.max(1, Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0))));
        s.put(x, y, z, st);
        for (int i = 1; i <= m; i++) {
            int tx = x0 + Math.round((x1 - x0) * i / (float) m), ty = y0 + Math.round((y1 - y0) * i / (float) m), tz = z0 + Math.round((z1 - z0) * i / (float) m);
            while (x != tx) { x += Integer.signum(tx - x); s.put(x, y, z, st); }
            while (z != tz) { z += Integer.signum(tz - z); s.put(x, y, z, st); }
            while (y != ty) { y += Integer.signum(ty - y); s.put(x, y, z, st); }
        }
    }

    /** A tree with a real crown: a trunk, four limbs reaching out and up, a cloud of leaves on each and one over the top. */
    public static void tree(Scene s, int x, int z, int y, int h, BlockState log, BlockState leaf, double r) {
        for (int i = 0; i < h; i++) s.put(x, y + i, z, log);
        for (int d = 0; d < 4; d++) {
            int len = 2 + (h(s, x, d, z) & 1), ly = y + h - 3 + (d & 1);
            limb(s, x + DX[d], ly, z + DZ[d], x + DX[d] * len, ly + 1, z + DZ[d] * len, axis(log, DX[d] != 0 ? Direction.Axis.X : Direction.Axis.Z));
            leaves(s, x + DX[d] * len, ly + 2, z + DZ[d] * len, r, leaf);
        }
        leaves(s, x, y + h + 1, z, r + 0.6, leaf);
    }

    /** A giant: a round trunk that flares into roots at the foot and carries a broad crown of several leaf clouds. */
    public static void giant(Scene s, int x, int z, int y, int h, int tr, BlockState log, BlockState leaf, double r) {
        for (int i = 0; i < h; i++) disc(s, x, z, i < 2 ? tr + 0.9 : tr, y + i, log);
        for (int d = 0; d < 8; d++) {
            double a = d * Math.PI / 4 + 0.3;
            for (int j = 1; j <= tr + 3; j++) {
                int rx = x + (int) Math.round(Math.cos(a) * (tr + j)), rz = z + (int) Math.round(Math.sin(a) * (tr + j));
                // a root runs down to the ground, or to whatever the tree stands on
                for (int k = y + Math.max(0, 2 - j); k >= Math.min(y - 1, s.g(rx, rz)); k--) {
                    if (k < y && s.taken(rx, k, rz)) break;
                    s.put(rx, k, rz, log);
                }
            }
            int lx = x + (int) Math.round(Math.cos(a) * (tr + r * 0.8)), lz = z + (int) Math.round(Math.sin(a) * (tr + r * 0.8)), ly = y + h - 2 - (d & 1) * 2;
            limb(s, x + (int) Math.round(Math.cos(a) * tr), ly - 2, z + (int) Math.round(Math.sin(a) * tr), lx, ly, lz, log);
            leaves(s, lx, ly + 1, lz, r, leaf);
        }
        leaves(s, x, y + h + 1, z, r + 1.5, leaf);
    }

    /** A palm: a leaning trunk and eight drooping fronds. */
    public static void palm(Scene s, int x, int z, int y, int h, int lean) {
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState(), frond = leaf(Blocks.JUNGLE_LEAVES);
        int tx = x, tz = z;
        for (int i = 0; i < h; i++) {
            if (i == h / 2 || i == h * 3 / 4) {                   // the lean: an elbow, so the trunk stays one piece
                tx += DX[lean & 3];
                tz += DZ[lean & 3];
                s.put(tx, y + i - 1, tz, log);
            }
            s.put(tx, y + i, tz, log);
        }
        s.put(tx, y + h, tz, frond);
        for (int d = 0; d < 8; d++) {
            int ax = d < 4 ? DX[d] : (d == 4 || d == 7 ? 1 : -1), az = d < 4 ? DZ[d] : (d < 6 ? 1 : -1), len = d < 4 ? 4 : 3;
            limb(s, tx, y + h, tz, tx + ax * 2, y + h, tz + az * 2, frond);
            limb(s, tx + ax * 2, y + h, tz + az * 2, tx + ax * len, y + h - 2, tz + az * len, frond);
        }
    }

    /**
     * Vines on the faces of what the scene has built inside a box, hanging down a few blocks where there is room.
     * Call it after the masonry is up.
     */
    public static void vines(Scene s, int x0, int y0, int z0, int x1, int y1, int z1, int pct) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y1; y >= y0; y--) {
                    if (s.taken(x, y, z) || h(s, x, y, z) * 100 >= pct * 256) continue;
                    for (int d = 0; d < 4; d++) {
                        BlockState back = s.get(x + DX[d], y, z + DZ[d]);
                        if (back == null || !back.canOcclude()) continue;
                        BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(DIR[d]), true);
                        int len = 1 + (h(s, x, y + 50, z) & 3);
                        for (int k = 0; k < len && y - k >= y0 && !s.taken(x, y - k, z); k++) s.put(x, y - k, z, vine);
                        break;
                    }
                }
    }

    /** Moss carpet on the tops of what the scene has built inside a box. */
    public static void moss(Scene s, int x0, int y0, int z0, int x1, int y1, int z1, int pct) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) {
                    BlockState b = s.get(x, y, z);
                    if (b == null || !b.canOcclude() || s.taken(x, y + 1, z) || h(s, x, y + 9, z) * 100 >= pct * 256) continue;
                    s.put(x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
    }

    /** Fallen stone at the foot of something broken: a scatter of the palette's own blocks, slabs and stairs on the ground. */
    public static void rubble(Scene s, int cx, int cz, int r, int n, Pal p) {
        for (int i = 0, tries = 0; i < n && tries < n * 6; tries++) {
            int x = cx + s.rnd.nextInt(2 * r + 1) - r, z = cz + s.rnd.nextInt(2 * r + 1) - r, g = s.g(x, z);
            if (s.taken(x, g + 1, z)) continue;
            int k = s.rnd.nextInt(5);
            s.put(x, g + 1, z, k == 0 ? p.base() : k == 1 ? p.slab() : k == 2 ? stair(p.stair(), DIR[s.rnd.nextInt(4)]) : k == 3 ? Props.LOOSE_STONE.get().of(s.rnd.nextInt(4)) : p.wallB());
            i++;
        }
    }

    /**
     * A small building, whole: a floor, walls on a foot course with timber posts at the corners, a window in each long
     * wall, a doorway two high in the middle of side {@code door} (0 north, 1 east, 2 south, 3 west), a pitched roof with
     * an overhang, and a lantern hung from the ridge. {@code holes} in a hundred of the roof has fallen in.
     */
    public static void house(Scene s, int x0, int z0, int x1, int z1, int y, int wallH, Pal p, int door, int holes) {
        boolean alongX = x1 - x0 >= z1 - z0;
        floor(s, x0, z0, x1, z1, y, p.floor(), p.plank(), 30);
        air(s, x0 + 1, y + 1, z0 + 1, x1 - 1, y + wallH + 3, z1 - 1);
        walls(s, x0, z0, x1, z1, y + 1, y + wallH, p, holes > 30 ? 2 : 0);
        for (int cx = x0; cx <= x1; cx += x1 - x0) for (int cz = z0; cz <= z1; cz += z1 - z0) for (int k = y + 1; k <= y + wallH; k++) s.put(cx, k, cz, p.log());
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        if (alongX) {
            for (int wx = x0 + 2; wx <= x1 - 2; wx += 3) {
                if (!(door == 0 && wx == mx)) s.put(wx, y + 2, z0, along(pane, true));
                if (!(door == 2 && wx == mx)) s.put(wx, y + 2, z1, along(pane, true));
            }
        } else {
            for (int wz = z0 + 2; wz <= z1 - 2; wz += 3) {
                if (!(door == 3 && wz == mz)) s.put(x0, y + 2, wz, along(pane, false));
                if (!(door == 1 && wz == mz)) s.put(x1, y + 2, wz, along(pane, false));
            }
        }
        int dx = door == 1 ? x1 : door == 3 ? x0 : mx, dz = door == 0 ? z0 : door == 2 ? z1 : mz;
        s.put(dx, y + 1, dz, AIR);
        s.put(dx, y + 2, dz, AIR);
        if (holes > 30) for (int k = y + 3; k <= y + wallH + 1; k++) s.put(dx, k, dz, AIR); // a ruin's doorway is open to the sky: no lintel left on nothing
        gable(s, x0, z0, x1, z1, y + wallH, p, alongX, 1, holes);
        int w = (alongX ? z1 - z0 : x1 - x0) + 3, ridge = y + wallH + (w - 1) / 2;
        if (holes < 30) s.put(mx, w % 2 == 1 ? ridge - 2 : ridge - 1, mz, lantern(true, false));
    }

    /** A pile of logs lying side by side: three, then two, then one. */
    public static void logPile(Scene s, int x, int z, int y, int len, boolean alongX, BlockState log) {
        BlockState l = axis(log, alongX ? Direction.Axis.X : Direction.Axis.Z);
        for (int row = 0; row < 3; row++)
            for (int w = 0; w < 3 - row; w++)
                for (int i = 0; i < len; i++) {
                    if (alongX) s.put(x + i, y + row, z + w, l);
                    else s.put(x + w, y + row, z + i, l);
                }
    }

    /** A table: a slab top on fence legs. */
    public static void table(Scene s, int x, int y, int z, int len, boolean alongX, Pal p) {
        for (int i = 0; i < len; i++) {
            int tx = alongX ? x + i : x, tz = alongX ? z : z + i;
            s.put(tx, y, tz, i == 0 || i == len - 1 ? p.fence() : AIR);
            s.put(tx, y + 1, tz, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
            if (i != 0 && i != len - 1) s.put(tx, y, tz, top(p.pslab()));
        }
    }

    /** A post with a lantern on it. */
    public static void lamp(Scene s, int x, int y, int z, int h, BlockState post, boolean soul) {
        for (int k = 0; k < h; k++) s.put(x, y + k, z, post);
        s.put(x, y + h, z, lantern(false, soul));
    }

    /** A stone person: {@code pose} 0..3, facing a way. */
    public static void stilled(Scene s, int x, int y, int z, int pose, Direction facing) {
        s.put(x, y, z, Props.STILLED.get().front(pose, facing));
    }
}
