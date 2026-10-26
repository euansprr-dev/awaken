package dev.awaken.city;

import dev.awaken.city.CityPainter.Ctx;
import dev.awaken.city.kit.CommonsBlocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The commons (Milestone 4, the Commons step; SPEC §16.3): Carillon's countryside outside the wall, from the wall's foot
 * to the painter's reach. Euan's walk out of the High Gate read rough: a dirt road with stone dropped on it, flat crop
 * patches, banner poles whose arms floated. Now:
 * <ul>
 * <li>The roads in are laid: setts through the barbican, then gravel shoulders between cobbled kerbs, two wheel ruts
 * and a worn crown; grass verges with wild flowers, a ditch, then a hedge or a fence, with field gates over culverts.
 * Milestones every 64 and lamp posts by turns with pennant lines strung across the road between two poles.</li>
 * <li>The fields are strips of one crop each at its own growth, between grass balks and irrigation channels; some are
 * reaped and stooked, some orchards, pasture or meadow (more towards the edge, so the tended land eases into the wild),
 * with hedgerows, farm tracks, scarecrows and lone oaks. Farmsteads stand among them ({@link CityFarms}).</li>
 * </ul>
 * Every column works itself out from the plan alone (a chunk painted alone equals the chunk in context). Past the reach,
 * the High Gate road runs on over the natural ground to the Wayfarers' fire ({@link CityTrail}).
 */
final class CityCommons {
    private CityCommons() {}

    // ---- the road's cross-section: distance from the centre line, in cells ------------------------------------------

    /** The carriageway (kerbs at 3.5, ruts at 2.5), the verges (lamps and poles at 5.5), the ditch, the hedge or fence. */
    static final double CARRIAGE = 3.5, VERGE = 5.5, DITCH = 6.5, EDGE = 7.5;
    /** Setts from the gate this far out (through the barbican), then the laid road; the ditch and the hedges start at 26. */
    static final int SETTS = 22, HEDGES_FROM = 26;
    /** Lamps on posts and pennant lines, by turns, every 14 from 24 out; milestones every 64 from 40. */
    static final int FURNISH_FROM = 24, FURNISH_STEP = 14, MILE_FROM = 40, MILE_STEP = 64;
    /** The pennant lines' height (the line's row; the poles below it). */
    static final int LINE_ROW = 7;
    /** A field gate (over a culvert) every 48 along, three wide, by turns either side. */
    static final int GATE_STEP = 48, GATE_AT = 30;

    static final BlockState MUD_BLOCK = of("minecraft:mud"), ROOTED_DIRT = of("minecraft:rooted_dirt"),
            STONE_TOP_SLAB = of("minecraft:stone_brick_slab[type=top]"), LILY = of("minecraft:lily_pad"),
            TALL_LOWER = of("minecraft:tall_grass[half=lower]"), TALL_UPPER = of("minecraft:tall_grass[half=upper]"),
            FERN = of("minecraft:fern"), COBBLE_WALL = of("minecraft:cobblestone_wall[up=true]");
    static final BlockState[] WILDFLOWERS = {of("minecraft:dandelion"), of("minecraft:poppy"), of("minecraft:oxeye_daisy"),
            of("minecraft:cornflower"), of("minecraft:azure_bluet"), of("minecraft:dandelion"), of("minecraft:poppy")};
    /** spruce_fence along x (east-west) and along z; field gates by facing, shut. */
    static final BlockState[] FENCE_RUN = {of("minecraft:spruce_fence[east=true,west=true]"), of("minecraft:spruce_fence[north=true,south=true]")};
    static final BlockState[] FIELD_GATE = new BlockState[4];
    static final BlockState[] MILESTONE = new BlockState[4];
    static final BlockState[][] SCARECROW = new BlockState[4][2], FINGERPOST = new BlockState[4][2];
    static final BlockState[] STOOK = new BlockState[4];

    static {
        for (int d = 0; d < 4; d++) {
            FIELD_GATE[d] = of("minecraft:spruce_fence_gate[facing=" + DIR[d] + ",open=false]");
            MILESTONE[d] = of("awaken:milestone[facing=" + DIR[d] + "]");
            STOOK[d] = of("awaken:stook[facing=" + DIR[d] + "]");
            for (int h = 0; h < 2; h++) {
                String half = h == 0 ? "lower" : "upper";
                SCARECROW[d][h] = of("awaken:scarecrow[facing=" + DIR[d] + ",half=" + half + "]");
                FINGERPOST[d][h] = of("awaken:fingerpost[facing=" + DIR[d] + ",half=" + half + ",to=camp]");
            }
        }
    }

