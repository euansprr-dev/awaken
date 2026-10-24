package dev.awaken.city;

import dev.awaken.city.CityBlocks.Roof;
import dev.awaken.city.CityPainter.Ctx;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The building grammar v1 (blockout): a plot becomes a stone or timber ground storey, 1-5 upper storeys (jettied over
 * the street in the old town), a roof of its district's palette (gable, hip, mansard, flat or sawtooth), a chimney.
 * Cores are solid (dark behind the glass, sealed attics) except the plots the plan marks enterable, which stay open
 * inside with a floor on every storey. The plan decides every shape; the world seed only picks shutters, flower boxes
 * and weathering.
 *
 * <p>Rows are counted from the plot's floor (row 0 = its ground block): the ground storey is rows 1-5 (5 is its top
 * beam), each upper storey 4 more; the eave row is the last storey's top beam ({@code Plot.eave}).
 */
final class CityGrammar {
    private CityGrammar() {}

    /** A plot's materials, chosen from its district's palette by the plan's variant (the same in every world). */
    record Style(BlockState ground, BlockState wall, BlockState trim, int wood, boolean timber, Roof roof, boolean shutters,
                 boolean boxes, boolean jetties, boolean tallChimney, BlockState flat) {}

    static Style style(Plot p) {
        int v = p.variant();
        int r = (v * 37 + p.id() * 11) % 100;
        return switch (p.district()) {
            case BELLWATCH -> new Style(pick(v, STONE_BRICKS, COBBLE, TUFF_BRICKS, STONE_BRICKS), pick(v / 3, CALCITE, WHITE_TERRACOTTA, SMOOTH_SANDSTONE, PALE_PLANKS),
                    STONE_BRICKS, v % 4 == 3 ? 1 : 0, true,
                    r < 52 ? SLATE : r < 74 ? SLATE_LIGHT : r < 86 ? SLATE_SMOOTH : r < 94 ? BLACKSTONE : SLATE_TUFF, true, true, true, false, SMOOTH_SLAB);
            case QUAY_D -> new Style(pick(v, STONE_BRICKS, COBBLE, STONE_BRICKS), pick(v / 3, SPRUCE_PLANKS, SPRUCE_PLANKS, MUD_BRICKS, DARK_PLANKS),
                    STONE_BRICKS, 1, v % 3 != 2, r < 45 ? SPRUCE : r < 75 ? DARK_OAK : MUD, false, false, true, false, SMOOTH_SLAB);
            case MARKET -> new Style(pick(v, STONE_BRICKS, SMOOTH_SANDSTONE, STONE_BRICKS), pick(v / 3, WHITE_TERRACOTTA, CALCITE, SMOOTH_SANDSTONE, CUT_SANDSTONE),
                    BRONZE, 0, false, r < 40 ? COPPER : r < 60 ? RESIN : r < 85 ? BRONZE_ROOF : GRANITE, true, true, false, false, SMOOTH_SLAB);
            case TERRACE -> new Style(pick(v, POLISHED_DIORITE, QUARTZ_BRICKS, STONE_BRICKS), pick(v / 3, CALCITE, QUARTZ_BRICKS, POLISHED_DIORITE, SMOOTH_QUARTZ),
                    POLISHED_ANDESITE, 0, false, r < 50 ? PALE : r < 75 ? QUARTZ : STONE_ROOF, false, false, false, false, SMOOTH_SLAB);
            case WORKS -> new Style(pick(v, STONE_BRICKS, BRICKS), pick(v / 3, BRICKS, BRICKS, MUD_BRICKS, CINNABAR_BRICKS),
                    STONE_BRICKS, 0, false, r < 30 ? BRICK : r < 60 ? CINNABAR : r < 80 ? BLACKSTONE : SLATE_SMOOTH, false, false, false, true, SMOOTH_SLAB);
            case GARDEN_D -> new Style(STONE_BRICKS, pick(v / 3, CALCITE, WHITE_TERRACOTTA, BIRCH_PLANKS, SMOOTH_SANDSTONE),
                    STONE_BRICKS, 1, v % 10 < 3, r < 45 ? GREEN : r < 80 ? WEATHERED : DARK_PRISMARINE, true, true, false, false, SMOOTH_SLAB);
            default -> new Style(pick(v, COBBLE, STONE_BRICKS), pick(v / 3, STONE_BRICKS, COBBLE, ANDESITE),
                    STONE_BRICKS, 0, false, r < 40 ? STONE_ROOF : r < 70 ? COBBLE_ROOF : SLATE_LIGHT, false, false, true, false, SMOOTH_SLAB);
        };
    }

