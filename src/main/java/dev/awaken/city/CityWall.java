package dev.awaken.city;

import dev.awaken.city.CityPainter.Ctx;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The ring wall's curtain (Milestone 4, the Walls step; look bible §5.7, §11 #12). One authored cross-section swept
 * along the 24 sides, so every column works itself out from the wall's own geometry (its side, how deep it stands in the
 * wall and how far along the side) and a chunk painted alone equals the same chunk in context.
 *
 * <p>Outside, from the foot up: a battered talus, buttresses every 8 with set-offs, string courses at rows 15 and 27 that
 * wrap round them (ledges), long arrow loops and putlog holes between, drain spouts with their moss streaks, and a
 * machicolated parapet two deep on corbels with merlons and loops. Six sides carry a roofed wall walk instead: a timber
 * hoarding hung out on beams under one slate roof from the hoarding to the inner parapet.
 *
 * <p>Inside, the town's side: an arcade of deep bays at the foot holding the life of the wall (stores, stalls under
 * awnings, a stable with hay, workshops, a smithy, a wayside shrine, woodpiles, laundry), a course at row 9 above it,
 * recessed blind panels between pilasters with ivy climbing in them, drainpipes up to the walk every third pier, long
 * banners and flagpoles, then a corbelled inner parapet with lanterns. The west side's arcade gives way to a grand stair
 * up the inside of the wall.
 *
 * <p>Rows follow the climbing grid (bible §3.1): every hold's top is an even height over the street (a lip in an odd
 * row), so the wall's ledges line up with the town's.
 */
final class CityWall {
    private CityWall() {}

    // ---- geometry -------------------------------------------------------------------------------------------------

    static final int SIDES = 24;
    static final double[] COS = new double[SIDES], SIN = new double[SIDES];
    /** Each side's outward and +along directions as the dominant grid side (0 N, 1 E, 2 S, 3 W). */
    static final int[] OUT = new int[SIDES], ALONG = new int[SIDES];
    /** The district behind each side (whose banners and character its stretch of wall wears). */
    static final int[] BEHIND = new int[SIDES];
    /** How far along each side the curtain runs clear of the corner towers (|a| below this). */
    static final double[] CLEAR = new double[SIDES];
    /** Face cells per block along a side (1 on the axis sides, 0.71 on the diagonals): the rhythm counts cells. */
    static final double[] SCALE = new double[SIDES];

    static {
        for (int k = 0; k < SIDES; k++) {
            double phi = Math.toRadians(15 * (k + 1));
            COS[k] = Math.cos(phi);
            SIN[k] = Math.sin(phi);
            OUT[k] = dominant(COS[k], SIN[k]);
            ALONG[k] = dominant(-SIN[k], COS[k]);
            CLEAR[k] = 240 * Math.sin(Math.toRadians(7.5)) - 7 * (Math.abs(SIN[k]) + Math.abs(COS[k])) - 1;
            SCALE[k] = Math.max(Math.abs(SIN[k]), Math.abs(COS[k]));
            int deg = 15 * (k + 1) % 360;
            BEHIND[k] = deg == 120 || deg == 135 || deg == 240 || deg == 255 ? QUAY_D
                    : deg < 45 || deg >= 315 ? GARDEN_D : deg < 135 ? WORKS : deg < 225 ? BELLWATCH : TERRACE;
        }
    }

    static int dominant(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? E : W;
        return dz > 0 ? S : N;
    }

    /** Sides whose wall walk is roofed over a timber hoarding (never the gate sides, the Roost's or the river's). */
    static boolean roofed(int k) {
        return k == 1 || k == 3 || k == 10 || k == 11 || k == 19 || k == 21;
    }

    /** The gate sides: the gatehouse stands in the middle of these, |a| up to about 17.5. */
    static boolean gateSide(int k) {
        return k == 5 || k == 17 || k == 23;
    }

    /** Where a column stands: the side it belongs to, its depth into the wall (centre; -5 outer face, 5 inner face), its
     * position along the side (0 at the side's middle, increasing toward +ALONG), and that position counted in face
     * cells (so a rhythm of 7 is seven cells on a diagonal side too). */
    record Geo(int k, double d, double a, int u) {
        int out() {
            return OUT[k];
        }

        int in() {
            return (OUT[k] + 2) & 3;
        }

        /** Layer outside the outer face: 1 is the first cell out. */
        int lout() {
            return (int) Math.ceil(-5 - d);
        }

        /** Layer inside the inner face: 1 is the first cell of the ring. */
        int lin() {
            return (int) Math.ceil(d - 5);
        }

        /** Layer within the wall: 0 the outermost cell, 9 the innermost. */
        int wo() {
            return (int) Math.floor(d + 5);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, BlockState> STATES = new java.util.concurrent.ConcurrentHashMap<>();

    /** A block state by spec, parsed once (the painter builds kit specs from parts: loops, ivy, shields). */
    static BlockState st(String spec) {
        BlockState s = STATES.get(spec);
        if (s == null) STATES.put(spec, s = of(spec));
        return s;
    }

    static double apothem(CityPlan p) {
        return p.wallRadius * Math.cos(Math.toRadians(7.5));
    }

    static Geo geo(CityPlan p, int x, int z) {
        double cx = x + 0.5, cz = z + 0.5, best = -1e9;
        int bk = 0;
        for (int k = 0; k < SIDES; k++) {
            double s = cx * COS[k] + cz * SIN[k];
            if (s > best) {
                best = s;
                bk = k;
            }
        }
        double a = -cx * SIN[bk] + cz * COS[bk];
        return new Geo(bk, apothem(p) - best, a, (int) Math.floor(a * SCALE[bk]));
    }

    /** No tower, gate or other landmark within r cells of the point at (side, along, depth). */
    static boolean clearAt(CityPlan p, int k, double a, double d, int r) {
        double ap = apothem(p) - d;
        int x = (int) Math.floor(ap * COS[k] - a * SIN[k]), z = (int) Math.floor(ap * SIN[k] + a * COS[k]);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                byte n = p.kind(x + dx, z + dz);
                if (n == LANDMARK || n == WATERGATE || water(n)) return false;
            }
        return true;
    }

