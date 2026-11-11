package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.Land.Course;
import dev.awaken.gates.realms.Land.LegKind;
import dev.awaken.gates.realms.Land.SiteKind;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.land.Causeway.nearest;
import static dev.awaken.gates.realms.kit.land.Causeway.p;
import static dev.awaken.gates.realms.kit.land.Causeway.through;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.FLOOR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.PAD;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.SPUR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.TRAIL;

/**
 * The Isles (SPEC §50.9, "Rolled lands"): tables standing out of a sea, each on a stalk narrower than itself, each higher
 * than the one before, spans from one to the next, and the last and highest the great thing. They stand round an arc that
 * bulges to one side, so from the way in on the first you look straight across open water at the last, with the others
 * stepping up beside the view. The way crosses each table and the span after it; the middle one is split from edge to
 * edge and crossed on what is left of a bridge; a small table hangs off the way between two of them (the way round), and
 * another is kept back behind the last. On the last a Warden holds the span's head, and the Lord's floor is in its middle.
 *
 * <p>Tables and spans are ground like any other, laid as heights; one piece ({@link Under}) then takes away everything
 * under them but the stalks, so their tops are where {@code ground} says and the sea runs on underneath.
 *
 * <p>Worked out with the last table due north of the way in; a seed may turn east to west.
 */
final class Isles extends Landform {
    private static final double WATER = 62, BED = WATER - 2.6;
    private static final int U = 0, V_ = 1, R = 2, TOP = 3;

    /** Every table: u, v, radius, the feet of its top, two phases its outline wobbles by. The first is the way in's, then those of the way in order, the great one, the one off the way and the one kept back. */
    private final List<double[]> caps = new ArrayList<>();
    /** How many tables stand between the first and the last, and which of them is split. */
    private final int between, riftAt;
    private final SimplexNoise nRoll, nFine;
    private boolean rift;
    private double uMid, vMid, uHalf, vHalf;

    Isles(GeneratedLand land) {
        super(land, 0x15135EL);
        between = 3 + rank / 2;
        riftAt = between == 5 ? 3 : 2;
        nRoll = N.noise(seed + 841);
        nFine = N.noise(seed + 842);
        int n = between + 2;
        double[] radius = new double[n], top = new double[n], along = new double[n];
        radius[0] = 30;
        top[0] = 78;
        for (int i = 1; i <= between; i++) {
            radius[i] = i == riftAt ? 32 : 26;
            top[i] = top[i - 1] + (i == 1 ? 8 : 9);
        }
        radius[n - 1] = 50;
        top[n - 1] = top[n - 2] + 12;
        for (int i = 1; i < n; i++) along[i] = along[i - 1] + radius[i - 1] + radius[i] + 28;
        // round an arc, most of a circle and more of it the more of them there are, so the last is never far from the first
        // (under Lens's Air a thing 300 blocks off is two thirds lost by day)
        double turn = Math.toRadians(170 + 15 * (between - 3)), rho = along[n - 1] / turn, mid = rho * Math.cos(turn / 2);
        for (int i = 0; i < n; i++) {
            double a = -turn / 2 + along[i] / rho;
            cap(i == 0 || i == n - 1 ? 0 : Math.rint(mid - rho * Math.cos(a)), Math.rint(-rho * Math.sin(a)), radius[i], top[i]);
        }
        // one off the way, between the table before the split one and the split one, on the inside of the arc
        double[] a = caps.get(riftAt - 1), b = caps.get(riftAt);
        double du = b[U] - a[U], dv = b[V_] - a[V_], l = Math.hypot(du, dv);
        cap(Math.rint((a[U] + b[U]) / 2 - dv / l * 34), Math.rint((a[V_] + b[V_]) / 2 + du / l * 34), 12, Math.rint((a[TOP] + b[TOP]) / 2));
        // and one kept back, off the far side of the last
        double[] g = caps.get(n - 1);
        cap(g[U] + 90, g[V_] - 6, 14, g[TOP] - 12);
        double u0 = 1e9, u1 = -1e9, v0 = 1e9, v1 = -1e9;
        for (double[] c : caps) {
            u0 = Math.min(u0, c[U] - c[R]);
            u1 = Math.max(u1, c[U] + c[R]);
            v0 = Math.min(v0, c[V_] - c[R]);
            v1 = Math.max(v1, c[V_] + c[R]);
        }
        uMid = (u0 + u1) / 2;
        vMid = (v0 + v1) / 2;
        uHalf = (u1 - u0) / 2 + 64;
        vHalf = (v1 - v0) / 2 + 64;
    }