    private static BlockState pick(int v, BlockState... options) {
        return options[Math.floorMod(v, options.length)];
    }

    static int inward(int side) {
        return (side + 2) & 3;
    }

    static int ccw(int side) {
        return (side + 3) & 3;
    }

    static int rise(boolean steep, int d) {
        if (d <= 0) return 0;
        if (!steep) return d;
        return d <= 3 ? 2 * d : 6 + (d - 3);
    }

    /** One plot, clipped to the buffer. */
    static void plot(Ctx c, CityBuffer b, Plot p) {
        CityPlan plan = c.plan;
        Style st = style(p);
        int x0 = p.x0(), z0 = p.z0(), x1 = p.x1(), z1 = p.z1();
        // jetties: upper storeys reach one block over a street at least 4 wide (old town, quay, wall houses)
        int jw = 0, je = 0, jn = 0, js = 0;
        if (st.jetties() && p.storeys() >= 2) {
            if (openWidth(plan, x0, z0, x1, z1, N) >= 4) jn = 1;
            if (openWidth(plan, x0, z0, x1, z1, S) >= 4) js = 1;
            if (openWidth(plan, x0, z0, x1, z1, W) >= 4) jw = 1;
            if (openWidth(plan, x0, z0, x1, z1, E) >= 4) je = 1;
        }
        Box base = new Box(x0, z0, x1, z1);
        Box up = new Box(x0 - jw, z0 - jn, x1 + je, z1 + js);
        int g = p.lift();
        int ax0 = Math.max(up.x0 - 1, b.px0), ax1 = Math.min(up.x1 + 1, b.x1());
        int az0 = Math.max(up.z0 - 1, b.pz0), az1 = Math.min(up.z1 + 1, b.z1());
        boolean[] jetty = {jn == 1, je == 1, js == 1, jw == 1};
        for (int x = ax0; x <= ax1; x++) {
            for (int z = az0; z <= az1; z++) {
                if (base.contains(x, z)) groundStorey(c, b, p, st, base, x, z, g);
                else if (p.storeys() >= 2) corbel(b, base, up, jetty, x, z, g);
                for (int s = 1; s < p.storeys(); s++) {
                    if (up.contains(x, z)) upperStorey(c, b, p, st, up, x, z, g, s);
                }
                roof(c, b, p, st, up, x, z, g);
                outside(c, b, p, st, base, up, x, z, g);
            }
        }
    }

    record Box(int x0, int z0, int x1, int z1) {
        boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        /** The perimeter side this cell is on (N and S win at corners), or -1. */
        int side(int x, int z) {
            if (!contains(x, z)) return -1;
            if (z == z0) return N;
            if (z == z1) return S;
            if (x == x0) return W;
            if (x == x1) return E;
            return -1;
        }

        boolean corner(int x, int z) {
            return (x == x0 || x == x1) && (z == z0 || z == z1);
        }

        /** Which side's outside cell this is (one step out, within the side's span), or -1. */
        int outside(int x, int z) {
            boolean inX = x >= x0 && x <= x1, inZ = z >= z0 && z <= z1;
            if (inX && z == z0 - 1) return N;
            if (inX && z == z1 + 1) return S;
            if (inZ && x == x0 - 1) return W;
            if (inZ && x == x1 + 1) return E;
            return -1;
        }

        int along(int side, int x, int z) {
            return side == N || side == S ? x - x0 : z - z0;
        }

        int length(int side) {
            return side == N || side == S ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        int w() {
            return x1 - x0 + 1;
        }

        int d() {
            return z1 - z0 + 1;
        }
    }

