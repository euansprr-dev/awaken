package dev.awaken.finds;

import org.jspecify.annotations.Nullable;

/**
 * Finding the ground a kind needs near a point (SPEC §58.4). Each answer is {x, y, z, quarter turn} or null when the
 * land round the point doesn't offer it; a cell with no fitting ground stays empty, which is part of why finds don't
 * come at an even rate. The kind faces south before turning: toward the water at a shore, the rock at a cliff, the
 * drop on a slope, along the run of a line.
 */
final class Anchors {
    private Anchors() {}

    /** South, west, north, east: where a kind's own south points after 0..3 quarter turns. */
    private static final int[] DX = {0, -1, 0, 1}, DZ = {1, 0, -1, 0};

    private static final int[][] OFF = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    /**
     * What a monument's spot is asked besides its ground (SPEC §62.3): 0 where it may stand, else why not, in the
     * caller's own numbers (a settlement near, another monument, the wrong land).
     */
    @FunctionalInterface
    interface Clear {
        int why(int x, int z);
    }

    /**
     * As {@link #find(Kind, Terrain, int, int, long)} for a monument, whose spots are also asked {@code clear}: the best
     * spot that is clear is taken, so a monument steps aside from a village or a spire the way it steps off rough
     * ground, and is lost only where its whole cell is taken. {@code why[0]} is left as the best spot's reason where
     * none was clear (untouched where the ground itself offered nothing).
     */
    static int @Nullable [] find(Kind k, Terrain t, int x, int z, long h, Clear clear, int[] why) {
        return switch (k.anchor()) {
            case TERRACE -> terrace(k, t, x, z, h, false, clear, why);
            case HIGH -> terrace(k, t, x, z, h, true, clear, why);
            default -> {
                int[] at = find(k, t, x, z, h);
                if (at != null) {
                    int w = clear.why(at[0], at[2]);
                    if (w != 0) {
                        why[0] = w;
                        at = null;
                    }
                }
                yield at;
            }
        };
    }

    static int @Nullable [] find(Kind k, Terrain t, int x, int z, long h) {
        return switch (k.anchor()) {
            case FLAT -> flat(k, t, x, z, h);
            case HILLTOP -> hilltop(k, t, x, z, h, 0);
            case PEAK -> hilltop(k, t, x, z, h, 58);
            case LINE -> line(k, t, x, z, h);
            case FIELD -> field(k, t, x, z, h);
            case TERRACE -> terrace(k, t, x, z, h, false, null, null);
            case HIGH -> terrace(k, t, x, z, h, true, null, null);
            case SHORE -> shore(k, t, x, z, h);
            case CLIFF -> cliff(k, t, x, z, h);
            case SLOPE -> slope(k, t, x, z, h);
        };
    }