    /** A stretch of curtain from a0 to a1 clear of the towers and gates (with a margin). */
    static boolean clearSpan(CityPlan p, int k, double a0, double a1, double d) {
        if (a0 < -CLEAR[k] || a1 > CLEAR[k]) return false;
        if (gateSide(k) && a1 > -19 && a0 < 18) return false;
        return clearAt(p, k, a0, d, 2) && clearAt(p, k, a1, d, 2) && clearAt(p, k, (a0 + a1) / 2, d, 2);
    }

    // ---- the rhythm along a side ----------------------------------------------------------------------------------

    /** Buttresses: three wide, centred every 8 along (on 0, ±8, ±16). */
    static boolean buttress(CityPlan p, Geo g) {
        int m = Math.floorMod(g.u + 1, 8);
        if (m > 2) return false;
        int centre = g.u + 1 - m;
        return clearSpan(p, g.k, (centre - 2.5) / SCALE[g.k], (centre + 2.5) / SCALE[g.k], -5.5);
    }

    /** The arcade's bay index and place in its 7-cell unit (0-1 the pier, 2-6 the opening; 4 its middle). */
    static int bay(Geo g) {
        return Math.floorDiv(g.u + 4, 7);
    }

    static int u7(Geo g) {
        return Math.floorMod(g.u + 4, 7);
    }

    /** Whether this column's bay (the whole unit, pier to pier) stands on clear curtain. */
    static boolean bayClear(CityPlan p, Geo g) {
        if (stairSide(g.k)) return false;
        double a0 = (bay(g) * 7 - 4) / SCALE[g.k], a1 = a0 + 7 / SCALE[g.k];
        return clearSpan(p, g.k, a0, a1, 4.5);
    }

    /** What a bay holds (seeded by the plan, so every world's wall is the same). */
    static final int B_STORE = 0, B_STABLE = 1, B_WORKSHOP = 2, B_STALL = 3, B_SMITHY = 4, B_SHRINE = 5, B_WOOD = 6,
            B_BENCH = 7, B_LAUNDRY = 8, B_STORE2 = 9;

    static int bayKind(Geo g) {
        return Ctx.shape(g.k, bay(g), 91) % 10;
    }

    /** The west side (k 11) carries the grand stair up the inside of the wall instead of the arcade. */
    static boolean stairSide(int k) {
        return k == 11;
    }

    static final int STAIR_FROM = -20, STAIR_TO = 18;

    /** Every third pier carries a drainpipe from the street to the walk (a climb line up the whole wall). */
    static boolean pipe(CityPlan p, Geo g) {
        if (u7(g) == 0 && Math.floorMod(bay(g), 3) == 1 && bayClear(p, g)) return true;
        // and one near each end of every stretch of curtain (the town's rule: a climb line within 5 of a run's ends)
        if (stairSide(g.k)) return false;
        int end = (int) Math.floor(CLEAR[g.k] * SCALE[g.k]) - 3;
        boolean at = g.u == end || g.u == -end - 1 || gateSide(g.k) && (g.u == 20 || g.u == -21);
        return at && clearAt(p, g.k, g.a + 0.5, 5.5, 1);
    }

    // ---- the cross-section ----------------------------------------------------------------------------------------

    static int top(CityPlan p) {
        return p.wallHeight - 1;
    }

    /** The talus and buttresses outside the outer face (the rows the courses wrap round). */
    static boolean outerSolid(CityPlan p, Geo g, int ry) {
        int l = g.lout();
        if (l < 1 || l > 4 || ry < 0) return ry <= 0;
        if (ry <= 0) return true;
        if (l == 1 && ry <= 5 || l == 2 && ry <= 3 || l == 3 && ry <= 1) return true;
        if (buttress(p, g)) return l <= 2 && ry <= 13 || l == 1 && ry <= 33;
        return false;
    }

    /** The curtain's own masonry: everything but the bays, the panels' recesses, the stair and the putlog holes. */
    static boolean wallSolid(Ctx c, Geo g, int x, int ry, int z) {
        CityPlan p = c.plan;
        int wo = g.wo();
        if (ry < 0 || ry > top(p)) return ry < 0;
        if (stairSide(g.k) && wo >= 7 && stairCarve(g, ry)) return false;
        if (wo >= 7 && ry >= 1 && ry <= 7 && bayOpen(g, ry) && bayClear(p, g)) return false;
        if (wo == 9 && ry >= 10 && ry <= 31 && panelOpen(g, ry) && bayClear(p, g)) return false;
        return true;
    }

    /** The bay's opening: 1-5 full width, then a pointed head (row 6 three wide, row 7 one). */
    static boolean bayOpen(Geo g, int ry) {
        int m = u7(g);
        if (m < 2) return false;
        if (ry <= 5) return true;
        if (ry == 6) return m >= 3 && m <= 5;
        return ry == 7 && m == 4;
    }

    /** A blind panel between pilasters, recessed one: rows 10-29 full width, a pointed head at 30-31. */
    static boolean panelOpen(Geo g, int ry) {
        int m = u7(g);
        if (m < 2) return false;
        if (ry <= 29) return true;
        if (ry == 30) return m >= 3 && m <= 5;
        return ry == 31 && m == 4;
    }

    /** The grand stair's tread height at this column (1..top), or -1 off the flight. */
    static int stairY(Geo g) {
        if (g.u < STAIR_FROM || g.u > STAIR_TO) return -1;
        return g.u - STAIR_FROM + 1;
    }

    static boolean stairCarve(Geo g, int ry) {
        if (g.u >= STAIR_FROM - 3 && g.u < STAIR_FROM) return ry >= 1 && ry <= 4; // the foot: a small hall off the ring
        int s = stairY(g);
        if (s < 0) return false;
        int wo = g.wo();
        if (wo == 9) return ry >= s + 2 && ry <= s + 4 && Math.floorMod(g.u, 4) != 0; // arched openings, piers every 4
        return ry >= s + 1 && ry <= s + 4;
    }

    /** Solid at a cell, for the courses' and caps' shapes: the curtain, the talus, the towers (as solid masonry). */
    static boolean solidAt(Ctx c, int x, int ry, int z) {
        CityPlan p = c.plan;
        byte k = p.kind(x, z);
        return switch (k) {
            case WALL -> wallSolid(c, geo(p, x, z), x, ry, z);
            case VOID -> outerSolid(p, geo(p, x, z), ry);
            case LANDMARK -> ry <= 45;
            case WATERGATE -> ry >= 10 && ry <= top(p);
            case RING -> ry <= 0 || innerSolid(p, geo(p, x, z), ry);
            default -> ry <= p.lift(x, z);
        };
    }