    /** Open ground in front of a side, sampled at its middle (how wide the street is there). */
    static int openWidth(CityPlan plan, int x0, int z0, int x1, int z1, int side) {
        int x, z, dx = 0, dz = 0;
        switch (side) {
            case N -> { x = (x0 + x1) / 2; z = z0 - 1; dz = -1; }
            case S -> { x = (x0 + x1) / 2; z = z1 + 1; dz = 1; }
            case W -> { x = x0 - 1; z = (z0 + z1) / 2; dx = -1; }
            default -> { x = x1 + 1; z = (z0 + z1) / 2; dx = 1; }
        }
        int n = 0;
        while (n < 8 && walkable(plan.kind(x, z))) {
            n++;
            x += dx;
            z += dz;
        }
        return n;
    }

    /** Whether a wall faces open ground (windows) or another building (a blind party wall). */
    static boolean exposed(CityPlan plan, int x, int z, int side) {
        int nx = x + (side == E ? 1 : side == W ? -1 : 0), nz = z + (side == S ? 1 : side == N ? -1 : 0);
        byte k = plan.kind(nx, nz);
        return k != PLOT && k != LANDMARK;
    }

    static boolean window(int t, int len) {
        return t >= 1 && t <= len - 2 && t % 3 == 1;
    }

    // ---- storeys --------------------------------------------------------------------------------------------

    static void groundStorey(Ctx c, CityBuffer b, Plot p, Style st, Box base, int x, int z, int g) {
        CityPlan plan = c.plan;
        int sd = base.side(x, z);
        b.set(x, g, z, FLOOR);
        if (sd < 0) {
            if (p.enter()) {
                b.column(x, g + 1, g + 4, z, AIR);
                b.set(x, g + 5, z, FLOOR);
            } else {
                b.column(x, g + 1, g + 5, z, CORE);
            }
            return;
        }
        boolean corner = base.corner(x, z);
        int t = base.along(sd, x, z), len = base.length(sd);
        int axis = sd == N || sd == S ? 0 : 1;
        boolean door = p.hasDoor() && x == p.doorX() && z == p.doorZ() && sd == p.front();
        boolean open = exposed(plan, x, z, sd);
        boolean shop = p.district() == MARKET || p.use().equals("shop");
        for (int r = 1; r <= 5; r++) {
            BlockState s;
            if (r == 5) s = st.timber() ? beam(st, axis) : (corner ? st.ground() : st.trim());
            else if (corner) s = st.timber() ? POST[st.wood()] : ((r & 1) == 1 ? POLISHED_ANDESITE : st.ground());
            else if (door && r <= 2) s = (r == 1 ? DOOR_LOWER : DOOR_UPPER)[doorKind(p)][inward(sd)];
            else if (door && r == 3) s = st.timber() ? beam(st, axis) : CHISELED_STONE_BRICKS;
            else if (open && !door && (r == 2 || r == 3) && (window(t, len) || shop && t >= 1 && t <= len - 2 && t % 3 != 0)) s = PANE[axis];
            else s = weathered(c, st.ground(), x, z, r);
            b.set(x, g + r, z, s);
        }
    }

    static int doorKind(Plot p) {
        return p.district() == QUAY_D ? 0 : p.district() == GARDEN_D || p.district() == TERRACE ? 2 : 1;
    }

    static BlockState beam(Style st, int axis) {
        return axis == 0 ? BEAM_X[st.wood()] : BEAM_Z[st.wood()];
    }

    static BlockState weathered(Ctx c, BlockState s, int x, int z, int r) {
        if (s != STONE_BRICKS && s != COBBLE) return s;
        int h = c.pct(x, z, 200 + r);
        if (s == STONE_BRICKS) return h < 8 ? MOSSY_STONE_BRICKS : h < 16 ? CRACKED_STONE_BRICKS : s;
        return h < 15 ? MOSSY_COBBLE : s;
    }

