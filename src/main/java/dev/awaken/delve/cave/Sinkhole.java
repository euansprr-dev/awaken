package dev.awaken.delve.cave;

import dev.awaken.delve.DelveContent;
import dev.awaken.delve.PitonBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A sinkhole (PLAN 8.2): a round shaft {@code 2R} across from the surface down to a cave system. Its routes:
 * <ul>
 *   <li><b>the shelves</b> (the E route, no rope): ledge rows every three blocks stacked in one sector of the wall, each
 *   row's end over the next row's start, alternating sides, so you walk a few blocks and drop three; a mossy step
 *   stone where each drop lands lets you climb back up (step on it, grab the lip two above);</li>
 *   <li><b>the dive</b>: opposite the shelves a spring spills over the rim into a pool five deep at the bottom, under the
 *   lip you jump from;</li>
 *   <li><b>the old rope</b>: a Delver's piton under the rim, a line hanging 32 down (the pipe grip climbs it);</li>
 *   <li>the rope (the Tether) off any wall, the D route.</li>
 * </ul>
 * At the bottom a passage leads to a grotto lit with lichen and glow berries, where a lode's heart glints in a niche in
 * the wall (the site was chosen beside a planned lode: W2's {@code LodePlans}), and another to the natural caves.
 *
 * <p>Spec: (x, z) the centre, y the floor (feet); p = [R, rim y, has lode, heart x, y, z, waterfall angle, shelf
 * angle, first].
 */
final class Sinkhole {
    private Sinkhole() {}

    static final int R = 0, RIM = 1, LODE = 2, HX = 3, HY = 4, HZ = 5, WATER = 6, SHELF = 7, FIRST = 8;
    /** Ledge rows every this many blocks (the city's climbing grid: a three-block drop is free). */
    static final int ROW = 3;
    static final int POOL_DEPTH = 5;
    private static final int BINS = 72;
    /** Half the sector (radians) round the spring kept sheer for the dive. */
    static final double DIVE = 0.45;

    static BoundingBox box(Spec s) {
        int r = s.p(R) + 10;
        BoundingBox b = new BoundingBox(s.x() - r, s.y() - POOL_DEPTH - 3, s.z() - r, s.x() + r, s.p(RIM) + 8, s.z() + r);
        if (s.p(LODE) != 0) b.encapsulate(new BoundingBox(s.p(HX) - 6, s.p(HY) - 3, s.p(HZ) - 6, s.p(HX) + 6, s.p(HY) + 6, s.p(HZ) + 6));
        return b;
    }

    /** The wall's radius at each height (rows) and angle (BINS columns), noise along the wall, a funnel at the top. */
    static float[][] walls(Spec s) {
        int y0 = s.y(), top = s.p(RIM) + 4, rad = s.p(R);
        float[][] w = new float[top - y0 + 1][BINS];
        double water = Math.toRadians(s.p(WATER));
        for (int y = y0; y <= top; y++) {
            for (int b = 0; b < BINS; b++) {
                double a = b * Mth.TWO_PI / BINS;
                double r;
                if (Math.abs(delta(a, water)) < DIVE) {
                    // the spring's side falls sheer to the pool: nothing to land on between the lip and the water
                    r = rad + 1.2;
                } else {
                    double n = Carve.noise(s.seed() + 1, s.x() + Math.cos(a) * rad * 1.3, y * 1.0, s.z() + Math.sin(a) * rad * 1.3, 8);
                    r = rad + (n - 0.5) * 2.4;
                    if (y > s.p(RIM) - 4) r += (y - (s.p(RIM) - 4)) * 0.75; // the lip opens out like a funnel
                }
                if (y < y0 + 5) r += (y0 + 5 - y) * 0.3; // the floor bellies out into the cave below
                w[y - y0][b] = (float) r;
            }
        }
        return w;
    }

    static float wall(float[][] w, Spec s, int y, double angle) {
        int row = Mth.clamp(y - s.y(), 0, w.length - 1);
        double t = (angle / Mth.TWO_PI) * BINS;
        int b0 = Math.floorMod((int) Math.floor(t), BINS), b1 = (b0 + 1) % BINS;
        double f = t - Math.floor(t);
        return (float) (w[row][b0] * (1 - f) + w[row][b1] * f);
    }

    static double angleOf(Spec s, double x, double z) {
        double a = Math.atan2(z - s.z(), x - s.x());
        return a < 0 ? a + Mth.TWO_PI : a;
    }

    /** The shortest signed angle from {@code b} to {@code a} (radians). */
    static double delta(double a, double b) {
        double d = (a - b) % Mth.TWO_PI;
        if (d > Math.PI) d -= Mth.TWO_PI;
        if (d < -Math.PI) d += Mth.TWO_PI;
        return d;
    }

    /** The rows' feet heights, lowest first: three apart from the floor up to just under the rim. */
    static int rows(Spec s) {
        return Math.max(0, (s.p(RIM) - 1 - s.y()) / ROW);
    }

    static int rowY(Spec s, int m) {
        return s.y() + ROW * m;
    }

    static void build(Spec s, Sink k) {
        float[][] w = walls(s);
        int cx = s.x(), cz = s.z(), yf = s.y(), rim = s.p(RIM), rad = s.p(R);
        double shelf = Math.toRadians(s.p(SHELF)), water = Math.toRadians(s.p(WATER));
        double half = 2.1 / Math.max(5, rad - 1);          // half the overlap where a row drops to the next (radians)
        double ext = 4.5 / Math.max(5, rad - 1);           // the shelf runs on past it this much
        int reach = rad + 7;
        int x0 = Math.max(cx - reach, k.minX()), x1 = Math.min(cx + reach, k.maxX());
        int z0 = Math.max(cz - reach, k.minZ()), z1 = Math.min(cz + reach, k.maxZ());
        int nRows = rows(s);

        // ---- the shaft, its shelves, the pool -----------------------------------------------------------------------
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = Math.sqrt(dx * dx + dz * dz);
                if (d > reach) continue;
                double a = angleOf(s, x + 0.5, z + 0.5);
                double fromShelf = delta(a, shelf);
                for (int y = yf; y <= rim + 4; y++) {
                    float r = wall(w, s, y, a);
                    if (d < r) Carve.air(k, x, y, z);
                }
                // the shelves: a row is one block thick where the next row runs under it, two where it doesn't
                for (int m = 1; m <= nRows; m++) {
                    int h = rowY(s, m);
                    float r = wall(w, s, h, a);
                    if (d < r - 2.25 || d > r + 0.6) continue;
                    boolean even = (m & 1) == 0;
                    double lo = even ? -half - ext : -half, hi = even ? half : half + ext;
                    if (fromShelf < lo || fromShelf > hi) continue;
                    Carve.put(k, x, h - 1, z, shelfBlock(s, x, h - 1, z));
                    boolean under = m > 1 && fromShelf > -half - 0.02 && fromShelf < half + 0.02; // the row below runs here
                    if (!under && d > r - 1.6) Carve.put(k, x, h - 2, z, Carve.rock(h - 2));
                    // the top: moss on the old shelves, a vine down its face now and then
                    if (Carve.rand(s.seed() + 5, x, h, z) < 0.35) Carve.put(k, x, h, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
                // the pool under the spring: five deep, its rim the shaft's floor
                double px = cx + Math.cos(water) * (wall(w, s, yf, water) - 4.5), pz = cz + Math.sin(water) * (wall(w, s, yf, water) - 4.5);
                double pd = Math.sqrt((x + 0.5 - px) * (x + 0.5 - px) + (z + 0.5 - pz) * (z + 0.5 - pz));
                if (pd < 5.2 && d < wall(w, s, yf, a) - 0.2) {
                    int depth = pd < 4 ? POOL_DEPTH : pd < 4.8 ? POOL_DEPTH - 2 : 1;
                    for (int y = yf - depth; y <= yf - 1; y++) Carve.put(k, x, y, z, Carve.WATER);
                    Carve.put(k, x, yf - depth - 1, z, pd < 3 ? Blocks.GRAVEL.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                }
            }
        }

        // ---- the step stones: where a row's drop lands, a stone to climb back up from -------------------------------
        for (int m = 1; m < nRows; m++) {
            int h = rowY(s, m);
            boolean upperEven = ((m + 1) & 1) == 0;
            // the row above ends on +half (even) or -half (odd); its drop lands just past that end, on this row
            double end = upperEven ? half : -half;
            double step = (upperEven ? 1 : -1) * 1.15 / Math.max(5, rad - 1.2);
            double a = shelf + end + step;
            float r = wall(w, s, h, a);
            int sx = Mth.floor(cx + Math.cos(a) * (r - 1.1)), sz = Mth.floor(cz + Math.sin(a) * (r - 1.1));
            Carve.put(k, sx, h, sz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            Carve.put(k, sx, h + 1, sz, Carve.AIR);
        }

        // ---- the way in at the rim: a cut path down onto the top row --------------------------------------------------
        if (nRows > 0) {
            int top = rowY(s, nRows);
            boolean even = (nRows & 1) == 0;
            double a = shelf + (even ? -half - ext * 0.5 : half + ext * 0.5);
            float r = wall(w, s, top, a);
            for (int i = 0; i <= 7; i++) {
                double rr = r - 0.5 + i;
                int fy = Math.min(rim, top + Math.max(0, i - 1));  // the floor climbs a block a block back to the rim
                for (int side = -1; side <= 1; side++) {
                    double aa = a + side * 0.9 / Math.max(5, rr);
                    int px = Mth.floor(cx + Math.cos(aa) * rr), pz = Mth.floor(cz + Math.sin(aa) * rr);
                    if (i > 0) Carve.put(k, px, fy - 1, pz, i < 2 ? shelfBlock(s, px, fy - 1, pz) : Blocks.COARSE_DIRT.defaultBlockState());
                    for (int y = fy; y <= rim + 4; y++) Carve.air(k, px, y, pz);
                }
                if (i == 7) k.mark("start", Mth.floor(cx + Math.cos(a) * rr), fy, Mth.floor(cz + Math.sin(a) * rr));
            }
        }

        // ---- the spring: a notch under the rim, its fall into the pool ------------------------------------------------
        {
            int sy = rim - 2;
            float r = wall(w, s, sy, water);
            int nx = Mth.floor(cx + Math.cos(water) * (r + 0.6)), nz = Mth.floor(cz + Math.sin(water) * (r + 0.6));
            int fx = Mth.floor(cx + Math.cos(water) * (r - 0.5)), fz = Mth.floor(cz + Math.sin(water) * (r - 0.5));
            if (fx == nx && fz == nz) fx += Mth.sign(cx - fx);
            Carve.put(k, nx, sy, nz, Carve.WATER);
            Carve.solid(k, nx, sy + 1, nz);
            k.flow(nx, sy, nz);
            for (int y = sy; y >= yf; y--) {
                Carve.air(k, fx, y, fz);
                Carve.put(k, fx, y, fz, Carve.FALLING);
            }
            k.flow(fx, yf, fz);
            // the lip you dive from: a smooth stone jutting over the drop beside the spring, its back on the rim
            double la = water + 0.16;
            int lx = Mth.floor(cx + Math.cos(la) * (rad + 0.6)), lz = Mth.floor(cz + Math.sin(la) * (rad + 0.6));
            int bx = Mth.floor(cx + Math.cos(la) * (rad + 1.7)), bz = Mth.floor(cz + Math.sin(la) * (rad + 1.7));
            Carve.put(k, lx, rim - 1, lz, Blocks.SMOOTH_STONE.defaultBlockState());
            Carve.put(k, bx, rim - 1, bz, Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = rim; y <= rim + 2; y++) {
                Carve.air(k, lx, y, lz);
                Carve.air(k, bx, y, bz);
            }
            k.mark("dive", lx, rim, lz);
            k.mark("pool", Mth.floor(cx + Math.cos(water) * (wall(w, s, yf, water) - 4.5)), yf - 2, Mth.floor(cz + Math.sin(water) * (wall(w, s, yf, water) - 4.5)));
        }

        // ---- the old rope: a Delver's piton under the rim, a quarter turn from the shelves ---------------------------
        {
            double a = shelf + Math.PI / 2;
            int py = rim - 2;
            float r = wall(w, s, py, a);
            int px = Mth.floor(cx + Math.cos(a) * (r - 0.5)), pz = Mth.floor(cz + Math.sin(a) * (r - 0.5));
            Direction out = inward(a);
            if (Carve.open(k, px, py, pz) || !k.inside(px, py, pz)) {
                Carve.solid(k, px - out.getStepX(), py, pz - out.getStepZ());
                Carve.put(k, px, py, pz, DelveContent.PITON.get().defaultBlockState().setValue(PitonBlock.FACING, out).setValue(PitonBlock.ROPED, true));
                for (int i = 1; i <= PitonBlock.ROPE; i++) {
                    int y = py - i;
                    if (y < yf || k.inside(px, y, pz) && !k.get(px, y, pz).isAir()) break;
                    Carve.put(k, px, y, pz, DelveContent.RUNNER_LINE.get().defaultBlockState());
                }
            }
        }

        // ---- the walls: vines where the light reaches, roots under the shelves, lichen low down -----------------------
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = Math.sqrt(dx * dx + dz * dz);
                if (d > reach || d < rad - 4) continue;
                double a = angleOf(s, x + 0.5, z + 0.5);
                if (Math.abs(delta(a, water)) < 0.35) continue; // keep the dive's line clear
                Direction toWall = inward(a).getOpposite();
                for (int y = yf; y <= rim; y++) {
                    if (!Carve.open(k, x, y, z) || !Carve.solidAt(k, x + toWall.getStepX(), y, z + toWall.getStepZ())) continue;
                    double depth = (rim - y) / (double) Math.max(1, rim - yf);
                    double vines = Carve.noise(s.seed() + 7, x, y * 0.25, z, 5);
                    if (depth < 0.55 && vines > 0.62) Carve.vine(k, x, y, z, toWall);
                    else if (depth > 0.7 && Carve.rand(s.seed() + 8, x, y, z) < 0.05) Carve.lichen(k, x, y, z);
                    if (Carve.rand(s.seed() + 9, x, y, z) < 0.25) Carve.roots(k, x, y, z);
                }
            }
        }

        // ---- the floor: moss and stones fallen from above ------------------------------------------------------------
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!k.inside(x, yf, z) || !k.get(x, yf, z).isAir() || !Carve.solidAt(k, x, yf - 1, z)) continue;
                double n = Carve.noise(s.seed() + 11, x, 0, z, 4);
                if (n > 0.55) Carve.put(k, x, yf - 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
                if (n > 0.72 && Carve.rand(s.seed() + 12, x, yf, z) < 0.5) Carve.put(k, x, yf, z, Blocks.MOSS_CARPET.defaultBlockState());
                else if (n < 0.2 && Carve.rand(s.seed() + 13, x, yf, z) < 0.12) Carve.put(k, x, yf - 1, z, Blocks.GRAVEL.defaultBlockState());
            }
        }

        // ---- the grotto at the bottom, toward the lode -----------------------------------------------------------------
        if (s.p(LODE) != 0) grotto(s, k, w);
        // ---- and the way on into the caves
        if (s.joined()) {
            double a = angleOf(s, s.jx() + 0.5, s.jz() + 0.5);
            float r = wall(w, s, yf, a);
            Carve.tunnel(k, cx + Math.cos(a) * (r - 1.5), yf, cz + Math.sin(a) * (r - 1.5), s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.6, 2.5, s.seed() + 21);
        }
    }

    /** The lit grotto: a room off the floor, a niche in its far wall where the lode's heart glints. */
    private static void grotto(Spec s, Sink k, float[][] w) {
        int hx = s.p(HX), hy = s.p(HY), hz = s.p(HZ), yf = s.y();
        double a = angleOf(s, hx + 0.5, hz + 0.5);
        double ux = Math.cos(a), uz = Math.sin(a);
        double gx = hx + 0.5 - ux * 5.5, gz = hz + 0.5 - uz * 5.5;
        int floor = yf;
        Carve.blob(k, gx, floor + 2.2, gz, 4.6, 3.0, 4.6, s.seed() + 31, 0.22, 3.5, floor);
        float r = wall(w, s, yf, a);
        Carve.tunnel(k, s.x() + 0.5 + ux * (r - 1.5), floor, s.z() + 0.5 + uz * (r - 1.5), gx, floor, gz, 1.6, 1.5, s.seed() + 32);
        // the niche: open in front of the heart, the heart itself left in the rock for the lode to fill
        for (double t = 1.2; t < 4.5; t += 0.5) {
            int nx = Mth.floor(hx + 0.5 - ux * t), nz = Mth.floor(hz + 0.5 - uz * t);
            Carve.air(k, nx, hy, nz);
            Carve.air(k, nx, hy + 1, nz);
        }
        Carve.solid(k, hx, hy, hz);
        Carve.solid(k, hx, hy - 1, hz);
        k.mark("goal", Mth.floor(gx), floor, Mth.floor(gz));
        k.mark("heart", hx, hy, hz);
        // the light: lichen on its walls, glow berries hanging from its roof, moss underfoot
        int gx0 = Mth.floor(gx) - 6, gz0 = Mth.floor(gz) - 6;
        if (!k.touches(gx0, gz0, gx0 + 12, gz0 + 12)) return;
        for (int x = gx0; x <= gx0 + 12; x++) {
            for (int z = gz0; z <= gz0 + 12; z++) {
                for (int y = floor; y <= floor + 6; y++) {
                    if (!Carve.open(k, x, y, z)) continue;
                    double rr = Carve.rand(s.seed() + 33, x, y, z);
                    if (rr < 0.16) Carve.lichen(k, x, y, z);
                    else if (rr < 0.19 && Carve.solidAt(k, x, y + 1, z)) vines(s, k, x, y, z);
                }
                if (Carve.open(k, x, floor, z) && Carve.solidAt(k, x, floor - 1, z) && Carve.noise(s.seed() + 34, x, 0, z, 3) > 0.5)
                    Carve.put(k, x, floor - 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
            }
        }
    }

    /** A strand of glow berries hanging down from the roof at (x, y, z), two to four long. */
    static void vines(Spec s, Sink k, int x, int y, int z) {
        int len = 2 + (int) (Carve.rand(s.seed() + 35, x, y, z) * 3);
        for (int i = 0; i < len; i++) {
            if (!Carve.open(k, x, y - i, z)) return;
            boolean tip = i == len - 1 || !Carve.open(k, x, y - i - 1, z);
            BlockState v = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, (i & 1) == 1 || tip);
            Carve.put(k, x, y - i, z, v);
            if (tip) return;
        }
    }

    /** The old stone of the shelves: the rock itself, mossy where the spring's mist reaches. */
    private static BlockState shelfBlock(Spec s, int x, int y, int z) {
        double n = Carve.rand(s.seed() + 4, x, y, z);
        if (n < 0.3) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (n < 0.45) return Blocks.COBBLESTONE.defaultBlockState();
        return y < 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** The horizontal direction pointing from the wall at {@code angle} into the shaft. */
    static Direction inward(double angle) {
        double x = -Math.cos(angle), z = -Math.sin(angle);
        return Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST) : (z > 0 ? Direction.SOUTH : Direction.NORTH);
    }
}