    /** A drainpipe's column on the ring (street lamps keep out of it). */
    static boolean pipeAt(CityPlan p, int x, int z) {
        if (p.kind(x, z) != RING) return false;
        Geo g = geo(p, x, z);
        return g.lin() == 1 && pipe(p, g);
    }

    /** The inner parapet's band on its corbels (the walk's last cell over the ring). */
    static boolean innerSolid(CityPlan p, Geo g, int ry) {
        return g.lin() == 1 && ry >= top(p) - 2 && ry <= top(p) && !pipe(p, g);
    }

    // ---- materials -----------------------------------------------------------------------------------------------

    static final BlockState TUFF = of("minecraft:tuff");
    static final BlockState COBBLED_DEEPSLATE = of("minecraft:cobbled_deepslate");
    static final BlockState STONE_BRICK_SLAB = of("minecraft:stone_brick_slab[type=bottom]");
    static final BlockState ANDESITE_SLAB = of("minecraft:polished_andesite_slab[type=bottom]");
    static final BlockState SPRUCE_PLANKS_ = of("minecraft:spruce_planks");
    static final BlockState SPRUCE_SLAB_TOP = of("minecraft:spruce_slab[type=top]");
    static final BlockState[] SPRUCE_LOG = {of("minecraft:stripped_spruce_log[axis=z]"), of("minecraft:stripped_spruce_log[axis=x]")};
    static final BlockState[] IRON_GRATE = dirs("minecraft:iron_trapdoor[facing=%s,half=top,open=false]");
    static final BlockState[] SPRUCE_SHUTTER = dirs("minecraft:spruce_trapdoor[facing=%s,half=bottom,open=true]");
    static final BlockState ANVIL = of("minecraft:anvil[facing=north]");
    static final BlockState CRAFTING = of("minecraft:crafting_table");
    static final BlockState SMITHING = of("minecraft:smithing_table");
    static final BlockState IRON_BARS_POST = of("minecraft:iron_bars");
    static final BlockState[] CANDLES = {of("minecraft:candle[candles=3,lit=true]"), of("minecraft:white_candle[candles=2,lit=true]"),
            of("minecraft:yellow_candle[candles=4,lit=true]")};

    /** Stairs in all five shapes for one half, indexed by the side they face (their tall part's side). */
    record Stairs(BlockState[] straight, BlockState[] innerLeft, BlockState[] innerRight, BlockState[] outerLeft, BlockState[] outerRight) {
        static Stairs of(String id, String half) {
            String s = id + "[facing=%s,half=" + half + ",shape=";
            return new Stairs(dirs(s + "straight]"), dirs(s + "inner_left]"), dirs(s + "inner_right]"), dirs(s + "outer_left]"), dirs(s + "outer_right]"));
        }
    }

    static final Stairs COURSE = Stairs.of("minecraft:stone_brick_stairs", "top");
    static final Stairs COURSE_MOSSY = Stairs.of("minecraft:mossy_stone_brick_stairs", "top");
    static final Stairs CAP = Stairs.of("minecraft:stone_brick_stairs", "bottom");
    static final Stairs CAP_MOSSY = Stairs.of("minecraft:mossy_stone_brick_stairs", "bottom");
    static final Stairs CAP_COBBLE = Stairs.of("minecraft:mossy_cobblestone_stairs", "bottom");
    static final Stairs CORBEL_ANDESITE = Stairs.of("minecraft:polished_andesite_stairs", "top");
    static final Stairs SPRUCE_STRUT = Stairs.of("minecraft:spruce_stairs", "top");
    /** The kit's slate for the roofed walks (bottom half: the roof's slope). */
    static final Stairs SLATE_ROOF = Stairs.of("awaken:slate_tile_stairs", "bottom");
    static final Stairs SLATE_EAVE = Stairs.of("awaken:slate_tile_stairs", "top");

    /**
     * A stair shaped by its solid neighbours at the same row, as vanilla would join it: straight toward one, an inner
     * corner toward two beside each other, an outer corner round a solid diagonal; a full block when it is boxed in from
     * opposite sides; null when nothing is beside it.
     */
    static BlockState shaped(Ctx c, Stairs st, BlockState full, int x, int ry, int z) {
        return shaped((xx, yy, zz) -> solidAt(c, xx, yy, zz), st, full, x, ry, z);
    }

    /** What counts as solid beside a shaped stair. */
    interface Solid {
        boolean at(int x, int ry, int z);
    }

    static BlockState shaped(Solid solid, Stairs st, BlockState full, int x, int ry, int z) {
        boolean[] s = new boolean[4];
        int n = 0;
        for (int d = 0; d < 4; d++) {
            s[d] = solid.at(x + CityPainter.NB[d][0], ry, z + CityPainter.NB[d][1]);
            if (s[d]) n++;
        }
        if (n == 1) {
            for (int d = 0; d < 4; d++) if (s[d]) return st.straight()[d];
        }
        if (n == 2) {
            for (int d = 0; d < 4; d++) {
                int cw = (d + 1) & 3;
                if (s[d] && s[cw]) return st.innerRight()[d]; // the second side is clockwise of the first
            }
            return full; // opposite sides
        }
        if (n >= 3) return full;
        for (int d = 0; d < 4; d++) {
            int cw = (d + 1) & 3;
            int dx = CityPainter.NB[d][0] + CityPainter.NB[cw][0], dz = CityPainter.NB[d][1] + CityPainter.NB[cw][1];
            if (solid.at(x + dx, ry, z + dz)) return st.outerRight()[d];
        }
        return null;
    }

    /** How mossy the stone is: wet at the foot, on the north faces and on the sheltered inner face. */
    static int moss(Geo g, int ry, boolean inner) {
        int m = ry < 3 ? 40 : ry < 8 ? 22 : ry < 16 ? 9 : ry < 30 ? 4 : 3;
        int o = inner ? g.in() : g.out();
        if (o == N) m = m * 17 / 10;
        else if (o == S) m = m * 6 / 10;
        if (inner) m += 4;
        return switch (BEHIND[g.k]) {
            case GARDEN_D, QUAY_D -> m * 3 / 2;
            case TERRACE -> m * 2 / 3;
            default -> m;
        };
    }

