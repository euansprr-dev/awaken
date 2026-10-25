package dev.awaken.city;

import dev.awaken.city.CityBlocks.Roof;
import dev.awaken.city.CityGrammar.Box;
import dev.awaken.city.CityPainter.Ctx;
import dev.awaken.city.CityPlan.Part;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The wall's towers and gatehouses (Milestone 4, the Walls step; look bible §11 #12). Each tower belongs to the district
 * behind it and wears that district's form, so the ring reads as places from the sky and from the fields:
 * Bellwatch's round drums under tall slate witch-hats with a timber hoarding round the top; the Terrace's square towers
 * with corner bartizans and a slate pyramid; the Garden Ward's octagons with verdigris spires and ivy; the Works'
 * crenellated brick-banded towers with a beacon and a clay roof inside the battlements; the Quay's water-gate towers
 * under copper with a lantern for the boats. The gate towers are D-shaped drums under the gate district's roof.
 *
 * <p>Every tower: a battered base, the wall's courses at rows 15 and 27 round it (ledges), loops on every storey, lit
 * windows in the guard room, the wall walk passing through a hall at the walk's level, and a door from the ring with a
 * newel stair up to that hall. A flag on every roof. Each part paints only its own box and a margin of four, from the
 * plan alone.
 */
final class CityTowers {
    private CityTowers() {}

    static final int MARGIN = 4;

    static final int ROUND = 0, OCT = 1, SQUARE = 2, D_SHAPE = 3;

