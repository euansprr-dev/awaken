package dev.awaken.city;

import dev.awaken.city.CityGrammar.Facade;
import dev.awaken.city.CityPlan.Plot;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The street life a facade carries (Milestone 4 step B3a; look bible §3.6, §3.7, §5): striped awnings over the
 * shopfronts, trade signs hung from wrought-iron brackets out over the street (or flat on the fascia where the street is
 * narrow or the house jettied), long banners down the Terrace's formal fronts, flagpoles with banners on the Market's
 * tall houses, and lanterns by the doors. Brackets reach three out from the wall, so plots are indexed with a margin of
 * three (CityLayout.PLOT_MARGIN).
 *
 * <p>Shapes come from the plan (which houses, where); the world seed picks only colours and trades' order.
 */
final class CityFacadeLife {
    private CityFacadeLife() {}

    static final BlockState[] WALL_LANTERN = dirs("awaken:wall_lantern[facing=%s]");
    static final String[] TRADES = {"bread", "fish", "produce", "cloth", "pottery", "candles", "flowers", "spice", "tools", "cheese",
            "tavern", "boots", "books", "keys"};
    static final int TAVERN = 10;
    /** sign_bracket[facing][arm, end] */
    static final BlockState[][] BRACKET = new BlockState[4][2];
    /** signboard[facing][trade][hanging, flat] */
    static final BlockState[][][] SIGN = new BlockState[4][TRADES.length][2];
    static final String[] DESIGN = {"bellwatch", "quay", "market", "terrace", "works", "garden", "wallring", "association"};
    /** banner_cloth[facing][design][top, middle, bottom][wall, pole] */
    static final BlockState[][][][] BANNER = new BlockState[4][DESIGN.length][3][2];
    static final BlockState[] FLAGPOLE = dirs("awaken:flagpole[facing=%s]");

    static {
        String[] parts = {"top", "middle", "bottom"};
        for (int d = 0; d < 4; d++) {
            String f = CityProps.SIDE[d];
            BRACKET[d][0] = of("awaken:sign_bracket[facing=" + f + ",part=arm]");
            BRACKET[d][1] = of("awaken:sign_bracket[facing=" + f + ",part=end]");
            for (int t = 0; t < TRADES.length; t++) {
                SIGN[d][t][0] = of("awaken:signboard[facing=" + f + ",trade=" + TRADES[t] + ",mount=hanging]");
                SIGN[d][t][1] = of("awaken:signboard[facing=" + f + ",trade=" + TRADES[t] + ",mount=flat]");
            }
            for (int k = 0; k < DESIGN.length; k++)
                for (int p = 0; p < 3; p++) {
                    BANNER[d][k][p][0] = of("awaken:banner_cloth[facing=" + f + ",design=" + DESIGN[k] + ",part=" + parts[p] + ",mount=wall]");
                    BANNER[d][k][p][1] = of("awaken:banner_cloth[facing=" + f + ",design=" + DESIGN[k] + ",part=" + parts[p] + ",mount=pole]");
                }
        }
    }

    /** A plot that trades from its ground floor (the Market's shopfronts; shops and taverns anywhere but the Terrace). */
    static boolean trades(Plot p) {
        return p.use().equals("shop") || p.use().equals("tavern");
    }

    /** Whether the outside cell at column t of side sd (base coordinates) carries an awning at row 3. */
    static boolean awning(Facade f, int sd, int t, int len, int fc) {
        if (fc != CityGrammar.F_STREET || t < 1 || t > len - 2) return false;
        if (f.st.shopfront()) return true;
        Plot p = f.p;
        return trades(p) && p.district() != TERRACE && sd == p.front() && f.doorT >= 0 && Math.abs(t - f.doorT) <= 2;
    }

    /** The trade a plot's sign shows (the same in every world: taverns their tankard, shops by their place). */
    static int trade(Plot p) {
        if (p.use().equals("tavern")) return TAVERN;
        int k = CityPainter.Ctx.shape(p.id(), 21, 81) % (TRADES.length - 1);
        return k >= TAVERN ? k + 1 : k;
    }

    static void paint(Facade f, CityBuffer b, int g) {
        Plot p = f.p;
        if (!p.hasDoor()) return;
        sign(f, b, g);
        if (p.district() == TERRACE) banners(f, b, g);
        if (p.district() == MARKET) flagpole(f, b, g);
    }

    /** Which houses hang a sign: every Market shopfront, shops and taverns, and some old-town houses near the bell square. */
    static boolean signed(Facade f) {
        Plot p = f.p;
        if (trades(p) || f.st.shopfront()) return true;
        if (p.district() != BELLWATCH) return false;
        int dx = p.doorX() + 150, dz = p.doorZ() + 62;
        return dx * dx + dz * dz < 48 * 48 && CityPainter.Ctx.shape(p.id(), 22, 82) < 30;
    }