    /** A repair patch in a newer or rougher stone: blobs on a coarse grid along the side, by district. -1 for none. */
    static int patch(Geo g, int ry, boolean inner) {
        int pu = Math.floorDiv(g.u, 7), pr = Math.floorDiv(ry, 6);
        int salt = inner ? 83 : 77;
        if (Ctx.shape(g.k * 64 + pu, pr, salt) >= (BEHIND[g.k] == BELLWATCH || BEHIND[g.k] == WORKS ? 20 : 13)) return -1;
        double cu = pu * 7 + 1.5 + Ctx.shape(pu, pr + g.k, salt + 1) % 4, cr = pr * 6 + 2 + Ctx.shape(pu, pr, salt + 2) % 3;
        double ru = 2.2 + Ctx.shape(pu, pr, salt + 3) % 3 * 0.6, rr = 1.6 + Ctx.shape(pu, pr, salt + 4) % 3 * 0.5;
        double du = (g.u + 0.5 - cu) / ru, dr = (ry + 0.5 - cr) / rr;
        if (du * du + dr * dr > 1) return -1;
        return Ctx.shape(g.k * 64 + pu, pr, salt + 5) % 3;
    }

    /** The curtain's stone at a cell. */
    static BlockState stone(Ctx c, Geo g, int x, int ry, int z, boolean inner) {
        if (ry == 6 || ry == 32) return POLISHED_ANDESITE; // the plinth course over the talus and the band under the corbels
        int h = c.pct(x, z, 400 + ry);
        int pt = patch(g, ry, inner);
        int dist = BEHIND[g.k];
        if (pt == 0) return h < 75 ? TUFF_BRICKS : TUFF;
        if (pt == 1) return h < 60 ? COBBLE : MOSSY_COBBLE;
        if (pt == 2) return dist == WORKS ? (h < 70 ? DEEPSLATE_BRICKS : COBBLED_DEEPSLATE) : ASHLAR;
        if (h < moss(g, ry, inner)) return MOSSY_STONE_BRICKS;
        if (ry <= 2 && !inner) return h < 50 ? COBBLE : STONE_BRICKS;
        int h2 = c.pct(x, z, 500 + ry);
        if (dist == WORKS && ry > 24 && h2 < 18) return TUFF_BRICKS; // soot from the stacks
        return h2 < 44 ? STONE_BRICKS : h2 < 86 ? ASHLAR : h2 < 95 ? CRACKED_STONE_BRICKS : ANDESITE;
    }

    // ---- the curtain's columns ------------------------------------------------------------------------------------

    /** A cell's outer neighbour side if it shows on the outer face at this row (its outside neighbour is open), else -1. */
    static int outerFace(Ctx c, int x, int ry, int z) {
        CityPlan p = c.plan;
        for (int d = 0; d < 4; d++) {
            int nx = x + CityPainter.NB[d][0], nz = z + CityPainter.NB[d][1];
            if (p.kind(nx, nz) == VOID && !outerSolid(p, geo(p, nx, nz), ry)) return d;
        }
        return -1;
    }

    /** The wall's columns (plan kind WALL). */
    static void curtain(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        Geo g = geo(p, x, z);
        int top = top(p), wo = g.wo();
        b.plate(x, z);
        b.column(x, -4, -1, z, STONE);
        boolean clear = bayClear(p, g);
        boolean inner = wo >= 6;
        boolean spoutCol = Math.floorMod(g.u, 9) == 5 && !roofed(g.k);
        for (int ry = 0; ry < top; ry++) {
            if (!wallSolid(c, g, x, ry, z)) {
                b.set(x, ry, z, AIR);
                continue;
            }
            BlockState s = stone(c, g, x, ry, z, inner);
            if (wo <= 1) {
                int of = (ry == 12 || ry == 24 || (ry >= 18 && ry <= 19) || (ry >= 28 && ry <= 29) || ry >= 20 && ry <= 31) ? outerFace(c, x, ry, z) : -1;
                if (of >= 0) {
                    int mu = Math.floorMod(g.u, 8);
                    if ((ry == 12 || ry == 24) && Math.floorMod(g.u, 4) == 2 && mu != 4) {
                        b.set(x, ry, z, AIR); // a putlog hole (its floor a hold in the odd row below)
                        continue;
                    }
                    if (mu == 4 && (ry == 18 || ry == 19 || ry == 28 || ry == 29)) {
                        s = loop(g, ry == 18 || ry == 28 ? 1 : 2, of, Math.floorMod(g.u, 16) == 4 ? 1 : 0);
                    } else if (spoutCol && ry >= 20 && c.pct(x, z, 610 + ry) < 70 + ry) s = MOSSY_STONE_BRICKS; // the spout's streak
                }
            }
            b.set(x, ry, z, s);
        }
        // panel backs: putlog holes in the recess at rows 14 and 22 (holds at 13 and 21)
        if (wo == 8 && clear && u7(g) >= 2 && Math.floorMod(g.u, 4) == 2) {
            b.set(x, 14, z, AIR);
            b.set(x, 22, z, AIR);
        }
        // the walk (open over the grand stair's last flight)
        b.set(x, top, z, wallSolid(c, g, x, top, z) ? walk(c, g, x, z) : AIR);
        b.column(x, top + 1, CityPainter.CLEAR_TOP, z, AIR);
        if (stairSide(g.k) && wo >= 7) stairColumn(c, b, g, x, z);
        else if (wo >= 7 && clear) bayContents(c, b, g, x, z);
        if (wo == 9 && clear) panelLife(c, b, g, x, z);
        walkLife(c, b, g, x, z);
        if (roofed(g.k)) roof(c, b, g, x, z);
    }

    static final String[] LOOP_STYLE = {"slit", "cross"};

    static BlockState loop(Geo g, int part, int facing, int style) {
        return st("awaken:arrow_loop[facing=" + DIR[facing] + ",style=" + LOOP_STYLE[style] + ",stone=stone_bricks,part="
                + (part == 1 ? "lower" : part == 2 ? "upper" : "single") + "]");
    }

