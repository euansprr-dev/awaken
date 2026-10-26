package dev.awaken.city;

import dev.awaken.city.CityPainter.Ctx;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityCommons.*;

/**
 * The fields round Carillon (M4, the Commons step; SPEC §16.3). Irregular fields on a jittered polar lattice between the
 * apron and 318 (each column belongs to its nearest seed); along the boundaries a hedge, a farm track or a grass balk.
 * Each field is one of:
 * <ul>
 * <li>arable: strips five wide, one crop each at its own growth (wheat green to gold, carrots, potatoes, beetroot, a
 * pumpkin patch, a ploughed or fallow strip), between grass balks and irrigation channels, a scarecrow in some;</li>
 * <li>reaped: stubble with stooks of wheat standing in rows;</li>
 * <li>an orchard: apple, blossom or cherry trees in rows on grass;</li>
 * <li>pasture or meadow: grass and flowers, a stack of hay, a lone oak.</li>
 * </ul>
 * The outer ring of fields (band 2 and out) is more pasture and meadow than plough: the tended land eases to the wild.
 */
final class CityFarms {
    private CityFarms() {}

    static final double FIELD_BAND = 21.0, FIELD_W = 26.0;
    static final int ARABLE = 0, REAPED = 1, ORCHARD = 2, PASTURE = 3, MEADOW = 4;

    /** What a field is, from the plan alone. */
    static int kind(int band, int sector) {
        int h = Ctx.shape(sector, band, 34);
        if (band >= 2) return h < 40 ? ARABLE : h < 50 ? REAPED : h < 56 ? ORCHARD : h < 80 ? PASTURE : MEADOW;
        return h < 64 ? ARABLE : h < 75 ? REAPED : h < 84 ? ORCHARD : h < 94 ? PASTURE : MEADOW;
    }

    /** Whether a pasture or meadow field has a lone oak at its seed (never near a road: its crown would be cut). */
    static boolean lone(CityPlan p, int band, int sector, double sx, double sz) {
        int k = kind(band, sector);
        if (k != PASTURE && k != MEADOW || Ctx.shape(sector, band, 43) >= 55) return false;
        for (int[] r : p.roads) if (CityPainter.segDist(sx, sz, r[0], r[1], r[2], r[3]) < 14) return false;
        return Math.hypot(sx, sz) < p.fieldsTo - 4;
    }