    static void upperStorey(Ctx c, CityBuffer b, Plot p, Style st, Box up, int x, int z, int g, int s) {
        CityPlan plan = c.plan;
        int base = 5 + 4 * (s - 1);
        int sd = up.side(x, z);
        if (sd < 0) {
            if (p.enter()) {
                b.column(x, g + base + 1, g + base + 3, z, AIR);
                b.set(x, g + base + 4, z, FLOOR);
            } else {
                b.column(x, g + base + 1, g + base + 4, z, CORE);
            }
            return;
        }
        boolean corner = up.corner(x, z);
        int t = up.along(sd, x, z), len = up.length(sd);
        int axis = sd == N || sd == S ? 0 : 1;
        boolean open = exposed(plan, x, z, sd);
        for (int r = 1; r <= 4; r++) {
            BlockState bs;
            if (st.timber()) {
                if (corner) bs = POST[st.wood()];
                else if (r == 4) bs = beam(st, axis);
                else if (t % 3 == 0) bs = POST[st.wood()];
                else if (open && (r == 2 || r == 3) && t % 3 == 2) bs = PANE[axis];
                else bs = st.wall();
            } else {
                if (corner) bs = st.ground() == st.wall() ? POLISHED_ANDESITE : st.ground();
                else if (r == 4 && s == p.storeys() - 1) bs = st.trim();
                else if (open && (r == 2 || r == 3) && window(t, len)) bs = PANE[axis];
                else if (r == 1 && open && window(t, len) && st.trim() == BRONZE) bs = BRONZE;
                else bs = st.wall();
            }
            b.set(x, g + base + r, z, bs);
        }
    }

    /** Corbels under a jetty: upside-down stairs every other block along the overhang. */
    static void corbel(CityBuffer b, Box base, Box up, boolean[] jetty, int x, int z, int g) {
        int sd = base.outside(x, z);
        if (sd < 0 || !jetty[sd]) return;
        if ((base.along(sd, x, z) & 1) == 0) b.set(x, g + 5, z, CORBEL[inward(sd)]);
    }

    // ---- outside the walls: door lanterns, shutters, flower boxes, a drainpipe -------------------------------

    static void outside(Ctx c, CityBuffer b, Plot p, Style st, Box base, Box up, int x, int z, int g) {
        CityPlan plan = c.plan;
        int sd = base.outside(x, z);
        if (sd >= 0 && sd == p.front() && p.hasDoor()) {
            int t = base.along(sd, x, z), dt = base.along(sd, p.doorX(), p.doorZ());
            if (t == dt + 1 && (p.enter() || p.use().equals("tavern"))) {
                b.set(x, g + 4, z, FENCE_TO[inward(sd)]);
                b.set(x, g + 3, z, LANTERN_HANGING);
            }
            // a drainpipe at the front's far corner on tall houses (a way down from the roof)
            byte under = plan.kind(x, z);
            if (p.storeys() >= 3 && t == base.length(sd) - 1 && (under == AVENUE || under == STREET || under == LANE || under == ALLEY)) {
                b.column(x, g + 1, g + p.eave() - 1, z, CHAIN_Y);
            }
        }
        int us = up.outside(x, z);
        if (us < 0 || !exposed(plan, x - (us == E ? 1 : us == W ? -1 : 0), z - (us == S ? 1 : us == N ? -1 : 0), us)) return;
        if (!walkable(plan.kind(x, z)) && plan.kind(x, z) != YARD && plan.kind(x, z) != GARDEN) return;
        int t = up.along(us, x, z), len = up.length(us);
        for (int s = 1; s < p.storeys(); s++) {
            int base0 = 5 + 4 * (s - 1);
            boolean win = st.timber() ? (t % 3 == 2 && t >= 1 && t <= len - 2) : window(t, len);
            if (st.boxes() && win && c.pct(p.id(), s * 16 + t, 60 + us) < 30) {
                b.set(x, g + base0 + 1, z, BOX_SHELF[us]);
                b.set(x, g + base0 + 2, z, FLOWERS[c.pct(p.id(), t, 61) % FLOWERS.length]);
            }
            if (st.shutters() && !st.timber() && c.pct(p.id(), 0, 62) < 60) {
                boolean beside = window(t - 1, len) || window(t + 1, len);
                if (beside && !win) {
                    int kind = c.pct(p.id(), 1, 63) % SHUTTER.length;
                    b.set(x, g + base0 + 2, z, SHUTTER[kind][us]);
                    b.set(x, g + base0 + 3, z, SHUTTER[kind][us]);
                }
            }
        }
    }

    // ---- roofs ----------------------------------------------------------------------------------------------

