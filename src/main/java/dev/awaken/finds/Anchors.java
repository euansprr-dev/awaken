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

    static int @Nullable [] find(Kind k, Terrain t, int x, int z, long h) {
        return switch (k.anchor()) {
            case FLAT -> flat(k, t, x, z, h);
            case HILLTOP -> hilltop(k, t, x, z, h, 0);
            case PEAK -> hilltop(k, t, x, z, h, 58);
            case LINE -> line(k, t, x, z, h);
            case FIELD -> field(k, t, x, z, h);
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
            int cx = x + (i < 9 ? OFF[i][0] * 22 : (int) Math.round(Math.cos((i - 9) * Math.PI / 8) * 46)), cz = z + (i < 9 ? OFF[i][1] * 22 : (int) Math.round(Math.sin((i - 9) * Math.PI / 8) * 46));
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
            if (sorted[16] - sorted[0] <= Math.max(3, k.slope())) return new int[] {cx, sorted[8], cz, (int) (h & 3)};
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
