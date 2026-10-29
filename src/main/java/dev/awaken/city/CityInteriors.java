package dev.awaken.city;

import dev.awaken.city.CityGrammar.Box;
import dev.awaken.city.CityGrammar.Facade;
import dev.awaken.city.CityGrammar.Style;
import dev.awaken.city.CityPainter.Ctx;
import dev.awaken.city.CityPlan.Plot;
import dev.awaken.city.CityRooms.Floor;
import dev.awaken.city.CityRoomsKit.Wood;
import dev.awaken.city.SettlementPaint.Interior;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.city.CityBlocks.AIR;
import static dev.awaken.city.CityBlocks.CHAIN_Y;
import static dev.awaken.city.CityBlocks.CITY_BRICKS;
import static dev.awaken.city.CityBlocks.FLOOR;
import static dev.awaken.city.CityBlocks.LADDER;
import static dev.awaken.city.CityBlocks.LANTERN;
import static dev.awaken.city.CityBlocks.LANTERN_HANGING;
import static dev.awaken.city.CityBlocks.MUD_BRICKS;
import static dev.awaken.city.CityBlocks.STONE_BRICKS;
import static dev.awaken.city.CityBlocks.T_BEAM_X;
import static dev.awaken.city.CityBlocks.T_BEAM_Z;
import static dev.awaken.city.CityGrammar.F_RUN;
import static dev.awaken.city.CityGrammar.STEP;
import static dev.awaken.city.CityGrammar.ccw;
import static dev.awaken.city.CityGrammar.cw;
import static dev.awaken.city.CityGrammar.inward;
import static dev.awaken.city.CityPlan.E;
import static dev.awaken.city.CityPlan.N;
import static dev.awaken.city.CityPlan.S;
import static dev.awaken.city.CityPlan.W;
import static dev.awaken.city.CityRooms.*;
import static dev.awaken.city.CityRoomsKit.*;

/**
 * The room filler (SPEC §28.6, PLAN 5.3): furnishes an enterable house from who lives there, inside the shell the
 * grammar built (ground storey rows 1-4 under the floor at row 5; each upper storey rows 1-3 of its own over its floor).
 * Generic over plots, so Carillon's B4 can adopt it for its own enterable houses; settlements call it today. Pure and
 * clipped like every painter: everything is decided from the plot, its facade (where the windows and the door are),
 * who lives there and the seeds, never from what the buffer holds, so a chunk painted alone matches its neighbours.
 *
 * <p>The house is planned whole (a few dozen cells a storey), then written through the buffer, which keeps its chunk's
 * share. Order: the hearth under the chimney, the way up (a staircase along a back or side wall, a ladder to a hatch
 * where none fits), beds (the couple upstairs, the children beside them; a cottage's along its back wall), the table
 * and its seats, the kitchen (stove, worktop, barrel: the Hearth's kitchen), the trade's blocks, then rugs, plants on
 * the sills and the light. Every piece is kept only if the door still reaches every bed, chest, seat and the stairs
 * (CityRooms.Floor.take). A storey has one light at most and a house two (the chunk's budget is 4-5): the stove's fire
 * in a brick hearth, the forge, or a lantern. Rooms are kept open: past a share of the floor (by wealth) nothing
 * optional is added.
 */
final class CityInteriors {
    private CityInteriors() {}

    static void furnish(Ctx c, CityBuffer b, Plot p, Interior who) {
        if (who == null || !p.enter() || "bell".equals(p.use())) return;
        if (p.x1() < b.px0 || p.x0() > b.x1() || p.z1() < b.pz0 || p.z0() > b.z1()) return;
        if (p.x1() - p.x0() < 3 || p.z1() - p.z0() < 3) return;
        House h = new House(c, p, who);
        h.plan();
        h.write(b);
    }

    static final int FACE_ONE = 0, FACE_IN = 1, FACE_ALONG = 2;

    /** A house being furnished: its storeys, who lives there, the palette, and the seeded draw that makes it its own. */
    static final class House {
        final Plot p;
        final Interior who;
        final Facade f;
        final Style st;
        final int storeys, front, back, wealth, fam, layout, maxLights;
        final String trade, use;
        final Wood wood;
        final BlockState floorBlock;
        final int bed, kidBed, carpet;
        final Floor[] floors;
        final int[] sideBias = new int[4];
        final boolean store, workshop;
        /** Cells of the kitchen and the trade's corner, as floor * 100000 + cell (pieces gather by them). */
        final List<Integer> kitchen = new ArrayList<>(), work = new ArrayList<>(), nightstands = new ArrayList<>();
        int lights, stove = -1, hearth = -1;
        int[] flue;
        long rs;

        House(Ctx c, Plot p, Interior who) {
            this.p = p;
            this.who = who;
            f = CityGrammar.facadeOf(c, p);
            st = f.st;
            storeys = Math.max(1, p.storeys());
            front = p.front() & 3;
            back = inward(front);
            boolean village = VillageStyles.isVillage(p);
            VillageStyles.Palette pal = village ? VillageStyles.palette(p) : null;
            wealth = village ? VillageStyles.wealth(p) : 1;
            fam = village ? VillageStyles.family(p) : -1;
            trade = who.trade() == null ? "" : who.trade();
            use = p.use() == null ? "" : p.use();
            store = use.equals("barn") || use.equals("granary");
            workshop = use.equals("smithy") || use.equals("mill");
            wood = CityRoomsKit.wood(pal != null ? pal.wood() : "spruce");
            floorBlock = pal != null ? pal.floor() : FLOOR;
            bed = colour(pal != null ? pal.bed() : "red");
            carpet = colour(pal != null ? pal.carpet() : "red");
            rs = c.seed ^ (p.id() * 0x9E3779B97F4A7C15L) ^ ((long) who.seed() * 0xC2B2AE3D27D4EB4FL) ^ 0x51F15EEDL;
            int[] kids = {LIGHT_BLUE, YELLOW, LIME, PINK, ORANGE, CYAN};
            int kc = kids[rnd(kids.length)];
            kidBed = kc == bed ? kids[(rnd(kids.length - 1) + 1 + indexOf(kids, kc)) % kids.length] : kc;
            for (int s = 0; s < 4; s++) sideBias[s] = rnd(9);
            layout = rnd(4);
            maxLights = store ? 0 : Math.min(2, storeys);
            floors = new Floor[storeys];
            for (int s = 0; s < storeys; s++) {
                Box bx = s == 0 ? f.base : f.up;
                floors[s] = new Floor(s, bx.x0() + 1, bx.z0() + 1, bx.x1() - 1, bx.z1() - 1, s == 0 ? 0 : 5 + 4 * (s - 1), s == 0 ? 4 : 3);
            }
            windows();
            door();
        }

        // ---- the draw ---------------------------------------------------------------------------------------------

        int rnd(int n) {
            rs += 0x9E3779B97F4A7C15L;
            long z = rs;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            return (int) Math.floorMod(z, (long) Math.max(1, n));
        }

        boolean chance(int pct) {
            return rnd(100) < pct;
        }

        static int indexOf(int[] a, int v) {
            for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
            return 0;
        }

        static void best(List<int[]> cand) {
            cand.sort((a, b) -> Integer.compare(b[0], a[0]));
        }

        static BlockState[] one(BlockState s) {
            return new BlockState[]{s, s, s, s};
        }

        static int key(Floor F, int k) {
            return F.idx * 100000 + k;
        }

        // ---- the shell: windows, door ------------------------------------------------------------------------------

        /** Which rows of the wall beside each inside cell are glass (or the door): only low things stand before them. */
        void windows() {
            for (int s = 0; s < storeys; s++) {
                Floor F = floors[s];
                Box bx = s == 0 ? f.base : f.up;
                for (int k = 0; k < F.n(); k++) {
                    int x = F.x(k), z = F.z(k);
                    for (int sd = 0; sd < 4; sd++) {
                        int wx = x + STEP[sd][0], wz = z + STEP[sd][1];
                        if (bx.side(wx, wz) != sd) continue;
                        F.walls[k] |= (byte) (1 << sd);
                        int t = bx.along(sd, wx, wz), len = bx.length(sd);
                        int rows;
                        if (s == 0) {
                            int[] o = f.outsideCell(bx, sd, t);
                            int open = f.groundOpen(sd, t, len, CityGrammar.faceClass(f.plan, p, sd, o[0], o[1]));
                            // a door or a shop window rows 1-2; a window rows 1-2, or row 2 alone where the style keeps them small
                            rows = open == 2 || open == 3 ? 0b110 : open == 1 ? (st.smallGround() ? 0b100 : 0b110) : 0;
                        } else {
                            int fc = f.upClass(sd, t);
                            boolean shows = fc >= F_RUN;
                            rows = shows && (f.inBay(sd, t) || f.win(sd, t, len, s)) ? 0b1100 : 0;
                        }
                        F.glass[k] |= (byte) rows;
                    }
                }
            }
        }

