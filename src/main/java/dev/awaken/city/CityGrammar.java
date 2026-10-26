package dev.awaken.city;

import dev.awaken.city.CityBlocks.Course;
import dev.awaken.city.CityBlocks.Roof;
import dev.awaken.city.CityPainter.Ctx;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The building grammar v2 (Milestone 4 step B1, the facade grammar; docs/carillon/m4/look-bible.md §3, §5, §9.2): a plot
 * becomes a masonry ground storey, 1-5 upper storeys (jettied once over the wider lanes of the old town), a roof of its
 * district's palette, a chimney; and every face it shows the city carries the climbing grid, so you just know you can
 * climb it.
 *
 * <p>Rows are counted from the plot's floor (row 0 = its ground block; a lip in row r has its top r above the street):
 * the ground storey is rows 1-5, each upper storey 4 more, the eave {@code Plot.eave} is the last storey's top beam. In
 * a facade's outside plane (the cells one block in front of the wall) anything that can be held sits only in the odd
 * rows: row 3 the hood over every ground opening, row 5 the shop cornice (or a jetty's beam ends), then per storey its
 * row 2 the window's box and its row 4 the floor course; the top course is the eave. Even rows hold only what has no
 * collision (shutters) or is covered. Courses run unbroken across a whole row of houses (every plot on a street shares
 * the rows), so each is a shimmy rail; every window stack is a ladder; and every street face has a drainpipe, a trellis
 * or a hoist rope at most 12 apart. Alleys, slots and the Quay's warehouse flanks are running faces: windows flush in
 * the wall, nothing in their outside plane. Cores stay solid (kit windows in the wall plane) except where the plan
 * marks a plot enterable.
 *
 * <p>The plan decides every shape (the same city in every world); the world seed only picks flowers, shutters' paint,
 * ivy and moss.
 */
final class CityGrammar {
    private CityGrammar() {}

    // window rhythms along a face
    static final int R_SINGLE = 0, R_PAIRS = 1, R_SYM = 2, R_TIMBER = 3;
    // face classes, by the ground in front of a face: nothing shows; a running face without or with (flush) windows;
    // a back face (the grid, no pipes or shutters); a street face (everything)
    static final int F_NONE = 0, F_BLIND = 1, F_RUN = 2, F_BACK = 3, F_STREET = 4;

    /**
     * A plot's facade language, chosen from its district's palette by the plan's variant (the same in every world):
     * materials, the three course profiles, window vocabulary, boxes, shutters, roof and its cap, and what the roof
     * grows (dormers, crow steps, a hoist, a crown on the chimney).
     */
    record Style(int district, BlockState ground, BlockState wall, boolean timber, int tone, Roof roof, BlockState cap,
                 Course cornice, Course course, Course hood, BlockState quoin, BlockState keystone, BlockState pilaster,
                 int win, int groundWin, boolean smallGround, int rhythm, int boxStyle, int bloomPct, int[] blooms,
                 int[] shutters, int shutterPct, boolean jetties, boolean tallChimney, BlockState flat, int crown,
                 boolean crow, boolean dormers, BlockState dormerCheek, int trellisPct, boolean shopfront, boolean loading) {}

    static final int[] NO_SHUTTERS = {};

    static Style style(Plot p) {
        // a settlement's house (SPEC §28.6): its land's and wealth's style, never a Carillon district's
        if (VillageStyles.isVillage(p)) return VillageStyles.style(p);
        int v = p.variant();
        int r = (v * 37 + p.id() * 11) % 100;          // the roof
        int w = (v * 53 + p.id() * 7 + 13) % 100;      // the walls
        int k = Ctx.shape(p.id(), 3, 91);              // everything else
        return switch (p.district()) {
            case BELLWATCH -> {
                boolean crow = p.storeys() >= 4 && p.roof() == CityPlan.Roof.GABLE && (k % 3 == 0);
                BlockState wall = w < 45 ? PLASTER[P_WHITE] : w < 75 ? PLASTER[P_CREAM] : w < 87 ? PALE_PLANKS : w < 94 ? PLASTER[P_OCHRE] : PLASTER[P_OXBLOOD];
                Roof roof = r < 40 ? SLATE_KIT : r < 65 ? SLATE : r < 80 ? SLATE_LIGHT : r < 90 ? TUFF_ROOF : r < 95 ? BLACKSTONE : SLATE_SMOOTH;
                int tone = k % 4 == 3 ? 1 : 0;
                Course beams = tone == 1 ? C_SPRUCE : C_DARK;
                yield new Style(BELLWATCH, pick(v, ASHLAR, COBBLE, TUFF_BRICKS, ASHLAR, ASHLAR), wall, true, tone, roof, CAP_SMOOTH,
                        beams, beams, C_STONE, POLISHED_ANDESITE, CHISELED_STONE_BRICKS, null,
                        W_LEADED, W_DORMER, true, R_TIMBER, 0, 35, new int[]{1, 1, 1, 2, 3, 4, 5},
                        new int[]{SH_BLUE, SH_SAGE, SH_OXBLOOD}, 70, true, false, SMOOTH_SLAB, k % 10 < 7 ? 0 : 1,
                        crow, true, wall, 30, false, false);
            }
            case QUAY_D -> {
                // brick and mud-brick warehouses; a few boarded ones (spruce boards on dark timber: dark oak boards read as a hole)
                BlockState wall = w < 50 ? CITY_BRICKS : w < 78 ? MUD_BRICKS : SPRUCE_PLANKS;
                boolean boarded = wall == SPRUCE_PLANKS;
                yield new Style(QUAY_D, pick(v, ASHLAR, COBBLE, ASHLAR), wall, boarded, 0,
                        r < 45 ? SPRUCE : r < 75 ? DARK_OAK : MUD, CAP_BRICK,
                        C_STONE, C_DARK, C_STONE, ASHLAR, CHISELED_STONE_BRICKS, null,
                        W_WAREHOUSE, W_BARRED, false, boarded ? R_TIMBER : R_SINGLE, 0, 15, new int[]{6, 6, 1, 2},
                        NO_SHUTTERS, 0, false, false, SMOOTH_SLAB, k % 2,
                        !boarded, false, wall, 0, false, true);
            }
            case MARKET -> {
                BlockState wall = w < 25 ? PLASTER[P_CREAM] : w < 45 ? PLASTER[P_ROSE] : w < 60 ? PLASTER[P_OCHRE] : w < 75 ? PLASTER[P_WHITE]
                        : w < 85 ? PLASTER[P_MAUVE] : PLASTER[P_CLAY];
                Roof roof = r < 40 ? CLAY : r < 60 ? COPPER : r < 75 ? RESIN : r < 90 ? BRONZE_ROOF : GRANITE;
                boolean iron = k % 10 < 7;
                // the floor courses differ house by house (the rows never do): bronze, sandstone or quartz
                int cv = Ctx.shape(p.id(), 13, 97);
                Course course = cv < 55 ? C_BRONZE : cv < 85 ? C_SANDSTONE : C_QUARTZ;
                yield new Style(MARKET, pick(v, CUT_SANDSTONE, ASHLAR, CUT_SANDSTONE), wall, false, 0, roof, CAP_SMOOTH,
                        C_SANDSTONE, course, C_BRONZE, CALCITE, CHISELED_SANDSTONE, p.storeys() >= 5 ? CALCITE : null,
                        W_SASH, W_SHOP, false, k % 3 == 1 ? R_PAIRS : R_SINGLE, iron ? 1 : 0, 50, new int[]{1, 1, 4, 5, 2, 3},
                        new int[]{SH_CREAM, SH_GREEN}, 30, false, false, SMOOTH_SLAB, 0,
                        false, true, VERDIGRIS, 0, true, false);
            }
            case TERRACE -> new Style(TERRACE, pick(v, POLISHED_DIORITE, QUARTZ_BRICKS, ASHLAR), pick(v / 3, CALCITE, QUARTZ_BRICKS, POLISHED_DIORITE, SMOOTH_QUARTZ),
                    false, 0, r < 50 ? PALE : r < 75 ? QUARTZ : STONE_ROOF, CAP_QUARTZ,
                    C_QUARTZ, Ctx.shape(p.id(), 13, 97) < 70 ? C_QUARTZ : C_DIORITE, C_DIORITE, POLISHED_DIORITE, CHISELED_QUARTZ, QUARTZ_PILLAR,
                    W_ARCHED, W_ARCHED, false, R_SYM, 2, 25, new int[]{5, 3, 1},
                    NO_SHUTTERS, 0, false, false, SMOOTH_SLAB, 2,
                    false, false, CALCITE, 0, false, false);
            case WORKS -> new Style(WORKS, pick(v, ASHLAR, CITY_BRICKS), w < 50 ? CITY_BRICKS : w < 70 ? MUD_BRICKS : CINNABAR_BRICKS,
                    false, 0, r < 30 ? BRICK : r < 60 ? CINNABAR : r < 80 ? BLACKSTONE : SLATE_SMOOTH, CAP_STONE,
                    C_STONE, C_BLACKSTONE, C_BRICK, ASHLAR, CHISELED_STONE_BRICKS, null,
                    W_WORKS, W_BARRED, false, R_PAIRS, 1, 0, new int[]{0},
                    NO_SHUTTERS, 0, false, true, SMOOTH_SLAB, 0,
                    false, false, CITY_BRICKS, 0, false, false);
            case GARDEN_D -> {
                BlockState wall = w < 40 ? PLASTER[P_WHITE] : w < 70 ? PLASTER[P_CREAM] : w < 85 ? BIRCH_PLANKS : PALE_PLANKS;
                yield new Style(GARDEN_D, ASHLAR, wall, k % 10 < 3, 1,
                        r < 45 ? GREEN : r < 80 ? WEATHERED : DARK_PRISMARINE, CAP_SMOOTH,
                        C_SANDSTONE, Ctx.shape(p.id(), 13, 97) < 70 ? C_SANDSTONE : C_QUARTZ, C_VERDIGRIS, CALCITE, CHISELED_STONE_BRICKS, null,
                        W_COTTAGE, W_COTTAGE, false, k % 10 < 3 ? R_TIMBER : R_SYM, 0, 70, new int[]{1, 2, 3, 4, 5, 6},
                        new int[]{SH_GREEN, SH_CREAM, SH_SAGE}, 80, false, false, SMOOTH_SLAB, 0,
                        false, false, PLASTER[P_WHITE], 70, false, false);
            }
            default -> {
                boolean timber = k % 10 < 6;
                yield new Style(WALLRING, pick(v, COBBLE, ASHLAR), timber ? (w < 50 ? PLASTER[P_WHITE] : PLASTER[P_CREAM]) : pick(v / 3, ASHLAR, COBBLE, ANDESITE),
                        timber, 1, r < 40 ? STONE_ROOF : r < 70 ? COBBLE_ROOF : SLATE_LIGHT, CAP_STONE,
                        timber ? C_SPRUCE : C_STONE, timber ? C_SPRUCE : C_STONE, C_COBBLE, ASHLAR, CHISELED_STONE_BRICKS, null,
                        W_LEADED, W_DORMER, true, timber ? R_TIMBER : R_SINGLE, 0, 20, new int[]{1, 2, 5, 6},
                        new int[]{SH_NATURAL}, 40, false, false, SMOOTH_SLAB, 2,
                        false, false, ASHLAR, 0, false, false);
            }
        };
    }

    /** The water mill's body (a landmark part built by the grammar): a boarded Quay house without its kit. */
    static Style millStyle() {
        return new Style(QUAY_D, COBBLE, DARK_PLANKS, true, 0, SLATE, CAP_SMOOTH, C_STONE, C_DARK, C_STONE, ASHLAR,
                CHISELED_STONE_BRICKS, null, W_WAREHOUSE, W_BARRED, false, R_TIMBER, 0, 0, new int[]{0}, NO_SHUTTERS, 0,
                false, false, SMOOTH_SLAB, 0, false, false, DARK_PLANKS, 0, false, false);
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

    static int cw(int side) {
        return (side + 1) & 3;
    }

    static int rise(boolean steep, int d) {
        if (d <= 0) return 0;
        if (!steep) return d;
        return d <= 3 ? 2 * d : 6 + (d - 3);
    }

    static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    // ---- one plot -------------------------------------------------------------------------------------------------

    /**
     * Everything one paint of a plot works out once: its boxes, which faces are jettied, the ground in front of each
     * face, where the windows, the loading bay and the climb lines go.
     */
    static final class Facade {
        final Ctx c;
        final PlanView plan;
        final Plot p;
        final Style st;
        final Box base, up;
        final boolean[] jetty = new boolean[4];
        /** Climb columns per side, in up-box coordinates along the side. */
        final boolean[][] climb = new boolean[4][];
        /** The loading bay's first column per side (up coordinates), or -1. */
        final int[] bay = {-1, -1, -1, -1};
        /** The door's column on its side (base coordinates), or -1. */
        final int doorT;
        final boolean trellis, shutters, balcony;
        final int shutterColour;
        /** The awnings' stripe colour (0-5; varies by world: a detail). */
        final int awningColour;
        /** An oriel's middle column on the front (up coordinates), or -1. */
        final int oriel;

        Facade(Ctx c, Plot p, Style st, Box base, Box up, boolean[] jetty) {
            this.c = c;
            this.plan = c.view;
            this.p = p;
            this.st = st;
            this.base = base;
            this.up = up;
            System.arraycopy(jetty, 0, this.jetty, 0, 4);
            doorT = p.hasDoor() ? base.along(p.front(), p.doorX(), p.doorZ()) : -1;
            trellis = Ctx.shape(p.id(), 5, 94) < st.trellisPct();
            // shutters and their paint vary by world (they hold nothing)
            shutters = st.shutters().length > 0 && c.pct(p.id(), 0, 62) < st.shutterPct();
            shutterColour = st.shutters().length > 0 ? st.shutters()[c.pct(p.id(), 1, 63) % st.shutters().length] : 0;
            awningColour = c.pct(p.id(), 2, 66) % 6;
            balcony = st.district() == MARKET && p.storeys() >= 3 && Ctx.shape(p.id(), 9, 95) < 34;
            if (st.loading() && !p.enter() && doorT >= 0) {
                int len = up.length(p.front());
                int t = doorT + baseOffset(p.front());
                if (len >= 6) bay[p.front()] = t + 1 <= len - 2 ? t : t - 1;
            }
            oriel = orielColumn();
            for (int sd = 0; sd < 4; sd++) climb[sd] = climbColumns(sd);
        }

        /**
         * A bay window on the first floor of some Market and Garden fronts (bible §4: 3 wide, 1 out on the cornice, its
         * roof the next course), round the window nearest the middle.
         */
        private int orielColumn() {
            int d = p.district();
            if (balcony || p.storeys() < 3 || (d != MARKET && d != GARDEN_D)) return -1;
            if (Ctx.shape(p.id(), 11, 96) >= (d == MARKET ? 30 : 35)) return -1;
            int sd = p.front(), len = up.length(sd);
            if (len < 7) return -1;
            int best = -1;
            for (int t = 2; t <= len - 3; t++) {
                if (!win(sd, t, len, 1) || upClass(sd, t - 1) != F_STREET || upClass(sd, t + 1) != F_STREET) continue;
                boolean clear = true;
                for (int u = t - 1; u <= t + 1; u++) {
                    int[] o = outsideCell(up, sd, u);
                    if (plan.treeAt(o[0], o[1]) || shared(plan, p, sd, o[0], o[1])) clear = false;
                }
                if (clear && (best < 0 || Math.abs(t - len / 2) < Math.abs(best - len / 2))) best = t;
            }
            return best;
        }

        boolean inOriel(int sd, int t) {
            return oriel >= 0 && sd == p.front() && Math.abs(t - oriel) <= 1;
        }

        /** Up-box along-coordinate of base column 0 on a side (1 where the side's start is jettied out). */
        int baseOffset(int sd) {
            return sd == N || sd == S ? base.x0 - up.x0 : base.z0 - up.z0;
        }

        /** The outside cell of a side at column t of a box. */
        int[] outsideCell(Box bx, int sd, int t) {
            return switch (sd) {
                case N -> new int[]{bx.x0 + t, bx.z0 - 1};
                case S -> new int[]{bx.x0 + t, bx.z1 + 1};
                case W -> new int[]{bx.x0 - 1, bx.z0 + t};
                default -> new int[]{bx.x1 + 1, bx.z0 + t};
            };
        }

        int upClass(int sd, int t) {
            int[] o = outsideCell(up, sd, t);
            return faceClass(plan, p, sd, o[0], o[1]);
        }

        boolean inBay(int sd, int t) {
            return bay[sd] >= 0 && (t == bay[sd] || t == bay[sd] + 1);
        }

        /** An upper storey's window at column t (up coordinates) of a side of length len, on storey s (1-based). */
        boolean win(int sd, int t, int len, int s) {
            if (t < 1 || t > len - 2 || inBay(sd, t)) return false;
            if (bay[sd] >= 0 && (t == bay[sd] - 1 || t == bay[sd] + 2)) return false;
            return switch (st.rhythm()) {
                case R_TIMBER -> {
                    if (t % 3 == 0) yield false;
                    // one window per bay between the posts, wandering by at most one between storeys (old houses settle)
                    int cell = 1 + (Ctx.shape(p.id() * 4 + sd, (t / 3) * 16 + s, 93) & 1);
                    yield t % 3 == cell;
                }
                case R_PAIRS -> t % 4 == 1 || t % 4 == 2;
                case R_SYM -> Math.min(t, len - 1 - t) % 3 == 1;
                default -> t % 3 == 1;
            };
        }

        /** Whether any upper storey has a window (or the bay) at column t. */
        boolean anyWin(int sd, int t, int len) {
            if (inBay(sd, t)) return true;
            for (int s = 1; s < p.storeys(); s++) if (win(sd, t, len, s)) return true;
            return false;
        }

        /** A ground opening at column t (base coordinates): 0 none, 1 window, 2 the door, 3 a shop window. */
        int groundOpen(int sd, int t, int len, int fc) {
            if (sd == p.front() && t == doorT) return 2;
            if (fc < F_RUN || t < 1 || t > len - 2) return 0;
            if (st.shopfront() && fc == F_STREET) return t % 3 != 0 ? 3 : 0;
            if (sd == p.front() && Math.abs(t - doorT) == 1) return 0;
            int ut = t + baseOffset(sd), ulen = up.length(sd);
            if (p.storeys() >= 2 && !inBay(sd, ut)) return win(sd, ut, ulen, 1) ? 1 : 0;
            return t % 3 == 1 ? 1 : 0;
        }

        /**
         * Where the climb lines go on a side: on every run of street face (3+ long) one within 5 of each end, so two
         * runs meeting at a party wall are never more than 11 apart, nor two lines within a run more than 12. A line
         * sits between the windows (never on a window stack, the door or the loading bay).
         */
        boolean[] climbColumns(int sd) {
            int len = up.length(sd);
            boolean[] out = new boolean[len];
            int a = -1;
            for (int t = 0; t <= len; t++) {
                boolean street = t < len && upClass(sd, t) == F_STREET;
                if (street && a < 0) a = t;
                if (!street && a >= 0) {
                    placeRun(sd, a, t - 1, len, out);
                    a = -1;
                }
            }
            return out;
        }

        private void placeRun(int sd, int a, int b, int len, boolean[] out) {
            int l = b - a + 1;
            if (l < 3) return;
            if (l <= 11) {
                out[best(sd, Math.max(a, b - 5), Math.min(b, a + 5), (a + b) / 2, len)] = true;
            } else {
                out[best(sd, a, a + 5, a + 2, len)] = true;
                out[best(sd, b - 5, b, b - 2, len)] = true;
            }
        }

        /**
         * The free column in [lo, hi] nearest want; failing that the nearest one without a tree in front of it (the
         * line then takes a window stack's place); failing that the middle of the range.
         */
        private int best(int sd, int lo, int hi, int want, int len) {
            for (int pass = 0; pass < 2; pass++) {
                int found = -1, bd = Integer.MAX_VALUE;
                for (int t = lo; t <= hi; t++) {
                    int[] o = outsideCell(up, sd, t);
                    if (plan.treeAt(o[0], o[1]) || underForeignJetty(plan, p, o[0], o[1]) || shared(plan, p, sd, o[0], o[1])
                            || pass == 0 && !free(sd, t, len)) continue;
                    int d = Math.abs(t - want) * 2 + (t < want ? 1 : 0);
                    if (d < bd) {
                        bd = d;
                        found = t;
                    }
                }
                if (found >= 0) return found;
            }
            return (lo + hi) / 2;
        }

        private boolean free(int sd, int t, int len) {
            if (anyWin(sd, t, len) || inOriel(sd, t)) return false;
            if (bay[sd] >= 0 && (t == bay[sd] - 1 || t == bay[sd] + 2)) return false;
            int bt = t - baseOffset(sd), blen = base.length(sd);
            if (bt >= 0 && bt < blen) {
                if (sd == p.front() && Math.abs(bt - doorT) <= 1) return false;
                int[] o = outsideCell(base, sd, bt);
                if (groundOpen(sd, bt, blen, faceClass(plan, p, sd, o[0], o[1])) != 0) return false;
            }
            return true;
        }

        /** The eave row on a side carries the roof's overhang (else a course). */
        boolean overhang(int sd) {
            return switch (p.roof()) {
                case FLAT -> false;
                case GABLE, SAW -> p.ridgeX() == (sd == N || sd == S);
                default -> true;
            };
        }
    }

    /**
     * What a face shows, by the ground in front of it: streets get the whole grid and the climb lines, yards and the
     * Garden Ward's lawns the grid; alleys are running faces (windows, nothing proud), the Quay's warehouse flanks blind
     * running faces; walls against a neighbour, a landmark or the curtain show nothing.
     */
    static int faceClass(PlanView plan, Plot p, int sd, int x, int z) {
        byte k = plan.kind(x, z);
        if (k == VOID && p.use().equals("farm")) return F_STREET; // a farmhouse faces its yard and the fields (the Commons step)
        if (k == PLOT || k == LANDMARK || k == WALL || k == VOID || water(k) || k == WATERGATE) return F_NONE;
        // (M4 B2: ground in front standing at the eave or over it, a terrace above a house's back, hides the face)
        if (plan.lift(x, z) >= p.lift() + p.eave() - 1) return F_NONE;
        boolean flank = p.district() == QUAY_D && sd != p.front() && sd != inward(p.front()) && p.storeys() >= 3;
        // (kit windows sit flush in the wall, so a run passes them: the flank keeps its windows)
        if (flank) return F_RUN;
        if (k == ALLEY) return sd == p.front() ? F_BACK : F_RUN;
        // a slot narrower than 3 between two houses can't take both faces' lips: it is a running face
        int depth = 0;
        while (depth < 3 && outsideOk(plan.kind(x + STEP[sd][0] * depth, z + STEP[sd][1] * depth))) depth++;
        if (depth < 3) return F_RUN;
        if (walkable(k)) return F_STREET;
        if (k == GARDEN) return p.district() == GARDEN_D ? F_STREET : F_BACK;
        if (k == YARD || k == FREE) return F_BACK;
        return F_NONE;
    }

    /** One plot, clipped to the buffer. */
    static void plot(Ctx c, CityBuffer b, Plot p) {
        Style st = style(p);
        int x0 = p.x0(), z0 = p.z0(), x1 = p.x1(), z1 = p.z1();
        Box base = new Box(x0, z0, x1, z1);
        boolean[] jetty = jetties(c.view, p, st, base);
        Box up = new Box(x0 - (jetty[W] ? 1 : 0), z0 - (jetty[N] ? 1 : 0), x1 + (jetty[E] ? 1 : 0), z1 + (jetty[S] ? 1 : 0));
        Facade f = new Facade(c, p, st, base, up, jetty);
        int g = p.lift();
        // everything a plot paints lies within 2 of its base box (the layout's margin)
        int ax0 = Math.max(x0 - 2, b.px0), ax1 = Math.min(x1 + 2, b.x1());
        int az0 = Math.max(z0 - 2, b.pz0), az1 = Math.min(z1 + 2, b.z1());
        for (int x = ax0; x <= ax1; x++) {
            for (int z = az0; z <= az1; z++) {
                if (base.contains(x, z)) groundStorey(f, b, x, z, g);
                else if (p.storeys() >= 2) corbel(f, b, x, z, g);
                for (int s = 1; s < p.storeys(); s++) {
                    if (up.contains(x, z)) upperStorey(f, b, x, z, g, s);
                }
                roof(f, b, x, z, g);
                outside(f, b, x, z, g);
            }
        }
        // (M4 B3a) signs on brackets out over the street, banners and flagpoles on the fronts
        CityFacadeLife.paint(f, b, g);
    }

    /** Jetties (Bellwatch only): once, at the first floor, over a lane 6+ wide, or 4-5 wide on its south or east side only, so the lane never closes at the lips. */
    static boolean[] jetties(PlanView plan, Plot p, Style st, Box base) {
        boolean[] j = new boolean[4];
        if (!st.jetties() || p.storeys() < 2) return j;
        return jettySides(plan, p, base, j);
    }

    private static boolean[] jettySides(PlanView plan, Plot p, Box base, boolean[] j) {
        for (int sd = 0; sd < 4; sd++) {
            int w = openWidth(plan, base.x0, base.z0, base.x1, base.z1, sd);
            j[sd] = w >= 6 || w >= 4 && (sd == S || sd == E);
        }
        return j;
    }

    /** The facade a plot gets (for the tests: its boxes, face classes, windows and climb lines). */
    static Facade facadeOf(Ctx c, Plot p) {
        Style st = style(p);
        Box base = new Box(p.x0(), p.z0(), p.x1(), p.z1());
        boolean[] jetty = jetties(c.view, p, st, base);
        Box up = new Box(p.x0() - (jetty[W] ? 1 : 0), p.z0() - (jetty[N] ? 1 : 0), p.x1() + (jetty[E] ? 1 : 0), p.z1() + (jetty[S] ? 1 : 0));
        return new Facade(c, p, st, base, up, jetty);
    }

    /**
     * Whether a cell next to plot p lies under a neighbour's jetty (the plan calls it street, but from row 5 up it is
     * that house's wall): nothing of p's goes there.
     */
    static boolean underForeignJetty(PlanView plan, Plot p, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int q = plan.plotAt(x + dx, z + dz);
                if (q < 0 || q == p.id()) continue;
                Plot o = plan.plot(q);
                if (o.district() != BELLWATCH || o.storeys() < 2) continue;
                Box ob = new Box(o.x0(), o.z0(), o.x1(), o.z1());
                boolean[] j = jettySides(plan, o, ob, new boolean[4]);
                if (x >= o.x0() - (j[W] ? 1 : 0) && x <= o.x1() + (j[E] ? 1 : 0) && z >= o.z0() - (j[N] ? 1 : 0) && z <= o.z1() + (j[S] ? 1 : 0))
                    return true;
            }
        }
        return false;
    }

    /**
     * Whether the outside cell of side sd at (x, z) also stands in front of another house's wall (an inside corner where
     * two fronts meet, or a neighbour's jetty beside it): neither face puts anything there.
     */
    static boolean shared(PlanView plan, Plot p, int sd, int x, int z) {
        int back = inward(sd);
        for (int d = 0; d < 4; d++) {
            if (d == back) continue;
            int nx = x + STEP[d][0], nz = z + STEP[d][1];
            int q = plan.plotAt(nx, nz);
            if (q >= 0 && q != p.id()) return true;
            if (q < 0 && underForeignJetty(plan, p, nx, nz) && !inUp(plan, p, nx, nz)) return true;
        }
        return false;
    }

    /** Whether a cell lies in plot p's own upper storeys (its base box plus its jetties). */
    private static boolean inUp(PlanView plan, Plot p, int x, int z) {
        if (p.district() != BELLWATCH || p.storeys() < 2) return x >= p.x0() && x <= p.x1() && z >= p.z0() && z <= p.z1();
        boolean[] j = jettySides(plan, p, new Box(p.x0(), p.z0(), p.x1(), p.z1()), new boolean[4]);
        return x >= p.x0() - (j[W] ? 1 : 0) && x <= p.x1() + (j[E] ? 1 : 0) && z >= p.z0() - (j[N] ? 1 : 0) && z <= p.z1() + (j[S] ? 1 : 0);
    }

    /** A mill, an inn or any landmark part built like a house: storeys and roof, no outside. */
    static void building(Ctx c, CityBuffer b, Plot p, Style st, Box box, int x, int z, int g) {
        Facade f = new Facade(c, p, st, box, box, new boolean[4]);
        if (!box.contains(x, z)) return;
        groundStorey(f, b, x, z, g);
        for (int s = 1; s < p.storeys(); s++) upperStorey(f, b, x, z, g, s);
        roof(f, b, x, z, g);
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

        /** For a cell diagonal to a corner: the north-or-south side, else -1. */
        int diagNS(int x, int z) {
            if (x != x0 - 1 && x != x1 + 1) return -1;
            return z == z0 - 1 ? N : z == z1 + 1 ? S : -1;
        }

        int diagWE(int x) {
            return x == x0 - 1 ? W : E;
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
    static int openWidth(PlanView plan, int x0, int z0, int x1, int z1, int side) {
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

    // ---- the wall plane -------------------------------------------------------------------------------------------

    static BlockState window(Facade f, int style, int half, int axis) {
        return f.p.enter() ? PANE[axis] : WINDOW[style][half];
    }

    static void groundStorey(Facade f, CityBuffer b, int x, int z, int g) {
        Plot p = f.p;
        Style st = f.st;
        Box base = f.base;
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
        int[] o = f.outsideCell(base, sd, t);
        int fc = faceClass(f.plan, p, sd, o[0], o[1]);
        int open = corner ? 0 : f.groundOpen(sd, t, len, fc);
        for (int r = 1; r <= 5; r++) {
            BlockState s;
            if (r == 5) s = st.timber() ? beam(st, axis) : corner ? st.quoin() : st.ground();
            else if (corner) s = ((r + 1) >> 1 & 1) == 1 ? st.quoin() : st.ground();
            else if (open == 2 && r <= 2) s = VillageStyles.isVillage(p) ? VillageStyles.door(p, r == 2, inward(sd))
                    : (r == 1 ? DOOR_LOWER : DOOR_UPPER)[doorKind(p)][inward(sd)];
            else if (open == 2 && r == 3) s = st.keystone();
            else if (open == 3 && r <= 2) s = window(f, W_SHOP, r - 1, axis);
            else if (open == 1 && st.smallGround() && r == 2) s = window(f, st.groundWin(), 2, axis);
            else if (open == 1 && !st.smallGround() && r <= 2) s = window(f, st.groundWin(), r - 1, axis);
            else if (r == 4 && st.shopfront() && fc == F_STREET && t >= 1 && t <= len - 2) s = FASCIA;
            else s = weathered(f.c, st.ground(), x, z, r);
            b.set(x, g + r, z, s);
        }
    }

    static int doorKind(Plot p) {
        return p.district() == QUAY_D ? 0 : p.district() == GARDEN_D || p.district() == TERRACE ? 2 : 1;
    }

    static BlockState beam(Style st, int axis) {
        return axis == 0 ? T_BEAM_X[st.tone()] : T_BEAM_Z[st.tone()];
    }

    static BlockState weathered(Ctx c, BlockState s, int x, int z, int r) {
        if (s != STONE_BRICKS && s != COBBLE) return s;
        int h = c.pct(x, z, 200 + r);
        if (s == STONE_BRICKS) return h < 8 ? MOSSY_STONE_BRICKS : h < 16 ? CRACKED_STONE_BRICKS : s;
        return h < 15 ? MOSSY_COBBLE : s;
    }

    static void upperStorey(Facade f, CityBuffer b, int x, int z, int g, int s) {
        Plot p = f.p;
        Style st = f.st;
        Box up = f.up;
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
        int fc = f.upClass(sd, t);
        boolean shows = fc >= F_RUN;
        boolean bay = shows && f.inBay(sd, t);
        boolean win = shows && !bay && f.win(sd, t, len, s);
        boolean pilaster = st.pilaster() != null && shows && fc >= F_BACK && !corner && len >= 9 && Math.min(t, len - 1 - t) % 3 == 0 && !win;
        for (int r = 1; r <= 4; r++) {
            BlockState bs;
            if (bay && (r == 2 || r == 3)) bs = loadingDoor(f, sd, t, r == 3);
            else if (win && (r == 2 || r == 3)) bs = window(f, st.win(), r - 2, axis);
            else if (st.timber()) {
                if (corner || t % 3 == 0) bs = T_POST[st.tone()];
                else if (r == 4) bs = beam(st, axis);
                else bs = st.wall();
            } else if (corner) bs = ((r + 1) >> 1 & 1) == 1 ? st.quoin() : st.wall();
            else if (pilaster) bs = st.pilaster();
            else bs = st.wall();
            b.set(x, g + base + r, z, bs);
        }
    }

    /** A quarter of a loading door: the viewer's left leaf is the higher column on north and east faces. */
    static BlockState loadingDoor(Facade f, int sd, int t, boolean upper) {
        if (f.p.enter()) return PANE[sd == N || sd == S ? 0 : 1];
        boolean left = (t == f.bay[sd] + 1) == (sd == N || sd == E);
        return LOADING[(upper ? 2 : 0) + (left ? 0 : 1)][sd];
    }

    /** Corbels under a jetty: upside-down stairs every other block along the overhang. */
    static void corbel(Facade f, CityBuffer b, int x, int z, int g) {
        int sd = f.base.outside(x, z);
        if (sd < 0 || !f.jetty[sd]) return;
        if ((f.base.along(sd, x, z) & 1) == 0) b.set(x, g + 5, z, CORBEL[inward(sd)]);
    }

    // ---- the outside plane: the climbing grid ----------------------------------------------------------------------

    /** Sets a block in the outside plane unless the ground there stands that high (a terrace edge, a stair). */
    static void put(Facade f, CityBuffer b, int x, int ry, int z, BlockState s) {
        if (ry <= f.plan.lift(x, z)) return;
        b.set(x, ry, z, s);
    }

    static void outside(Facade f, CityBuffer b, int x, int z, int g) {
        Plot p = f.p;
        int bs = f.base.outside(x, z);
        boolean foreign = (bs >= 0 || f.up.outside(x, z) >= 0 || f.up.diagNS(x, z) >= 0) && underForeignJetty(f.plan, p, x, z);
        if (bs >= 0 && outsideOk(f.plan.kind(x, z)) && !shared(f.plan, p, bs, x, z)) groundPlane(f, b, bs, x, z, g, foreign);
        if (foreign) return;
        if (!outsideOk(f.plan.kind(x, z))) return;
        int us = f.up.outside(x, z);
        if (us >= 0) {
            if (!shared(f.plan, p, us, x, z)) upperPlane(f, b, us, x, z, g);
        } else if (f.up.diagNS(x, z) >= 0) {
            corner(f, b, x, z, g);
        }
    }

    static boolean outsideOk(byte k) {
        return walkable(k) || k == YARD || k == GARDEN || k == FREE;
    }

    /** Rows 1-5 in front of the ground storey: the hood over each opening (row 3), the shop cornice (row 5). */
    static void groundPlane(Facade f, CityBuffer b, int sd, int x, int z, int g, boolean foreign) {
        Plot p = f.p;
        Style st = f.st;
        int t = f.base.along(sd, x, z), len = f.base.length(sd);
        int fc = faceClass(f.plan, p, sd, x, z);
        int in = inward(sd);
        int open = f.groundOpen(sd, t, len, fc);
        if (fc < F_BACK) {
            if (open == 2) put(f, b, x, g + 3, z, st.hood().straight()[in]);
            return;
        }
        // (M4 B3a) a striped awning over every shopfront and over the doors of shops and taverns: a hold at the hood's
        // row and a soft landing that bounces (bible §3.6); elsewhere the hood over each opening
        boolean awning = CityFacadeLife.awning(f, sd, t, len, fc);
        if (awning) put(f, b, x, g + 3, z, CityProps.AWNING_KIT[sd][f.awningColour][0]);
        else if (open != 0) put(f, b, x, g + 3, z, st.hood().straight()[in]);
        // a lantern by the doors people go in by, and by some house doors: it glows, it lights nothing (decision 26)
        if (sd == p.front() && p.hasDoor() && t == f.doorT + 1
                && (p.enter() || p.use().equals("tavern") || f.c.pct(p.id(), 3, 67) < 30)) {
            put(f, b, x, g + (awning ? 2 : 3), z, CityFacadeLife.WALL_LANTERN[sd]);
        }
        // (a one-storey house's eave is its row 5: the roof carries it there)
        if (!f.jetty[sd] && !foreign && (p.eave() > 5 || !f.overhang(sd))) put(f, b, x, g + 5, z, st.cornice().straight()[in]);
    }

    /** Rows 5 to the eave in front of the upper storeys: beam ends, boxes, shutters, courses, then the climb line. */
    static void upperPlane(Facade f, CityBuffer b, int sd, int x, int z, int g) {
        Plot p = f.p;
        Style st = f.st;
        Box up = f.up;
        int t = up.along(sd, x, z), len = up.length(sd);
        int fc = f.upClass(sd, t);
        int in = inward(sd);
        int eave = p.eave();
        if (fc >= F_BACK && p.storeys() >= 2) {
            // a jetty's beam ends, two out from the ground wall, in the row of the corbels (moulded, as the courses)
            if (f.jetty[sd]) {
                int bt = t - f.baseOffset(sd);
                if (bt >= 0 && bt < f.base.length(sd) && (bt & 1) == 0) put(f, b, x, g + 5, z, st.course().straight()[in]);
            }
            boolean street = fc == F_STREET;
            int middle = len / 2;
            for (int s = 1; s < p.storeys(); s++) {
                int r0 = 5 + 4 * (s - 1);
                boolean win = f.win(sd, t, len, s);
                if (s == 1 && f.inOriel(sd, t)) {
                    // the oriel: an apron, then glass all round, roofed by the floor course
                    int axis = sd == N || sd == S ? 0 : 1;
                    put(f, b, x, g + r0 + 1, z, st.wall());
                    put(f, b, x, g + r0 + 2, z, window(f, st.win(), 0, axis));
                    put(f, b, x, g + r0 + 3, z, window(f, st.win(), 1, axis));
                } else if (f.inBay(sd, t)) {
                    put(f, b, x, g + r0 + 2, z, SILL_BOARD[sd]);
                } else if (win || f.balcony && s == 1 && street && Math.abs(t - middle) <= 1 && t >= 1 && t <= len - 2) {
                    put(f, b, x, g + r0 + 2, z, box(f, sd, t, s, win ? st.boxStyle() : 1));
                } else if (street && f.shutters && t >= 0 && t < len) {
                    boolean leftWin = f.win(sd, t - 1, len, s), rightWin = f.win(sd, t + 1, len, s);
                    if (leftWin != rightWin) {
                        // a shutter hinges on the edge beside its window
                        boolean viewerLeft = (rightWin && (sd == S || sd == W)) || (leftWin && (sd == N || sd == E));
                        int hinge = viewerLeft ? 1 : 0;
                        put(f, b, x, g + r0 + 2, z, SHUTTER_KIT[f.shutterColour][hinge][0][sd]);
                        put(f, b, x, g + r0 + 3, z, SHUTTER_KIT[f.shutterColour][hinge][1][sd]);
                    }
                }
                // the floor course; the top one is the eave, carried by the roof where it overhangs
                int cr = r0 + 4;
                if (cr < eave || !f.overhang(sd)) put(f, b, x, g + cr, z, st.course().straight()[in]);
            }
        }
        // the climb line last: a drainpipe (or a trellis of ivy) from the street to the eave; the courses break for it
        if (fc == F_STREET && f.climb[sd][t]) {
            int low = Math.max(1, f.plan.lift(x, z) - g + 1);
            boolean trellis = f.trellis && !f.jetty[sd];
            for (int r = low; r <= eave; r++) {
                BlockState s;
                if (trellis) s = f.c.pct(x * 31 + r, z, 64) < 82 || r <= low + 1 ? TRELLIS_IVY[sd] : TRELLIS[sd];
                else s = r == low ? PIPE_SHOE[sd] : r == eave ? PIPE_HEAD[sd] : PIPE[sd];
                b.set(x, g + r, z, s);
            }
        }
        hoist(f, b, sd, t, x, z, g);
    }

    /** A window's box: wood, iron or stone; flowers on some (seeded), a plain sill box on the rest. */
    static BlockState box(Facade f, int sd, int t, int s, int style) {
        Style st = f.st;
        int bloom = 0;
        if (f.c.pct(f.p.id() * 8 + sd, s * 16 + t, 60) < st.bloomPct()) {
            int[] bl = st.blooms();
            bloom = bl[f.c.pct(f.p.id(), sd * 64 + s * 16 + t, 61) % bl.length];
        }
        return BOX[style][bloom][sd];
    }

    /** The course round an outside corner, so a shimmy turns it (rows 5 where nothing is jettied, then every floor). */
    static void corner(Facade f, CityBuffer b, int x, int z, int g) {
        Box up = f.up;
        int a = up.diagNS(x, z), c = up.diagWE(x);
        int ta = c == W ? 0 : up.length(a) - 1, tc = a == N ? 0 : up.length(c) - 1;
        if (f.upClass(a, ta) < F_BACK || f.upClass(c, tc) < F_BACK) return;
        if (!freeCorner(f, x, z)) return;
        Style st = f.st;
        int ia = inward(a), ic = inward(c);
        if (!f.jetty[a] && !f.jetty[c] && f.p.eave() > 5) put(f, b, x, g + 5, z, st.cornice().corner(ia, ic));
        if (f.p.storeys() < 2) return;
        for (int s = 1; s < f.p.storeys(); s++) {
            int cr = 5 + 4 * s;
            if (cr < f.p.eave() || !f.overhang(a) || !f.overhang(c)) put(f, b, x, g + cr, z, st.course().corner(ia, ic));
        }
    }

    /** A free-standing outside corner: never in front of another house's face (its own lips and lines go there). */
    static boolean freeCorner(Facade f, int x, int z) {
        for (int[] d : STEP) {
            int nx = x + d[0], nz = z + d[1];
            int q = f.plan.plotAt(nx, nz);
            if (q >= 0 && q != f.p.id() || q < 0 && !f.up.contains(nx, nz) && underForeignJetty(f.plan, f.p, nx, nz)) return false;
        }
        return true;
    }

    // ---- roofs ----------------------------------------------------------------------------------------------------

    static void roof(Facade f, CityBuffer b, int x, int z, int g) {
        Plot p = f.p;
        Style st = f.st;
        Box up = f.up;
        PlanView plan = f.plan;
        int e = g + p.eave();
        boolean inside = up.contains(x, z);
        if (!inside) {
            // the overhang: only over open ground, never onto a neighbour's wall; hips turn their corners
            byte k = plan.kind(x, z);
            if (k == PLOT || k == LANDMARK || p.roof() == CityPlan.Roof.FLAT || !outsideOk(k)) return;
            if (underForeignJetty(plan, p, x, z)) return;
            int os = up.outside(x, z);
            if (os >= 0) {
                if (f.overhang(os)) b.set(x, e, z, st.roof().stair()[inward(os)]);
                return;
            }
            int a = up.diagNS(x, z);
            if (a >= 0 && (p.roof() == CityPlan.Roof.HIP || p.roof() == CityPlan.Roof.MANSARD) && freeCorner(f, x, z)) {
                int ia = inward(a), ic = inward(up.diagWE(x));
                Roof r = st.roof();
                b.set(x, e, z, ic == ccw(ia) ? r.outerLeft()[ia] : r.outerRight()[ia]);
            }
            return;
        }
        int dW = x - up.x0 + 1, dE = up.x1 - x + 1, dN = z - up.z0 + 1, dS = up.z1 - z + 1;
        boolean wallCell = up.side(x, z) >= 0;
        if (dormer(f, b, x, z, e, dW, dE, dN, dS)) {
            chimney(f, b, x, z, e);
            return;
        }
        switch (p.roof()) {
            case FLAT -> {
                b.set(x, e, z, st.flat());
                if (wallCell) balustrade(f, b, x, z, e);
            }
            case GABLE -> gable(f, b, x, z, e, dW, dE, dN, dS);
            case SAW -> saw(b, p, st, up, x, z, e);
            case HIP, MANSARD -> hip(f, b, x, z, e, dW, dE, dN, dS, p.roof() == CityPlan.Roof.MANSARD);
        }
        chimney(f, b, x, z, e);
        // attic sealed: nothing hollow under any roof
        if (!wallCell && b.get(x, e, z) == null) b.set(x, e, z, CORE);
    }

    /** A flat roof's balustrade (a "no": walls can't be held), open where a climb line arrives so a climber gets on. */
    static void balustrade(Facade f, CityBuffer b, int x, int z, int e) {
        if (gapAt(f, x, z)) return;
        boolean diorite = f.p.district() == TERRACE;
        int m = 0;
        for (int d = 0; d < 4; d++) {
            int nx = x + STEP[d][0], nz = z + STEP[d][1];
            if (f.up.side(nx, nz) >= 0 && !gapAt(f, nx, nz)) m |= 1 << d;
        }
        b.set(x, e + 1, z, (diorite ? DIORITE_WALLS : STONE_WALLS)[m]);
    }

    /** A wall cell beside (or behind) a climb column or a corner of a flat roof's climbing bay. */
    static boolean gapAt(Facade f, int x, int z) {
        Box up = f.up;
        for (int sd = 0; sd < 4; sd++) {
            boolean onSide = switch (sd) {
                case N -> z == up.z0;
                case S -> z == up.z1;
                case W -> x == up.x0;
                default -> x == up.x1;
            };
            if (!onSide) continue;
            int t = up.along(sd, x, z);
            boolean[] cl = f.climb[sd];
            for (int d = -1; d <= 1; d++) if (t + d >= 0 && t + d < cl.length && cl[t + d]) return true;
        }
        return false;
    }

    static void gable(Facade f, CityBuffer b, int x, int z, int e, int dW, int dE, int dN, int dS) {
        Plot p = f.p;
        Style st = f.st;
        Box up = f.up;
        boolean rx = p.ridgeX();
        int d = rx ? Math.min(dN, dS) : Math.min(dW, dE);
        int span = rx ? up.d() : up.w();
        int hs = (span + 1) / 2;
        boolean lowSide = rx ? dN <= dS : dW <= dE;
        int upDir = rx ? (lowSide ? S : N) : (lowSide ? E : W);
        int h = e + rise(p.steep(), d);
        boolean gableEnd = rx ? (x == up.x0 || x == up.x1) : (z == up.z0 || z == up.z1);
        int endSide = rx ? (x == up.x0 ? W : E) : (z == up.z0 ? N : S);
        boolean ridge = d == hs && (span & 1) == 1;
        boolean top = d == hs;
        Roof roof = st.roof();
        if (gableEnd && st.crow()) {
            // a crow-stepped gable: the wall stands one over the roof in steps, each capped (the staircase to the ridge)
            int wallTop = ridge ? h + 1 : h;
            for (int y = e + 1; y <= wallTop; y++) b.set(x, y, z, gableFace(f, endSide, x, y, z, e, h, rx));
            b.set(x, wallTop + 1, z, st.district() == QUAY_D ? STONE_SLAB : CAP_SMOOTH);
            return;
        }
        // the solid under the slope: gable walls at the ends, roof material just under the surface, core within
        for (int y = e + 1; y < h; y++) {
            BlockState s;
            if (gableEnd) s = gableFace(f, endSide, x, y, z, e, h, rx);
            else s = y >= h - 2 ? roof.block() : CORE;
            b.set(x, y, z, s);
        }
        if (top) {
            if (ridge) b.set(x, h, z, roof.block());
            else b.set(x, h, z, roof.stair()[upDir]);
            b.set(x, h + 1, z, st.cap());
        } else {
            boolean moss = (st.district() == BELLWATCH || st.district() == WALLRING) && f.c.pct(x, z, 301) < 5;
            b.set(x, h, z, moss ? MOSSY_STAIR[upDir] : roof.stair()[upDir]);
        }
    }

    /**
     * A cell of a gable's wall: timber framing (or masonry under crow steps), an attic window at the middle, the Quay's
     * loading doors going on up to the hoist.
     */
    static BlockState gableFace(Facade f, int endSide, int x, int y, int z, int e, int h, boolean rx) {
        Style st = f.st;
        Box up = f.up;
        int t = up.along(endSide, x, z), len = up.length(endSide);
        int fc = f.upClass(endSide, t);
        int axis = endSide == N || endSide == S ? 0 : 1;
        if (fc >= F_RUN) {
            if (f.inBay(endSide, t) && (y == e + 1 || y == e + 2) && h >= e + 3) return loadingDoor(f, endSide, t, y == e + 2);
            boolean middle = t == (len - 1) / 2;
            if (middle && f.bay[endSide] < 0 && y == e + 2 && h >= e + 3 && len >= 5)
                return window(f, st.district() == MARKET ? W_ROUND : W_DORMER, 2, axis);
        }
        if (st.timber() && !st.crow()) {
            int along = rx ? z : x;
            if ((y - e) % 4 == 0) return rx ? T_BEAM_Z[st.tone()] : T_BEAM_X[st.tone()];
            return Math.floorMod(along, 3) == 0 ? T_POST[st.tone()] : st.wall();
        }
        if (st.crow() && st.district() == BELLWATCH) return f.st.ground();
        return st.wall();
    }

    static void hip(Facade f, CityBuffer b, int x, int z, int e, int dW, int dE, int dN, int dS, boolean mansard) {
        Plot p = f.p;
        Style st = f.st;
        Box up = f.up;
        int dX = Math.min(dW, dE), dZ = Math.min(dN, dS);
        int d = Math.min(dX, dZ);
        int hs = (Math.min(up.w(), up.d()) + 1) / 2;
        Roof roof = st.roof();
        if (mansard) {
            if (d >= 3) {
                // the deck: a flat upper street, its curb in verdigris
                for (int y = e + 1; y <= e + 4; y++) b.set(x, y, z, y == e + 4 ? (d == 3 ? VERDIGRIS : roof.block()) : CORE);
                b.set(x, e + 5, z, SMOOTH_SLAB);
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
        if (d >= hs) {
            b.set(x, h, z, roof.block());
            b.set(x, h + 1, z, st.cap());
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

    /**
     * Dormers (bible §4): on Bellwatch's steep roofs over an eave of 9+ and all round the Market's mansards, every 4
     * along each open eave, 3 wide: a window between two 2-tall cheeks under its own little gable, whose ridge runs
     * into the slope. Returns true where it painted the cell's column.
     */
    static boolean dormer(Facade f, CityBuffer b, int x, int z, int e, int dW, int dE, int dN, int dS) {
        Plot p = f.p;
        Style st = f.st;
        if (!st.dormers()) return false;
        boolean mansard = p.roof() == CityPlan.Roof.MANSARD;
        if (!mansard && !(p.steep() && p.eave() >= 9 && (p.roof() == CityPlan.Roof.GABLE || p.roof() == CityPlan.Roof.HIP))) return false;
        Box up = f.up;
        int sd = dN == 1 ? N : dS == 1 ? S : dW == 1 ? W : dE == 1 ? E : -1;
        if (sd < 0) return false;
        if (!f.overhang(sd)) return false;
        int len = up.length(sd), t = up.along(sd, x, z);
        // inside the slope, clear of the hips and the gable ends
        int other = sd == N || sd == S ? Math.min(dW, dE) : Math.min(dN, dS);
        if (other < (p.roof() == CityPlan.Roof.GABLE ? 2 : 3)) return false;
        int a = -1;
        for (int k = t - 1; k <= t + 1; k++) if (k >= 3 && k <= len - 4 && (k - 3) % 4 == 0) a = k;
        if (a < 0) return false;
        // (a in [3, len - 4] keeps the whole footprint on the slope, clear of hip lines and gable ends)
        if (f.upClass(sd, a) < F_RUN) return false;
        if (chimneyAt(f, x, z)) return false;
        int axis = sd == N || sd == S ? 0 : 1;
        BlockState cheek = st.dormerCheek();
        if (t == a) {
            b.set(x, e + 1, z, cheek);
            b.set(x, e + 2, z, window(f, W_DORMER, 2, axis));
            b.set(x, e + 3, z, st.roof().block());
            b.set(x, e + 4, z, st.cap());
        } else {
            b.set(x, e + 1, z, cheek);
            b.set(x, e + 2, z, cheek);
            // the little gable's slope rises toward the dormer's middle
            int toward = sd == N || sd == S ? (t < a ? E : W) : (t < a ? S : N);
            b.set(x, e + 3, z, st.roof().stair()[toward]);
        }
        return true;
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

    /** Where a plot's chimney stands (its footprint's north-west cell), or null. */
    static int[] chimneySpot(Facade f) {
        Plot p = f.p;
        Box up = f.up;
        int roll = Ctx.shape(p.id(), 7, 77);
        boolean works = f.st.tallChimney();
        if (!works && (roll >= 55 || p.roof() == CityPlan.Roof.FLAT)) return null;
        if (works && roll >= 28) return null;
        if (p.ridgeX()) return new int[]{roll % 2 == 0 ? up.x0 + 1 : up.x1 - (works ? 2 : 1), (up.z0 + up.z1) / 2};
        return new int[]{(up.x0 + up.x1) / 2, roll % 2 == 0 ? up.z0 + 1 : up.z1 - (works ? 2 : 1)};
    }

    static boolean chimneyAt(Facade f, int x, int z) {
        int[] c = chimneySpot(f);
        if (c == null) return false;
        int size = f.st.tallChimney() ? 2 : 1;
        return x >= c[0] - 1 && x <= c[0] + size && z >= c[1] - 1 && z <= c[1] + size;
    }

    /** A brick stack on one gable, capped by the kit's crown with a pot on it (smoke comes from the pot); stacks in the Works. */
    static void chimney(Facade f, CityBuffer b, int x, int z, int e) {
        Plot p = f.p;
        Style st = f.st;
        Box up = f.up;
        int[] cs = chimneySpot(f);
        if (cs == null) return;
        boolean works = st.tallChimney();
        int roll = Ctx.shape(p.id(), 7, 77);
        int size = works ? 2 : 1;
        if (x < cs[0] || x >= cs[0] + size || z < cs[1] || z >= cs[1] + size) return;
        int span = p.ridgeX() ? up.d() : up.w();
        int top = e + rise(p.steep(), (span + 1) / 2) + (works ? 14 + roll % 7 : 2);
        if (works) {
            BlockState brick = st.wall() == CINNABAR_BRICKS ? CINNABAR_BRICKS : CITY_BRICKS;
            for (int y = e; y <= top; y++) b.set(x, y, z, (top - y) == 2 ? POLISHED_BLACKSTONE_BRICKS : brick);
            b.set(x, top + 1, z, STONE_SLAB);
            return;
        }
        BlockState brick = st.crown() == 1 ? MUD_BRICKS : st.crown() == 2 ? STONE_BRICKS : CITY_BRICKS;
        for (int y = e; y < top; y++) b.set(x, y, z, brick);
        b.set(x, top, z, CROWN[st.crown()]);
        b.set(x, top + 1, z, POT);
    }

    /**
     * The Quay's hoist: where a warehouse turns its stepped gable to the street, a beam out of the gable's top over the
     * loading bay and a rope down to the street (a climb line, and a bar to hang from 17+ up).
     */
    static void hoist(Facade f, CityBuffer b, int sd, int t, int x, int z, int g) {
        Plot p = f.p;
        if (!f.st.loading() || f.bay[sd] < 0 || t != f.bay[sd] || p.roof() != CityPlan.Roof.GABLE) return;
        boolean gableFront = p.ridgeX() == (sd == W || sd == E);
        if (!gableFront) return;
        Box up = f.up;
        int span = p.ridgeX() ? up.d() : up.w();
        int dAcross = (p.ridgeX() ? Math.min(z - up.z0, up.z1 - z) : Math.min(x - up.x0, up.x1 - x)) + 1;
        int h = p.eave() + rise(p.steep(), Math.min(dAcross, (span + 1) / 2));
        int beam = Math.min(h, p.eave() + 4);
        if (beam < p.eave() + 3) return;
        int low = Math.max(1, f.plan.lift(x, z) - g + 1);
        b.set(x, g + beam, z, HOIST[sd]);
        for (int r = low; r < beam; r++) b.set(x, g + r, z, ROPE);
    }
}