    private void cap(double u, double v, double radius, double top) {
        caps.add(new double[]{u, v, radius, top, r.nextDouble() * 6.283, r.nextDouble() * 6.283});
    }

    // ---- the big shape --------------------------------------------------------------------------------------------------------

    /** How far a point is from a table's middle as the table counts it: its outline wobbles. */
    private static double reach(double[] c, double u, double v) {
        double du = u - c[U], dv = v - c[V_], d = Math.hypot(du, dv), a = Math.atan2(dv, du);
        return d / (1 + 0.07 * Math.sin(3 * a + c[4]) + 0.04 * Math.sin(5 * a + c[5]));
    }

    /** A table's own height at a point: level, with a shoulder rounded down a few blocks at its edge, and nothing past it. */
    private static double rise(double[] c, double d) {
        if (d >= c[R]) return BED;
        return c[TOP] - Math.min(3.5, c[R] * 0.2) * N.smooth(c[R] - Math.min(7, c[R] * 0.4), c[R], d);
    }

    /** The table a point is on, or null over the sea. */
    private double[] on(double u, double v, double inset) {
        for (double[] c : caps) if (reach(c, u, v) < c[R] - inset) return c;
        return null;
    }

    @Override
    double bs(double u, double v) {
        double y = BED;
        for (double[] c : caps) y = Math.max(y, rise(c, reach(c, u, v)));
        return y;
    }

    @Override
    double natural(double x, double z, double calm) {
        double u = mir * x, v = z;
        for (double[] c : caps) {
            double d = reach(c, u, v);
            if (d >= c[R]) continue;
            // a table's top rolls a little, away from its edge and from any way
            return rise(c, d) + (1.3 * N.fbm(nRoll, u, v, 1 / 19.0, 2) * calm + 0.35 * nFine.get(u / 6.2, v / 6.2)) * N.smooth(c[R] - 1, c[R] - 9, d);
        }
        return BED;
    }

    @Override
    double cut(double x, double z, double s) {
        if (!rift) return s;
        double[] c = caps.get(riftAt);
        double du = (mir * x - c[U]) / (c[R] + 6);
        if (Math.abs(du) >= 1) return s;
        // (a sheer cut: a column eased half way down would be left standing on nothing once the table is hollowed under)
        return Math.abs(z - c[V_]) < 6.8 * Math.sqrt(1 - du * du) && s > BED ? BED : s;
    }

    @Override
    double heat(double x, double z) {
        if (!rift) return 0;
        double[] c = caps.get(riftAt);
        return Math.abs(mir * x - c[U]) < c[R] + 6 ? N.smooth(11, 7, Math.abs(z - c[V_])) : 0;
    }

    @Override
    double edge(int x, int z) {
        return Math.max(Math.abs(mir * x - uMid) - uHalf, Math.abs(z - vMid) - vHalf);
    }

    @Override
    int half() {
        double far = Math.max(Math.abs(uMid) + uHalf, Math.abs(vMid) + vHalf);
        return ((int) Math.ceil(far * 1.08 + 44) + 15) / 16 * 16;
    }

    @Override
    float mouthYaw() {
        return 180;
    }

    // ---- what is under a table ------------------------------------------------------------------------------------------------