        /** The door's inner cell and the one past it stay open; you come in there. */
        void door() {
            Floor G = floors[0];
            if (p.hasDoor()) {
                int in = inward(front);
                int k = G.cell(p.doorX() + STEP[in][0], p.doorZ() + STEP[in][1]);
                if (k >= 0) {
                    G.mark(k, KEEP);
                    G.start = k;
                    G.mark(G.step(k, in), KEEP);
                }
            }
            if (G.start < 0) {
                int k = G.cell(G.x0 + G.w / 2, G.z0 + G.d / 2);
                G.mark(k, KEEP);
                G.start = k;
            }
        }

        Floor upper() {
            return storeys >= 2 && floors[1].start >= 0 ? floors[1] : null;
        }

        /** Whether a storey still has room for something optional (the share of floor kept open, by wealth). */
        boolean room(Floor F) {
            int used = 0, area = 0;
            for (int k = 0; k < F.n(); k++) {
                if (F.state[k] == HOLE || F.kind[k] == K_STAIR || F.kind[k] == K_LADDER) continue;
                area++;
                if (F.state[k] == USED) used++;
            }
            double cap = store || workshop ? 0.6 : wealth == 0 ? 0.42 : wealth == 1 ? 0.5 : 0.56;
            return used < cap * area;
        }

        /** Takes the storey's one light if the house still has one to give. */
        boolean canLight(Floor F) {
            return !F.lit && lights < maxLights;
        }

        void useLight(Floor F) {
            F.lit = true;
            lights++;
        }

        // ---- the plan ----------------------------------------------------------------------------------------------

        void plan() {
            boolean inn = use.equals("inn") || trade.equals("inn");
            if (!store) hearth();
            if (storeys >= 2) wayUp();
            if (store) storeRoom();
            else if (inn) inn();
            else home();
            if (!store) {
                for (Floor F : floors) {
                    if (F.idx > 0 && F.start < 0) continue;
                    rugs(F);
                    sills(F);
                }
                doormat();
            }
            lamps();
        }

        // ---- the hearth --------------------------------------------------------------------------------------------

        /**
         * A stove in a brick hearth under the chimney, where the chimney stands just inside a gable wall with no window
         * behind it on any storey: the flue climbs through every storey to the stack. Better houses get a chimney
         * breast three wide with a mantel (and candles on it). The stove burns (the storey's light).
         */
        void hearth() {
            int[] cs = CityGrammar.chimneySpot(f);
            if (cs == null || st.tallChimney()) return;
            Floor G = floors[0];
            int k = G.cell(cs[0], cs[1]);
            if (k < 0) return;
            int sd = p.ridgeX() ? (2 * cs[0] < p.x0() + p.x1() ? W : E) : (2 * cs[1] < p.z0() + p.z1() ? N : S);
            if (!G.wall(k, sd) || G.glass[k] != 0 || !G.free(k)) return;
            int in = inward(sd);
            int mouth = G.step(k, in);
            if (mouth < 0 || G.state[mouth] == USED || G.state[mouth] == HOLE) return;
            for (int s = 1; s < storeys; s++) {
                Floor U = floors[s];
                int u = U.cell(cs[0], cs[1]);
                if (u < 0 || U.glass[u] != 0 || !U.free(u)) return;
            }
            int l = G.step(k, ccw(sd)), r = G.step(k, cw(sd));
            boolean wide = wealth >= 1 && chance(75) && blank(G, l, sd, 3) && blank(G, r, sd, 3);
            if (!(wide && G.take(new int[]{k, l, r}, new int[]{mouth}))) {
                wide = false;
                if (!G.take(new int[]{k}, new int[]{mouth})) return;
            }
            int crown = Math.clamp(st.crown(), 0, 2);
            BlockState brick = crown == 1 ? MUD_BRICKS : crown == 2 ? STONE_BRICKS : CITY_BRICKS;
            BlockState[] mantel = MANTEL[crown];
            boolean lit = canLight(G);
            G.put(k, 1, cooker(in, lit));
            if (lit) useLight(G);
            for (int row = 2; row <= G.rows; row++) G.put(k, row, wide && row == 3 ? mantel[sd] : brick);
            G.kind[k] = K_HEARTH;
            G.against[k] = (byte) sd;
            if (wide) {
                for (int e : new int[]{l, r}) {
                    G.put(e, 1, brick);
                    G.put(e, 2, brick);
                    G.put(e, 3, mantel[sd]);
                    if (chance(wealth == 2 ? 70 : 40)) G.put(e, 4, CANDLES[rnd(3)][0]);
                    G.kind[e] = K_HEARTH;
                    G.against[e] = (byte) sd;
                }
            }
            for (int s = 1; s < storeys; s++) {
                Floor U = floors[s];
                int u = U.cell(cs[0], cs[1]);
                U.mark(u, USED);
                U.kind[u] = K_HEARTH;
                U.floorAt[u] = brick;
                for (int row = 1; row <= U.rows; row++) U.put(u, row, brick);
            }
            hearth = k;
            stove = k;
            flue = cs;
            kitchen.add(key(G, k));
        }

        /** A free cell against wall sd with no glass in its rows 1..top. */
        static boolean blank(Floor F, int k, int sd, int top) {
            return k >= 0 && F.free(k) && F.wall(k, sd) && F.clear(k, top);
        }

        /** The household's stove by trade: a smoker for the baker and the hunter, the forge in a smithy, else a range. */
        BlockState cooker(int facing, boolean lit) {
            int on = lit ? 1 : 0;
            if (use.equals("smithy")) return BLAST[on][facing];
            if (trade.equals("baker") || trade.equals("hunter")) return SMOKER[on][facing];
            return FURNACE[on][facing];
        }

        // ---- the way up ----------------------------------------------------------------------------------------------

        void wayUp() {
            for (int s = 0; s + 1 < storeys; s++) {
                Floor lo = floors[s], hi = floors[s + 1];
                if (lo.start < 0) return;
                // a poor cottage's loft is often reached by a ladder through a hatch
                boolean ladderFirst = wealth == 0 && lo.n() <= 30 && chance(40);
                boolean done = ladderFirst ? ladder(lo, hi) || stairs(lo, hi) : stairs(lo, hi) || ladder(lo, hi);
                if (!done) return;
            }
        }

        /**
         * A straight staircase along a wall, one step a row: rise 5 from the ground (the fifth step sits in the floor
         * above), 4 between upper storeys. The floor above is open over every step whose climber's head would reach it,
         * with a rail of open trapdoors along the hole; the space under the steps is boarded where the wall is blank
         * (a barrel tucked under the high end in better houses). The foot and the top keep a free cell to step on.
         */
        boolean stairs(Floor lo, Floor hi) {
            int rise = hi.base - lo.base;
            int n = rise;
            if (n < 2) return false;
            int holeFrom = Math.max(0, rise - 4), holeTo = n - 2;
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < lo.n(); k++) {
                if (lo.walls[k] == 0) continue;
                for (int sd = 0; sd < 4; sd++) {
                    if (!lo.wall(k, sd)) continue;
                    for (int turn = 0; turn < 2; turn++) {
                        int dir = turn == 0 ? cw(sd) : ccw(sd);
                        int[] run = stairRun(lo, hi, k, sd, dir, n);
                        if (run != null) cand.add(run);
                    }
                }
            }
            best(cand);
            for (int[] run : cand) {
                int sd = run[1], dir = run[2], a = run[3], e = run[4];
                int[] cs = new int[n];
                System.arraycopy(run, 5, cs, 0, n);
                byte[] save = lo.state.clone();
                for (int k : cs) lo.mark(k, USED);
                if (lo.state[a] == FREE) lo.mark(a, KEEP);
                lo.targets.add(a);
                if (!lo.ok()) {
                    lo.targets.removeLast();
                    System.arraycopy(save, 0, lo.state, 0, save.length);
                    continue;
                }
                boolean nook = wealth >= 1 && chance(50);
                for (int i = 0; i < n; i++) {
                    int k = cs[i];
                    lo.kind[k] = K_STAIR;
                    if (i < n - 1) lo.put(k, i + 1, wood.stair()[dir]);
                    if (lo.glass[k] != 0) continue;
                    for (int row = 1; row <= Math.min(i, lo.rows); row++) {
                        boolean barrel = nook && row == 1 && i >= n - 2 && lo.free(lo.step(k, inward(sd)));
                        lo.put(k, row, barrel ? BARREL_LYING[inward(sd)] : wood.planks());
                    }
                }
                for (int i = holeFrom; i <= holeTo; i++) {
                    int u = hi.cell(lo.x(cs[i]), lo.z(cs[i]));
                    hi.mark(u, HOLE);
                    hi.kind[u] = K_STAIR;
                    hi.floorAt[u] = AIR;
                    hi.put(u, 1, wood.rail()[sd]);
                }
                int top = hi.cell(lo.x(cs[n - 1]), lo.z(cs[n - 1]));
                hi.mark(top, KEEP);
                hi.kind[top] = K_STAIR;
                hi.floorAt[top] = wood.stair()[dir];
                hi.mark(e, KEEP);
                hi.start = top;
                return true;
            }
            return false;
        }