    /** The wall walk's paving: dressed edges, a worn middle, moss in the corners by the parapets. */
    static BlockState walk(Ctx c, Geo g, int x, int z) {
        int wo = g.wo(), h = c.pct(x, z, 700);
        if (wo == 0 || wo == 9) return h < 22 ? MOSSY_STONE_BRICKS : POLISHED_ANDESITE;
        if (wo == 4 || wo == 5) return h < 55 ? ANDESITE : h < 75 ? STONE_BRICKS : CRACKED_STONE_BRICKS;
        return h < 50 ? STONE_BRICKS : h < 70 ? ASHLAR : h < 82 ? CRACKED_STONE_BRICKS : h < 92 ? ANDESITE : COBBLE;
    }

    /** On the walk: braziers by the outer parapet, crates of bolts, a lantern at the stair heads. */
    static void walkLife(Ctx c, CityBuffer b, Geo g, int x, int z) {
        int top = top(c.plan), wo = g.wo();
        if (roofed(g.k)) return;
        int m = Math.floorMod(g.u + 12, 24);
        if (wo == 1 && m == 0 && Math.abs(g.a) < CLEAR[g.k] - 2 && !(gateSide(g.k) && Math.abs(g.a) < 19)) b.set(x, top + 1, z, BRAZIER_FLOOR);
        else if (wo == 1 && (m == 2 || m == 3) && c.pct(x, z, 710) < 60) b.set(x, top + 1, z, CityProps.CRATES[0]);
    }

    static final BlockState BRAZIER_FLOOR = of("awaken:brazier[facing=north,mount=floor]");

    // ---- the inner face: the arcade, the panels, the stair --------------------------------------------------------

    /** Arch shoulders and keystone over a bay (stairs facing the pier), its floor, and what it holds. */
    static void bayContents(Ctx c, CityBuffer b, Geo g, int x, int z) {
        int m = u7(g), wo = g.wo(), in = g.in(), along = ALONG[g.k];
        if (m < 2) return;
        b.set(x, 0, z, wo == 9 ? POLISHED_ANDESITE : c.pct(x, z, 720) < 35 ? COBBLE : STONE_BRICKS);
        // the head: upside-down stairs whose tall half leans on the pier side
        if (m == 2) b.set(x, 6, z, COURSE.straight()[(along + 2) & 3]);
        if (m == 6) b.set(x, 6, z, COURSE.straight()[along]);
        if (m == 3) b.set(x, 7, z, COURSE.straight()[(along + 2) & 3]);
        if (m == 5) b.set(x, 7, z, COURSE.straight()[along]);
        if (m == 4 && wo == 9) b.set(x, 8, z, CHISELED_STONE_BRICKS);
        int kind = bayKind(g), bi = bay(g) + 1000; // (kept positive: it picks from tables)
        int h = Ctx.shape(bi, g.k, 93 + wo * 7 + m);
        boolean back = wo == 7, mid = wo == 8, front = wo == 9;
        switch (kind) {
            case B_STORE, B_STORE2 -> {
                if (back || mid && h < 60) {
                    // (stacks one high or three, never two: a two-high stack is a hold in an even row)
                    b.set(x, 1, z, h < 40 ? CityProps.CRATES[h % CityProps.CRATES.length] : h < 70 ? CityProps.SACKS[h % 3][h % 3] : CityProps.CASK[0]);
                    if (back && h % 4 == 0) b.column(x, 2, 3, z, CityProps.CRATES[(h / 3) % CityProps.CRATES.length]);
                }
            }
            case B_STABLE -> {
                if (back) b.column(x, 1, m == 4 ? 1 : 3, z, HAY);
                else if (mid && (m == 3 || m == 5)) b.set(x, 1, z, HAY);
            }
            case B_WORKSHOP -> {
                if (back) b.set(x, 1, z, m == 3 ? CRAFTING : m == 5 ? CityProps.GRINDSTONE : CityProps.LOGS[g.out() % 2 == 0 ? 3 : 2]);

            }
            case B_STALL -> {
                if (front && m >= 3 && m <= 5) {
                    b.set(x, 1, z, CityProps.COUNTER[in][1]);
                    b.set(x, 2, z, CityProps.WARES[(bi * 3 + m) % CityProps.WARES.length]);
                }
                if (back) b.set(x, 1, z, m % 2 == 0 ? CityProps.CRATES[(bi + m) % CityProps.CRATES.length] : CityProps.SACKS[1][bi % 3]);
            }
            case B_SMITHY -> {
                if (back) b.set(x, 1, z, m == 3 ? SMITHING : m == 4 ? CityProps.COAL : CityProps.RAW_IRON);
                if (mid && m == 4) b.set(x, 1, z, ANVIL);
                if (back && m == 4) b.set(x, 7, z, LANTERN_HANGING);
            }
            case B_SHRINE -> {
                if (back && m == 4) {
                    b.set(x, 1, z, CHISELED_STONE_BRICKS);
                    b.set(x, 2, z, CANDLES[bi % CANDLES.length]);
                    b.set(x, 3, z, st("awaken:wall_shield[facing=" + DIR[in] + ",arms=carillon]"));
                }
                if (back && (m == 3 || m == 5)) b.set(x, 1, z, FLOWERS[(bi + m) % FLOWERS.length]);
            }
            case B_WOOD -> {
                if (back || mid) {
                    BlockState log = CityProps.LOGS[ALONG[g.k] % 2 == 1 ? 2 : 3];
                    b.column(x, 1, back ? 3 : 1, z, log);
                }
            }
            case B_BENCH -> {
                if (back && m == 4) b.set(x, 1, z, CityProps.BENCH[in]);
                if (back && (m == 2 || m == 6)) b.set(x, 1, z, CityProps.PLANTER[1][bi % CityProps.BLOOM.length]);
            }
            case B_LAUNDRY -> {
                if (front && m >= 2) b.set(x, 4, z, CityProps.LINE[along % 2 == 1 ? 0 : 1][1][bi % 4][m == 4 ? 2 : m == 3 || m == 5 ? 1 : 0]);
                if (back && m == 4) b.set(x, 1, z, CityProps.SACKS[0][2]);
            }
            default -> {
            }
        }
    }