    /** How thick a table or a span is at a point: three blocks, and a table thickens toward its stalk. */
    private double thick(double u, double v) {
        for (double[] c : caps) {
            double d = reach(c, u, v);
            if (d < c[R]) return 3 + 4 * N.smooth(c[R], c[R] * 0.36, d);
        }
        return 3;
    }

    /** Whether a cell is inside a table's stalk: a stalk is about a third of its table across, and flares at its foot and under the table. */
    private double stalk(double u, double v, int y) {
        for (double[] c : caps) {
            double d = reach(c, u, v);
            if (d >= c[R]) continue;
            double under = c[TOP] - 8;
            return c[R] * 0.36 * (1 + 0.5 * N.smooth(under - 10, under, y) + 0.35 * N.smooth(WATER + 10, WATER, y)) - d;
        }
        return -1;
    }

    /**
     * Takes away everything under the tables and the spans but the stalks: a column keeps the top few blocks of itself,
     * unless a neighbour stands so far over it that the two would not touch, when it goes altogether (the one-block skirt
     * a way's edge leaves beside a span). The sea is put back where it was. The skin says what a stalk's side, a table's
     * underside and a span's underside are made of.
     */
    final class Under extends Piece<GeneratedLand.Kit> {
        Under() {
            int h = half();
            box(0, 0, h, h);
            mayOverlap = true;
            startsWay = true;
            label = "under the tables";
        }

        /** Whether a neighbour's lowest row stands clear over a column's top: the two do not touch. */
        private static boolean over(int neighbourLow, int top) {
            return neighbourLow != Integer.MAX_VALUE && neighbourLow > top;
        }

        @Override
        public void draw(V v, GeneratedLand.Kit m) {
            int[] gy = new int[20 * 20], low = new int[20 * 20];
            for (int a = 0; a < 20; a++) for (int c = 0; c < 20; c++) {
                int x = v.x0 - 2 + a, z = v.z0 - 2 + c, g = land.ground(x, z);
                gy[a * 20 + c] = g;
                // the lowest row a column keeps
                low[a * 20 + c] = g <= WATER + 1 ? Integer.MAX_VALUE : g - (int) Math.round(thick(mir * x, z)) + 1;
            }
            BlockState water = land.dress().water(), bed = land.dress().dark();
            for (int a = 1; a < 19; a++) for (int c = 1; c < 19; c++) {
                int i = a * 20 + c, g = gy[i];
                if (g <= WATER + 1) continue;
                int x = v.x0 - 2 + a, z = v.z0 - 2 + c, keep = low[i];
                double u = mir * x;
                boolean table = on(u, z, 0) != null;
                if (over(low[i - 20], g) || over(low[i + 20], g) || over(low[i - 1], g) || over(low[i + 1], g)) keep = Integer.MAX_VALUE;
                for (int y = (int) WATER - 3; y < Math.min(keep, g + 1); y++) {
                    double in = stalk(u, z, y);
                    if (in > 0) {
                        if (in < 1.7) v.set(x, y, z, land.stalk(GeneratedLand.STALK, x, y, z));
                        continue;
                    }
                    v.set(x, y, z, y <= WATER ? water : null);
                }
                if (keep == Integer.MAX_VALUE) {
                    // gone altogether: whatever lay on it (a half step, a drift) goes with it
                    v.set(x, g + 1, z, null);
                    v.set(x, (int) WATER - 4, z, bed);
                    continue;
                }
                if (stalk(u, z, keep) <= 0) {
                    v.set(x, keep, z, land.stalk(table ? GeneratedLand.CAP_UNDER : GeneratedLand.SPAN_UNDER, x, keep, z));
                    v.set(x, (int) WATER - 4, z, bed);
                }
            }
        }
    }

    // ---- the way ----------------------------------------------------------------------------------------------------------------