        /** A staircase from cell k0 along wall sd climbing toward dir: {score, sd, dir, foot, landing, cells...} or null. */
        int[] stairRun(Floor lo, Floor hi, int k0, int sd, int dir, int n) {
            int[] out = new int[5 + n];
            int k = k0, windows = 0;
            for (int i = 0; i < n; i++) {
                if (k < 0 || !lo.free(k) || !lo.wall(k, sd)) return null;
                int u = hi.cell(lo.x(k), lo.z(k));
                if (u < 0 || !hi.free(u)) return null;
                // a step never stands in front of glass (step i fills row i + 1)
                if ((lo.glass[k] & (1 << (i + 1))) != 0) return null;
                if (lo.glass[k] != 0) windows++;
                out[5 + i] = k;
                k = lo.step(k, dir);
            }
            int c0 = out[5], cn = out[5 + n - 1];
            int a = lo.step(c0, inward(dir));
            if (a < 0 || lo.state[a] == USED || lo.state[a] == HOLE) a = lo.step(c0, inward(sd));
            if (a < 0 || lo.state[a] == USED || lo.state[a] == HOLE) return null;
            int top = hi.cell(lo.x(cn), lo.z(cn));
            int e = hi.step(top, dir);
            if (!hi.free(e)) e = hi.step(top, inward(sd));
            if (!hi.free(e)) return null;
            int sc = (sd == front ? -40 : sd == back ? 12 : 8) + 2 * sideBias[sd] + rnd(6) - 3 * windows;
            if (lo.start >= 0) sc -= lo.dist(a, lo.start);
            out[0] = sc;
            out[1] = sd;
            out[2] = dir;
            out[3] = a;
            out[4] = e;
            return out;
        }