    static void roof(Ctx c, CityBuffer b, Plot p, Style st, Box up, int x, int z, int g) {
        CityPlan plan = c.plan;
        int e = g + p.eave();
        boolean inside = up.contains(x, z);
        // overhang ring: only over open ground, never onto a neighbour's wall
        if (!inside) {
            int os = up.outside(x, z);
            if (os < 0) return;
            byte k = plan.kind(x, z);
            if (k == PLOT || k == LANDMARK) return;
            if (p.roof() == Roof_FLAT) return;
            if (p.roof() == Roof_GABLE || p.roof() == Roof_SAW) {
                boolean eaveSide = p.ridgeX() ? (os == N || os == S) : (os == E || os == W);
                if (!eaveSide) return;
            }
            b.set(x, e, z, st.roof().stair()[inward(os)]);
            return;
        }
        int dW = x - up.x0 + 1, dE = up.x1 - x + 1, dN = z - up.z0 + 1, dS = up.z1 - z + 1;
        int dX = Math.min(dW, dE), dZ = Math.min(dN, dS);
        boolean wallCell = up.side(x, z) >= 0;
        BlockState fill = p.enter() ? CORE : CORE;
        switch (p.roof()) {
            case FLAT -> {
                b.set(x, e, z, st.flat());
                if (wallCell) b.set(x, e + 1, z, p.district() == TERRACE ? DIORITE_WALL_POST : WALL_POST);
            }
            case GABLE -> gable(c, b, p, st, up, x, z, e, dW, dE, dN, dS);
            case SAW -> saw(b, p, st, up, x, z, e);
            case HIP, MANSARD -> hip(b, p, st, up, x, z, e, dW, dE, dN, dS, p.roof() == CityPlan.Roof.MANSARD);
        }
        chimney(c, b, p, st, up, x, z, e);
        // attic sealed: nothing hollow under any roof
        if (!wallCell && b.get(x, e, z) == null) b.set(x, e, z, fill);
    }

    private static final CityPlan.Roof Roof_FLAT = CityPlan.Roof.FLAT, Roof_GABLE = CityPlan.Roof.GABLE, Roof_SAW = CityPlan.Roof.SAW;

    static void gable(Ctx c, CityBuffer b, Plot p, Style st, Box up, int x, int z, int e, int dW, int dE, int dN, int dS) {
        boolean rx = p.ridgeX();
        int d = rx ? Math.min(dN, dS) : Math.min(dW, dE);
        int span = rx ? up.d() : up.w();
        int hs = (span + 1) / 2;
        boolean lowSide = rx ? dN <= dS : dW <= dE;
        int upDir = rx ? (lowSide ? S : N) : (lowSide ? E : W);
        int h = e + rise(p.steep(), d);
        boolean gableEnd = rx ? (x == up.x0 || x == up.x1) : (z == up.z0 || z == up.z1);
        boolean ridge = d == hs && (span & 1) == 1;
        Roof roof = st.roof();
        // the solid under the slope: gable walls at the ends, roof material just under the surface, core within
        for (int y = e + 1; y < h; y++) {
            BlockState s;
            if (gableEnd) s = gableWall(st, x, y, z, rx, e);
            else s = y >= h - 2 ? roof.block() : CORE;
            b.set(x, y, z, s);
        }
        if (ridge) {
            b.set(x, h, z, roof.block());
            b.set(x, h + 1, z, roof.slab());
        } else {
            b.set(x, h, z, roof.stair()[upDir]);
        }
    }

    static BlockState gableWall(Style st, int x, int y, int z, boolean rx, int e) {
        if (st.timber()) {
            int along = rx ? z : x;
            if ((y - e) % 4 == 0) return rx ? BEAM_Z[st.wood()] : BEAM_X[st.wood()];
            return Math.floorMod(along, 3) == 0 ? POST[st.wood()] : st.wall();
        }
        return st.wall();
    }

