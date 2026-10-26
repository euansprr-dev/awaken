package dev.awaken.delve.cave;

import dev.awaken.delve.DelveContent;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Whether a body with only the Runner's Kit can get from one point of a place to another (PLAN 8.1: every place has a
 * route that never needs the rope; G46). A search over the feet's cells of a {@link Grid} with the moves the body really
 * has and their numbers (SPEC §17, RunnerRules, BodyRules): walk and step (0.6), jump (1.25 up, three clear over you),
 * grab a lip and climb (2.4), drop (3, a fall that does no harm), lower into a hang and let go (4.5), a running jump over
 * a gap of one or two, ladders, vines and ropes in the place (the pipe grip and the free-run climb them), swim (falling
 * into water from any height is safe), and the slide's crawl under a one-high gap. Never the Tether.
 */
final class Routes {
    private Routes() {}

    static final double STEP = 0.6, JUMP = 1.25, HANG = 2.4, DROP = 3.0, HANG_DROP = 4.5, HEAD = 1.8, CRAWL = 0.6, JUMP_CLEAR = 3.05;

    record Result(boolean found, int explored, String why) {}

    private static final TagKey<Block> RUNNER_CLIMBS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("runner_climbs"));

    private static final class Shape {
        final boolean empty;
        final double bottom, top;

        Shape(boolean empty, double bottom, double top) {
            this.empty = empty;
            this.bottom = bottom;
            this.top = top;
        }
    }

    private static final Map<BlockState, Shape> SHAPES = new IdentityHashMap<>();

    private static synchronized Shape shape(BlockState s) {
        return SHAPES.computeIfAbsent(s, st -> {
            if (climbable(st) || !st.getFluidState().isEmpty() && st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty())
                return new Shape(true, 0, 0);
            VoxelShape v = st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (v.isEmpty()) return new Shape(true, 0, 0);
            return new Shape(false, v.min(Direction.Axis.Y), v.max(Direction.Axis.Y));
        });
    }

    static boolean climbable(BlockState s) {
        return s.is(BlockTags.CLIMBABLE) || s.is(RUNNER_CLIMBS) || s.is(DelveContent.RUNNER_LINE.get());
    }

    static boolean water(BlockState s) {
        return s.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    /** The search itself: {@code from} and {@code to} are feet cells; arriving within a block of {@code to} counts. */
    static Result find(Grid g, int[] from, int[] to) {
        if (from == null || to == null) return new Result(false, 0, "no start or goal marked");
        int n = g.sx * g.sy * g.sz;
        boolean[] seen = new boolean[n];
        IntArrayFIFOQueue q = new IntArrayFIFOQueue();
        int start = index(g, from[0], from[1], from[2]);
        if (start < 0) return new Result(false, 0, "start outside the grid");
        seen[start] = true;
        q.enqueue(start);
        int explored = 0;
        double bestD = Double.MAX_VALUE;
        int best = start;
        int[] c = new int[3];
        while (!q.isEmpty()) {
            int i = q.dequeueInt();
            explored++;
            cell(g, i, c);
            double dd = Math.abs(c[0] - to[0]) + Math.abs(c[1] - to[1]) * 1.5 + Math.abs(c[2] - to[2]);
            if (dd < bestD) {
                bestD = dd;
                best = i;
            }
            if (Math.abs(c[0] - to[0]) <= 1 && Math.abs(c[2] - to[2]) <= 1 && Math.abs(c[1] - to[1]) <= 1) return new Result(true, explored, "");
            expand(g, c[0], c[1], c[2], seen, q);
        }
        cell(g, best, c);
        return new Result(false, explored, "got no closer than " + c[0] + " " + c[1] + " " + c[2] + " (goal " + to[0] + " " + to[1] + " " + to[2] + ")");
    }

    private static int index(Grid g, int x, int y, int z) {
        int lx = x - g.x0, ly = y - g.y0, lz = z - g.z0;
        if (lx < 0 || ly < 0 || lz < 0 || lx >= g.sx || ly >= g.sy || lz >= g.sz) return -1;
        return (ly * g.sz + lz) * g.sx + lx;
    }

    private static void cell(Grid g, int i, int[] out) {
        out[0] = g.x0 + i % g.sx;
        out[2] = g.z0 + (i / g.sx) % g.sz;
        out[1] = g.y0 + i / (g.sx * g.sz);
    }

    private static void visit(Grid g, int x, int y, int z, boolean[] seen, IntArrayFIFOQueue q) {
        int i = index(g, x, y, z);
        if (i < 0 || seen[i]) return;
        seen[i] = true;
        q.enqueue(i);
    }

    // ---- the cell's facts ------------------------------------------------------------------------------------------

    /** The feet's height if standing with the feet in cell y (on its partial floor or the block under it), else NaN. */
    static double feet(Grid g, int x, int y, int z) {
        BlockState here = g.get(x, y, z);
        Shape h = shape(here);
        if (!h.empty) {
            if (h.bottom <= 0.01 && h.top < 0.99) return y + h.top;
            return Double.NaN;
        }
        Shape b = shape(g.get(x, y - 1, z));
        if (!b.empty && b.top > 0.99) return y - 1 + b.top;
        return Double.NaN;
    }

    /** How high the column is clear from height {@code f} (blocks; at most {@code want}). */
    static double clear(Grid g, int x, int z, double f, double want) {
        int y0 = (int) Math.floor(f + 1e-6);
        double top = f + want;
        for (int y = y0; y <= (int) Math.floor(top - 1e-6); y++) {
            Shape s = shape(g.get(x, y, z));
            if (s.empty) continue;
            double lo = y + s.bottom, hi = y + s.top;
            if (hi <= f + 0.01) continue; // the floor itself
            return Math.max(0, lo - f);
        }
        return want;
    }

    private static boolean stairsUnder(Grid g, int x, int y, int z) {
        return g.get(x, y, z).getBlock() instanceof StairBlock || g.get(x, y - 1, z).getBlock() instanceof StairBlock;
    }

    // ---- the moves -------------------------------------------------------------------------------------------------

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static void expand(Grid g, int x, int y, int z, boolean[] seen, IntArrayFIFOQueue q) {
        BlockState here = g.get(x, y, z);
        double f = feet(g, x, y, z);
        boolean standing = !Double.isNaN(f) && clear(g, x, z, f, CRAWL) >= CRAWL;
        boolean climb = climbable(here);
        boolean swim = water(here);
        if (!standing && !climb && !swim) {
            // in the air with nothing to hold: fall to whatever is under (the move that brought us here judged the fall)
            int land = landing(g, x, y, z, 128);
            if (land != Integer.MIN_VALUE) visit(g, x, land, z, seen, q);
            return;
        }
        if (standing) {
            double room = clear(g, x, z, f, JUMP_CLEAR + 1);
            double headA = clear(g, x, z, f, HEAD);
            for (int[] d : DIRS) {
                int nx = x + d[0], nz = z + d[1];
                // level with the feet and above: a step, a jump, a lip to climb, a ladder, water to wade into
                for (int ny = y + 3; ny >= y; ny--) {
                    BlockState b = g.get(nx, ny, nz);
                    if (water(b) && ny <= f + 0.6) visit(g, nx, ny, nz, seen, q);
                    if (climbable(b) && ny <= f + 1.01) visit(g, nx, ny, nz, seen, q);
                    double fb = feet(g, nx, ny, nz);
                    if (!Double.isNaN(fb)) tryMove(g, x, z, f, headA, room, nx, ny, nz, fb, seen, q);
                }
                // below: down the column through the air to the first floor, water or ladder
                for (int ny = y - 1; ny >= y - 132; ny--) {
                    BlockState b = g.get(nx, ny, nz);
                    double fb = feet(g, nx, ny, nz);
                    if (water(b) || climbable(b)) {
                        if (clear(g, nx, nz, ny, f + CRAWL - ny) >= f + CRAWL - ny - 1e-3) visit(g, nx, ny, nz, seen, q);
                        break;
                    }
                    if (!Double.isNaN(fb)) {
                        tryMove(g, x, z, f, headA, room, nx, ny, nz, fb, seen, q);
                        break;
                    }
                    if (!shape(b).empty) break;
                }
                // a running jump over a gap of one or two
                for (int span = 2; span <= 3; span++) {
                    boolean gapClear = true;
                    for (int s = 1; s < span; s++) {
                        int gx = x + d[0] * s, gz = z + d[1] * s;
                        if (clear(g, gx, gz, f, 2.9) < 2.9 - 1e-3) {
                            gapClear = false;
                            break;
                        }
                    }
                    if (!gapClear || room < 2.9) break;
                    int tx = x + d[0] * span, tz = z + d[1] * span;
                    for (int ny = y + 1; ny >= y - 3; ny--) {
                        double fc = feet(g, tx, ny, tz);
                        if (Double.isNaN(fc)) continue;
                        double dh = fc - f;
                        if (dh <= STEP && dh >= -DROP + (span == 3 ? 1 : 0) && clear(g, tx, tz, fc, HEAD) >= HEAD - 1e-3) visit(g, tx, ny, tz, seen, q);
                        break;
                    }
                }
            }
        }
        if (climb) {
            if (climbable(g.get(x, y + 1, z)) || shape(g.get(x, y + 1, z)).empty) visit(g, x, y + 1, z, seen, q);
            if (climbable(g.get(x, y - 1, z))) visit(g, x, y - 1, z, seen, q);
            else {
                int land = landing(g, x, y, z, (int) DROP + 1);
                if (land != Integer.MIN_VALUE) visit(g, x, land, z, seen, q);
            }
            for (int[] d : DIRS) {
                int nx = x + d[0], nz = z + d[1];
                for (int ny = y + 1; ny >= y - 3; ny--) {
                    BlockState b = g.get(nx, ny, nz);
                    if (climbable(b) || water(b)) {
                        visit(g, nx, ny, nz, seen, q);
                        continue;
                    }
                    double fb = feet(g, nx, ny, nz);
                    if (!Double.isNaN(fb) && fb - y <= JUMP && y - fb <= DROP && clear(g, nx, nz, fb, HEAD) >= HEAD - 1e-3) visit(g, nx, ny, nz, seen, q);
                }
            }
        }
        if (swim) {
            boolean source = here.getFluidState().isSource();
            BlockState up = g.get(x, y + 1, z);
            if (source && (water(up) && up.getFluidState().isSource() || shape(up).empty)) visit(g, x, y + 1, z, seen, q);
            if (water(g.get(x, y - 1, z))) visit(g, x, y - 1, z, seen, q);
            for (int[] d : DIRS) {
                int nx = x + d[0], nz = z + d[1];
                for (int ny = y + 1; ny >= y - 1; ny--) {
                    BlockState b = g.get(nx, ny, nz);
                    if (water(b) || climbable(b)) {
                        visit(g, nx, ny, nz, seen, q);
                        continue;
                    }
                    double fb = feet(g, nx, ny, nz);
                    if (!Double.isNaN(fb) && fb - y <= JUMP && clear(g, nx, nz, fb, HEAD) >= HEAD - 1e-3) visit(g, nx, ny, nz, seen, q);
                }
                // out over a low edge from the surface
                double fb = feet(g, nx, y + 2, nz);
                if (source && !Double.isNaN(fb) && fb - y <= JUMP + 0.5 && clear(g, nx, nz, fb, HEAD) >= HEAD - 1e-3) visit(g, nx, y + 2, nz, seen, q);
            }
        }
    }


    /** One move between standing cells: walk, step, drop, jump, climb a lip, or lower and let go, by their numbers. */
    private static void tryMove(Grid g, int x, int z, double f, double headA, double room, int nx, int ny, int nz, double fb,
                                boolean[] seen, IntArrayFIFOQueue q) {
        double headB = clear(g, nx, nz, fb, HEAD);
        if (headB < CRAWL) return;
        double dh = fb - f;
        boolean crawl = headA < HEAD - 1e-3 || headB < HEAD - 1e-3;
        double need = crawl ? CRAWL : HEAD;
        double stepMax = stairsUnder(g, nx, ny, nz) ? 1.01 : STEP;
        boolean ok;
        if (crawl) {
            // the slide's crawl: on along a one-high gap, never up or down much
            ok = Math.abs(dh) <= STEP && clear(g, x, z, f, Math.max(0, dh) + need) >= Math.max(0, dh) + need - 1e-3;
        } else if (dh <= stepMax && dh >= -DROP) {
            // walk, step, or drop: the body passes through the higher of the two heights
            double hi = Math.max(f, fb);
            ok = clear(g, x, z, f, hi - f + need) >= hi - f + need - 1e-3 && clear(g, nx, nz, fb, hi - fb + need) >= hi - fb + need - 1e-3;
        } else if (dh > stepMax && dh <= JUMP) {
            ok = room >= JUMP_CLEAR - 0.05 && headB >= HEAD - 1e-3;
        } else if (dh > JUMP && dh <= HANG) {
            // grab the lip and climb: the hanging body needs the column clear to just over the lip
            ok = clear(g, x, z, f, dh + 0.3) >= dh + 0.3 - 1e-3 && headB >= HEAD - 1e-3;
        } else if (dh < -DROP && dh >= -HANG_DROP) {
            // lower into a hang at the edge and let go (RunnerRules: a drop of 4 or more; feet 1.55 under the lip)
            ok = dh <= -4.0 + 1e-3 && clear(g, nx, nz, fb, f - fb + HEAD) >= f - fb + HEAD - 1e-3;
        } else {
            ok = false;
        }
        if (ok) visit(g, nx, ny, nz, seen, q);
    }

    /**
     * Where a body falling from feet cell y in this column comes to rest: the cell of the first floor (a fall of at most
     * {@code maxFall}) or of the first water (any height); MIN_VALUE when it would land harder than that or never.
     */
    private static int landing(Grid g, int x, int y, int z, int maxFall) {
        for (int ny = y - 1; ny >= y - 132; ny--) {
            BlockState b = g.get(x, ny, z);
            if (water(b) || climbable(b)) return ny;
            double fb = feet(g, x, ny, z);
            if (!Double.isNaN(fb)) return y - fb <= maxFall ? ny : Integer.MIN_VALUE;
            if (!shape(b).empty) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** For a failing test: the cells the search reached, as a count. */
    static String describe(Result r) {
        return r.found() ? "found (" + r.explored() + " cells)" : r.why() + " after " + r.explored() + " cells";
    }

    @SuppressWarnings("unused")
    private static void unused() {
        Arrays.fill(new int[0], 0);
    }
}