    /**
     * The trade sign: on a bracket at row 5 reaching two and three out over a street at least five wide, beside the door
     * (catch it from the awning, swing off it: runner_swings); flat over the door on the fascia where the street is
     * narrower or the front jettied.
     */
    static void sign(Facade f, CityBuffer b, int g) {
        if (!signed(f)) return;
        Plot p = f.p;
        int sd = p.front(), len = f.base.length(sd);
        int[] step = CityGrammar.STEP[sd];
        int trade = trade(p);
        if (!f.jetty[sd]) {
            for (int dt : new int[]{2, -2, 3, -3}) {
                int t = f.doorT + dt;
                if (t < 1 || t > len - 2) continue;
                int[] o = f.outsideCell(f.base, sd, t);
                if (CityGrammar.faceClass(f.plan, p, sd, o[0], o[1]) != CityGrammar.F_STREET) continue;
                if (CityGrammar.openWidth(f.plan, o[0] - step[0], o[1] - step[1], o[0] - step[0], o[1] - step[1], sd) < 5) continue;
                int ax = o[0] + step[0], az = o[1] + step[1], ex = ax + step[0], ez = az + step[1];
                if (f.plan.treeAt(ax, az) || f.plan.treeAt(ex, ez) || lampNear(f.plan, ex, ez)) continue;
                if (g + 4 <= f.plan.lift(ex, ez) + 2) continue;
                CityGrammar.put(f, b, ax, g + 5, az, BRACKET[sd][0]);
                CityGrammar.put(f, b, ex, g + 5, ez, BRACKET[sd][1]);
                CityGrammar.put(f, b, ex, g + 4, ez, SIGN[sd][trade][0]);
                return;
            }
        }
        // flat over the door: on the fascia (row 4) in front of the wall
        int[] o = f.outsideCell(f.base, sd, f.doorT);
        if (CityGrammar.faceClass(f.plan, p, sd, o[0], o[1]) < CityGrammar.F_BACK) return;
        CityGrammar.put(f, b, o[0], g + 4, o[1], SIGN[sd][trade][1]);
    }

    static boolean lampNear(CityPlan plan, int x, int z) {
        for (int[] l : plan.lamps) if (Math.abs(l[0] - x) <= 1 && Math.abs(l[1] - z) <= 1) return true;
        return false;
    }

    /** The column (up coordinates) of a side nearest `want` that has no window, climb line or bay, or -1. */
    static int blindColumn(Facade f, int sd, int want) {
        int len = f.up.length(sd);
        for (int d = 0; d < len; d++) {
            for (int t : new int[]{want - d, want + d}) {
                if (t < 1 || t > len - 2) continue;
                if (f.anyWin(sd, t, len) || f.climb[sd][t] || f.inOriel(sd, t)) continue;
                if (f.upClass(sd, t) != CityGrammar.F_STREET) continue;
                int[] o = f.outsideCell(f.up, sd, t);
                if (CityGrammar.shared(f.plan, f.p, sd, o[0], o[1]) || f.plan.treeAt(o[0], o[1])) continue;
                return t;
            }
        }
        return -1;
    }

    /**
     * The Terrace's banners: two long cloths down each formal front of three storeys or more, under the second floor's
     * course (rows 10-12, between the windows), in the Terrace's or the Association's colours.
     */
    static void banners(Facade f, CityBuffer b, int g) {
        Plot p = f.p;
        if (p.storeys() < 3 || p.eave() < 13) return;
        int sd = p.front(), len = f.up.length(sd);
        int design = (p.id() & 1) == 0 ? 3 : 7;
        for (int want : new int[]{1, len - 2}) {
            int t = blindColumn(f, sd, want);
            if (t < 0 || Math.abs(t - want) > 2) continue;
            int[] o = f.outsideCell(f.up, sd, t);
            CityGrammar.put(f, b, o[0], g + 12, o[1], BANNER[sd][design][0][0]);
            CityGrammar.put(f, b, o[0], g + 11, o[1], BANNER[sd][design][1][0]);
            CityGrammar.put(f, b, o[0], g + 10, o[1], BANNER[sd][design][2][0]);
        }
    }

    /**
     * A flagpole out from the Market's tall houses (one in three of four storeys or more) at row 11, a banner hanging
     * from its tip two out (rows 8-10): catch the pole and swing (runner_swings).
     */
    static void flagpole(Facade f, CityBuffer b, int g) {
        Plot p = f.p;
        if (p.storeys() < 4 || CityPainter.Ctx.shape(p.id(), 23, 83) >= 34) return;
        int sd = p.front(), len = f.up.length(sd);
        int t = blindColumn(f, sd, len / 2);
        if (t < 0) return;
        int[] step = CityGrammar.STEP[sd];
        int[] o = f.outsideCell(f.up, sd, t);
        int bx = o[0] + step[0], bz = o[1] + step[1];
        if (f.plan.treeAt(bx, bz) || !CityPlan.walkable(f.plan.kind(bx, bz))) return;
        CityGrammar.put(f, b, o[0], g + 11, o[1], FLAGPOLE[sd]);
        CityGrammar.put(f, b, bx, g + 10, bz, BANNER[sd][2][0][1]);
        CityGrammar.put(f, b, bx, g + 9, bz, BANNER[sd][2][1][1]);
        CityGrammar.put(f, b, bx, g + 8, bz, BANNER[sd][2][2][1]);
    }
}