    /** The recessed panels: ivy climbing from the bays in patches (the climb), arch shoulders at the panel's head. */
    static void panelLife(Ctx c, CityBuffer b, Geo g, int x, int z) {
        int m = u7(g), along = ALONG[g.k], in = g.in();
        if (m < 2) return;
        if (m == 2) b.set(x, 30, z, COURSE.straight()[(along + 2) & 3]);
        if (m == 6) b.set(x, 30, z, COURSE.straight()[along]);
        if (m == 3) b.set(x, 31, z, COURSE.straight()[(along + 2) & 3]);
        if (m == 5) b.set(x, 31, z, COURSE.straight()[along]);
        int ivyTop = ivyTop(g, true);
        for (int ry = 10; ry <= Math.min(29, ivyTop); ry++) {
            int dens = ivyDensity(g, ry, ivyTop, c.pct(x, z, 730 + ry));
            if (dens >= 0) b.set(x, ry, z, ivy(in, dens, g));
        }
    }

    /** How high a patch of ivy reaches at this column (0 for none): patches every ~9 along, seeded by the plan. */
    static int ivyTop(Geo g, boolean inner) {
        int pu = Math.floorDiv(g.u + 3, 9);
        int salt = inner ? 120 : 130;
        int chance = inner ? 55 : g.out() == N ? 45 : BEHIND[g.k] == GARDEN_D ? 40 : 12;
        if (Ctx.shape(g.k * 64 + pu, 0, salt) >= chance) return 0;
        double centre = pu * 9 - 3 + 4.5 + (Ctx.shape(pu, g.k, salt + 1) % 5 - 2);
        double half = 1.8 + Ctx.shape(pu, g.k, salt + 2) % 4 * 0.7;
        double off = Math.abs(g.u + 0.5 - centre) / half;
        if (off > 1.25) return 0;
        int full = 12 + Ctx.shape(pu, g.k, salt + 3) % 18;
        return (int) (full * (1 - 0.55 * off * off));
    }

    /** Ivy density in a patch: thin at its top and its edges (2 dense, 1 patchy, 0 tendrils, -1 none). */
    static int ivyDensity(Geo g, int ry, int topRow, int h) {
        int fromTop = topRow - ry;
        if (fromTop < 0) return -1;
        if (fromTop == 0) return h < 50 ? 0 : -1;
        if (fromTop <= 2) return h < 30 ? 0 : 1;
        return h < 12 ? 1 : 2;
    }

    static BlockState ivy(int facing, int density, Geo g) {
        String tone = BEHIND[g.k] == BELLWATCH ? "dark" : g.k % 7 == 3 ? "autumn" : "green";
        return st("awaken:wall_ivy[facing=" + DIR[facing] + ",density=" + density + ",tone=" + tone + "]");
    }

    /** The grand stair (west side): treads two wide behind a parapet, under arched openings toward the town. */
    static void stairColumn(Ctx c, CityBuffer b, Geo g, int x, int z) {
        int wo = g.wo(), s = stairY(g);
        int up = ALONG[g.k];
        if (g.u >= STAIR_FROM - 3 && g.u < STAIR_FROM) {
            b.set(x, 0, z, POLISHED_ANDESITE);
            if (wo == 9 && g.u == STAIR_FROM - 2) b.set(x, 4, z, st("awaken:wall_lantern[facing=" + DIR[g.in()] + "]"));
            return;
        }
        if (s < 0) return;
        if (wo == 9) {
            b.set(x, s + 1, z, STONE_WALLS[(up == N || up == S) ? 5 : 10]);
            if (Math.floorMod(g.u, 8) == 4) b.set(x, s + 2, z, LANTERN);
            return;
        }
        b.set(x, s, z, CAP.straight()[up]);
        if (Math.floorMod(g.u, 4) == 0) b.set(x, s + 4, z, COURSE.straight()[g.in()]);
    }

    // ---- outside the outer face -----------------------------------------------------------------------------------

    /** The cells just outside the wall (plan VOID within 4 of the outer face): talus, buttresses, courses, the parapet. */
    static void outerBand(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        Geo g = geo(p, x, z);
        int l = g.lout();
        if (l < 1 || l > 4) return;
        int in = g.in(), top = top(p);
        boolean butt = buttress(p, g);
        // the talus: three steps of rough stone, their caps stairs leaning on the wall
        for (int ry = 0; ry <= 5; ry++) {
            if (!outerSolid(p, g, ry)) break;
            boolean capRow = !outerSolid(p, g, ry + 1);
            BlockState fill = ry <= 1 ? (c.pct(x, z, 800 + ry) < 55 ? MOSSY_COBBLE : COBBLE) : stone(c, g, x, ry, z, false);
            if (capRow) {
                Stairs st = ry <= 1 ? CAP_COBBLE : ry <= 3 ? CAP_MOSSY : CAP;
                BlockState cap = shaped(c, st, fill, x, ry, z);
                b.set(x, ry, z, cap != null ? cap : st.straight()[in]);
            } else b.set(x, ry, z, fill);
        }
        if (butt) {
            int to = l <= 2 ? (l == 2 ? 13 : 33) : -1;
            for (int ry = 6; ry <= to; ry++) {
                boolean capRow = ry == to;
                if (capRow) {
                    BlockState cap = shaped(c, CAP, STONE_BRICKS, x, ry, z);
                    b.set(x, ry, z, cap != null ? cap : CAP.straight()[in]);
                } else b.set(x, ry, z, (ry - 6) % 4 == 3 ? ASHLAR : stone(c, g, x, ry, z, false));
            }
        }
        // string courses at 15 and 27, wrapping round the buttresses
        for (int ry : COURSE_ROWS) {
            if (outerSolid(p, g, ry)) continue;
            BlockState lip = shaped(c, ry < 20 ? COURSE_MOSSY : COURSE, STONE_BRICKS, x, ry, z);
            if (lip != null) b.set(x, ry, z, lip);
        }
        // ivy on the sheltered faces, from the talus up
        if (l == 1 && !butt) {
            int it = ivyTop(g, false);
            for (int ry = 6; ry <= it + 4; ry++) {
                if (ry == 15 || ry == 27) continue;
                int dens = ivyDensity(g, ry, it + 4, c.pct(x, z, 830 + ry));
                if (dens >= 0 && b.get(x, ry, z) == AIR) b.set(x, ry, z, ivy(g.out(), dens, g));
            }
        }
        if (roofed(g.k)) hoarding(c, b, g, x, z);
        else parapet(c, b, g, x, z, butt);
    }

    static final int[] COURSE_ROWS = {15, 27};