    /** One tower worked out from its part: its form, centre, radius, district, rows. */
    record T(Part part, Box box, double cx, double cz, double r, int form, int out, int toCity, int doff, int nX, int nZ,
             int district, int eave, boolean gate) {
        /** The cell's distance from the axis in the tower's own metric (circle, octagon or square). */
        double rho(int x, int z) {
            double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
            return switch (form) {
                case ROUND -> Math.sqrt(dx * dx + dz * dz);
                case OCT -> Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) * 0.7071 * 1.08);
                case SQUARE -> Math.max(Math.abs(dx), Math.abs(dz));
                default -> {
                    double o = dx * NBX[out] + dz * NBZ[out];
                    yield o > 0 ? Math.sqrt(dx * dx + dz * dz) : Math.max(Math.abs(dx), Math.abs(dz)) - 0.0;
                }
            };
        }

        /** Inside the shaft at a row (the base flares out by one over its first rows: the batter). */
        boolean inside(int x, int z, int ry) {
            if (ry > eave) return false;
            double flare = ry <= 1 ? 1.0 : ry <= 3 ? 0.5 : 0;
            if (form == D_SHAPE) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                double o = dx * NBX[out] + dz * NBZ[out];
                if (o <= 0) return box.contains(x, z) || flare > 0 && grown(x, z, flare);
                return Math.sqrt(dx * dx + dz * dz) <= r + flare;
            }
            return rho(x, z) <= r + flare;
        }

        private boolean grown(int x, int z, double f) {
            return x >= box.x0() - f && x <= box.x1() + f && z >= box.z0() - f && z <= box.z1() + f;
        }
    }

    static final int[] NBX = {0, 1, 0, -1}, NBZ = {-1, 0, 1, 0};

    static T make(Ctx c, Part p, Box box) {
        double cx = (box.x0() + box.x1() + 1) / 2.0, cz = (box.z0() + box.z1() + 1) / 2.0;
        double ang = Math.toDegrees(Math.atan2(cz, cx));
        if (ang < 0) ang += 360;
        boolean gate = p.kind().equals("gate_tower");
        int district = "3".equals(p.style()) ? QUAY_D : ang < 45 || ang >= 315 ? GARDEN_D : ang < 135 ? WORKS : ang < 225 ? BELLWATCH : TERRACE;
        int out = CityWall.dominant(cx, cz), toCity = (out + 2) & 3;
        int form;
        double r;
        if (gate) {
            // the gate tower's outward half is a drum; which way is out is the gate's, not the tower's own corner
            out = CityWall.dominant(cx, cz);
            int across = (out == N || out == S) ? box.w() : box.d();
            form = D_SHAPE;
            r = across / 2.0 - 0.2;
            toCity = (out + 2) & 3;
        } else {
            form = switch (district) {
                case BELLWATCH -> ROUND;
                case GARDEN_D, QUAY_D -> OCT;
                default -> SQUARE;
            };
            r = form == SQUARE ? box.w() / 2.0 : box.w() / 2.0 - 0.3;
        }
        // the gate towers stand taller than the plan's blockout (their gallery must clear the walk's hall)
        int eave = gate ? p.h() + 4 : p.h() - 6;
        int door = doorSide(c.plan, box, toCity, (int) Math.floor(cx), (int) Math.floor(cz));
        toCity = door & 3;
        int doff = (door >> 2) - 8;
        // the newel stands in toward the town (or to one side) so that the wall walk's middle line passes its stairwell by
        // (the well two and a half clear of the line), with its ring and a shell round it inside the tower and room for
        // the door's corridor; the first place that does all of it, else the one with the most clearance
        int in = CityWall.dominant(-cx, -cz), lat = (in + 1) & 3;
        T best = null;
        double bestScore = -1e9;
        for (int shift = 4; shift >= 0; shift--)
            for (int side : new int[]{0, 2, -2, 3, -3}) {
                int nX = (int) Math.floor(cx) + shift * NBX[in] + side * NBX[lat], nZ = (int) Math.floor(cz) + shift * NBZ[in] + side * NBZ[lat];
                T t = new T(p, box, cx, cz, r, form, out, toCity, doff, nX, nZ, district, eave, gate);
                if (!stairFits(c.plan, t)) continue;
                double clear = CityWall.geo(c.plan, nX, nZ).d();
                if (clear >= 2.5) return t;
                if (clear > bestScore) {
                    bestScore = clear;
                    best = t;
                }
            }
        return best != null ? best : new T(p, box, cx, cz, r, form, out, toCity, doff, (int) Math.floor(cx), (int) Math.floor(cz), district, eave, gate);
    }

    /**
     * Where the door opens: the town side first, then the flanks, on the axis or up to five cells off it, wherever open
     * street-level ground runs three out from the face. Returns side | (offset + 8) << 2.
     */
    static int doorSide(CityPlan p, Box box, int toCity, int ncx, int ncz) {
        int[] order = {toCity, (toCity + 1) & 3, (toCity + 3) & 3};
        int[] offs = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5};
        for (int sd : order)
            for (int off : offs) {
                int ok = 0;
                for (int out = 1; out <= 3; out++) {
                    int x = sd == E ? box.x1() + out : sd == W ? box.x0() - out : ncx + off;
                    int z = sd == S ? box.z1() + out : sd == N ? box.z0() - out : ncz + off;
                    byte k = p.kind(x, z);
                    if (walkable(k) && k != LOWQUAY && !water(k) && p.lift(x, z) <= 1) ok++;
                }
                if (ok == 3) return sd | (off + 8) << 2;
            }
        return toCity | 8 << 2;
    }

    // ---- palettes -------------------------------------------------------------------------------------------------

    static Roof roofOf(int district) {
        return switch (district) {
            case GARDEN_D -> GREEN;
            case WORKS -> CLAY;
            case QUAY_D -> COPPER;
            default -> SLATE_KIT;
        };
    }

    static BlockState masonry(Ctx c, T t, int x, int ry, int z) {
        int h = c.pct(x, z, 900 + ry);
        if (ry == 6 || ry == 15 || ry == 27 || ry == 32) return CityWall.dress(t.district).full(); // the wall's dressed bands run round
        int moss = (ry < 4 ? 40 : ry < 12 ? 18 : 6) * (t.district == GARDEN_D || t.district == QUAY_D ? 3 : 2) / 2;
        if (h < moss) return MOSSY_STONE_BRICKS;
        int h2 = c.pct(x, z, 960 + ry);
        return switch (t.district) {
            case BELLWATCH -> h2 < 40 ? STONE_BRICKS : h2 < 60 ? COBBLE : h2 < 75 ? TUFF_BRICKS : h2 < 88 ? ASHLAR : CRACKED_STONE_BRICKS;
            case WORKS -> ry % 6 == 3 ? BRICKS : h2 < 55 ? STONE_BRICKS : h2 < 75 ? ASHLAR : h2 < 88 ? TUFF_BRICKS : CRACKED_STONE_BRICKS;
            case TERRACE -> h2 < 55 ? ASHLAR : h2 < 90 ? STONE_BRICKS : CRACKED_STONE_BRICKS;
            default -> h2 < 50 ? STONE_BRICKS : h2 < 80 ? ASHLAR : h2 < 92 ? CRACKED_STONE_BRICKS : ANDESITE;
        };
    }

    static final BlockState BRAZIER = of("awaken:brazier[facing=north,mount=floor]");
    static final BlockState[] DOOR_L = dirs("minecraft:dark_oak_door[facing=%s,half=lower,hinge=left,open=false]");
    static final BlockState[] DOOR_U = dirs("minecraft:dark_oak_door[facing=%s,half=upper,hinge=left,open=false]");
    static final BlockState[] LIT_ARCH = {of("awaken:lit_window[style=arched,half=lower,lit=true]"), of("awaken:lit_window[style=arched,half=upper,lit=true]")};
    static final BlockState[] SPRUCE_STAIR_TOP = dirs("minecraft:spruce_stairs[facing=%s,half=top]");
    static final BlockState SPRUCE_PLANKS_ = of("minecraft:spruce_planks");

    static BlockState loop(int facing, int part, int style, String stone) {
        return CityWall.st("awaken:arrow_loop[facing=" + DIR[facing] + ",style=" + (style == 0 ? "slit" : style == 1 ? "cross" : "window")
                + ",stone=" + stone + ",part=" + (part == 1 ? "lower" : part == 2 ? "upper" : "single") + "]");
    }

    // ---- painting -------------------------------------------------------------------------------------------------

    static void tower(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        T t = make(c, p, box);
        boolean foot = t.inside(x, z, 0);
        if (!t.inside(x, z, 4) && box.contains(x, z) && c.plan.kind(x, z) == LANDMARK) {
            // the box's corners beside a drum: the curtain runs on into the tower's flank (no hole in the walk); the
            // batter's flare, where it reaches, is painted over it below
            double d = CityWall.geo(c.plan, x, z).d();
            if (d >= -5 && d < 5 && !approach(t, x, z)) CityWall.curtain(c, b, x, z);
        }
        if (foot) shaft(c, b, t, x, z);
        else banners(b, t, x, z);
        ring(c, b, t, x, z);
        top(c, b, t, x, z);
    }

    /** The masonry shaft from the foot to the eave, its openings, the door and stair, the hall. */
    static void shaft(Ctx c, CityBuffer b, T t, int x, int z) {
        CityPlan p = c.plan;
        int walkTop = CityWall.top(p);
        b.plate(x, z);
        b.column(x, -4, -1, z, STONE);
        int ncx = t.nX, ncz = t.nZ;
        int sx = x - ncx, sz = z - ncz;
        // (the water gates' towers straddle the bend of the wall at the river: no stair, the walk reaches them)
        boolean stairs = t.district != QUAY_D;
        int stair = stairs && Math.abs(sx) <= 1 && Math.abs(sz) <= 1 && !(sx == 0 && sz == 0) ? ringIndex(sx, sz) : -1;
        int start = ringIndex(NBX[t.toCity], NBZ[t.toCity]);
        boolean corridor = stairs && onCorridor(t, x, z, ncx, ncz);
        CityWall.Geo g = CityWall.geo(p, x, z);
        boolean walkBand = g.d() >= -4 && g.d() < 4.5 && !t.gate || t.gate && Math.abs(g.d()) <= 1.5;
        for (int ry = 0; ry <= t.eave; ry++) {
            if (!t.inside(x, z, ry)) continue;
            int face = surface(t, x, z, ry);
            BlockState s = masonry(c, t, x, ry, z);
            // the newel stair: treads climbing clockwise round the newel from the corridor to the hall
            if (stair >= 0 && ry >= 1 && ry <= walkTop + 3) {
                int j = Math.floorMod(stair - start, 8);
                if (ry <= j) {
                    b.set(x, ry, z, s);
                    continue;
                }
                int k = Math.floorMod(ry - 1 - j, 8);
                if (ry - 1 <= walkTop - 1 && k == 0) {
                    int nx = RING[(stair + 1) % 8][0] - sx, nz = RING[(stair + 1) % 8][1] - sz;
                    b.set(x, ry, z, STEP_STONE.straight()[CityWall.dominant(nx, nz)]);
                } else b.set(x, ry, z, AIR);
                continue;
            }
            if (corridor && ry >= 1 && ry <= 2) {
                b.set(x, ry, z, AIR);
                continue;
            }
            // the hall at the walk's level: the interior and the walk's way through the shell
            if (ry > walkTop && ry <= walkTop + 3) {
                if (face < 0 && interior(t, x, z, ry) || walkBand) {
                    b.set(x, ry, z, ry == walkTop + 3 && !(face < 0 && interior(t, x, z, ry)) ? s : AIR);
                    if (ry == walkTop + 3 && sx == 0 && sz == 0) b.set(x, ry, z, LANTERN_HANGING);
                    continue;
                }
            }
            if (face >= 0) s = dress(c, t, x, ry, z, face, s);
            b.set(x, ry, z, s);
        }
        // the door in the shaft's face (above the batter, which opens in front of it), its lintel and a lamp
        if (corridor && surface(t, x, z, 3) == t.toCity) {
            b.set(x, 1, z, DOOR_L[t.toCity]);
            b.set(x, 2, z, DOOR_U[t.toCity]);
            b.set(x, 3, z, CHISELED_STONE_BRICKS);
        }
    }

    static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    static int ringIndex(int sx, int sz) {
        for (int i = 0; i < 8; i++) if (RING[i][0] == sx && RING[i][1] == sz) return i;
        return -1;
    }

    /**
     * The way in from the door to the stair's foot, one wide: straight in on the door's line (the tower's axis, or off it
     * by doff), then across, two short of the newel, to the cell before the stair's first tread.
     */
    static boolean onCorridor(T t, int x, int z, int ncx, int ncz) {
        int dx = x - ncx, dz = z - ncz;
        int ax = NBX[t.toCity], az = NBZ[t.toCity];
        int along = ax != 0 ? dx * ax : dz * az, perp = ax != 0 ? dz : dx;
        int line = ax != 0 ? (int) Math.floor(t.cz) + t.doff - ncz : (int) Math.floor(t.cx) + t.doff - ncx;
        if (perp == line && along >= (line == 0 ? 1 : 2)) return true;
        return along == 2 && perp * (perp - line) <= 0;
    }

    /**
     * A newel stair fits: its ring with a shell round it inside the shaft, and the door's corridor reaching the face (the
     * door at least two out from the newel on its line, three when it is off the newel's line).
     */
    static boolean stairFits(CityPlan p, T t) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) if (!t.inside(t.nX + dx, t.nZ + dz, 3) || !t.inside(t.nX + dx, t.nZ + dz, t.eave - 8)) return false;
        int ax = NBX[t.toCity], az = NBZ[t.toCity];
        int line = ax != 0 ? (int) Math.floor(t.cz) + t.doff - t.nZ : (int) Math.floor(t.cx) + t.doff - t.nX;
        int need = line == 0 ? 2 : 3;
        int x = ax != 0 ? t.nX + need * ax : t.nX + line, z = az != 0 ? t.nZ + need * az : t.nZ + line;
        return t.inside(x, z, 3);
    }

    /** The way to the door across the box's open corner: the door's line, outside the shaft, in front of the face. */
    static boolean approach(T t, int x, int z) {
        int ax = NBX[t.toCity], az = NBZ[t.toCity];
        int line = ax != 0 ? (int) Math.floor(t.cz) + t.doff : (int) Math.floor(t.cx) + t.doff;
        if ((ax != 0 ? z : x) != line) return false;
        int along = ax != 0 ? (x - t.nX) * ax : (z - t.nZ) * az;
        return along >= 1;
    }

    /** Inside by two from the shell (the hall's room). */
    static boolean interior(T t, int x, int z, int ry) {
        for (int d = 0; d < 4; d++) {
            if (!t.inside(x + NBX[d] * 2, z + NBZ[d] * 2, ry)) return false;
        }
        return true;
    }

    /** The side a shaft cell shows to the open air on (its first open neighbour), or -1 inside. */
    static int surface(T t, int x, int z, int ry) {
        for (int d = 0; d < 4; d++) if (!t.inside(x + NBX[d], z + NBZ[d], ry)) return d;
        return -1;
    }

    /**
     * A surface cell's dressing: two-block loops staggered round the shaft on each storey, lit arched windows in the
     * guard room under the eave, quoins on the square towers' corners, ivy on the Garden's lower storeys.
     */
    static BlockState dress(Ctx c, T t, int x, int ry, int z, int face, BlockState s) {
        String stone = t.district == TERRACE ? "stone_bricks" : t.district == BELLWATCH ? "mossy" : "stone_bricks";
        int around = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z + 0.5 - t.cz, x + 0.5 - t.cx)) / 30.0), 12);
        // the guard room's windows (two tall, lit), toward the city and the fields on each axis
        if (ry == t.eave - 6 || ry == t.eave - 5) {
            if (centred(t, x, z, face)) return LIT_ARCH[ry == t.eave - 6 ? 0 : 1];
        }
        int storey = -1;
        if (ry == 12 || ry == 13) storey = 0;
        else if (ry == 20 || ry == 21) storey = 1;
        else if (ry == 28 || ry == 29) storey = 2;
        else if (ry == t.eave - 13 || ry == t.eave - 12) storey = 3;
        if (storey >= 0 && Math.floorMod(around + storey * 3, 4) == 0 && !t.inside(x + NBX[face], z + NBZ[face], ry)
                && c.plan.kind(x + NBX[face], z + NBZ[face]) != WALL) {
            boolean lower = ry == 12 || ry == 20 || ry == 28 || ry == t.eave - 13;
            return loop(face, lower ? 1 : 2, storey == 3 ? 1 : 0, stone);
        }
        if (t.form == SQUARE && quoin(t, x, z, ry) && ry > 6) return t.district == TERRACE ? POLISHED_DIORITE : t.district == WORKS ? BRICKS : ASHLAR;
        return s;
    }

    /** One of the two cells nearest the middle of a face (on each of the four axes). */
    static boolean centred(T t, int x, int z, int face) {
        double dx = x + 0.5 - t.cx, dz = z + 0.5 - t.cz;
        double perp = (face == N || face == S) ? dx : dz;
        return perp >= -1 && perp < 0;
    }

    /** Quoins: the corner stone and one beside it, turning the corner on alternate rows (long and short). */
    static boolean quoin(T t, int x, int z, int ry) {
        double dx = Math.abs(x + 0.5 - t.cx), dz = Math.abs(z + 0.5 - t.cz);
        return ry % 2 == 0 ? dx > t.r - 1 && dz > t.r - 2 : dz > t.r - 1 && dx > t.r - 2;
    }

    /**
     * Three long banners of the district, one over another (nine tall: they read from the fields), on the middle of the
     * tower's field face under its gallery
     * (painted from the cell in front of the face, so each column still paints alone).
     */
    static void banners(CityBuffer b, T t, int x, int z) {
        int fx = x - NBX[t.out], fz = z - NBZ[t.out], top = t.eave - 7;
        if (t.inside(x, z, top) || !t.inside(fx, fz, top) || !centred(t, fx, fz, t.out)) return;
        int design = CityWall.designOf(t.district);
        for (int s = 0; s < 3; s++)
            for (int i = 0; i < 3; i++) b.set(x, top - s * 3 - i, z, CityFacadeLife.BANNER[t.out][design][i][0]);
    }

    /** The courses round the shaft (rows 15 and 27, the wall's ledges), joined by shape. */
    static void ring(Ctx c, CityBuffer b, T t, int x, int z) {
        CityPlan p = c.plan;
        byte k = p.kind(x, z);
        for (int ry : CityWall.COURSE_ROWS) {
            if (t.inside(x, z, ry) || k == WALL || k == WATERGATE || water(k)) continue;
            CityWall.Dress dr = CityWall.dress(t.district);
            BlockState lip = CityWall.shaped((xx, yy, zz) -> t.inside(xx, zz, yy), dr.course(), dr.full(), x, ry, z);
            if (lip != null) b.set(x, ry, z, lip);
        }
    }

    // ---- the top: gallery, roof, bartizans, flag ------------------------------------------------------------------

    static void top(Ctx c, CityBuffer b, T t, int x, int z) {
        int e = t.eave;
        double rho = t.rho(x, z);
        Roof roof = roofOf(t.district);
        if (t.district == WORKS && !t.gate) {
            worksTop(c, b, t, x, z, rho);
            return;
        }
        // the gallery round the top: corbels, then a parapet one out with loops (timber hoarding on Bellwatch's drums)
        boolean shell = t.inside(x, z, e);
        byte under = c.plan.kind(x, z);
        boolean ring1 = !shell && ringCell(t, x, z, 1.25) && under != WALL && under != AVENUE && under != WATERGATE;
        if (ring1) {
            b.set(x, e - 4, z, Math.floorMod(x + z, 2) == 0 ? CityWall.CORBEL_ANDESITE.straight()[towardAxis(t, x, z)] : AIR);
            boolean timber = t.district == BELLWATCH;
            for (int ry = e - 3; ry <= e; ry++) {
                BlockState s = timber ? (Math.floorMod(x + z, 3) == 0 ? T_POST[0] : SPRUCE_PLANKS_) : masonry(c, t, x, ry, z);
                if (ry == e - 1 && Math.floorMod(x * 3 + z, 4) == 0) s = timber ? AIR : loop(awayFromAxis(t, x, z), 0, 0, "stone_bricks");
                b.set(x, ry, z, s);
            }
        }
        // the roof: a cone (round), a spire (octagon) or a pyramid (square), its eave one and a half out
        double r0 = t.r + 1.6;
        if (rho > r0) {
            if (t.district == TERRACE && !t.gate) bartizan(c, b, t, x, z);
            return;
        }
        double slope = switch (t.district) {
            case BELLWATCH -> 2.6;
            case GARDEN_D -> 3.0;
            case QUAY_D -> 1.4;
            default -> t.gate ? 2.2 : 1.8;
        };
        double yt = e + 1 + (r0 - rho) * slope;
        int ytop = (int) Math.floor(yt);
        for (int ry = e + 1; ry <= ytop; ry++) b.set(x, ry, z, roof.block());
        if (rho > r0 - 1.0) b.set(x, e + 1, z, roof.stair()[towardAxis(t, x, z)]); // the eave's kick
        else if (yt - ytop >= 0.5) b.set(x, ytop + 1, z, roof.slab());
        if (t.district == TERRACE && !t.gate) bartizan(c, b, t, x, z);
        // the finial: a mast with a banner at the apex cell; a lantern for the boats on the Quay's
        int ax = (int) Math.floor(t.cx), az = (int) Math.floor(t.cz);
        int apex = e + 1 + (int) Math.floor((r0 - t.rho(ax, az)) * slope);
        if (x == ax && z == az) {
            if (t.district == QUAY_D) {
                b.set(x, apex + 1, z, POLISHED_ANDESITE);
                b.set(x, apex + 2, z, LANTERN);
                b.set(x, apex + 3, z, COPPER_ROD);
            } else {
                for (int ry = apex + 1; ry <= apex + 5; ry++) b.set(x, ry, z, ry == apex + 5 ? CityProps.POLE_TOP : CityProps.POLE);
            }
        }
        int fd = t.out;
        if (t.district != QUAY_D && x == ax + NBX[fd] && z == az + NBZ[fd]) b.set(x, apex + 5, z, CityFacadeLife.FLAGPOLE[fd]);
        if (t.district != QUAY_D && x == ax + 2 * NBX[fd] && z == az + 2 * NBZ[fd]) {
            int design = CityWall.designOf(t.district);
            for (int i = 0; i < 3; i++) b.set(x, apex + 4 - i, z, CityFacadeLife.BANNER[(fd + 1) & 3][design][i][1]);
        }
    }

    /** The cells of the first ring outside the shaft at the eave (within `w` of it). */
    static boolean ringCell(T t, int x, int z, double w) {
        double rho = t.rho(x, z);
        if (t.form == D_SHAPE) {
            boolean any = false;
            for (int d = 0; d < 4; d++) any |= t.inside(x + NBX[d], z + NBZ[d], t.eave);
            return any;
        }
        return rho > t.r && rho <= t.r + w;
    }

    static int towardAxis(T t, int x, int z) {
        return CityWall.dominant(t.cx - (x + 0.5), t.cz - (z + 0.5));
    }

    static int awayFromAxis(T t, int x, int z) {
        return (towardAxis(t, x, z) + 2) & 3;
    }

    /** The Terrace's corner turrets: corbelled out under the eave, a loop each, little verdigris caps and rods. */
    static void bartizan(Ctx c, CityBuffer b, T t, int x, int z) {
        int e = t.eave;
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                double bx = t.cx + sx * (t.r + 0.2), bz = t.cz + sz * (t.r + 0.2);
                double dx = x + 0.5 - bx, dz = z + 0.5 - bz, rr = Math.sqrt(dx * dx + dz * dz);
                if (rr > 2.4) continue;
                int toward = CityWall.dominant(-sx, -sz);
                if (rr <= 1.0) b.set(x, e - 8, z, CityWall.CORBEL_ANDESITE.straight()[CityWall.dominant(t.cx - bx, t.cz - bz)]);
                if (rr <= 1.7) {
                    b.set(x, e - 7, z, CityWall.CORBEL_ANDESITE.straight()[CityWall.dominant(t.cx - bx, t.cz - bz)]);
                    for (int ry = e - 6; ry <= e + 2; ry++) b.set(x, ry, z, ry == e - 2 && rr > 1.0 ? CALCITE : masonry(c, t, x, ry, z));
                }
                double cap = e + 3 + (2.3 - rr) * 2.4;
                if (rr <= 2.3) for (int ry = e + 3; ry <= (int) cap; ry++) b.set(x, ry, z, GREEN.block());
                if (rr < 0.75) b.set(x, (int) cap + 1, z, COPPER_ROD);
                if (rr > 1.0 && rr <= 1.7 && toward >= 0 && Math.floorMod(x + z, 2) == 0) b.set(x, e - 3, z, loop(CityWall.dominant(dx, dz), 0, 0, "calcite"));
            }
    }

    /** The Works' towers: battlements two deep round an open deck, a beacon, and a clay-roofed watch room inside. */
    static void worksTop(Ctx c, CityBuffer b, T t, int x, int z, double rho) {
        int e = t.eave;
        boolean shell = t.inside(x, z, e);
        if (!shell) {
            if (ringCell(t, x, z, 1.0)) {
                b.set(x, e - 4, z, Math.floorMod(x + z, 2) == 0 ? CityWall.CORBEL_ANDESITE.straight()[towardAxis(t, x, z)] : AIR);
                for (int ry = e - 3; ry <= e + 1; ry++) b.set(x, ry, z, ry % 6 == 3 ? BRICKS : masonry(c, t, x, ry, z));
                if (Math.floorMod(x + z, 2) == 0) b.set(x, e + 2, z, masonry(c, t, x, e + 2, z));
            }
            return;
        }
        b.set(x, e, z, POLISHED_ANDESITE);
        double inner = t.r - 2.5;
        if (rho > inner) return; // the open deck round the room
        Box room = new Box((int) Math.ceil(t.cx - inner), (int) Math.ceil(t.cz - inner), (int) Math.floor(t.cx + inner) - 1, (int) Math.floor(t.cz + inner) - 1);
        if (!room.contains(x, z)) return;
        for (int ry = e + 1; ry <= e + 3; ry++) b.set(x, ry, z, room.side(x, z) >= 0 ? (ry == e + 2 && !room.corner(x, z) ? CITY_BRICKS : BRICKS) : AIR);
        CityLandmarks.pyramid(b, room, x, z, e + 4, CLAY, 1);
        if (x == (int) Math.floor(t.cx) + NBX[t.out] * ((int) inner + 1) && z == (int) Math.floor(t.cz) + NBZ[t.out] * ((int) inner + 1))
            b.set(x, e + 1, z, BRAZIER);
    }

    // ---- the gate passage -----------------------------------------------------------------------------------------

    static final BlockState[] PORTCULLIS = new BlockState[6];
    static final BlockState[] GATE_LEAF = dirs("minecraft:dark_oak_trapdoor[facing=%s,half=bottom,open=true]");

    static {
        String[] parts = {"bottom", "middle", "top"};
        for (int a = 0; a < 2; a++)
            for (int i = 0; i < 3; i++) PORTCULLIS[a * 3 + i] = of("awaken:portcullis[axis=" + (a == 0 ? "x" : "z") + ",part=" + parts[i] + "]");
    }

    /**
     * The gate passage between its towers: a pointed arch through, the portcullis half raised behind the outer arch,
     * murder holes in the vault, lamps down the passage walls, the gate's leaves folded back, the city's arms and the
     * district's banners over the arch, a machicolated box over the gate, the room over the gate on the wall walk's line,
     * and a battlemented roof with the bell-gable and braziers.
     */
    static void gatePassage(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        int h = p.h();
        boolean alongZ = p.axis().equals("z");
        double cx = (box.x0() + box.x1() + 1) / 2.0, cz = (box.z0() + box.z1() + 1) / 2.0;
        int outSide = CityWall.dominant(cx, cz), inSide = (outSide + 2) & 3;
        int width = alongZ ? box.w() : box.d(), length = alongZ ? box.d() : box.w();
        int outerAlong = (outSide == N || outSide == W) ? 0 : length - 1;
        int crossAxis = alongZ ? 0 : 1; // the portcullis spans across the road
        int walkTop = CityWall.top(c.plan);
        if (!box.contains(x, z)) {
            // in front of the outer face: the arms, banners and the machicolated box over the arch
            int along = alongZ ? z - box.z0() : x - box.x0();
            int across = alongZ ? x - box.x0() : z - box.z0();
            int beyond = outerAlong == 0 ? -along : along - (length - 1);
            if (across < 0 || across >= width || beyond < 1 || beyond > 2) return;
            if (beyond == 1) {
                if (across == width / 2 - 1) b.set(x, 13, z, CityWall.st("awaken:wall_shield[facing=" + DIR[outSide] + ",arms=carillon]"));
                if (across == width / 2) b.set(x, 13, z, CityWall.st("awaken:wall_shield[facing=" + DIR[outSide] + ",arms=" + armsOf(cx, cz) + "]"));
                if (across == 1 || across == width - 2) {
                    int design = CityWall.designOf(districtOfGate(cx, cz));
                    for (int s = 0; s < 2; s++)
                        for (int i = 0; i < 3; i++) b.set(x, 19 - s * 3 - i, z, CityFacadeLife.BANNER[outSide][design][i][0]);
                }
                if (across >= 3 && across <= width - 4) {
                    b.set(x, 21, z, CityWall.CORBEL_ANDESITE.straight()[inSide]);
                    for (int ry = 22; ry <= 26; ry++) b.set(x, ry, z, ry == 24 || ry == 25 ? loop(outSide, ry == 24 ? 1 : 2, 0, "stone_bricks") : ASHLAR);
                    b.set(x, 27, z, STONE_SLAB);
                    if (across % 2 == 0) b.column(x, 28, 29, z, STONE_BRICKS); // merlons two high (their tops in an odd row)
                }
            }
            return;
        }
        int across = alongZ ? x - box.x0() : z - box.z0();
        int along = alongZ ? z - box.z0() : x - box.x0();
        int edge = Math.min(across, width - 1 - across);
        int open = edge == 0 ? 0 : edge == 1 ? 7 : edge == 2 ? 8 : 9;
        int fromOuter = Math.abs(along - outerAlong);
        b.plate(x, z);
        b.column(x, -4, -1, z, STONE);
        for (int ry = open + 1; ry < h; ry++) b.set(x, ry, z, ry == 32 ? POLISHED_ANDESITE : CityLandmarks.masonry(c, x, ry, z));
        if (open > 0) {
            b.column(x, 1, open, z, AIR);
            boolean faceRow = fromOuter == 0 || along == length - 1 - outerAlong;
            b.set(x, open + 1, z, faceRow ? (edge >= 4 ? CHISELED_STONE_BRICKS : POLISHED_ANDESITE) : edge == 1 ? STONE_SLAB_TOP : POLISHED_ANDESITE);
            // murder holes in the vault
            if (!faceRow && fromOuter >= 2 && edge >= 2 && Math.floorMod(across + along, 3) == 0) b.set(x, open + 1, z, AIR);
            // the portcullis, half raised, one in from the outer arch
            if (fromOuter == 1) {
                b.set(x, open, z, PORTCULLIS[crossAxis * 3 + 2]);
                for (int ry = 6; ry < open; ry++) b.set(x, ry, z, PORTCULLIS[crossAxis * 3 + (ry == 6 ? 0 : 1)]);
            }
            // the gate's leaves folded back against the passage walls, behind the portcullis
            if (edge == 1 && fromOuter >= 3 && fromOuter <= 5) {
                int wallSide = across == 1 ? (alongZ ? W : N) : (alongZ ? E : S);
                for (int ry = 1; ry <= 6; ry++) b.set(x, ry, z, GATE_LEAF[(wallSide + 2) & 3]);
            }
            // lamps down the passage walls and one hung in the middle
            if (edge == 1 && (fromOuter == 7 || fromOuter == 10)) {
                int wallSide = across == 1 ? (alongZ ? W : N) : (alongZ ? E : S);
                b.set(x, 4, z, CityWall.WALL_LANTERN[(wallSide + 2) & 3]);
            }
            if (across == width / 2 && along == length / 2 + 1) b.set(x, open, z, LANTERN_HANGING);
        } else {
            b.column(x, 0, 9, z, CityLandmarks.masonry(c, x, 5, z));
        }
        // the room over the gate, on the wall walk's line (the walk runs through the gatehouse)
        CityWall.Geo g = CityWall.geo(c.plan, x, z);
        if (Math.abs(g.d()) <= 1.5) {
            b.column(x, walkTop + 1, walkTop + 3, z, AIR); // (lit from the gate towers' halls either side)
        }
        // the top: battlements round a walk, braziers at its corners, and the gate hall's slate hip roof inside them
        // with a gilded finial (the city's sign over every gate)
        b.set(x, h, z, POLISHED_ANDESITE);
        int ins = CityLandmarks.inset(box, x, z);
        if (ins == 0) {
            b.set(x, h + 1, z, STONE_BRICKS);
            if (((x + z) & 1) == 0) {
                b.set(x, h + 2, z, STONE_BRICKS);
                b.set(x, h + 3, z, STONE_SLAB);
            }
        }
        if ((x == box.x0() + 1 || x == box.x1() - 1) && (z == box.z0() + 1 || z == box.z1() - 1)) b.set(x, h + 1, z, BRAZIER);
        Box hall = new Box(box.x0() + 2, box.z0() + 2, box.x1() - 2, box.z1() - 2);
        if (hall.contains(x, z)) {
            CityLandmarks.pyramidHip(b, hall, x, z, h + 1, SLATE_KIT);
            int hs = (Math.min(hall.w(), hall.d()) + 1) / 2;
            int dd = Math.min(Math.min(x - hall.x0(), hall.x1() - x), Math.min(z - hall.z0(), hall.z1() - z)) + 1;
            if (dd >= hs && across == width / 2 - 1 && along == length / 2) {
                b.set(x, h + hs + 1, z, BRONZE);
                b.set(x, h + hs + 2, z, GILDED);
                b.set(x, h + hs + 3, z, COPPER_ROD);
            }
        }
    }

    static int districtOfGate(double cx, double cz) {
        int o = CityWall.dominant(cx, cz);
        return o == N ? TERRACE : o == E ? GARDEN_D : o == S ? WORKS : BELLWATCH;
    }

    static String armsOf(double cx, double cz) {
        return switch (districtOfGate(cx, cz)) {
            case TERRACE -> "terrace";
            case GARDEN_D -> "garden";
            case WORKS -> "works";
            default -> "bellwatch";
        };
    }
}