    /** The spread of the ground over a ring of eight points, or -1 where any of it is under water. */
    private static int spread(Terrain t, int x, int z, int r, int centre) {
        int lo = centre, hi = centre, sea = t.seaLevel();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int f = t.floor(x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r));
            if (f < sea) return -1;
            lo = Math.min(lo, f);
            hi = Math.max(hi, f);
        }
        return hi - lo;
    }

    /**
     * Ground a place can level: seventeen points (the middle and two rings) all dry and within the kind's slope of each
     * other. The site's height is their median, so levelling cuts about as much as it fills. Twenty-five tries: the point, a ring round it and a wider ring.
     */
    private static int @Nullable [] field(Kind k, Terrain t, int x, int z, long h) {
        int r = Math.max(8, k.reach() * 4 / 5), sea = t.seaLevel();
        int[] f = new int[17];
        for (int i = 0; i < 25; i++) {               // the point itself, then a ring of eight, then a wider ring of sixteen
            int cx = x + (i < 9 ? OFF[i][0] * 22 : (int) Math.round(Math.cos((i - 9) * Math.PI / 8) * 34)), cz = z + (i < 9 ? OFF[i][1] * 22 : (int) Math.round(Math.sin((i - 9) * Math.PI / 8) * 34));
            f[0] = t.floor(cx, cz);
            boolean dry = f[0] > sea;
            for (int j = 0; j < 16 && dry; j++) {
                double a = j * Math.PI / 4 + (j >= 8 ? Math.PI / 8 : 0);
                int rr = j >= 8 ? r : r / 2;
                f[1 + j] = t.floor(cx + (int) Math.round(Math.cos(a) * rr), cz + (int) Math.round(Math.sin(a) * rr));
                dry = f[1 + j] > sea;
            }
            if (!dry) continue;
            int[] sorted = f.clone();
            java.util.Arrays.sort(sorted);
            // two more than the kind's own slope: the levelling and its blend take that up (two cells in three found no ground: the second census)
            if (sorted[16] - sorted[0] <= Math.max(3, k.slope()) + 2) return new int[] {cx, sorted[8], cz, (int) (h & 3)};
        }
        return null;
    }

    /** The forty-nine spots a terrace is tried at: a grid 24 apart round the cell's own point, nearest first. All inside the cell's edge. */
    private static final int[][] GRID = grid();

    private static int[][] grid() {
        java.util.List<int[]> g = new java.util.ArrayList<>();
        for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++) g.add(new int[] {i, j});
        g.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return g.toArray(int[][]::new);
    }

    /**
     * A monument's own ground (SPEC §62.3), in its own unturned offsets on a lattice eight apart: the columns its
     * terrace levels, the ring round them that the land is eased over, and what else it builds on the land's own ground
     * (piers, a fallen piece). And where its steps leave the terrace: {x, z, step x, step z}.
     */
    private record Foot(int[] terrace, int[] edge, int[] built, int[] way, boolean terraced) {}

    /**
     * What ground this very monument would shape: it is built once on level land with the site's own seed, and its
     * shaped columns read off. So a hall is tried as the long box it is, a stair as the small terrace at its foot and a
     * line of piers, a road as its piers alone. (Before this every monument was tried as a disc as wide as its reach, and
     * in a counted world the Sleepers' Hall, a hundred long and twenty-eight wide, stood nowhere.)
     */
    private static @Nullable Foot foot(Kind k, long seed) {
        Scene sc;
        try {
            sc = Sites.build(Sites.forced(k, 0, 64, 0, 0, seed), Terrain.flat(64));
        } catch (RuntimeException e) {
            return null;
        }
        int R = k.reach(), n = 2 * R + 1;
        boolean[] shaped = new boolean[n * n], built = new boolean[n * n];
        for (long key : sc.shaped) {
            int x = net.minecraft.core.BlockPos.getX(key), z = net.minecraft.core.BlockPos.getZ(key);
            if (Math.abs(x) <= R && Math.abs(z) <= R) shaped[(x + R) * n + z + R] = true;
        }
        for (var e : sc.blocks().long2ObjectEntrySet()) {
            var b = e.getValue();
            if (!b.isSolid() || b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(net.minecraft.tags.BlockTags.LOGS)) continue;   // what grows there is the land's
            // only what meets the ground: a deck high over the land is not standing on it, so a road may cross a stream
            int dy = net.minecraft.core.BlockPos.getY(e.getLongKey());
            if (dy < -3 || dy > 2) continue;
            int x = net.minecraft.core.BlockPos.getX(e.getLongKey()), z = net.minecraft.core.BlockPos.getZ(e.getLongKey());
            if (Math.abs(x) <= R && Math.abs(z) <= R) built[(x + R) * n + z + R] = true;
        }
        it.unimi.dsi.fastutil.ints.IntArrayList te = new it.unimi.dsi.fastutil.ints.IntArrayList(), ed = new it.unimi.dsi.fastutil.ints.IntArrayList(), bu = new it.unimi.dsi.fastutil.ints.IntArrayList();
        int span = (R + 16 + 7) & ~7, south = Integer.MIN_VALUE, southX = 0, southN = 0, pads = 0;
        for (int x = -span; x <= span; x += 8) for (int z = -span; z <= span; z += 8) if (near(shaped, n, R, x, z, 3)) pads++;
        // (eight columns of the lattice make a terrace; a camp's little pad does not. Without one, a road say, the ground
        // it stands on is under what it builds, and the ring round that is its edge)
        if (pads < 8) for (int i = 0; i < shaped.length; i++) shaped[i] |= built[i];
        for (int x = -span; x <= span; x += 8)
            for (int z = -span; z <= span; z += 8) {
                if (near(shaped, n, R, x, z, 3)) {
                    te.add(x);
                    te.add(z);
                    if (z > south) { south = z; southX = 0; southN = 0; }
                    if (z == south) { southX += x; southN++; }
                } else if (near(shaped, n, R, x, z, 16)) {
                    ed.add(x);
                    ed.add(z);
                } else if (near(built, n, R, x, z, 3)) {
                    bu.add(x);
                    bu.add(z);
                }
            }
        int[] way = !sc.ways.isEmpty() ? sc.ways.get(0) : new int[] {southN == 0 ? 0 : southX / southN, south == Integer.MIN_VALUE ? 0 : south, 0, 1};
        return new Foot(te.toIntArray(), ed.toIntArray(), bu.toIntArray(), way, pads >= 8);
    }

    /** Whether any marked column lies within {@code r} of (x, z). */
    private static boolean near(boolean[] mask, int n, int R, int x, int z, int r) {
        for (int i = Math.max(-R, x - r); i <= Math.min(R, x + r); i++)
            for (int j = Math.max(-R, z - r); j <= Math.min(R, z + r); j++)
                if (mask[(i + R) * n + j + R] && (i - x) * (i - x) + (j - z) * (j - z) <= r * r + r) return true;
        return false;
    }

    /** A kind's own offset turned 0..3 quarter turns, the way {@link Scene} turns it. */
    private static int tx(int lx, int lz, int rot) {
        return switch (rot & 3) { case 1 -> -lz; case 2 -> -lx; case 3 -> lz; default -> lx; };
    }

    private static int tz(int lx, int lz, int rot) {
        return switch (rot & 3) { case 1 -> lx; case 2 -> -lz; case 3 -> -lx; default -> lz; };
    }

    /**
     * Ground a monument can stand on (SPEC §62.3), by its own footprint. A cell offers forty-nine spots, 24 apart, and at
     * each the monument is tried in all four turns. A spot and a turn fit when:
     * <ul>
     * <li>every column the terrace levels is dry, and they lie within the kind's slope (and two) of each other. The
     * site's height is six tenths of the way up them, so the terrace is built up more than it is cut in;</li>
     * <li>the ring the land is eased over is dry, and nowhere more than 28 under the floor (40 for a mast or an orrery,
     * which belong on crowns) or 24 over it: no wall and no eased bank makes more than that look meant;</li>
     * <li>what stands on the land's own ground (piers, fallen pieces) is dry and neither buried (12 over) nor on
     * stilts (40 under). A monument with no terrace (a road) is held to its slope across what it builds instead, and to
     * the same ring round that, which is not eased and so may hold a river: a road may stand on a bank, never a crag.</li>
     * </ul>
     * Of all that fit, the best is taken, never the first: the gentlest ground (anything within six from low to high
     * counts as gentle, so a monument still finds a fall to look down), calm land round it, and a way in that is a walk:
     * the ground twelve past the head of its steps a little under the floor, not a climb and not a drop. For a mast or
     * an orrery the highest spot, a rough crown counting against it. Last, the one taken is looked at for water standing
     * over its floor (a lake held above the sea), and the next best tried if there is any.
     * <p>
     * {@code seed} is the site's own, so the footprint tried is the one that will be built. {@code t} should remember
     * its columns ({@link Terrain#memo}): spots and turns share a lattice, so a thousand columns answer for everything.
     */
    private static int @Nullable [] terrace(Kind k, Terrain t, int x, int z, long seed, boolean highest, @Nullable Clear clear, int @Nullable [] why) {
        Foot f = foot(k, seed);
        if (f == null) return null;
        int sea = t.seaLevel(), slope = Math.max(4, k.slope()) + 2, own = (int) ((seed >>> 7) & 3);
        int[] stand = f.terrace;
        if (stand.length < 2) return null;
        int[] hs = new int[stand.length / 2];
        java.util.ArrayList<double[]> fit = new java.util.ArrayList<>();
        int bx = x & ~7, bz = z & ~7;
        for (int[] o : GRID) {
            int cx = bx + o[0] * 24, cz = bz + o[1] * 24;
            turns:
            for (int rot = 0; rot < 4; rot++) {
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int i = 0; i < hs.length; i++) {
                    int v = t.floor(cx + tx(stand[2 * i], stand[2 * i + 1], rot), cz + tz(stand[2 * i], stand[2 * i + 1], rot));
                    if (v <= sea) continue turns;
                    hs[i] = v;
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                    if (hi - lo > slope) continue turns;
                }
                int[] sorted = hs.clone();
                java.util.Arrays.sort(sorted);
                int y = sorted[sorted.length * 6 / 10];
                if (y > sea + 110) continue;                       // (never so high that what stands on it nears the world's roof)
                int elo = lo, ehi = hi;
                for (int i = 0; i < f.edge.length; i += 2) {
                    int v = t.floor(cx + tx(f.edge[i], f.edge[i + 1], rot), cz + tz(f.edge[i], f.edge[i + 1], rot));
                    // (round a terrace the ring is eased, so it must be dry; round piers it is left alone, and a river may run there)
                    if (f.terraced ? v <= sea : false) continue turns;
                    if (y - v > (highest ? 40 : 28) || v - y > 24) continue turns;
                    elo = Math.min(elo, v);
                    ehi = Math.max(ehi, v);
                }
                for (int i = 0; i < f.built.length; i += 2) {
                    int v = t.floor(cx + tx(f.built[i], f.built[i + 1], rot), cz + tz(f.built[i], f.built[i + 1], rot));
                    if (v <= sea || y - v > 40 || v - y > 12) continue turns;
                }
                // the way in: the ground twelve past the head of the steps, on the lattice
                int wx = f.way[0] + f.way[2] * 12, wz = f.way[1] + f.way[3] * 12;
                int drop = y - t.floor((cx + tx(wx, wz, rot) + 4) & ~7, (cz + tz(wx, wz, rot) + 4) & ~7);
                double front = drop < -3 ? 12 : drop > 10 ? 8 + (drop - 10) / 4.0 : Math.abs(drop - 2) / 4.0;
                int spread = hi - lo;
                double score = (highest ? 2 * spread - y : Math.max(6, spread)) + (ehi - elo) / 2.0 + front + 0.01 * ((rot - own) & 3);
                fit.add(new double[] {score, cx, y, cz, rot});
            }
        }
        fit.sort(java.util.Comparator.comparingDouble(c -> c[0]));   // (a stable sort: among equals, the spot nearest the cell's own)
        boolean told = false;
        long refused = Long.MIN_VALUE;                              // the last spot that was not clear: its other turns need no asking
        candidates:
        for (double[] c : fit) {
            int cx = (int) c[1], cz = (int) c[3], rot = (int) c[4];
            if (clear != null) {
                long at = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
                if (at == refused) continue;
                int w = clear.why(cx, cz);
                if (w != 0) {
                    if (!told && why != null) why[0] = w;
                    told = true;
                    refused = at;
                    continue;
                }
            }
            for (int[] part : f.terraced ? new int[][] {f.terrace, f.edge, f.built} : new int[][] {f.terrace, f.built})
                for (int i = 0; i < part.length; i += 2) {
                    int px = cx + tx(part[i], part[i + 1], rot), pz = cz + tz(part[i], part[i + 1], rot);
                    if (t.surface(px, pz) > t.floor(px, pz)) continue candidates;   // water over the floor: a lake held above the sea
                }
            return new int[] {cx, (int) c[2], cz, rot};
        }
        return null;
    }

    private static int @Nullable [] flat(Kind k, Terrain t, int x, int z, long h) {
        int r = Math.max(3, k.reach() * 3 / 5), sea = t.seaLevel();
        for (int i = 0; i < 5; i++) {
            int cx = x + (i == 1 ? 11 : i == 2 ? -11 : 0), cz = z + (i == 3 ? 11 : i == 4 ? -11 : 0);
            int f = t.floor(cx, cz);
            if (f < sea + 1) continue;
            int s = spread(t, cx, cz, r, f);
            if (s >= 0 && s <= k.slope()) return new int[] {cx, f, cz, (int) (h & 3)};
        }
        return null;
    }

    private static int @Nullable [] hilltop(Kind k, Terrain t, int x, int z, long h, int above) {
        int bx = x, bz = z, best = Integer.MIN_VALUE;
        for (int i = -3; i <= 3; i++)
            for (int j = -3; j <= 3; j++) {
                int f = t.floor(x + i * 8, z + j * 8);
                if (f > best) {
                    best = f;
                    bx = x + i * 8;
                    bz = z + j * 8;
                }
            }
        // climb the last few blocks to the true top
        for (int step = 0; step < 6; step++) {
            int nx = bx, nz = bz;
            for (int d = 0; d < 4; d++) {
                int f = t.floor(bx + DX[d] * 2, bz + DZ[d] * 2);
                if (f > best) {
                    best = f;
                    nx = bx + DX[d] * 2;
                    nz = bz + DZ[d] * 2;
                }
            }
            if (nx == bx && nz == bz) break;
            bx = nx;
            bz = nz;
        }
        if (best < t.seaLevel() + 6 + above) return null;
        long sum = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            sum += t.floor(bx + (int) Math.round(Math.cos(a) * 26), bz + (int) Math.round(Math.sin(a) * 26));
        }
        if (best - sum / 8.0 < 5) return null; // it has to stand clear of the land round it
        int s = spread(t, bx, bz, 3, best);
        if (s < 0 || s > Math.max(3, k.slope())) return null;
        return new int[] {bx, best, bz, (int) (h & 3)};
    }

    private static int @Nullable [] line(Kind k, Terrain t, int x, int z, long h) {
        int half = Math.max(8, k.length() / 2), sea = t.seaLevel();
        for (int tries = 0; tries < 4; tries++) {
            int d = (int) ((h + tries) & 3);
            int prev = Integer.MIN_VALUE, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean ok = true;
            for (int s = -half; s <= half && ok; s += 4) {
                int f = t.floor(x + DX[d] * s, z + DZ[d] * s);
                if (f < sea + 1 || (prev != Integer.MIN_VALUE && Math.abs(f - prev) > 2)) ok = false;
                prev = f;
                lo = Math.min(lo, f);
                hi = Math.max(hi, f);
            }
            if (ok && hi - lo <= 8) return new int[] {x, t.floor(x, z), z, d};
        }
        return null;
    }

    private static int @Nullable [] shore(Kind k, Terrain t, int x, int z, long h) {
        int sea = t.seaLevel();
        for (int tries = 0; tries < 4; tries++) {
            int d = (int) ((h + tries) & 3);
            boolean prevDry = t.floor(x, z) >= sea;
            for (int s = 4; s <= 40; s += 4) {
                int px = x + DX[d] * s, pz = z + DZ[d] * s;
                boolean dry = t.floor(px, pz) >= sea;
                if (dry != prevDry) {
                    // the heart on the dry side, facing the water
                    int face = prevDry ? d : (d + 2) & 3;
                    int hx = prevDry ? px - DX[d] * 4 : px, hz = prevDry ? pz - DZ[d] * 4 : pz;
                    // walk to the very edge: the last dry column before the water
                    for (int e = 0; e < 5 && t.floor(hx + DX[face], hz + DZ[face]) >= sea; e++) {
                        hx += DX[face];
                        hz += DZ[face];
                    }
                    hx -= DX[face] * 2;
                    hz -= DZ[face] * 2;
                    int f = t.floor(hx, hz);
                    if (f < sea || f > sea + 4) break;
                    if (t.floor(hx + DX[face] * 6, hz + DZ[face] * 6) > sea - 2) break; // a puddle, not open water
                    return new int[] {hx, f, hz, face};
                }
                prevDry = dry;
            }
        }
        return null;
    }

    private static int @Nullable [] cliff(Kind k, Terrain t, int x, int z, long h) {
        int sea = t.seaLevel();
        for (int tries = 0; tries < 4; tries++) {
            int d = (int) ((h + tries) & 3);
            for (int s = -20; s <= 20; s += 4) {
                int px = x + DX[d] * s, pz = z + DZ[d] * s;
                int f = t.floor(px, pz);
                if (f < sea + 1) continue;
                if (t.floor(px + DX[d] * 6, pz + DZ[d] * 6) - f < 9) continue;
                // step up to the foot itself
                int e = 0;
                while (e < 5 && t.floor(px + DX[d], pz + DZ[d]) - f <= 1) {
                    px += DX[d];
                    pz += DZ[d];
                    f = t.floor(px, pz);
                    e++;
                }
                px -= DX[d] * 2;
                pz -= DZ[d] * 2;
                f = t.floor(px, pz);
                int s2 = spread(t, px - DX[d] * 3, pz - DZ[d] * 3, 3, f);
                if (s2 < 0 || s2 > 4) continue;
                return new int[] {px, f, pz, d};
            }
        }
        return null;
    }

    private static int @Nullable [] slope(Kind k, Terrain t, int x, int z, long h) {
        int sea = t.seaLevel();
        for (int i = 0; i < 5; i++) {
            int cx = x + (i == 1 ? 12 : i == 2 ? -12 : 0), cz = z + (i == 3 ? 12 : i == 4 ? -12 : 0);
            int f = t.floor(cx, cz);
            if (f < sea + 1) continue;
            int bestD = -1, bestDrop = 0;
            for (int d = 0; d < 4; d++) {
                int drop = f - t.floor(cx + DX[d] * 8, cz + DZ[d] * 8);
                if (drop > bestDrop) {
                    bestDrop = drop;
                    bestD = d;
                }
            }
            if (bestD < 0 || bestDrop < 3 || bestDrop > 11) continue;
            if (t.floor(cx + DX[bestD] * 8, cz + DZ[bestD] * 8) < sea) continue;
            return new int[] {cx, f, cz, bestD};
        }
        return null;
    }
}