    // ---- crops -----------------------------------------------------------------------------------------------------

    static final BlockState[] WHEAT = new BlockState[8], CARROTS = new BlockState[8], POTATOES = new BlockState[8], BEETS = new BlockState[4];
    static final BlockState PUMPKIN = of("minecraft:pumpkin"), PUMPKIN_STEM = of("minecraft:pumpkin_stem[age=7]"),
            BERRIES = of("minecraft:sweet_berry_bush[age=2]"), HAY_Y = of("minecraft:hay_block[axis=y]");

    static {
        for (int a = 0; a < 8; a++) {
            WHEAT[a] = of("minecraft:wheat[age=" + a + "]");
            CARROTS[a] = of("minecraft:carrots[age=" + a + "]");
            POTATOES[a] = of("minecraft:potatoes[age=" + a + "]");
        }
        for (int a = 0; a < 4; a++) BEETS[a] = of("minecraft:beetroots[age=" + a + "]");
    }

    // ---- the road frame --------------------------------------------------------------------------------------------

    /**
     * The nearest road to a cell's centre within {@code max} of its line: its index, with [0] the distance along it from
     * the gate end and [1] the signed distance off it (positive on the side of the road's left-hand normal (-dz, dx)). -1
     * when none.
     */
    static int road(CityPlan p, int x, int z, double max, double[] o) {
        int best = -1;
        double bo = max;
        for (int i = 0; i < p.roads.size(); i++) {
            int[] r = p.roads.get(i);
            double dx = r[2] - r[0], dz = r[3] - r[1], len = Math.hypot(dx, dz);
            double ux = dx / len, uz = dz / len;
            double vx = x + 0.5 - r[0], vz = z + 0.5 - r[1];
            double along = vx * ux + vz * uz;
            if (along < -1 || along > len + 1) continue;
            double off = vx * -uz + vz * ux;
            if (Math.abs(off) <= bo) {
                bo = Math.abs(off);
                best = i;
                o[0] = along;
                o[1] = off;
            }
        }
        return best;
    }

    /** A road's direction out (from the gate) and its positive side, as N/E/S/W. */
    static int out(CityPlan p, int i) {
        int[] r = p.roads.get(i);
        return CityWall.dominant(r[2] - r[0], r[3] - r[1]);
    }

    static int side(CityPlan p, int i) {
        int[] r = p.roads.get(i);
        return CityWall.dominant(-(r[3] - r[1]), r[2] - r[0]);
    }

    // ---- the ground outside the wall ---------------------------------------------------------------------------------

    /**
     * One column outside the wall (plan VOID within the reach), its dirt and clearing already done by the painter: the
     * road's cross-section near a road, else the apron's grass (and the wall's outer band), the fields, or the meadow
     * past them.
     */
    static void ground(Ctx c, CityBuffer b, int x, int z, double r, boolean apron) {
        CityPlan p = c.plan;
        int ri = road(p, x, z, 11.5, c.rd);
        double along = c.rd[0], off = c.rd[1], aoff = Math.abs(off);
        if (ri >= 0 && aoff <= CARRIAGE) {
            carriage(c, b, x, 0, z, along, off);
            furnish(c, b, ri, x, 0, z, along, off, r <= p.fieldsTo);
            return;
        }
        boolean roadside = ri >= 0 && aoff <= EDGE;
        if (apron && !roadside) {
            b.set(x, 0, z, GRASS);
            if (c.pct(x, z, 32) < 10) b.set(x, 1, z, SHORT_GRASS);
            CityWall.outerBand(c, b, x, z); // the wall's outer foot and face (the Walls step)
            return;
        }
        if (roadside) {
            boolean hedged = along >= HEDGES_FROM && r >= p.fieldsFrom - 2 && r <= p.fieldsTo;
            if (aoff <= VERGE || !hedged) verge(c, b, x, 0, z, along, off);
            else if (aoff <= DITCH) ditch(c, b, ri, x, z, along, off);
            else edge(c, b, ri, x, z, along, off);
            furnish(c, b, ri, x, 0, z, along, off, r <= p.fieldsTo);
            if (apron) CityWall.outerBand(c, b, x, z);
        } else if (r > p.fieldsTo) meadow(c, b, x, z, true);
        else CityFarms.fields(c, b, x, z, r);
        if (ri >= 0) roadTree(c, b, ri, x, z, along, off, r);
    }