    /** A thin span between two points, its height going evenly from one to the other, standing sheer out of the sea. */
    private void strand(double u0, double v0, double f0, double u1, double v1, double f1) {
        double len = Math.hypot(u1 - u0, v1 - v0);
        int n = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            st(N.lerp(u0, u1, t), N.lerp(v0, v1, t), N.lerp(f0, f1, t), 1.6, 0.6, SPUR);
        }
        land.way(mir * u0, v0, mir * u1, v1, 2);
    }

    /** The point on a table's edge that faces another point, so far in from the edge. */
    private static double[] rim(double[] c, double u, double v, double in) {
        double du = u - c[U], dv = v - c[V_], l = Math.max(1e-6, Math.hypot(du, dv)), far = c[R] * 0.93 - in;
        return new double[]{c[U] + du / l * far, c[V_] + dv / l * far};
    }

    @Override
    void lay() {
        List<String> none = List.of();
        int n = between + 2;
        double[] first = caps.get(0), great = caps.get(n - 1), split = caps.get(riftAt), ledge = caps.get(n), kept = caps.get(n + 1);
        int mouthV = (int) Math.round(first[V_] - 20), mouthFeet = (int) first[TOP], ru = (int) split[U], rv = (int) split[V_];
        int heartV = (int) great[V_], heartFeet = (int) great[TOP], wardenV = heartV + 37;

        // ---- the main way: across each table by its middle, over the split one dead straight, round to the last one's south side
        Trail t = new Trail();
        t.add(0, mouthV, 0);
        List<double[]> run = new ArrayList<>(List.of(p(0, mouthV - 1), p(0, mouthV - 5)));
        for (int i = 1; i < riftAt; i++) run.add(p(caps.get(i)[U], caps.get(i)[V_]));
        run.addAll(List.of(p(ru, rv + 27), p(ru, rv + 20), p(ru, rv + 13)));
        through(t, 0, run.toArray(new double[0][]));
        int iLipS = t.last() + 1;
        for (int i = 12; i >= 7; i--) t.add(ru, rv + i, 0);
        int iBridge = t.last();
        for (int i = 7; i <= 12; i++) t.add(ru, rv - i, 0);
        int iLipN = t.last();
        run = new ArrayList<>(List.of(p(ru, rv - 13), p(ru, rv - 20), p(ru, rv - 27)));
        for (int i = riftAt + 1; i <= between; i++) run.add(p(caps.get(i)[U], caps.get(i)[V_]));
        double[] last = caps.get(between), land0 = rim(great, last[U], last[V_], 4);
        run.addAll(List.of(land0, p(0, wardenV), p(0, wardenV - 5), p(0, wardenV - 9), p(0, heartV + 25)));
        through(t, 0, run.toArray(new double[0][]));
        int iEnd = t.last();
        // every point wants its table's height; over the sea, a height between the two tables the span joins
        for (int i = 0, from = 0; i <= iEnd; i++) {
            double[] c = on(t.x(i), t.z(i), 0.5);
            if (c == null) continue;
            t.p.get(i)[2] = c[TOP];
            for (int k = from + 1; k < i; k++) t.p.get(k)[2] = N.lerp(t.p.get(from)[2], c[TOP], (k - from) / (double) (i - from));
            from = i;
        }
        int[] denAt = new int[between + 1];
        for (int i = 1; i <= between; i++) denAt[i] = i == riftAt ? nearest(t, ru, rv - 21, iLipN, iEnd) : nearest(t, caps.get(i)[U], caps.get(i)[V_], 0, iEnd);
        int iAmb = nearest(t, ru, rv + 22, 0, iLipS), iAmb2 = nearest(t, land0[0], land0[1], iLipN, iEnd), iW = nearest(t, 0, wardenV, iAmb2, iEnd);
        t.level(0, 5);
        for (int i = 1; i <= between; i++) t.level(denAt[i] - 3, denAt[i] + 3);
        t.level(iAmb - 3, iAmb + 3);
        t.level(iLipS - 2, iLipN + 2);
        t.level(iAmb2 - 3, iEnd);
        t.settle(mouthFeet, 0.42);
        int deckFeet = (int) Math.round(t.feet(iBridge));
        rift = true;

        // ---- stations: a way on a table lies in it as any path does; a span stands sheer, three blocks thick, with nothing beside it
        st(0, mouthV, mouthFeet, 6.5, 5, FLOOR);
        for (int i = 0; i <= iEnd; i++) {
            st(t.x(i), t.z(i), t.feet(i), 2.6, on(t.x(i), t.z(i), 8) != null ? 6 : 0.6, TRAIL);
            land.wayPoint(mir * t.x(i), t.z(i), t.feet(i));
            if (i > 0 && i % 4 == 0 && !(i - 4 <= iBridge && i > iBridge)) land.way(mir * t.x(i - 4), t.z(i - 4), mir * t.x(i), t.z(i), 3);
        }
        for (int i = 1; i <= between; i++) st(t.x(denAt[i]), t.z(denAt[i]), t.feet(denAt[i]), 8, 4, PAD);
        st(t.x(iAmb), t.z(iAmb), t.feet(iAmb), 7, 3, PAD);
        st(t.x(iAmb2), t.z(iAmb2), t.feet(iAmb2), 7, 3, PAD);
        // the table off the way: a span to it from the table before the split one, and one on from it to the split one's near half
        double[] before = caps.get(riftAt - 1), l0 = rim(before, ledge[U], ledge[V_], 3), l1 = rim(ledge, before[U], before[V_], 2), l2 = rim(ledge, ru, rv + 22, 2),
                l3 = rim(split, ledge[U], ledge[V_] + 12, 3);
        int ledgeFeet = (int) ledge[TOP];
        st(ledge[U], ledge[V_], ledgeFeet, 5, 3, PAD);
        strand(l0[0], l0[1], before[TOP], l1[0], l1[1], ledgeFeet);
        strand(l2[0], l2[1], ledgeFeet, l3[0], l3[1], split[TOP]);
        // the one kept back: a span down to it from the far side of the last
        double[] k0 = rim(great, kept[U], kept[V_], 3), k1 = rim(kept, great[U], great[V_], 2);
        int keptFeet = (int) kept[TOP];
        st(kept[U], kept[V_], keptFeet, 5.2, 3, PAD);
        strand(k0[0], k0[1], heartFeet, k1[0], k1[1], keptFeet);
        st(0, wardenV, heartFeet, 12, 3, FLOOR);
        st(0, heartV, heartFeet, 23.5, 3, FLOOR);
        st(0, heartV - 30, heartFeet, 16, 3, FLOOR);
        land.freeze();

        // ---- what stands on it: first of all, nothing under any of it
        land.must(new Under());
        land.putSite(SiteKind.MOUTH, 0, mouthFeet, mouthV, 12, "");
        land.putSite(SiteKind.PORTAL, 0, mouthFeet, mouthV + 6, 2, "");
        land.must(new LandPieces.Arrival(0, mouthFeet, mouthV, land));
        stone(lx(8), mouthV + 2, "the first table");
        boolean[] deck = {true, true, false, false, false, true, true, true, false, false, true, true, true};
        land.must(new LandPieces.Bridge(lx(ru), deckFeet, rv - 6, deck, true, land));
        List<int[]> ledges = new ArrayList<>();
        for (int i : new int[]{8, 5, 0, -5, -8}) ledges.add(new int[]{lx(ru), deckFeet, rv + i});
        land.putCourse(new Course("split", lx(ru) - 10, (int) WATER - 4, rv - 12, lx(ru) + 10, deckFeet + 9, rv + 12, ledges));
        for (int s = -1; s <= 1; s += 2) land.maybe(new LandPieces.LampPost(lx(ru + 4), rv + 10 * s, 3, land));
        land.must(new LandPieces.Court(0, heartFeet, wardenV, 11.4, land));
        boolean south = Math.abs(land0[0]) < 12;
        land.must(south ? new LandPieces.Gate(0, wardenV + 10, 9, true, land) : new LandPieces.Gate(lx(Math.signum(land0[0]) * 10), wardenV, 9, land));
        stone(lx(-7), wardenV + 5, "the last table");
        land.must(land.hall(0, heartV, heartFeet, 23, seed + 97));
        land.heartDoor(-3, heartV + 22, 3, heartV + 22, heartFeet, 6, 0, heartV, 22.4);

        // ---- the run
        int dens = 0, herds = 0;
        String next = "fight1";
        land.putLeg("arrive", LegKind.ARRIVE, "The way in", 0, mouthFeet, mouthV, 6, -1, none, "", next);
        for (int i = 1; i <= between; i++) {
            int x = lx(t.x(denAt[i])), feet = (int) t.feet(denAt[i]), z = li(t.z(denAt[i]));
            String id = "fight" + i, after = i == between ? "ambush2" : i + 1 == riftAt ? "ambush" : "fight" + (i + 1);
            if (i == riftAt) {
                // the split table: an ambush on its near half, the bridge, then the den on its far half
                land.putSite(SiteKind.DEN, lx(t.x(iAmb)), (int) t.feet(iAmb), li(t.z(iAmb)), 9, "");
                land.putLeg("ambush", LegKind.AMBUSH, "A quiet edge", lx(t.x(iAmb)), (int) t.feet(iAmb), li(t.z(iAmb)), 7, dens++, land.ambushPack(0), "", "split");
                land.putLeg("split", LegKind.MOVEMENT, "A broken bridge", lx(ru), deckFeet, rv + 8, 9, -1, none, "", id);
            }
            den(t, denAt[i], 8, dens, i);
            if (i + 1 == riftAt) {
                land.putLeg(id, LegKind.FIGHT, "A den on a table", x, feet, z, 8, dens++, land.herd(herds++), "", after, "ledge");
                land.putLeg("ledge", LegKind.BREATHER, "A small table off the way", lx(ledge[U]), ledgeFeet, li(ledge[V_]), 4, -1, none, "", "ambush");
            } else land.putLeg(id, LegKind.FIGHT, i == riftAt ? "A den past the bridge" : "A den on a table", x, feet, z, 8, dens++, land.herd(herds++), "", after);
        }
        land.putSite(SiteKind.DEN, lx(t.x(iAmb2)), (int) t.feet(iAmb2), li(t.z(iAmb2)), 9, "");
        land.putLeg("ambush2", LegKind.AMBUSH, "The head of the last span", lx(t.x(iAmb2)), (int) t.feet(iAmb2), li(t.z(iAmb2)), 7, dens++, land.ambushPack(1), "", "warden");
        land.putSite(SiteKind.WARDEN, 0, heartFeet, wardenV, 11, "");
        land.putLeg("warden", LegKind.WARDEN, "The court before the door", 0, heartFeet, wardenV, 11, 0, land.wardenPack(0), "", "lord", "hidden");
        land.putLeg("hidden", LegKind.HIDDEN, "A place kept back", lx(kept[U]), keptFeet, li(kept[V_]), 5, -1, none, "");
        land.putSite(SiteKind.LORD, 0, heartFeet, heartV, 22, land.ctx.bp().heart().lord());
        land.putLeg("lord", LegKind.LORD, "The middle of the last table", 0, heartFeet, heartV, 22, -1, none, "");

        find(ledge[U] + 2, ledge[V_] + 1);
        find(kept[U] + 1, kept[V_] + 2);
        find(kept[U] - 2, kept[V_] - 1);
        find(-8, wardenV - 3);
        find(first[U] - 10, mouthV + 3);
        find(t.x(denAt[between]) + 5, t.z(denAt[between]) + 4);
        land.must(new LandPieces.StoneRing(lx(kept[U]), li(kept[V_]), 5.6, 6, mir * k1[0], k1[1], seed + 98, land));
    }
}