    /** Machicolations: corbels in two tiers carrying a parapet two deep, slots between the corbels, merlons and loops. */
    static void parapet(Ctx c, CityBuffer b, Geo g, int x, int z, boolean butt) {
        int l = g.lout(), in = g.in(), top = top(c.plan);
        if (l > 2) return;
        int m3 = Math.floorMod(g.u, 3);
        boolean corb = m3 != 2, merlon = m3 != 2;
        if (l == 1) {
            if (corb || butt) b.set(x, 34, z, butt ? stone(c, g, x, 34, z, false) : CORBEL_ANDESITE.straight()[in]);
            for (int ry = 35; ry <= 38; ry++) b.set(x, ry, z, corb || butt ? stone(c, g, x, ry, z, false) : AIR);
            if (!corb && !butt && Math.floorMod(g.u, 9) == 5) b.set(x, 33, z, st("awaken:wall_spout[facing=" + DIR[g.out()] + "]"));
        } else {
            if (corb) b.set(x, 35, z, CORBEL_ANDESITE.straight()[in]);
            for (int ry = 36; ry <= 38; ry++) b.set(x, ry, z, ry == 36 ? POLISHED_ANDESITE : ASHLAR);
        }
        // a breastwork one high (you see out over it), merlons three high and two deep, loops in the outer row; the
        // crenels' sills are walls (a "no": the walk's outer edge can't be jumped)
        b.set(x, top, z, l == 1 ? POLISHED_ANDESITE : ASHLAR);
        b.set(x, top + 1, z, stone(c, g, x, top + 1, z, false));
        if (merlon) {
            boolean loopCell = l == 2 && Math.floorMod(g.u, 6) == 0;
            b.set(x, top + 2, z, stone(c, g, x, top + 2, z, false));
            b.set(x, top + 3, z, loopCell ? loop(g, 1, g.out(), 0) : stone(c, g, x, top + 3, z, false));
            b.set(x, top + 4, z, loopCell ? loop(g, 2, g.out(), 0) : stone(c, g, x, top + 4, z, false));
            b.set(x, top + 5, z, ANDESITE_SLAB);
        } else if (l == 2) b.set(x, top + 2, z, STONE_WALLS[(ALONG[g.k] == N || ALONG[g.k] == S) ? 5 : 10]);
    }

    /**
     * A timber hoarding on beams (the roofed sides): struts and beams out of the wall at every third cell, a plank floor
     * level with the walk with slots, a timber front with shuttered openings, and the roof's outer slope over it.
     */
    static void hoarding(Ctx c, CityBuffer b, Geo g, int x, int z) {
        int l = g.lout(), in = g.in(), top = top(c.plan);
        if (l > 3) return;
        boolean beam = Math.floorMod(g.u, 3) == 0;
        int axis = (g.out() == E || g.out() == W) ? 1 : 0;
        if (l == 1 && beam) b.set(x, top - 2, z, SPRUCE_STRUT.straight()[in]);
        if (beam) b.set(x, top - 1, z, SPRUCE_LOG[axis]);
        b.set(x, top, z, l == 2 && !beam && Math.floorMod(g.u, 3) == 2 ? IRON_GRATE[in] : SPRUCE_PLANKS_);
        if (l == 3) {
            for (int ry = top + 1; ry <= top + 3; ry++) {
                BlockState s = beam ? T_POST[0] : SPRUCE_PLANKS_;
                if (!beam && ry == top + 2 && Math.floorMod(g.u, 6) == 2) s = SPRUCE_SHUTTER[g.out()];
                b.set(x, ry, z, s);
            }
        }
        roofCell(b, g, x, z, top);
    }

    // ---- the roofed walk ------------------------------------------------------------------------------------------

    /** The roof's height at a layer: the eave over the hoarding (layer -3) at top + 4, the ridge over the walk's middle. */
    static int roofY(int top, int layer) {
        // layer: -3..-1 the hoarding, 0..9 the walk, 10 the inner band
        int fromOut = layer + 3, fromIn = 10 - layer;
        return top + 4 + Math.min(fromOut, fromIn + 2) - 0;
    }

    static int layer(Geo g) {
        if (g.d < -5) return -g.lout();
        if (g.d >= 5) return 9 + g.lin();
        return g.wo();
    }

    static void roofCell(CityBuffer b, Geo g, int x, int z, int top) {
        int ly = layer(g);
        if (ly < -3 || ly > 10) return;
        int y = roofY(top, ly), yo = roofY(top, ly - 1), yi = roofY(top, ly + 1);
        if (ly == 4 || ly == 5) {
            b.set(x, y, z, SLATE_KIT.block());
            b.set(x, y + 1, z, CAP_SMOOTH);
            return;
        }
        boolean outerSlope = y > yo || ly == -3; // rising inward: the tall half toward the inside
        int face = outerSlope ? g.in() : g.out();
        Stairs st = (ly == -3 || ly == 10) ? SLATE_EAVE : SLATE_ROOF;
        b.set(x, y, z, st.straight()[face]);
    }

    /** Over the walk on a roofed side: the roof, posts on the inner parapet, lanterns under the ridge. */
    static void roof(Ctx c, CityBuffer b, Geo g, int x, int z) {
        int top = top(c.plan);
        roofCell(b, g, x, z, top);
        if ((g.wo() == 4) && Math.floorMod(g.u, 8) == 4) b.set(x, roofY(top, 4) - 1, z, LANTERN_HANGING);
    }

    // ---- the ring side --------------------------------------------------------------------------------------------

    static final BlockState[] WALL_LANTERN = dirs("awaken:wall_lantern[facing=%s]");