    /**
     * The carriageway at one cell, its top at row {@code y}: setts from the gate, then the laid road (kerbs of cobble,
     * gravel shoulders worn through to dirt in patches, wheel ruts of coarse dirt with the odd puddle, a crown of trodden
     * path with a tuft of grass further out). Coherent wear (noise), never a scatter of single blocks.
     */
    static void carriage(Ctx c, CityBuffer b, int x, int y, int z, double along, double off) {
        int k = (int) Math.abs(off);
        int h = c.pct(x, z, 610);
        double wear = CityWall.vnoise(611, x * 1.3, z * 1.3);
        BlockState s;
        if (along < SETTS + wear * 6) s = k == 3 ? (h < 70 ? COBBLE : MOSSY_COBBLE) : h < 50 ? COBBLE : h < 78 ? STONE : h < 92 ? ANDESITE : MOSSY_COBBLE;
        else s = switch (k) {
            case 3 -> h < 64 ? COBBLE : h < 86 ? MOSSY_COBBLE : STONE;
            case 2 -> wear > 0.7 && h < 45 ? MUD_BLOCK : h < 72 ? COARSE_DIRT : h < 90 ? PATH : ROOTED_DIRT;
            case 1 -> wear < 0.32 || h >= 80 ? PATH : GRAVEL;
            default -> along > 60 && wear > 0.62 && h < 30 ? GRASS : PATH;
        };
        b.set(x, y, z, s);
        b.column(x, y + 1, y + 14, z, AIR);
    }

    /** A grass verge: short grass, drifts of wild flowers, a clump of tall grass. */
    static void verge(Ctx c, CityBuffer b, int x, int y, int z, double along, double off) {
        b.set(x, y, z, GRASS);
        int h = c.pct(x, z, 620);
        double drift = CityWall.vnoise(621, x * 0.9, z * 0.9);
        if (drift > 0.58 && h < 30) b.set(x, y + 1, z, WILDFLOWERS[c.pct(x, z, 622) % WILDFLOWERS.length]);
        else if (h < 34) b.set(x, y + 1, z, SHORT_GRASS);
        else if (h < 38 && Math.abs(off) > 4.5) {
            b.set(x, y + 1, z, TALL_LOWER);
            b.set(x, y + 2, z, TALL_UPPER);
        }
    }

    /** Whether a field gate (over a culvert) stands here: three wide every 48 along, by turns either side. */
    static boolean gate(CityPlan p, int ri, double along, double off) {
        int g = (int) Math.floor((along - GATE_AT) / GATE_STEP);
        if (g < 0) return false;
        double a = along - GATE_AT - g * GATE_STEP;
        boolean left = ((g + ri) & 1) == 0;
        return a < 3 && (off > 0) == left;
    }

    /** The ditch beside the verge: water a block down; a culvert under a field gate's track (a slab deck over it). */
    static void ditch(Ctx c, CityBuffer b, int ri, int x, int z, double along, double off) {
        b.set(x, -1, z, WATER);
        if (gate(c.plan, ri, along, off)) {
            b.set(x, 0, z, STONE_TOP_SLAB);
            return;
        }
        b.set(x, 0, z, AIR);
        if (c.pct(x, z, 630) < 5) b.set(x, 0, z, LILY);
    }

    /** The field's edge along the road: a hedge (leaves, two high in places) or a spruce fence, a gate at a track. */
    static void edge(Ctx c, CityBuffer b, int ri, int x, int z, double along, double off) {
        CityPlan p = c.plan;
        b.set(x, 0, z, GRASS);
        int axis = out(p, ri) == N || out(p, ri) == S ? 1 : 0;
        if (gate(p, ri, along, off)) {
            double a = along - GATE_AT - Math.floor((along - GATE_AT) / GATE_STEP) * GATE_STEP;
            int sd = side(p, ri);
            if (a >= 1 && a < 2) b.set(x, 1, z, FIELD_GATE[off > 0 ? sd : (sd + 2) & 3]);
            else b.set(x, 1, z, CityWall.SPRUCE_FENCE_);
            return;
        }
        int seg = (int) Math.floor(along / 24);
        boolean fence = Ctx.shape(ri * 64 + seg, off > 0 ? 1 : 0, 631) < 35;
        if (fence) {
            b.set(x, 1, z, FENCE_RUN[axis]);
            return;
        }
        b.set(x, 1, z, hedgeLeaf(c, x, z));
        if (CityWall.vnoise(632, x * 1.5, z * 1.5) > 0.5) b.set(x, 2, z, hedgeLeaf(c, x, z + 7));
    }