    static void hip(CityBuffer b, Plot p, Style st, Box up, int x, int z, int e, int dW, int dE, int dN, int dS, boolean mansard) {
        int dX = Math.min(dW, dE), dZ = Math.min(dN, dS);
        int d = Math.min(dX, dZ);
        int hs = (Math.min(up.w(), up.d()) + 1) / 2;
        Roof roof = st.roof();
        if (mansard) {
            if (d >= 3) {
                for (int y = e + 1; y <= e + 4; y++) b.set(x, y, z, y == e + 4 ? roof.block() : CORE);
                b.set(x, e + 5, z, SLATE.slab());
                return;
            }
            int h = e + 2 * d;
            for (int y = e + 1; y < h; y++) b.set(x, y, z, y >= h - 2 ? roof.block() : CORE);
            b.set(x, h, z, facing(roof, dW, dE, dN, dS));
            return;
        }
        int dd = Math.min(d, hs);
        int h = e + rise(p.steep(), dd);
        for (int y = e + 1; y < h; y++) b.set(x, y, z, y >= h - 2 ? roof.block() : CORE);
        boolean top = d >= hs;
        if (top) {
            b.set(x, h, z, roof.block());
            b.set(x, h + 1, z, roof.slab());
        } else {
            b.set(x, h, z, facing(roof, dW, dE, dN, dS));
        }
    }

    /** The stair on a hipped slope: facing up toward the ridge; outer corners where two slopes meet. */
    static BlockState facing(Roof roof, int dW, int dE, int dN, int dS) {
        int dX = Math.min(dW, dE), dZ = Math.min(dN, dS);
        int fx = dW <= dE ? E : W, fz = dN <= dS ? S : N;
        if (dX < dZ) return roof.stair()[fx];
        if (dZ < dX) return roof.stair()[fz];
        return fx == ccw(fz) ? roof.outerLeft()[fz] : roof.outerRight()[fz];
    }

    /** Sawtooth sheds (the Works): teeth along the ridge axis, a glazed face and a slope in each 4 blocks. */
    static void saw(CityBuffer b, Plot p, Style st, Box up, int x, int z, int e) {
        boolean rx = p.ridgeX();
        int s = rx ? z - up.z0 : x - up.x0;
        int q = s % 4;
        Roof roof = st.roof();
        int glassFace = rx ? N : W;
        int upDir = glassFace;
        switch (q) {
            case 0 -> {
                b.set(x, e + 1, z, rx ? PANE[0] : PANE[1]);
                b.set(x, e + 2, z, rx ? PANE[0] : PANE[1]);
                b.set(x, e + 3, z, roof.block());
            }
            case 1 -> {
                b.set(x, e + 1, z, CORE);
                b.set(x, e + 2, z, roof.block());
                b.set(x, e + 3, z, roof.stair()[upDir]);
            }
            case 2 -> {
                b.set(x, e + 1, z, roof.block());
                b.set(x, e + 2, z, roof.stair()[upDir]);
            }
            default -> b.set(x, e + 1, z, roof.stair()[upDir]);
        }
    }

    /** A brick stack on one gable (old town, quay, wall houses), tall smokestacks in the Works. */
    static void chimney(Ctx c, CityBuffer b, Plot p, Style st, Box up, int x, int z, int e) {
        int roll = Ctx.shape(p.id(), 7, 77);
        boolean works = st.tallChimney();
        if (!works && (roll >= 55 || p.roof() == CityPlan.Roof.FLAT)) return;
        if (works && roll >= 28) return;
        int cxp, czp;
        if (p.ridgeX()) {
            cxp = roll % 2 == 0 ? up.x0 + 1 : up.x1 - (works ? 2 : 1);
            czp = (up.z0 + up.z1) / 2;
        } else {
            czp = roll % 2 == 0 ? up.z0 + 1 : up.z1 - (works ? 2 : 1);
            cxp = (up.x0 + up.x1) / 2;
        }
        // a works smokestack is 2x2 and stands well clear of the roof; a house chimney is one brick stack
        int size = works ? 2 : 1;
        if (x < cxp || x >= cxp + size || z < czp || z >= czp + size) return;
        int span = p.ridgeX() ? up.d() : up.w();
        int top = e + rise(p.steep(), (span + 1) / 2) + (works ? 14 + roll % 7 : 2);
        BlockState brick = st.wall() == CINNABAR_BRICKS ? CINNABAR_BRICKS : BRICKS;
        for (int y = e; y <= top; y++) b.set(x, y, z, works && (top - y) == 2 ? POLISHED_BLACKSTONE_BRICKS : brick);
        b.set(x, top + 1, z, works ? STONE_SLAB : POT);
    }
}