    /**
     * The field a column lies in (SPEC §16.3's lattice; the painter's original fields kept their shapes), into f: [0]
     * band, [1] sector, [2] seed x, [3] seed z, [4] the gap to the second-nearest seed (under 1.2: a boundary), [5] its
     * band, [6] its sector, [7] a lone oak's trunk offset dx and [8] dz within its crown (NaN when none).
     */
    static void cell(CityPlan p, int x, int z, double r, double[] f) {
        double a = Math.atan2(z, x);
        int b0 = (int) Math.floor((r - p.fieldsFrom) / FIELD_BAND);
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        f[7] = Double.NaN;
        double tree = 3.4;
        for (int band = b0 - 1; band <= b0 + 1; band++) {
            if (band < 0) continue;
            double rb = p.fieldsFrom + (band + 0.5) * FIELD_BAND;
            int n = Math.max(16, (int) (2 * Math.PI * rb / FIELD_W));
            double step = 2 * Math.PI / n;
            int k0 = (int) Math.floor((a + Math.PI) / step);
            for (int k = k0 - 1; k <= k0 + 1; k++) {
                int kk = Math.floorMod(k, n);
                double ja = (Ctx.shape(band, kk, 37) / 100.0 - 0.5) * 0.7, jr = (Ctx.shape(band, kk, 38) / 100.0 - 0.5) * 0.6;
                double sa = -Math.PI + (k + 0.5 + ja) * step, sr = rb + jr * FIELD_BAND;
                double sx = Math.cos(sa) * sr, sz = Math.sin(sa) * sr;
                double dx = x - sx, dz = z - sz;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < best) {
                    second = best;
                    f[5] = f[0];
                    f[6] = f[1];
                    best = d;
                    f[0] = band;
                    f[1] = kk;
                    f[2] = sx;
                    f[3] = sz;
                } else if (d < second) {
                    second = d;
                    f[5] = band;
                    f[6] = kk;
                }
                // a lone oak's crown reaches over the boundary into the next field
                int tx = (int) Math.floor(sx), tz = (int) Math.floor(sz);
                double td = Math.hypot(x - tx, z - tz);
                if (td < tree && lone(p, band, kk, sx, sz)) { // (checked only near a seed: cheap)
                    tree = td;
                    f[7] = x - tx;
                    f[8] = z - tz;
                }
            }
        }
        f[4] = second - best;
    }

    static void fields(Ctx c, CityBuffer b, int x, int z, double r) {
        CityPlan p = c.plan;
        double[] f = c.fc;
        cell(p, x, z, r, f);
        int band = (int) f[0], sector = (int) f[1];
        if (f[4] < 1.2) boundary(c, b, x, z, band, sector, (int) f[5], (int) f[6]);
        else {
            switch (kind(band, sector)) {
                case ARABLE -> arable(c, b, x, z, band, sector, f[2], f[3]);
                case REAPED -> reaped(c, b, x, z, band, sector);
                case ORCHARD -> orchard(c, b, x, z, r, band, sector);
                case PASTURE -> pasture(c, b, x, z, band, sector, f[2], f[3]);
                default -> meadow(c, b, x, z, false);
            }
        }
        if (!Double.isNaN(f[7])) {
            int dx = (int) f[7], dz = (int) f[8];
            if (dx == 0 && dz == 0) {
                b.column(x, 1, 5, z, OAK_TRUNK);
                b.set(x, 6, z, OAK_LEAVES);
            } else bigCrown(c, b, x, z, dx, dz);
        }
    }

    /** Between two fields: a hedge (most), a farm track or a grass balk, the same all along that pair's boundary. */
    static void boundary(Ctx c, CityBuffer b, int x, int z, int b1, int s1, int b2, int s2) {
        int lo = Math.min(b1 * 4096 + s1, b2 * 4096 + s2), hi = Math.max(b1 * 4096 + s1, b2 * 4096 + s2);
        int kind = Ctx.shape(lo, hi, 33);
        int h = c.pct(x, z, 36);
        if (kind < 64) {
            b.set(x, 0, z, GRASS);
            b.set(x, 1, z, hedgeLeaf(c, x, z));
            if (CityWall.vnoise(632, x * 1.5, z * 1.5) > 0.42) b.set(x, 2, z, hedgeLeaf(c, x, z + 7));
        } else if (kind < 84) {
            b.set(x, 0, z, h < 62 ? PATH : h < 80 ? COARSE_DIRT : GRASS);
            if (h >= 80 && h < 90) b.set(x, 1, z, SHORT_GRASS);
        } else {
            b.set(x, 0, z, GRASS);
            if (h < 30) b.set(x, 1, z, SHORT_GRASS);
            else if (h < 38) b.set(x, 1, z, WILDFLOWERS[h % WILDFLOWERS.length]);
        }
    }

    /**
     * An arable field in strips five wide along one axis: four rows of the strip's crop, then a divider (an irrigation
     * channel after every other strip, else a grass balk). The strip's crop and growth come from the field's season and
     * the strip; single plants lag a stage here and there.
     */
    static void arable(Ctx c, CityBuffer b, int x, int z, int band, int sector, double sx, double sz) {
        int axis = Ctx.shape(sector, band, 40) & 1;
        int s = (axis == 0 ? z : x) + Ctx.shape(sector, band, 44) % 5;
        int si = Math.floorDiv(s, 5), pos = Math.floorMod(s, 5);
        if (pos == 0) {
            // (never by the river's bank, where the channel's water would find the river)
            if ((si & 1) == 0 && CityPainter.riverDistance(c.plan, x, z) > 16) b.set(x, 0, z, WATER);
            else {
                b.set(x, 0, z, GRASS);
                if (c.pct(x, z, 47) < 30) b.set(x, 1, z, SHORT_GRASS);
            }
            return;
        }
        int strip = Ctx.shape(sector * 31 + si, band, 45);
        int season = Ctx.shape(sector, band, 41);
        int lag = c.pct(x, z, 46) < 16 ? 1 : 0;
        b.set(x, 0, z, FARMLAND);
        // a scarecrow keeps some fields (on its seed, in a crop row)
        if (x == (int) Math.floor(sx) && z == (int) Math.floor(sz) && Ctx.shape(sector, band, 42) < 50) {
            int face = Ctx.shape(sector, band, 48) & 3;
            b.set(x, 1, z, SCARECROW[face][0]);
            b.set(x, 2, z, SCARECROW[face][1]);
            return;
        }
        int grow = Math.max(0, Math.min(7, 2 + season * 6 / 100 + strip % 3 - 1 - lag));
        BlockState crop;
        if (strip < 42) crop = WHEAT[grow];
        else if (strip < 57) crop = CARROTS[grow];
        else if (strip < 70) crop = POTATOES[grow];
        else if (strip < 80) crop = BEETS[grow / 2];
        else if (strip < 86) crop = (pos & 1) == 1 ? PUMPKIN_STEM : c.pct(x, z, 49) < 45 ? PUMPKIN : null;
        else if (strip < 93) crop = null; // ploughed, waiting
        else {
            b.set(x, 0, z, GRASS); // left fallow this year
            if (c.pct(x, z, 50) < 40) b.set(x, 1, z, SHORT_GRASS);
            return;
        }
        if (crop != null) b.set(x, 1, z, crop);
    }

    /** A reaped field: stubble (the first shoots of the next sowing) and stooks of wheat in rows four apart. */
    static void reaped(Ctx c, CityBuffer b, int x, int z, int band, int sector) {
        b.set(x, 0, z, FARMLAND);
        int ox = Ctx.shape(sector, band, 51) % 4, oz = Ctx.shape(sector, band, 52) % 4;
        if (Math.floorMod(x + ox, 4) == 1 && Math.floorMod(z + oz, 4) == 1) b.set(x, 1, z, STOOK[c.pct(x, z, 53) & 3]);
        else if (c.pct(x, z, 54) < 22) b.set(x, 1, z, WHEAT[0]);
    }

    /**
     * An orchard: fruit trees five apart in rows on grass (apples in leaf, apples in blossom, or cherries), each a trunk
     * to row 2 and a crown of three rows. A tree stands only where its own cell is orchard (never on a boundary).
     */
    static void orchard(Ctx c, CityBuffer b, int x, int z, double r, int band, int sector) {
        b.set(x, 0, z, GRASS);
        int h = c.pct(x, z, 55);
        int ox = Ctx.shape(sector, band, 56) % 5, oz = Ctx.shape(sector, band, 57) % 5;
        int gx = Math.floorDiv(x + ox, 5) * 5 - ox + 2, gz = Math.floorDiv(z + oz, 5) * 5 - oz + 2;
        int dx = x - gx, dz = z - gz;
        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && trunkOk(c, gx, gz, band, sector)) {
            int sort = Ctx.shape(sector, band, 58) % 3;
            BlockState trunk = sort == 2 ? CHERRY_TRUNK : OAK_TRUNK;
            BlockState leaf = sort == 2 ? CHERRY_LEAVES : sort == 1 && c.pct(x, z, 59) < 45 ? FLOWERING_AZALEA : OAK_LEAVES;
            if (dx == 0 && dz == 0) {
                b.column(x, 1, 2, z, trunk);
                b.column(x, 3, 4, z, leaf);
                b.set(x, 5, z, leaf);
                return;
            }
            b.set(x, 3, z, leaf);
            if ((dx == 0 || dz == 0) || c.pct(x, z, 60) < 40) b.set(x, 4, z, leaf);
            if (h < 25) b.set(x, 1, z, SHORT_GRASS);
            return;
        }
        if (h < 30) b.set(x, 1, z, SHORT_GRASS);
        else if (h < 34) b.set(x, 1, z, WILDFLOWERS[h % WILDFLOWERS.length]);
    }

    /** A tree's cell must be this orchard's own ground (not a boundary, not another field) with that ground round it. */
    static boolean trunkOk(Ctx c, int gx, int gz, int band, int sector) {
        double[] g = c.fc2;
        cell(c.plan, gx, gz, Math.sqrt((double) gx * gx + (double) gz * gz), g);
        if ((int) g[0] != band || (int) g[1] != sector || g[4] < 2.6) return false;
        return CityCommons.road(c.plan, gx, gz, 10.5, c.rd2) < 0;
    }

    /** Pasture: grass grazed short, a few flowers, a stack of hay by its seed. */
    static void pasture(Ctx c, CityBuffer b, int x, int z, int band, int sector, double sx, double sz) {
        b.set(x, 0, z, GRASS);
        int h = c.pct(x, z, 61);
        int hx = (int) Math.floor(sx) + 3, hz = (int) Math.floor(sz) + 1;
        if (Ctx.shape(sector, band, 62) < 60 && x >= hx && x <= hx + 1 && z == hz) {
            b.set(x, 1, z, HAY_Y);
            if (x == hx) b.set(x, 2, z, HAY_Y);
            return;
        }
        if (h < 22) b.set(x, 1, z, SHORT_GRASS);
        else if (h < 25) b.set(x, 1, z, WILDFLOWERS[h % WILDFLOWERS.length]);
    }

    /** One column's share of a lone oak's crown: wide and ragged, rows 4 to 8, the trunk dx, dz away. */
    static void bigCrown(Ctx c, CityBuffer b, int x, int z, int dx, int dz) {
        double d = Math.sqrt(dx * dx + dz * dz);
        double[] rad = {2.0, 3.0, 3.3, 2.7, 1.6};
        for (int i = 0; i < rad.length; i++) {
            int ry = 4 + i;
            if (d > rad[i] + 0.3) continue;
            if (d > rad[i] - 0.8 && c.pct(x, z, 680 + ry) < 30) continue;
            BlockState was = b.get(x, ry, z);
            if (was == null || was.isAir()) b.set(x, ry, z, c.pct(x, z, 690 + ry) < 85 ? OAK_LEAVES : AZALEA_LEAVES);
        }
    }
}