    static BlockState hedgeLeaf(Ctx c, int x, int z) {
        int h = c.pct(x, z, 633);
        return h < 58 ? OAK_LEAVES : h < 86 ? AZALEA_LEAVES : FLOWERING_AZALEA;
    }

    /** Grass past the fields (and on pasture and meadows): short and tall grass, drifts of flowers, the odd bush. */
    static void meadow(Ctx c, CityBuffer b, int x, int z, boolean wild) {
        b.set(x, 0, z, GRASS);
        int h = c.pct(x, z, 640);
        double drift = CityWall.vnoise(641, x * 0.6, z * 0.6);
        if (drift > 0.6 && h < 28) b.set(x, 1, z, WILDFLOWERS[c.pct(x, z, 642) % WILDFLOWERS.length]);
        else if (h < (wild ? 40 : 30)) b.set(x, 1, z, SHORT_GRASS);
        else if (h < (wild ? 48 : 34)) {
            b.set(x, 1, z, TALL_LOWER);
            b.set(x, 2, z, TALL_UPPER);
        } else if (wild && h < 50) b.set(x, 1, z, h < 49 ? FERN : AZALEA_LEAVES);
    }

    // ---- the road's furniture: lamps, pennant lines, milestones -----------------------------------------------------

    /**
     * Lamps on posts and pennant lines by turns, every 14 from 24 out: a lamp at each side's verge; a pennant line strung
     * across the road at row 7 between two line poles, bunting along it and a district banner hung from it beside each
     * pole (in the fields only: the road past them keeps its lamps). Milestones on the right-hand verge every 64.
     */
    static void furnish(Ctx c, CityBuffer b, int ri, int x, int y, int z, double along, double off, boolean lines) {
        CityPlan p = c.plan;
        double aoff = Math.abs(off);
        int step = (int) Math.floor(along);
        if (step >= MILE_FROM && Math.floorMod(step - MILE_FROM, MILE_STEP) == 0 && off > 4 && off <= 5) {
            b.set(x, y + 1, z, MILESTONE[(side(p, ri) + 2) & 3]); // its face to the road
            return;
        }
        if (step < FURNISH_FROM || Math.floorMod(step - FURNISH_FROM, FURNISH_STEP) != 0) return;
        boolean lamp = Math.floorMod((step - FURNISH_FROM) / FURNISH_STEP, 2) == 0;
        if (lamp) {
            if (aoff > 5 && aoff <= 6) {
                b.set(x, y + 1, z, WALL_POST);
                b.column(x, y + 2, y + 3, z, FENCE);
                b.set(x, y + 4, z, LANTERN);
            }
            return;
        }
        if (!lines) return;
        if (aoff > 5 && aoff <= 6) {
            for (int ry = y + 1; ry < y + LINE_ROW; ry++) b.set(x, ry, z, CityProps.POLE);
            b.set(x, y + LINE_ROW, z, CityProps.POLE_TOP);
            return;
        }
        if (aoff > 5) return;
        // the line across: cells from -4.5 to 4.5 (ten), sagging to the middle
        int o = out(p, ri);
        int axis = o == N || o == S ? 0 : 1;
        double t = (off + 5) / 10.0;
        double sagT = Math.sin(Math.PI * t);
        int sag = sagT > 0.9 ? 2 : sagT > 0.55 ? 1 : 0;
        int set = Ctx.shape(ri * 97 + step, 0, 650) & 3;
        b.set(x, y + LINE_ROW, z, CityProps.LINE[axis][2][set][sag]);
        if (aoff > 4) {
            // a district banner hung from the line by each pole, its cloth to the walker leaving the city
            int design = CityWall.designOf(o == N ? TERRACE : o == E ? GARDEN_D : WORKS);
            for (int i = 0; i < 3; i++) b.set(x, y + LINE_ROW - 1 - i, z, CityFacadeLife.BANNER[(o + 2) & 3][design][i][1]);
        }
    }

    // ---- hedgerow trees along the roads ------------------------------------------------------------------------------

