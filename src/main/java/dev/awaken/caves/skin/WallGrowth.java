package dev.awaken.caves.skin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Ochre Deep's walls, grown (SPEC §42.10; Euan's playtest 9 Nov: "very randomly placed on walls", "repetitive, not
 * connected"). The other rules dress a wall by the cell, in the two rows over a floor; this one starts a few living things at
 * places the rock suggests and lets them spread along it, so what is on a wall belongs to one run and has a reason to be there:
 * <ul>
 * <li>an <b>ember vine</b> climbs from the foot of a wall, along ledges and up the creases of corners, and wraps round
 * outer corners, with side shoots and blooms: a single line of connected tiles (the state says which way it leaves each block);
 * <li>a <b>rust drape</b> hangs from a corner of roof and wall, a real curtain of a few blocks;
 * <li><b>rust crust</b> stains the rock round the vines' roots and in damp, and a little lichen grows beside it, both as
 * patches that thin out to nothing.
 * </ul>
 * Everything is a hash of the seed and a place, as the rest of the skin is, and stays inside the chunk (a run ends in a tip at
 * the chunk's edge). The lists are short: only the Ochre Deep pays for this.
 */
public final class WallGrowth {
    private WallGrowth() {}

    /** Most runs a chunk takes, and the cells a run may have, side shoots and all. */
    static final int MAX_RUNS = 3, MAX_CELLS = 48;
    /** A run's length (steps of its main line, before side shoots). */
    static final int MIN_LEN = 7, MAX_LEN = 20;
    /** A wall's foot can start a run where a field at 14 blocks passes this: most walls, with a few long stretches left bare. */
    static final float GATE = -0.3F;
    /** A roof corner takes a drape where the damp field passes this. */
    static final float DRAPE_DAMP = -0.15F;
    static final int MAX_DRAPES = 6, MAX_PATCHES = 5;

    /** Where this chunk's walls begin (the feet of walls and the corners of roof and wall); a couple of short arrays a chunk. */
    public static final class Sites {
        static final int CAP = 512;
        final int[] feet = new int[CAP], roofs = new int[CAP];
        int footN, roofN;

        private static int code(int x, int y, int z, int wall) {
            return (x & 15) | (z & 15) << 4 | (y + 2048) << 8 | wall << 22;
        }

        public void foot(int x, int y, int z, int wall) {
            if (footN < CAP) feet[footN++] = code(x, y, z, wall);
        }

        public void roof(int x, int y, int z, int wall) {
            if (roofN < CAP) roofs[roofN++] = code(x, y, z, wall);
        }

        boolean any() {
            return footN > 0 || roofN > 0;
        }
    }

    public static int dress(CaveSkinFeature.Cells c, long seed, int cx, int cz, Sites sites) {
        int placed = 0;
        // vines: the feet that lie in a colony of the broad field, their pick by hash, spaced out a little
        int[] picked = new int[MAX_RUNS];
        int n = 0;
        for (int round = 0; round < MAX_RUNS; round++) {
            int bestSite = -1;
            float bestRoll = 1F;
            for (int i = 0; i < sites.footN; i++) {
                int code = sites.feet[i];
                int x = cx + (code & 15), z = cz + (code >> 4 & 15), y = (code >> 8 & 0x3FFF) - 2048;
                if (CaveSkinFeature.field3(seed, x, y, z, 14, 0xE3B1) < GATE) continue;
                float roll = CaveSkinFeature.chance(seed, x, y, z, 0x71) + round * 0.05F;
                if (roll >= bestRoll) continue;
                boolean near = false;
                for (int k = 0; k < n; k++) {
                    int other = picked[k];
                    int dx = (other & 15) - (code & 15), dz = (other >> 4 & 15) - (code >> 4 & 15);
                    if (dx * dx + dz * dz < 25) near = true;
                }
                if (near) continue;
                bestRoll = roll;
                bestSite = code;
            }
            if (bestSite < 0) break;
            picked[n++] = bestSite;
        }
        for (int i = 0; i < n; i++) {
            int code = picked[i];
            int x = cx + (code & 15), z = cz + (code >> 4 & 15), y = (code >> 8 & 0x3FFF) - 2048, wall = code >> 22 & 3;
            int len = MIN_LEN + (int) (CaveSkinFeature.chance(seed, x, y, z, 0x72) * (MAX_LEN - MIN_LEN + 1));
            placed += run(c, seed, cx, cz, x, y, z, wall, len);
            // the rock stained round the vine's root
            placed += patch(c, seed, cx, cz, x, y + 2, z, wall, 3 + (int) (CaveSkinFeature.chance(seed, x, y, z, 0x73) * 2), CaveSkin.RUST_CRUST.get());
        }
        // drapes from the corners of roof and wall where it's damp
        int drapes = 0;
        for (int i = 0; i < sites.roofN && drapes < MAX_DRAPES; i++) {
            int code = sites.roofs[i];
            int x = cx + (code & 15), z = cz + (code >> 4 & 15), y = (code >> 8 & 0x3FFF) - 2048;
            if (CaveSkinFeature.field(seed, x, z, 12, 0x5EED) < DRAPE_DAMP) continue;
            if (CaveSkinFeature.chance(seed, x, y, z, 0x74) > 0.12F) continue;
            int done = drape(c, seed, cx, cz, x, y, z, code >> 22 & 3);
            if (done > 0) {
                placed += done;
                drapes++;
            }
        }
        // crust and lichen in the damp at a few more feet of walls, a patch each, at any height a wall has
        int patches = 0;
        for (int i = 0; i < sites.footN && patches < MAX_PATCHES; i++) {
            int code = sites.feet[i];
            int x = cx + (code & 15), z = cz + (code >> 4 & 15), y = (code >> 8 & 0x3FFF) - 2048;
            float roll = CaveSkinFeature.chance(seed, x, y, z, 0x75);
            if (roll > 0.07F) continue;
            int up = 1 + (int) (CaveSkinFeature.chance(seed, x, y, z, 0x76) * 5);
            MultifaceBlock kind = roll < 0.03F ? CaveSkinFeature.lichen(y, y + 60) : CaveSkin.RUST_CRUST.get();
            placed += patch(c, seed, cx, cz, x, y + up, z, code >> 22 & 3, 2 + (int) (CaveSkinFeature.chance(seed, x, y, z, 0x77) * 3), kind);
            patches++;
        }
        return placed;
    }

    private static final int U = 0, D = 1, L = 2, R = 3;
    private static final int[] BIT = {1, 2, 4, 8}, BACK = {D, U, R, L};

    /** One run from a wall's foot: the main line, then its side shoots; laid only if it came to something. */
    private static int run(CaveSkinFeature.Cells c, long seed, int cx, int cz, int sx, int sy, int sz, int wall, int length) {
        int[] xs = new int[MAX_CELLS], ys = new int[MAX_CELLS], zs = new int[MAX_CELLS], walls = new int[MAX_CELLS], masks = new int[MAX_CELLS];
        // the first free cell up the wall (rubble may lie at its foot)
        int y0 = sy;
        while (y0 < sy + 3 && !free(c, cx, cz, sx, y0, sz, wall)) y0++;
        if (y0 == sy + 3) return 0;
        xs[0] = sx;
        ys[0] = y0;
        zs[0] = sz;
        walls[0] = wall;
        masks[0] = CaveSkinFeature.rock(c.get(sx, y0 - 1, sz)) != null ? BIT[D] : 0;         // rooted in the floor it stands on
        int n = 1;
        // the walkers: which cell they stand on, the way they're heading, the steps they have left
        int[] wCell = new int[6], wHead = new int[6], wLeft = new int[6];
        int walkers = 1, shoots = 0;
        wCell[0] = 0;
        wHead[0] = U;
        wLeft[0] = length;
        for (int k = 0; k < walkers; k++) {
            int cell = wCell[k], heading = wHead[k], left = wLeft[k];
            int step = 0;
            while (left-- > 0 && n < MAX_CELLS) {
                int x = xs[cell], y = ys[cell], z = zs[cell], w = walls[cell];
                float best = 0;
                int bestH = -1, bx = 0, by = 0, bz = 0, bw = 0;
                for (int h = 0; h < 4; h++) {
                    if (h == BACK[heading]) continue;                            // never back on itself
                    // what would that step be
                    Direction wd = CaveSkinFeature.AROUND[w], dir = world(h, wd);
                    int nx = x + dir.getStepX(), ny = y + dir.getStepY(), nz = z + dir.getStepZ(), nw = w;
                    if (!inChunk(nx, nz, cx, cz) || !c.get(nx, ny, nz).isAir() || has(xs, ys, zs, n, nx, ny, nz)) continue;
                    if (!ochre(c.get(nx + wd.getStepX(), ny, nz + wd.getStepZ()))) {
                        // no wall behind it: round an outer corner if we were going along the wall, else not this way
                        if (h != L && h != R) continue;
                        nx += wd.getStepX();
                        nz += wd.getStepZ();
                        Direction back = dir.getOpposite();
                        if (!inChunk(nx, nz, cx, cz) || !c.get(nx, ny, nz).isAir() || has(xs, ys, zs, n, nx, ny, nz)) continue;
                        nw = ordinal(back);
                        if (!ochre(c.get(nx + back.getStepX(), ny, nz + back.getStepZ()))) continue;
                    }
                    if (!CaveSkinFeature.clean(c, nx, ny, nz)) continue;
                    float base = h == heading ? 1F : h == U ? 0.8F : h == D ? 0.15F : 0.5F;
                    float crease;
                    if (h == L || h == R) {
                        // hug a ledge: rock over it or under it
                        crease = (rock(c, nx, ny + 1, nz) ? 0.9F : 0F) + (rock(c, nx, ny - 1, nz) ? 0.4F : 0F);
                    } else {
                        // climb a crease: rock to one side on the wall
                        Direction right = CaveSkinFeature.AROUND[nw].getClockWise();
                        crease = rock(c, nx + right.getStepX(), ny, nz + right.getStepZ()) || rock(c, nx - right.getStepX(), ny, nz - right.getStepZ()) ? 0.9F : 0F;
                    }
                    float score = base * (1 + crease) * (0.45F + 0.55F * CaveSkinFeature.chance(seed, x, y, z, 0x80 + h + step * 7 + k * 31));
                    if (score > best) {
                        best = score;
                        bestH = h;
                        bx = nx;
                        by = ny;
                        bz = nz;
                        bw = nw;
                    }
                }
                if (bestH < 0) break;
                masks[cell] |= BIT[bestH];
                xs[n] = bx;
                ys[n] = by;
                zs[n] = bz;
                walls[n] = bw;
                masks[n] = BIT[BACK[bestH]];
                // now and then a side shoot, off to either side of the way we're going
                if (k == 0 && step >= 2 && shoots < 3 && left > 2 && CaveSkinFeature.chance(seed, bx, by, bz, 0x90) < 0.24F && walkers < 6) {
                    wCell[walkers] = n;
                    wHead[walkers] = bestH == L || bestH == R ? (CaveSkinFeature.chance(seed, bx, by, bz, 0x91) < 0.8F ? U : D)
                            : (CaveSkinFeature.chance(seed, bx, by, bz, 0x91) < 0.5F ? L : R);
                    wLeft[walkers] = 3 + (int) (CaveSkinFeature.chance(seed, bx, by, bz, 0x92) * 6);
                    walkers++;
                    shoots++;
                }
                n++;
                cell = n - 1;
                heading = bestH;
                step++;
            }
        }
        if (n < 3) return 0;
        // blooms: at every fork, and now and then along a line, never two together
        boolean[] blooms = new boolean[n];
        for (int i = 0; i < n; i++) {
            int bits = Integer.bitCount(masks[i]);
            if (bits >= 3 || bits == 1 && CaveSkinFeature.chance(seed, xs[i], ys[i], zs[i], 0x93) < 0.4F) blooms[i] = true;
            else if (i % (3 + (int) (CaveSkinFeature.chance(seed, sx, sy, sz, 0x94) * 3)) == 2) blooms[i] = true;
        }
        for (int i = 1; i < n; i++) if (blooms[i] && blooms[i - 1]) blooms[i] = false;
        var vine = CaveSkin.EMBER_VINE.get();
        for (int i = 0; i < n; i++) {
            BlockState s = vine.defaultBlockState().setValue(SkinBlocks.EmberVine.FACING, CaveSkinFeature.AROUND[walls[i]])
                    .setValue(SkinBlocks.EmberVine.UP, (masks[i] & BIT[U]) != 0).setValue(SkinBlocks.EmberVine.DOWN, (masks[i] & BIT[D]) != 0)
                    .setValue(SkinBlocks.EmberVine.LEFT, (masks[i] & BIT[L]) != 0).setValue(SkinBlocks.EmberVine.RIGHT, (masks[i] & BIT[R]) != 0)
                    .setValue(SkinBlocks.EmberVine.BLOOM, blooms[i]);
            c.set(xs[i], ys[i], zs[i], s);
        }
        return n;
    }

    /** A drape of two to five blocks down the wall from the roof corner at (x, y, z). */
    private static int drape(CaveSkinFeature.Cells c, long seed, int cx, int cz, int x, int y, int z, int wall) {
        Direction wd = CaveSkinFeature.AROUND[wall];
        int len = 2 + (int) (CaveSkinFeature.chance(seed, x, y, z, 0x95) * 4), n = 0;
        while (n < len && free(c, cx, cz, x, y - n, z, wall) && CaveSkinFeature.clean(c, x, y - n, z)) n++;
        // keep it off the floor: a curtain that stops short
        while (n > 0 && CaveSkinFeature.rock(c.get(x, y - n, z)) != null) n--;
        if (n < 2) return 0;
        var drape = CaveSkin.RUST_DRAPE.get();
        for (int k = 0; k < n; k++) {
            c.set(x, y - k, z, drape.defaultBlockState().setValue(SkinBlocks.RustDrape.FACING, wd).setValue(SkinBlocks.RustDrape.TIP, k == n - 1));
        }
        return n;
    }

    /** A patch of a crust or lichen on one wall, round a cell, the cells thinner the further out so it fades into the rock. */
    private static int patch(CaveSkinFeature.Cells c, long seed, int cx, int cz, int sx, int sy, int sz, int wall, int radius, MultifaceBlock kind) {
        Direction wd = CaveSkinFeature.AROUND[wall], right = wd.getClockWise();
        BlockState s = kind.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(wd), true);
        int placed = 0;
        for (int v = -radius; v <= radius; v++) {
            for (int u = -radius; u <= radius; u++) {
                float d = (u * u + v * v) / (float) (radius * radius);
                int x = sx + right.getStepX() * u, y = sy + v, z = sz + right.getStepZ() * u;
                if (d > 1 || CaveSkinFeature.chance(seed, x, y, z, 0x96) < d * d + 0.08F) continue;
                if (!free(c, cx, cz, x, y, z, wall) || !CaveSkinFeature.clean(c, x, y, z)) continue;
                c.set(x, y, z, s);
                placed++;
            }
        }
        return placed;
    }

    private static boolean inChunk(int x, int z, int cx, int cz) {
        return (x & ~15) == cx && (z & ~15) == cz;
    }

    /** Air in the chunk, with this region's rock behind it on the wall. */
    private static boolean free(CaveSkinFeature.Cells c, int cx, int cz, int x, int y, int z, int wall) {
        Direction wd = CaveSkinFeature.AROUND[wall];
        return inChunk(x, z, cx, cz) && c.get(x, y, z).isAir() && ochre(c.get(x + wd.getStepX(), y, z + wd.getStepZ()));
    }

    private static boolean ochre(BlockState s) {
        return s.is(CaveSkin.OCHRE_STONE.get());
    }

    private static boolean rock(CaveSkinFeature.Cells c, int x, int y, int z) {
        return CaveSkinFeature.rock(c.get(x, y, z)) != null;
    }

    private static boolean has(int[] xs, int[] ys, int[] zs, int n, int x, int y, int z) {
        for (int i = 0; i < n; i++) if (xs[i] == x && ys[i] == y && zs[i] == z) return true;
        return false;
    }

    private static Direction world(int h, Direction wall) {
        return switch (h) {
            case U -> Direction.UP;
            case D -> Direction.DOWN;
            case L -> wall.getCounterClockWise();
            default -> wall.getClockWise();
        };
    }

    private static int ordinal(Direction d) {
        return switch (d) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            default -> 3;
        };
    }
}