        /** A ladder up a blank wall to a hatch in the floor above (an open hatch over a ladder climbs on). */
        boolean ladder(Floor lo, Floor hi) {
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < lo.n(); k++) {
                if (lo.walls[k] == 0 || !lo.free(k) || lo.glass[k] != 0) continue;
                int u = hi.cell(lo.x(k), lo.z(k));
                if (!hi.free(u)) continue;
                for (int sd = 0; sd < 4; sd++) {
                    if (!lo.wall(k, sd)) continue;
                    int e = -1;
                    for (int s : new int[]{inward(sd), cw(sd), ccw(sd)}) {
                        int m = hi.step(u, s);
                        if (hi.free(m)) {
                            e = m;
                            break;
                        }
                    }
                    if (e < 0) continue;
                    int sc = (sd == front ? -20 : sd == back ? 8 : 4) + sideBias[sd] + (lo.corner(k) ? 6 : 0) + rnd(6);
                    cand.add(new int[]{sc, k, sd, u, e});
                }
            }
            best(cand);
            for (int[] cd : cand) {
                int k = cd[1], sd = cd[2], u = cd[3], e = cd[4];
                lo.mark(k, KEEP);
                lo.targets.add(k);
                if (!lo.ok()) {
                    lo.targets.removeLast();
                    lo.mark(k, FREE);
                    continue;
                }
                lo.kind[k] = K_LADDER;
                for (int row = 1; row <= lo.rows; row++) lo.put(k, row, LADDER[inward(sd)]);
                hi.floorAt[u] = wood.hatch()[inward(sd)];
                hi.mark(u, KEEP);
                hi.kind[u] = K_LADDER;
                hi.mark(e, KEEP);
                hi.start = u;
                return true;
            }
            return false;
        }

        // ---- a home ------------------------------------------------------------------------------------------------

        void home() {
            Floor G = floors[0], up = upper();
            int people = Math.max(0, who.adults()) + Math.max(0, who.children());
            if (workshop) {
                trade(G, true);
                beds(up, up != null ? null : G);
                if (up != null && people > 0) {
                    table(up, people);
                    storage(up);
                }
                return;
            }
            beds(up, G);
            if (stove < 0) stove(G);
            table(G, Math.max(1, people));
            kitchen(G);
            storage(up != null && chance(50) ? up : G);
            trade(G, false);
            extras(G, up);
        }

        /** The couple (or one), anyone else grown, then the children beside each other: upstairs when there is one. */
        void beds(Floor a, Floor b) {
            if (a == null) {
                a = b;
                b = null;
            }
            if (a == null) return;
            int adults = Math.max(0, who.adults()), kids = Math.max(0, who.children());
            if (adults >= 2) {
                if (bedOn(a, b, bed, true, null) == null) {
                    int[] one = bedOn(a, b, bed, false, null);
                    bedOn(a, b, bed, false, one);
                }
            } else if (adults == 1) {
                bedOn(a, b, bed, false, null);
            }
            for (int i = 2; i < adults; i++) bedOn(a, b, bed, false, null);
            int[] near = null;
            for (int i = 0; i < kids; i++) {
                int[] at = bedOn(a, b, kidBed, false, near);
                if (near == null) near = at;
            }
        }

        /** A bed on the first of two storeys with room: {storey, head cell} or null. */
        int[] bedOn(Floor a, Floor b, int colour, boolean pair, int[] near) {
            for (Floor F : new Floor[]{a, b}) {
                if (F == null) continue;
                int k = bed(F, colour, pair, near != null && near[0] == F.idx ? near[1] : -1);
                if (k >= 0) return new int[]{F.idx, k};
            }
            return null;
        }

        /** A bed (a couple's two side by side), head to a wall, foot into the room. Returns the head's cell or -1. */
        int bed(Floor F, int colour, boolean pair, int near) {
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < F.n(); k++) {
                if (F.walls[k] == 0) continue;
                for (int sd = 0; sd < 4; sd++) {
                    if (!F.wall(k, sd)) continue;
                    int in = inward(sd), foot = F.step(k, in);
                    if (!bedCell(F, k) || !bedCell(F, foot)) continue;
                    int sc = bedScore(F, k, sd, foot, near);
                    if (!pair) {
                        cand.add(new int[]{sc, k, sd, -1});
                        continue;
                    }
                    int k2 = F.step(k, cw(sd)), f2 = F.step(k2, in);
                    if (k2 < 0 || !F.wall(k2, sd) || !bedCell(F, k2) || !bedCell(F, f2)) continue;
                    cand.add(new int[]{(sc + bedScore(F, k2, sd, f2, near)) / 2, k, sd, k2});
                }
            }
            best(cand);
            for (int[] cd : cand) {
                int k = cd[1], sd = cd[2], k2 = cd[3], in = inward(sd);
                int[] cells = k2 < 0 ? new int[]{k, F.step(k, in)} : new int[]{k, F.step(k, in), k2, F.step(k2, in)};
                if (!F.take(cells, F.around(cells))) continue;
                for (int i = 0; i < cells.length; i += 2) {
                    F.put(cells[i], 1, BED[colour][sd][1]);
                    F.put(cells[i + 1], 1, BED[colour][sd][0]);
                    F.kind[cells[i]] = K_BED;
                    F.kind[cells[i + 1]] = K_BED;
                }
                bedside(F, k, k2, sd);
                return k;
            }
            return -1;
        }

        static boolean bedCell(Floor F, int k) {
            return F.free(k) && F.clear(k, 1);
        }

        int bedScore(Floor F, int k, int sd, int foot, int near) {
            int sc = sideBias[sd] + rnd(8) + (sd == back ? 14 : sd == front ? -14 : 5);
            if (F.corner(k) || F.walls[foot] != 0) sc += 8;
            if (near >= 0) sc += Math.max(0, 14 - 5 * F.dist(k, near));
            if (F.start >= 0 && F.dist(foot, F.start) <= 1) sc -= 14;
            if (F.idx == 0 && stove >= 0 && F.dist(k, stove) <= 2) sc -= 10;
            if (F.glass[k] != 0) sc += 3;
            return sc;
        }

        /** A nightstand by the head (a barrel with a candle, or a chest), and in better houses a chest at the foot. */
        void bedside(Floor F, int k, int k2, int sd) {
            int in = inward(sd);
            if (chance(wealth == 0 ? 35 : wealth == 1 ? 60 : 85) && room(F)) {
                int[] sides = k2 < 0 ? new int[]{F.step(k, cw(sd)), F.step(k, ccw(sd))} : new int[]{F.step(k2, cw(sd)), F.step(k, ccw(sd))};
                int first = rnd(2);
                for (int i = 0; i < 2; i++) {
                    int s = sides[(first + i) & 1];
                    if (!blank(F, s, sd, 0) || !F.clear(s, 1) || !F.take(new int[]{s}, F.around(s))) continue;
                    boolean barrel = wealth >= 1 && chance(65);
                    F.put(s, 1, barrel ? BARREL : CHEST[in]);
                    F.kind[s] = K_THING;
                    F.against[s] = (byte) sd;
                    F.shelf[s] = barrel;
                    if (barrel) nightstands.add(key(F, s));
                    if (barrel && chance(45)) {
                        F.put(s, 2, CANDLES[rnd(3)][0]);
                        F.shelf[s] = false;
                    }
                    break;
                }
            }
            if (wealth >= 1 && chance(wealth == 2 ? 60 : 35) && room(F)) {
                int e = F.step(F.step(k, in), in);
                if (e >= 0 && F.free(e) && F.clear(e, 1) && F.take(new int[]{e}, F.around(e))) {
                    F.put(e, 1, CHEST[in]);
                    F.kind[e] = K_THING;
                }
            }
        }

        /** A stove against a wall where there's no hearth (cold: a lantern lights the room). */
        void stove(Floor G) {
            int k = wallPiece(G, new BlockState[]{cooker(N, false), cooker(E, false), cooker(S, false), cooker(W, false)},
                    FACE_IN, null, false, kitchen, 4, -4);
            if (k >= 0) stove = k;
        }

        /**
         * A table (a post with a cloth on it, or a bare plate in a poor house) and seats round it facing it, by the
         * house's layout: in the room's middle, by the hearth, under a window or in a corner nook. Two posts for a big
         * household. Returns the table's cell or -1.
         */
        int table(Floor F, int people) {
            int want = Math.clamp(people, 1, wealth == 0 ? 2 : 4);
            boolean two = people >= 4 && wealth >= 1 && F.w >= 5 && F.d >= 5 && chance(60);
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < F.n(); k++) {
                if (!tableCell(F, k)) continue;
                int sc = tableScore(F, k);
                cand.add(new int[]{sc, k, -1});
                if (!two) continue;
                for (int ax : new int[]{E, S}) {
                    int k2 = F.step(k, ax);
                    if (tableCell(F, k2) && F.walls[k] == 0 && F.walls[k2] == 0) cand.add(new int[]{(sc + tableScore(F, k2)) / 2 + 4, k, k2});
                }
            }
            best(cand);
            int tries = 0;
            for (int[] cd : cand) {
                if (++tries > 40) break;
                int k = cd[1], k2 = cd[2];
                int[] top = k2 < 0 ? new int[]{k} : new int[]{k, k2};
                int[] seats = seats(F, top);
                for (int m = Math.min(seats.length, k2 < 0 ? want : Math.min(6, people)); m >= 1; m--) {
                    if (placeTable(F, top, seats, m)) return k;
                }
            }
            return -1;
        }

        static boolean tableCell(Floor F, int k) {
            return k >= 0 && F.free(k) && F.clear(k, 1);
        }

        int tableScore(Floor F, int k) {
            int sc = rnd(8);
            boolean open = F.walls[k] == 0;
            if (F.start >= 0 && F.dist(k, F.start) <= 1) sc -= 12;
            switch (layout) {
                case 0 -> sc += (open ? 14 : 0) - 2 * F.fromMiddle(k);
                case 1 -> sc += (open ? 6 : 0) + (stove >= 0 && F.idx == 0 ? Math.max(0, 18 - 6 * F.dist(k, stove)) : 0);
                case 2 -> sc += F.glass[k] != 0 ? 16 : open ? 4 : 0;
                default -> sc += F.corner(k) ? 16 : open ? 6 : 2;
            }
            return sc;
        }

        /** Free cells round a table where a seat can stand, opposite pairs first: cell * 4 + the way the seat faces. */
        int[] seats(Floor F, int[] top) {
            int[] out = new int[top.length * 4];
            int n = 0;
            int firstAxis = rnd(2);
            for (int pass = 0; pass < 2; pass++) {
                int axis = pass == 0 ? firstAxis : 1 - firstAxis;
                for (int t : top) {
                    for (int sd : axis == 0 ? new int[]{N, S} : new int[]{E, W}) {
                        int m = F.step(t, sd);
                        if (m < 0 || !F.free(m) || !F.clear(m, 1)) continue;
                        boolean mine = false;
                        for (int q : top) mine |= q == m;
                        if (!mine) out[n++] = m * 4 + sd;
                    }
                }
            }
            return java.util.Arrays.copyOf(out, n);
        }

        boolean placeTable(Floor F, int[] top, int[] seats, int m) {
            int[] cells = new int[top.length + m];
            int[][] needs = new int[m][];
            System.arraycopy(top, 0, cells, 0, top.length);
            for (int i = 0; i < m; i++) {
                cells[top.length + i] = seats[i] >> 2;
                needs[i] = F.around(seats[i] >> 2);
            }
            if (!F.take(cells, needs)) return false;
            boolean cloth = wealth == 2 || wealth == 1 && chance(65);
            BlockState topper = cloth ? CARPET[wealth == 2 && chance(40) ? WHITE : carpet] : wood.plate();
            for (int i = 0; i < top.length; i++) {
                int mask = 0;
                if (top.length == 2) {
                    int other = top[1 - i];
                    for (int sd = 0; sd < 4; sd++) if (F.step(top[i], sd) == other) mask |= 1 << sd;
                }
                F.put(top[i], 1, wood.fence()[mask]);
                F.put(top[i], 2, topper);
                F.kind[top[i]] = K_TABLE;
            }
            for (int i = 0; i < m; i++) {
                int sk = seats[i] >> 2, facing = seats[i] & 3;
                F.put(sk, 1, wood.stair()[facing]);
                F.kind[sk] = K_SEAT;
            }
            return true;
        }

        /** The kitchen by the stove: a worktop, a barrel, a rack of jars over one of them in better houses. */
        void kitchen(Floor G) {
            if (wealth >= 1 || chance(55)) wallPiece(G, one(CRAFTING), FACE_ONE, null, false, kitchen, 0, 2);
            if ((wealth >= 1 || chance(50)) && room(G)) wallPiece(G, one(BARREL), FACE_ONE, null, false, kitchen, 3, 2);
            if (wealth >= 1 && chance(wealth == 2 ? 70 : 40)) rack(G, kitchen, wealth == 2 && chance(50) ? 3 : 0);
        }

        /** The household's chest, in a corner where it can. */
        void storage(Floor F) {
            if (F == null) return;
            wallPiece(F, CHEST, FACE_IN, null, false, null, 10, 3);
        }

        /** What better houses have more of: books, a second barrel. */
        void extras(Floor G, Floor up) {
            if (wealth == 2) {
                for (int i = 0; i < 2; i++) {
                    Floor F = up != null && i == 1 ? up : G;
                    if (room(F)) bookshelf(F, F.rows >= 4 && chance(60) ? 2 : 1, null);
                }
            }
            if (up != null && room(up) && chance(wealth == 0 ? 30 : 60)) storage(up);
        }

        /** A bookshelf against a blank wall, one or two high (a candle or a pot on a single one). */
        int bookshelf(Floor F, int high, List<Integer> group) {
            BlockState[] above = high >= 2 ? new BlockState[]{BOOKSHELF} : null;
            int k = wallPiece(F, one(BOOKSHELF), FACE_ONE, above, true, group, 2, -20);
            if (k >= 0 && high < 2 && chance(40)) {
                F.put(k, 2, chance(50) ? CANDLES[rnd(3)][0] : plant(false));
                F.shelf[k] = false;
            }
            return k;
        }

        /** A goods rack (jars, cloth, bread, pots, books, tools) on the wall over a piece of the group, at row 2. */
        void rack(Floor F, List<Integer> group, int goods) {
            for (int key : group) {
                if (key / 100000 != F.idx) continue;
                int k = key % 100000;
                int sd = F.against[k];
                if (sd < 0 || F.height[k] != 1 || !F.shelf[k] || (F.glass[k] & 0b100) != 0) continue;
                F.put(k, 2, CityProps.SHELF_KIT[inward(sd)][goods]);
                F.shelf[k] = false;
                return;
            }
        }

        // ---- the trades ----------------------------------------------------------------------------------------------

        /**
         * The trade's blocks. A workshop (the smithy, the mill) is its trade's whole floor; a home keeps the trade's
         * two or three telling blocks, the rest only where there's room (upstairs when the ground is full).
         */
        void trade(Floor G, boolean full) {
            Floor up = upper();
            String t = use.equals("smithy") ? "smith" : use.equals("mill") ? "miller" : trade;
            List<Integer> w = work;
            switch (t) {
                case "smith" -> {
                    if (full) {
                        if (hearth < 0) forge(G);
                        freePiece(G, ANVIL[rnd(4)], w);
                        wallPiece(G, one(TUB), FACE_ONE, null, false, w, 0, 0);
                        wallPiece(G, one(SMITHING), FACE_ONE, null, false, w, 0, 2);
                        if (room(G)) wallPiece(G, GRINDSTONE, FACE_ALONG, null, false, w, 0, 2);
                        if (room(G)) wallPiece(G, one(BARREL), FACE_ONE, null, false, w, 4, 2);
                        if (room(G)) shelfPiece(G, 5, w);
                        if (room(G)) storage(G);
                    } else {
                        wallPiece(G, GRINDSTONE, FACE_ALONG, null, false, w, 0, 2);
                        if (room(G)) shelfPiece(G, 5, w);
                    }
                }
                case "farmer" -> {
                    wallPiece(G, one(COMPOSTER[rnd(3)]), FACE_ONE, null, false, w, 4, 2);
                    hay(G, w);
                    more(G, up, one(CityProps.SACKS[1 + rnd(2)][0]), w);
                    more(G, up, one(CityProps.PRODUCE_KIT[new int[]{1, 2, 4}[rnd(3)]]), w);
                }
                case "shepherd", "weaver" -> {
                    wallPiece(G, LOOM, FACE_IN, null, false, w, 0, 2);
                    more(G, up, one(CityProps.SACKS[2][2]), w);
                    if (room(G)) wallPiece(G, one(WOOL[WHITE]), FACE_ONE, new BlockState[]{WOOL[new int[]{WHITE, LIGHT_GRAY, BROWN, carpet}[rnd(4)]]}, true, w, 6, -10);
                    if (room(G)) shelfPiece(G, 1, w);
                }
                case "woodcutter" -> {
                    wallPiece(G, STONECUTTER, FACE_IN, null, false, w, 0, 2);
                    logPile(G, w);
                    if (room(G)) freePiece(G, wood.log()[1], w);
                }
                case "miner" -> {
                    wallPiece(G, GRINDSTONE, FACE_ALONG, null, false, w, 0, 2);
                    more(G, up, one(BARREL), w);
                    more(G, up, one(CityProps.CRATES[0]), w);
                    if (room(G)) shelfPiece(G, 5, w);
                }
                case "fisher" -> {
                    more(G, up, one(BARREL), w);
                    net(G, w);
                    more(G, up, one(CityProps.CRATES[3]), w);
                }
                case "hunter" -> {
                    wallPiece(G, one(FLETCHING), FACE_ONE, null, false, w, 0, 2);
                    more(G, up, one(BARREL), w);
                }
                case "carter" -> {
                    wallPiece(G, one(CityProps.CRATES[0]), FACE_ONE, null, false, w, 6, 2);
                    more(G, up, one(CityProps.CRATES[1]), w);
                    more(G, up, one(CityProps.SACKS[1][0]), w);
                }
                case "elder" -> {
                    Floor F = up != null && chance(50) ? up : G;
                    wallPiece(F, LECTERN, FACE_IN, null, false, w, 0, 2);
                    bookshelf(F, F.rows >= 4 ? 2 : 1, w);
                    if (room(F)) bookshelf(F, F.rows >= 4 ? 2 : 1, w);
                }
                case "scribe", "bellringer" -> {
                    // a study: the lectern, a desk with its chair and a candle, shelves of books (six make it a Study)
                    Floor F = up != null && chance(45) ? up : G;
                    wallPiece(F, LECTERN, FACE_IN, null, false, w, 0, 2);
                    if (t.equals("scribe")) {
                        int desk = wallPiece(F, one(CARTOGRAPHY), FACE_ONE, null, false, w, 0, 4);
                        if (desk >= 0) {
                            chair(F, desk);
                            F.put(desk, 2, CANDLES[rnd(3)][0]);
                            F.shelf[desk] = false;
                        }
                        for (int i = 0; i < 3 && room(F); i++) bookshelf(F, F.rows >= 4 || i > 0 ? 2 : 1, w);
                    } else if (room(F)) {
                        bookshelf(F, 1, w);
                    }
                }
                case "miller" -> {
                    if (full) {
                        freePiece(G, GRINDSTONE[rnd(4)], w);
                        if (room(G)) freePiece(G, GRINDSTONE[rnd(4)], w);
                        for (int i = 0; i < 3 && room(G); i++) wallPiece(G, one(CityProps.SACKS[2][i == 0 ? 0 : 1]), FACE_ONE, null, false, w, 6, 2);
                        if (room(G)) wallPiece(G, one(BARREL), FACE_ONE, null, false, w, 4, 2);
                        if (room(G)) wallPiece(G, one(CityProps.CRATES[0]), FACE_ONE, null, false, w, 6, 2);
                    } else {
                        wallPiece(G, GRINDSTONE, FACE_ALONG, null, false, w, 0, 2);
                        more(G, up, one(CityProps.SACKS[2][1]), w);
                    }
                }
                case "baker" -> {
                    // an oven beside the range, a board of loaves on a trestle, flour
                    if (hearth >= 0) wallPiece(G, SMOKER[0], FACE_IN, null, false, kitchen, 0, -4);
                    int board = wallPiece(G, CityProps.TABLE, FACE_ALONG_AXIS, new BlockState[]{CityProps.WARES[0]}, false, kitchen, 0, 2);
                    if (board >= 0) G.shelf[board] = false;
                    more(G, up, one(CityProps.SACKS[1 + rnd(2)][1]), w);
                    if (room(G)) rack(G, kitchen, 2);
                }
                case "healer" -> {
                    wallPiece(G, one(BARREL), FACE_ONE, new BlockState[]{BREWING}, true, w, 0, -10);
                    wallPiece(G, one(TUB), FACE_ONE, null, false, w, 0, 2);
                    if (room(G)) shelfPiece(G, 0, w);
                }
                case "washer" -> {
                    wallPiece(G, one(TUB), FACE_ONE, null, false, w, 0, 2);
                    if (room(G)) wallPiece(G, one(TUB), FACE_ONE, null, false, w, 0, 2);
                    more(G, up, one(BARREL), w, LINEN);
                    more(G, up, one(CityProps.SACKS[1][2]), w);
                }
                case "lamplighter" -> {
                    more(G, up, one(BARREL), w, CANDLES[2][0]);
                    // the lamplighter's ladder, leant on a blank wall
                    int k = wallPiece(G, LADDER, FACE_IN, new BlockState[]{null, null}, true, w, 0, -30);
                    if (k >= 0) {
                        G.put(k, 2, G.at(k, 1));
                        G.put(k, 3, G.at(k, 1));
                    }
                }
                case "watch" -> {
                    wallPiece(G, one(TARGET), FACE_ONE, null, false, w, 4, -6);
                    wallPiece(G, GRINDSTONE, FACE_ALONG, null, false, w, 0, 2);
                    more(G, up, one(BARREL), w);
                }
                default -> {
                }
            }
        }

        static final int FACE_ALONG_AXIS = 3;

        /** An optional piece of the trade: on the ground if there's room, else upstairs. */
        void more(Floor G, Floor up, BlockState[] s, List<Integer> w) {
            more(G, up, s, w, null);
        }

        void more(Floor G, Floor up, BlockState[] s, List<Integer> w, BlockState on) {
            BlockState[] above = on == null ? null : new BlockState[]{on};
            if (room(G) && wallPiece(G, s, FACE_ONE, above, false, w, 4, 2) >= 0) return;
            if (up != null && room(up)) wallPiece(up, s, FACE_ONE, above, false, w, 4, 2);
        }

        /** The smith's forge where the hearth didn't take it: a blast furnace against a blank wall, burning. */
        void forge(Floor G) {
            boolean lit = canLight(G);
            int k = wallPiece(G, BLAST[lit ? 1 : 0], FACE_IN, null, false, work, 0, -6);
            if (k >= 0 && lit) useLight(G);
        }

        /** A goods rack standing on the floor against a wall (jars 0, cloth 1, bread 2, pots 3, books 4, tools 5). */
        int shelfPiece(Floor F, int goods, List<Integer> group) {
            BlockState[] s = new BlockState[4];
            for (int d = 0; d < 4; d++) s[d] = CityProps.SHELF_KIT[d][goods];
            return wallPiece(F, s, FACE_IN, null, false, group, 2, -4);
        }

        /** Hay for the farmer: one bale, two where the wall is blank. */
        void hay(Floor F, List<Integer> group) {
            BlockState[] above = chance(50) ? new BlockState[]{HAY_BALE[rnd(2) == 0 ? 0 : 2]} : null;
            if (wallPiece(F, one(HAY_BALE[1]), FACE_ONE, above, true, group, 8, -4) < 0 && above != null)
                wallPiece(F, one(HAY_BALE[1]), FACE_ONE, null, false, group, 8, 2);
        }

        /** The woodcutter's pile: stripped logs of the house's wood lying along a wall, two long, stacked where it's blank. */
        void logPile(Floor F, List<Integer> group) {
            // by the way into the room: a north or south wall runs along x
            BlockState[] s = {wood.log()[0], wood.log()[2], wood.log()[0], wood.log()[2]};
            for (int i = 0; i < 2 && room(F); i++) {
                int k = wallPiece(F, s, FACE_IN, null, false, group, 4, 2);
                if (k >= 0 && F.clear(k, 2) && chance(60)) {
                    F.put(k, 2, F.at(k, 1));
                    F.shelf[k] = false;
                }
                if (k < 0) return;
            }
        }

        /** The fisher's net hung to dry on a blank wall, floor to ceiling (it has no collision; it still keeps its cell). */
        void net(Floor F, List<Integer> group) {
            BlockState[] s = {CityProps.NET[0], CityProps.NET[1], CityProps.NET[0], CityProps.NET[1]};
            int k = wallPiece(F, s, FACE_IN, new BlockState[]{null, null}, true, group, 6, -30);
            if (k >= 0) {
                BlockState n = F.at(k, 1);
                F.put(k, 2, n);
                F.put(k, 3, n);
                F.shelf[k] = false;
            }
        }

        /** A seat at a desk: a stair in front of it, facing it. */
        void chair(Floor F, int desk) {
            int sd = F.against[desk];
            if (sd < 0) return;
            int in = inward(sd), m = F.step(desk, in);
            if (m < 0 || !F.free(m) || !F.clear(m, 1) || !F.take(new int[]{m}, F.around(m))) return;
            F.put(m, 1, wood.stair()[in]);
            F.kind[m] = K_SEAT;
        }

        // ---- the inn -------------------------------------------------------------------------------------------------

        /** The taproom below (the bar, tables, the hearth), rooms above: the keeper's bed and guest beds with chests. */
        void inn() {
            Floor G = floors[0], up = upper();
            bar(G);
            if (stove < 0 && chance(50)) stove(G);
            int n = 0;
            for (int i = 0; i < 3 && room(G); i++) if (table(G, i == 0 ? 4 : 2 + rnd(2)) >= 0) n++;
            if (n == 0) table(G, 2);
            if (room(G)) wallPiece(G, one(BARREL), FACE_ONE, null, false, work, 4, 2);
            Floor rooms = up != null ? up : G;
            int adults = Math.max(0, who.adults());
            if (adults >= 2) bedOn(rooms, null, bed, true, null);
            else if (adults == 1) bedOn(rooms, null, bed, false, null);
            for (int i = 0; i < 4 && room(rooms); i++) {
                int k = bed(rooms, bed, false, -1);
                if (k < 0) break;
            }
            storage(rooms);
        }

        /**
         * The bar along a side or back wall: casks and barrels against the wall (a brewing stand on one), the barkeep's
         * aisle, the counter (upside-down stairs, open below on the drinkers' side), stools before it. Open at one end.
         */
        boolean bar(Floor G) {
            List<int[]> cand = new ArrayList<>();
            for (int sd : new int[]{cw(front), ccw(front), back}) {
                int in = inward(sd);
                int len = sd == N || sd == S ? G.w : G.d, depth = sd == N || sd == S ? G.d : G.w;
                if (len < 4 || depth < 6) continue;
                int[] row = new int[len];
                int m = 0;
                for (int k = 0; k < G.n() && m < len; k++) if (G.wall(k, sd)) row[m++] = k;
                if (m < len) continue;
                int span = len <= 5 ? len - 1 : len - 2;
                for (int gapFirst = 0; gapFirst < 2; gapFirst++) {
                    int u0 = gapFirst == 1 ? len - span : 0;
                    boolean okRun = true;
                    for (int u = u0; u < u0 + span && okRun; u++) {
                        int aisle = G.step(row[u], in), ctr = G.step(aisle, in);
                        okRun = aisle >= 0 && (G.state[aisle] == FREE || G.state[aisle] == KEEP) && G.free(ctr) && G.clear(ctr, 1);
                    }
                    if (!okRun) continue;
                    int sc = (sd == back ? 6 : 0) + sideBias[sd] + rnd(6);
                    cand.add(new int[]{sc, sd, u0, span, row[0], row[len - 1]});
                }
            }
            best(cand);
            for (int[] cd : cand) {
                int sd = cd[1], u0 = cd[2], span = cd[3], in = inward(sd);
                int len = sd == N || sd == S ? G.w : G.d;
                int[] row = new int[len];
                int m = 0;
                for (int k = 0; k < G.n() && m < len; k++) if (G.wall(k, sd)) row[m++] = k;
                byte[] save = G.state.clone();
                int far = G.step(row[gapEnd(u0) ? u0 : u0 + span - 1], in);
                for (int u = u0; u < u0 + span; u++) {
                    int aisle = G.step(row[u], in);
                    if (G.state[aisle] == FREE) G.mark(aisle, KEEP);
                    G.mark(G.step(aisle, in), USED);
                }
                G.targets.add(far);
                if (!G.ok()) {
                    G.targets.removeLast();
                    System.arraycopy(save, 0, G.state, 0, save.length);
                    continue;
                }
                boolean brewing = false;
                int axis = sd == N || sd == S ? 1 : 2;
                for (int u = u0; u < u0 + span; u++) {
                    int wallCell = row[u], aisle = G.step(wallCell, in), ctr = G.step(aisle, in), stool = G.step(ctr, in);
                    G.put(ctr, 1, wood.stairTop()[sd]);
                    G.kind[ctr] = K_THING;
                    G.shelf[ctr] = true;
                    if (G.free(wallCell) && G.clear(wallCell, 1) && G.take(new int[]{wallCell}, new int[]{aisle})) {
                        boolean cask = (u - u0) % 3 == 1;
                        G.put(wallCell, 1, cask ? CityProps.CASK[0] : BARREL);
                        G.kind[wallCell] = K_THING;
                        G.against[wallCell] = (byte) sd;
                        if ((G.glass[wallCell] & 0b100) == 0) {
                            if (!brewing && !cask) {
                                G.put(wallCell, 2, BREWING);
                                brewing = true;
                            } else {
                                G.put(wallCell, 2, CityProps.CASK[axis]);
                            }
                        }
                    }
                    if ((u - u0) % 2 == 1 && G.free(stool) && G.clear(stool, 1) && G.take(new int[]{stool}, G.around(stool))) {
                        G.put(stool, 1, wood.stair()[in]);
                        G.kind[stool] = K_SEAT;
                    }
                }
                // on the counter: a cheese board and a candle or two
                int c1 = G.step(G.step(row[u0 + span / 2], in), in);
                G.put(c1, 2, CityProps.WARES[chance(50) ? 9 : 0]);
                G.shelf[c1] = false;
                int c2 = G.step(G.step(row[u0 + (span > 2 ? span - 1 : 0)], in), in);
                if (c2 != c1) {
                    G.put(c2, 2, CANDLES[1 + rnd(2)][0]);
                    G.shelf[c2] = false;
                }
                return true;
            }
            return false;
        }

        /** Whether the bar's gap is at its far end (the aisle's closed end is then at u0). */
        static boolean gapEnd(int u0) {
            return u0 == 0;
        }

        // ---- barns and granaries -------------------------------------------------------------------------------------

        /** Hay stacked against the walls (sacks heaped in a granary, and its keystone), crates and barrels, a hay cart in a big barn. No beds, no light. */
        void storeRoom() {
            boolean granary = use.equals("granary");
            List<Integer> g = work;
            for (Floor F : floors) {
                if (F.idx > 0 && F.start < 0) continue;
                if (F.idx == 0 && !granary && F.w >= 5 && F.d >= 5) cart(F);
                // every granary keeps its keystone against a wall: what a Storehouse's Drop is built round (SPEC §43.12)
                if (F.idx == 0 && granary) wallPiece(F, one(dev.awaken.kin.works.WorksContent.GRANARY_KEYSTONE.get().defaultBlockState()), FACE_ONE, null, false, g, 6, 2);
                int n = Math.max(3, F.n() / 3);
                for (int i = 0; i < n && room(F); i++) {
                    int roll = rnd(100);
                    if (granary) {
                        if (roll < 45) wallPiece(F, one(CityProps.SACKS[2][rnd(2)]), FACE_ONE, null, false, g, 6, 2);
                        else if (roll < 70) wallPiece(F, one(CityProps.CRATES[new int[]{0, 1, 2, 4}[rnd(4)]]), FACE_ONE, null, false, g, 6, 2);
                        else if (roll < 85) wallPiece(F, one(BARREL), FACE_ONE, null, false, g, 4, 2);
                        else hay(F, g);
                    } else {
                        if (roll < 45) {
                            int high = Math.min(F.rows - 1, 1 + rnd(3));
                            BlockState[] above = new BlockState[high - 1];
                            for (int a = 0; a < above.length; a++) above[a] = a == above.length - 1 && chance(40) ? HAY_BALE[rnd(2) == 0 ? 0 : 2] : HAY_BALE[1];
                            if (wallPiece(F, one(HAY_BALE[1]), FACE_ONE, above.length == 0 ? null : above, true, g, 10, -6) < 0)
                                wallPiece(F, one(HAY_BALE[1]), FACE_ONE, null, false, g, 10, 2);
                        } else if (roll < 62) wallPiece(F, one(CityProps.SACKS[rnd(3)][0]), FACE_ONE, null, false, g, 4, 2);
                        else if (roll < 77) wallPiece(F, one(CityProps.CRATES[new int[]{4, 1, 2}[rnd(3)]]), FACE_ONE, null, false, g, 4, 2);
                        else if (roll < 88) wallPiece(F, one(COMPOSTER[rnd(3)]), FACE_ONE, null, false, g, 4, 2);
                        else wallPiece(F, one(BARREL), FACE_ONE, null, false, g, 4, 2);
                    }
                }
            }
        }

        /** A hay cart standing in the barn's middle, its shafts toward the door. */
        void cart(Floor F) {
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < F.n(); k++) {
                if (!F.free(k) || F.walls[k] != 0) continue;
                for (int sd = 0; sd < 4; sd++) {
                    int s = F.step(k, sd);
                    if (s < 0 || !F.free(s) || F.walls[s] != 0) continue;
                    int sc = rnd(10) - F.fromMiddle(k) + (sd == front ? 6 : 0);
                    cand.add(new int[]{sc, k, sd, s});
                }
            }
            best(cand);
            for (int[] cd : cand) {
                int k = cd[1], sd = cd[2], s = cd[3];
                if (!F.take(new int[]{k, s}, F.around(k, s))) continue;
                F.put(k, 1, CityProps.HAY_BODY[sd]);
                F.put(s, 1, CityProps.HAY_SHAFTS[sd]);
                F.kind[k] = K_THING;
                F.kind[s] = K_THING;
                return;
            }
        }

        // ---- placing ---------------------------------------------------------------------------------------------------

        /**
         * One thing against a wall: the best free wall cell by score that keeps every way in walkable. States are by
         * facing (FACE_IN: into the room; FACE_ALONG: along the wall; FACE_ALONG_AXIS: by the wall's axis, 0 along x;
         * FACE_ONE: s[0]). Above stands on it, row 2 up (solid: blocks, so the glass must be clear there too; else small
         * things a window may show behind). Corner and under (in front of a window) add to the score. Returns the cell or -1.
         */
        int wallPiece(Floor F, BlockState[] s, int face, BlockState[] above, boolean solid, List<Integer> group, int corner, int under) {
            int top = above != null && solid ? 1 + above.length : 1;
            if (top > F.rows) return -1;
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < F.n(); k++) {
                if (F.walls[k] == 0 || !F.free(k) || !F.clear(k, top)) continue;
                for (int sd = 0; sd < 4; sd++) {
                    if (!F.wall(k, sd)) continue;
                    int sc = sideBias[sd] + rnd(6) + (sd == front && F.idx == 0 ? -5 : 0);
                    if (F.corner(k)) sc += corner;
                    if (F.glass[k] != 0) sc += under;
                    sc += near(F, k, group);
                    if (F.start >= 0 && F.dist(k, F.start) <= 1) sc -= 10;
                    cand.add(new int[]{sc, k, sd});
                }
            }
            best(cand);
            for (int[] cd : cand) {
                int k = cd[1], sd = cd[2];
                if (!F.take(new int[]{k}, F.around(k))) continue;
                int dir = switch (face) {
                    case FACE_IN -> inward(sd);
                    case FACE_ALONG -> cw(sd);
                    case FACE_ALONG_AXIS -> sd == N || sd == S ? 0 : 1;
                    default -> 0;
                };
                BlockState b = s[Math.min(dir, s.length - 1)];
                F.put(k, 1, b);
                if (above != null) for (int i = 0; i < above.length; i++) if (above[i] != null) F.put(k, 2 + i, above[i]);
                F.kind[k] = K_THING;
                F.against[k] = (byte) sd;
                F.shelf[k] = above == null && fullTop(b);
                if (group != null) group.add(key(F, k));
                return k;
            }
            return -1;
        }

        /** Something standing free in the room (the anvil, a millstone, a chopping block), near its group. */
        int freePiece(Floor F, BlockState s, List<Integer> group) {
            List<int[]> cand = new ArrayList<>();
            for (int k = 0; k < F.n(); k++) {
                if (!F.free(k) || !F.clear(k, 1)) continue;
                int sc = rnd(8) + near(F, k, group) + (F.walls[k] == 0 ? 6 : 0);
                if (F.start >= 0 && F.dist(k, F.start) <= 1) sc -= 12;
                cand.add(new int[]{sc, k});
            }
            best(cand);
            for (int[] cd : cand) {
                int k = cd[1];
                if (!F.take(new int[]{k}, F.around(k))) continue;
                F.put(k, 1, s);
                F.kind[k] = K_THING;
                if (group != null) group.add(key(F, k));
                return k;
            }
            return -1;
        }

        /** How much a cell gains for standing by its group (the kitchen by the stove, the trade's things together). */
        static int near(Floor F, int k, List<Integer> group) {
            if (group == null || group.isEmpty()) return 0;
            int dm = 99;
            for (int key : group) if (key / 100000 == F.idx) dm = Math.min(dm, F.dist(key % 100000, k));
            return dm == 99 ? 0 : dm <= 1 ? 18 : dm == 2 ? 9 : dm == 3 ? 3 : -2;
        }

        /** Whether a block's top is whole (a pot, a candle or a lantern can stand on it). */
        static boolean fullTop(BlockState b) {
            return b == BARREL || b == CRAFTING || b == SMITHING || b == FLETCHING || b == CARTOGRAPHY || b == BOOKSHELF
                    || b == HAY_BALE[1] || b == CityProps.TABLE[0] || b == CityProps.TABLE[1];
        }

        // ---- the finish: rugs, plants, light -----------------------------------------------------------------------

        /**
         * A rug on the open floor: before the hearth, between the beds, in the middle of the room; a border in a
         * prosperous house's big one, a rag rug or nothing in a poor one.
         */
        void rugs(Floor F) {
            if (wealth == 0 && !chance(35)) return;
            int maxW = wealth == 0 ? 2 : 3, maxD = wealth == 0 ? 1 : wealth == 1 ? 2 : 3;
            if (chance(50)) {
                int t = maxW;
                maxW = maxD;
                maxD = t;
            }
            int near = F.idx == 0 && hearth >= 0 ? F.step(hearth, inward(floors[0].against[hearth])) : -1;
            int[] bestR = null;
            int bestS = Integer.MIN_VALUE;
            for (int rw = 1; rw <= maxW; rw++) {
                for (int rd = 1; rd <= maxD; rd++) {
                    if (rw * rd < 2) continue;
                    for (int i = 0; i + rw <= F.w; i++) {
                        for (int j = 0; j + rd <= F.d; j++) {
                            boolean okR = true;
                            int mid = -1;
                            for (int a = 0; a < rw && okR; a++) {
                                for (int b2 = 0; b2 < rd && okR; b2++) {
                                    int k = (i + a) + (j + b2) * F.w;
                                    okR = rugCell(F, k);
                                    if (a == rw / 2 && b2 == rd / 2) mid = k;
                                }
                            }
                            if (!okR) continue;
                            int sc = rw * rd * 6 - F.fromMiddle(mid) + rnd(5);
                            if (near >= 0) sc += Math.max(0, 12 - 4 * F.dist(mid, near));
                            if (sc > bestS) {
                                bestS = sc;
                                bestR = new int[]{i, j, rw, rd};
                            }
                        }
                    }
                }
            }
            if (bestR == null) return;
            int main = wealth == 0 ? (chance(50) ? BROWN : LIGHT_GRAY) : trade.equals("hunter") && F.idx == 0 ? BROWN : carpet;
            boolean border = wealth == 2 && bestR[2] == 3 && bestR[3] == 3;
            int edge = main == WHITE ? BROWN : main == BROWN ? WHITE : chance(50) ? WHITE : BROWN;
            for (int a = 0; a < bestR[2]; a++) {
                for (int b2 = 0; b2 < bestR[3]; b2++) {
                    int k = (bestR[0] + a) + (bestR[1] + b2) * F.w;
                    boolean rim = a == 0 || b2 == 0 || a == bestR[2] - 1 || b2 == bestR[3] - 1;
                    F.put(k, 1, CARPET[border && rim ? edge : main]);
                }
            }
        }

        static boolean rugCell(Floor F, int k) {
            return (F.state[k] == FREE || F.state[k] == KEEP) && F.height[k] == 0 && F.floorAt[k] == null && F.kind[k] == K_NONE;
        }

        /** A mat inside the door in tidier houses. */
        void doormat() {
            Floor G = floors[0];
            if (G.start < 0 || wealth == 0 || !chance(45) || !rugCell(G, G.start)) return;
            G.put(G.start, 1, CARPET[chance(50) ? BROWN : carpet]);
        }

        /**
         * Plants on the sills: a pot on whatever stands under a window (a barrel, a worktop), a planter box under a high
         * one, a pot plant on the floor before a low one. More the better off the house.
         */
        void sills(Floor F) {
            int pct = wealth == 0 ? 20 : wealth == 1 ? 50 : 80;
            boolean herbs = trade.equals("healer");
            for (int k = 0; k < F.n(); k++) {
                if (F.glass[k] == 0) continue;
                int low = F.sill(k);
                if (F.height[k] == low - 1 && low >= 2 && F.shelf[k] && F.at(k, low) == null) {
                    if (chance(pct)) {
                        F.put(k, low, plant(herbs));
                        F.shelf[k] = false;
                    }
                } else if (F.height[k] == 0 && F.free(k) && wealth >= 1 && room(F)) {
                    if (low >= 2 && chance(pct / 3) && F.take(new int[]{k}, F.around(k))) {
                        F.put(k, 1, CityProps.PLANTER[0][herbs ? 5 : rnd(5)]);
                        F.kind[k] = K_THING;
                    } else if (low == 1 && chance(pct / 4) && F.take(new int[]{k}, F.around(k))) {
                        F.put(k, 1, herbs ? HERBS[rnd(3)] : HERBS[1 + rnd(2)]);
                        F.kind[k] = K_THING;
                    }
                }
            }
        }

        /** A pot plant: the land's own now and then, flowers else (herbs for the healer). */
        BlockState plant(boolean herbs) {
            if (herbs) return HERBS[rnd(HERBS.length)];
            if (chance(45)) {
                BlockState land = switch (fam) {
                    case VillageStyles.DESERT -> chance(50) ? CACTUS : DEAD_BUSH;
                    case VillageStyles.SAVANNA -> DEAD_BUSH;
                    case VillageStyles.MIRE -> PROPAGULE;
                    case VillageStyles.JUNGLE -> BAMBOO;
                    case VillageStyles.CHERRY -> CHERRY;
                    case VillageStyles.FROST -> SPRUCE_SAPLING;
                    default -> HERBS[rnd(3)];
                };
                return land;
            }
            return SILL_FLOWERS[rnd(SILL_FLOWERS.length)];
        }

        /**
         * Each storey without its light gets one (two a house at most): a lantern on a chain over the table, on a
         * nightstand by the bed, or from the ceiling over the open floor.
         */
        void lamps() {
            for (Floor F : floors) {
                if (!canLight(F) || F.idx > 0 && F.start < 0) continue;
                // a lantern on a nightstand upstairs, now and then
                if (F.idx > 0 && chance(40)) {
                    boolean done = false;
                    for (int key : nightstands) {
                        if (key / 100000 != F.idx) continue;
                        int k = key % 100000;
                        if (F.shelf[k] && F.at(k, 2) == null && (F.glass[k] & 0b100) == 0) {
                            F.put(k, 2, LANTERN);
                            F.shelf[k] = false;
                            useLight(F);
                            done = true;
                            break;
                        }
                    }
                    if (done) continue;
                }
                int row = F.rows >= 4 ? F.rows - 1 : F.rows;
                int bestK = -1, bestS = Integer.MIN_VALUE;
                for (int k = 0; k < F.n(); k++) {
                    byte kd = F.kind[k];
                    if (kd == K_STAIR || kd == K_HEARTH || kd == K_LADDER || F.state[k] == HOLE) continue;
                    if (F.height[k] >= row || !ceilingOver(F, k)) continue;
                    int sc = -F.fromMiddle(k) + rnd(3);
                    if (kd == K_TABLE) sc += 20;
                    if (F.walls[k] != 0) sc -= 4;
                    if (sc > bestS) {
                        bestS = sc;
                        bestK = k;
                    }
                }
                if (bestK < 0) continue;
                F.put(bestK, row, LANTERN_HANGING);
                for (int r = row + 1; r <= F.rows; r++) F.put(bestK, r, CHAIN_Y);
                useLight(F);
            }
        }

        /** Whether a lantern can hang under the cell's ceiling (no hole, hatch or step over it). */
        boolean ceilingOver(Floor F, int k) {
            if (F.idx + 1 >= storeys) return true;
            Floor U = floors[F.idx + 1];
            int u = U.cell(F.x(k), F.z(k));
            return u < 0 || U.floorAt[u] == null || U.kind[u] == K_HEARTH;
        }

        // ---- writing -------------------------------------------------------------------------------------------------

        void write(CityBuffer b) {
            int g = p.lift();
            for (Floor F : floors) {
                for (int k = 0; k < F.n(); k++) {
                    int x = F.x(k), z = F.z(k);
                    if (!b.contains(x, z)) continue;
                    b.set(x, g + F.base, z, F.floorAt[k] != null ? F.floorAt[k] : floorBlock);
                    for (int r = 1; r <= F.rows; r++) {
                        BlockState s = F.at(k, r);
                        if (s != null) b.set(x, g + F.base + r, z, s);
                    }
                }
            }
            ceiling(b, g);
        }

        /**
         * The top storey's ceiling in the palette's boards, crossed by tie beams (the frame's own timber in a timbered
         * house) every third block across the ridge. A flat roof's ceiling is the roof itself: left alone, as is the
         * chimney's foot.
         */
        void ceiling(CityBuffer b, int g) {
            if (p.roof() == CityPlan.Roof.FLAT) return;
            Floor T = floors[storeys - 1];
            int row = g + T.base + T.rows + 1;
            boolean beams = st.timber() || wealth >= 1;
            int phase = (int) Math.floorMod(rs >>> 7, 3L);
            for (int k = 0; k < T.n(); k++) {
                int x = T.x(k), z = T.z(k);
                if (!b.contains(x, z)) continue;
                if (flue != null && x == flue[0] && z == flue[1]) continue;
                BlockState s = floorBlock;
                if (beams) {
                    if (p.ridgeX() && Math.floorMod(x - T.x0 - phase, 3) == 1) s = st.timber() ? T_BEAM_Z[st.tone()] : wood.log()[2];
                    else if (!p.ridgeX() && Math.floorMod(z - T.z0 - phase, 3) == 1) s = st.timber() ? T_BEAM_X[st.tone()] : wood.log()[0];
                }
                b.set(x, row, z, s);
            }
        }
    }
}