    /**
     * Oaks standing in the roadside hedges, one in some 24-cell stretches of each side, never by a pole or a gate: a
     * trunk to row 4 and a ragged crown over the ditch and the verge. Every column near one paints its share.
     */
    static void roadTree(Ctx c, CityBuffer b, int ri, int x, int z, double along, double off, double r) {
        CityPlan p = c.plan;
        if (Math.abs(off) < 4 || Math.abs(off) > 11) return;
        int seg0 = (int) Math.floor(along / 24);
        for (int seg = seg0 - 1; seg <= seg0 + 1; seg++) {
            double ta = seg * 24 + 12 + Ctx.shape(ri * 64 + seg, 3, 652) % 7 - 3;
            if (ta < HEDGES_FROM + 6) continue;
            int sideSign = off > 0 ? 1 : -1;
            if (Ctx.shape(ri * 64 + seg, off > 0 ? 1 : 0, 653) >= 38 || Ctx.shape(ri * 64 + seg, off > 0 ? 1 : 0, 631) < 35) continue;
            double furn = Math.floorMod((int) Math.floor(ta) - FURNISH_FROM, FURNISH_STEP);
            if (furn < 4 || furn > FURNISH_STEP - 4) continue; // clear of the lamps and the poles
            double ga = Math.floorMod((int) Math.floor(ta) - GATE_AT, GATE_STEP);
            if (ga < 5 || ga > GATE_STEP - 3) continue;
            int[] rr = p.roads.get(ri);
            double len = Math.hypot(rr[2] - rr[0], rr[3] - rr[1]);
            double ux = (rr[2] - rr[0]) / len, uz = (rr[3] - rr[1]) / len;
            double tx = rr[0] + ux * ta + -uz * EDGE * sideSign, tz = rr[1] + uz * ta + ux * EDGE * sideSign;
            double tr = Math.hypot(tx, tz);
            if (tr < p.fieldsFrom || tr > p.fieldsTo - 3) continue;
            int txc = (int) Math.floor(tx), tzc = (int) Math.floor(tz);
            int dx = x - txc, dz = z - tzc;
            if (dx == 0 && dz == 0) {
                b.column(x, 1, 4, z, OAK_TRUNK);
                b.set(x, 5, z, OAK_LEAVES);
                continue;
            }
            crown(c, b, x, z, dx, dz, 3, 2.6, seg * 7 + ri);
        }
    }

    /** One column's share of a rounded crown of leaves centred over a trunk (dx, dz away), from row {@code base}. */
    static void crown(Ctx c, CityBuffer b, int x, int z, int dx, int dz, int base, double rad, int salt) {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > rad + 0.4) return;
        for (int ry = base; ry <= base + 3; ry++) {
            double rr = ry == base ? rad * 0.75 : ry == base + 3 ? rad * 0.45 : rad;
            if (d > rr + 0.3) continue;
            if (d > rr - 0.7 && c.pct(x, z, 660 + ry + salt) < 30) continue; // ragged edges
            BlockState was = b.get(x, ry, z);
            if (was == null || was.isAir()) b.set(x, ry, z, c.pct(x, z, 670 + ry) < 82 ? OAK_LEAVES : AZALEA_LEAVES);
        }
    }

    // ---- the yard's pennant lines -------------------------------------------------------------------------------------

    /**
     * The High Gate yard's two pennant lines, one down each side of the yard between line poles at its corners, bunting
     * along them and the Terrace's banners hung from them by the poles (they replace the masts whose arms floated).
     */
    static void yardLines(CityBuffer b, int x, int z) {
        if (x != -16 && x != 15 || z < -255 || z > -245) return;
        int ry0 = 9;
        if (z == -255 || z == -245) {
            for (int ry = 1; ry < ry0; ry++) b.set(x, ry, z, CityProps.POLE);
            b.set(x, ry0, z, CityProps.POLE_TOP);
            return;
        }
        int k = z + 255; // 1..9 along the line
        double t = Math.sin(Math.PI * k / 10.0);
        int sag = t > 0.9 ? 2 : t > 0.55 ? 1 : 0;
        b.set(x, ry0, z, CityProps.LINE[1][2][x < 0 ? 1 : 2][sag]);
        if (k == 1 || k == 9) {
            int design = CityWall.designOf(TERRACE);
            for (int i = 0; i < 3; i++) b.set(x, ry0 - 1 - i, z, CityFacadeLife.BANNER[x < 0 ? E : W][design][i][1]);
        }
    }
}