    /** The ring's cells in front of the inner face (plan RING within 3 of it): the course, the parapet's band, pipes. */
    static void innerBand(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        Geo g = geo(p, x, z);
        int l = g.lin();
        if (l < 1 || l > 3) return;
        // wall pieces face away from the wall (into the town: `in`); stairs face the wall (`out`)
        int top = top(p), out = g.out(), in = g.in(), m = u7(g), bi = bay(g);
        boolean clear = bayClear(p, g);
        if (l == 1) {
            boolean pipe = pipe(p, g);
            if (pipe) {
                for (int ry = 1; ry <= top; ry++) b.set(x, ry, z, ry == 1 ? PIPE_SHOE[in] : ry == top ? PIPE_HEAD[in] : PIPE[in]);
            } else {
                BlockState lip = shaped(c, COURSE, STONE_BRICKS, x, 9, z);
                if (lip != null) b.set(x, 9, z, lip);
                // the corbel table and the parapet's band over the ring
                if (Math.floorMod(g.u, 2) == 0) b.set(x, top - 3, z, COURSE.straight()[out]);
                for (int ry = top - 2; ry <= top; ry++) b.set(x, ry, z, ry == top - 2 ? POLISHED_ANDESITE : ASHLAR);
            }
            // the inner parapet: a "no" (a fall into the ring), open where the pipe arrives; lanterns on posts every 16
            if (!pipe) {
                boolean lamp = Math.floorMod(g.u, 16) == 8 && !roofed(g.k);
                boolean post = roofed(g.k) && Math.floorMod(g.u, 4) == 0;
                if (post) {
                    b.column(x, top + 1, roofY(top, 10) - 1, z, T_POST[0]);
                } else {
                    b.set(x, top + 1, z, lamp ? WALL_POST : STONE_WALLS[parapetMask(c, x, z)]);
                    if (lamp) b.set(x, top + 2, z, LANTERN);
                }
            }
            if (roofed(g.k)) roofCell(b, g, x, z, top);
            if (!clear || stairSide(g.k)) return;
            BlockState here4 = b.get(x, 4, z);
            // the life at the foot: awnings over the stalls and workshops (a hood at row 3: a hold, and a soft landing)
            int kind = bayKind(g);
            if (m >= 2 && !pipe && (kind == B_STALL || kind == B_WORKSHOP || kind == B_SMITHY)) {
                int colour = Ctx.shape(bi, g.k, 95) % 6;
                b.set(x, 3, z, CityProps.AWNING_KIT[in][colour][0]);
            }
            if (m == 1 && !pipe && (here4 == null || here4.isAir())) b.set(x, 4, z, WALL_LANTERN[in]);
            // ivy up the pilasters, where a patch reaches them
            if (m < 2 && !pipe) {
                int it = ivyTop(g, true);
                for (int ry = 10; ry <= Math.min(30, it); ry++) {
                    int dens = ivyDensity(g, ry, it, c.pct(x, z, 840 + ry));
                    if (dens >= 0) b.set(x, ry, z, ivy(in, dens, g));
                }
            }
            // long banners on every third pilaster, a flagpole with a banner on every fourth
            if (m == 1 && !pipe && Math.floorMod(bi, 3) == 0) {
                int design = designOf(BEHIND[g.k]);
                for (int s = 0; s < 2; s++) {
                    int t = 27 - s * 3;
                    b.set(x, t, z, CityFacadeLife.BANNER[in][design][0][0]);
                    b.set(x, t - 1, z, CityFacadeLife.BANNER[in][design][1][0]);
                    b.set(x, t - 2, z, CityFacadeLife.BANNER[in][design][2][0]);
                }
            } else if (m == 1 && !pipe && Math.floorMod(bi, 4) == 2) b.set(x, 13, z, CityFacadeLife.FLAGPOLE[in]);
            return;
        }
        if (l == 2 && clear && !stairSide(g.k) && m == 1 && Math.floorMod(bay(g), 3) != 0 && Math.floorMod(bay(g), 4) == 2) {
            int design = designOf(BEHIND[g.k]);
            b.set(x, 12, z, CityFacadeLife.BANNER[in][design][0][1]);
            b.set(x, 11, z, CityFacadeLife.BANNER[in][design][1][1]);
            b.set(x, 10, z, CityFacadeLife.BANNER[in][design][2][1]);
        }
    }

    /** The banner design of a district (banner_cloth's designs: bellwatch quay market terrace works garden wallring). */
    static int designOf(int district) {
        return switch (district) {
            case BELLWATCH -> 0;
            case QUAY_D -> 1;
            case TERRACE -> 3;
            case WORKS -> 4;
            case GARDEN_D -> 5;
            default -> 6;
        };
    }

    /** The inner parapet's connections: toward neighbouring parapet cells. */
    static int parapetMask(Ctx c, int x, int z) {
        int mask = 0;
        for (int d = 0; d < 4; d++) {
            int nx = x + CityPainter.NB[d][0], nz = z + CityPainter.NB[d][1];
            byte k = c.plan.kind(nx, nz);
            if (k == RING) {
                Geo n = geo(c.plan, nx, nz);
                if (n.lin() == 1 && !pipe(c.plan, n)) mask |= 1 << d;
            } else if (k == LANDMARK) mask |= 1 << d;
        }
        return mask;
    }

    // ---- over the river ---------------------------------------------------------------------------------------------

    /**
     * The wall over the river (plan WATERGATE): a deep arch open to row 9 with a dressed rim, the water portcullis
     * half lowered behind the outer face, masonry to the walk, battlements on the outer edge and the inner parapet.
     */
    static void waterGate(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        Geo g = geo(p, x, z);
        int top = top(p), wo = g.wo();
        for (int ry = 10; ry < top; ry++) b.set(x, ry, z, stone(c, g, x, ry, z, wo >= 6));
        b.set(x, 10, z, wo == 0 || wo == 9 ? CHISELED_STONE_BRICKS : POLISHED_ANDESITE);
        b.set(x, top, z, walk(c, g, x, z));
        if (wo == 1) {
            int axis = ALONG[g.k] == E || ALONG[g.k] == W ? 0 : 1;
            b.set(x, 9, z, CityTowers.PORTCULLIS[axis * 3 + 2]);
            for (int ry = 3; ry < 9; ry++) b.set(x, ry, z, CityTowers.PORTCULLIS[axis * 3 + (ry == 3 ? 0 : 1)]);
        }
        if (wo == 0) {
            b.set(x, top + 1, z, stone(c, g, x, top + 1, z, false));
            if (Math.floorMod(g.u, 3) != 2) {
                b.set(x, top + 2, z, stone(c, g, x, top + 2, z, false));
                b.set(x, top + 3, z, stone(c, g, x, top + 3, z, false));
                b.set(x, top + 4, z, ANDESITE_SLAB);
            }
        } else if (wo == 9) {
            boolean lamp = Math.floorMod(g.u, 8) == 4;
            b.set(x, top + 1, z, lamp ? WALL_POST : STONE_WALLS[(ALONG[g.k] == N || ALONG[g.k] == S) ? 5 : 10]);
            if (lamp) b.set(x, top + 2, z, LANTERN);
        }
    }
}
