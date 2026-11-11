package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.Land.Course;
import dev.awaken.gates.realms.Land.LegKind;
import dev.awaken.gates.realms.Land.SiteKind;
import dev.awaken.gates.realms.kit.N;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.land.GeneratedLand.FLOOR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.PAD;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.TRAIL;

/**
 * The Basin (SPEC §50.9, "Rolled lands"): a wide bowl among peaks, its sides in benches stepping down to a lake, and on an
 * island in the lake the great thing. You arrive at the lip of the highest bench and see all of it. The way goes down bench by
 * bench, turning back on itself at the foot of each ramp so the island is always in front of you: along the top bench,
 * down a ramp cut in the cliff, back along the next bench and over a crevasse on a broken bridge, down again, back along
 * the third, down to the shore, where a Warden holds the landing, and out across the lake to the island, which is the
 * Lord's. A steep track cuts the first corner, and a pocket is cut into the cliff behind the lowest bench.
 *
 * <p>Worked out with the lake's middle at 0,0 and the way in due south of it; a seed may turn east to west.
 */
final class Basin extends Landform {
    private static final double ISLE = 30, LAKE = 88, RISER = 6, STEP = 12, WATER = 62;
    private static final int ISLE_FEET = 65, SHORE_FEET = 66;

    /** How many benches stand over the shore, and how wide each is. */
    private final int benches;
    private final double bench;
    private final SimplexNoise nSnow, nCrag, nTop, nFine;
    private final double ph1, ph2;
    private int riftV, deckFeet;
    private double riftMid, riftHalf = 11;
    private boolean rift;

    Basin(GeneratedLand land) {
        super(land, 0xBA5121L);
        benches = 3;
        bench = 24 + 9 * rank;
        nSnow = N.noise(seed + 821);
        nCrag = N.noise(seed + 822);
        nTop = N.noise(seed + 823);
        nFine = N.noise(seed + 824);
        ph1 = r.nextDouble() * 6.283;
        ph2 = r.nextDouble() * 6.283;
    }

    // ---- the big shape --------------------------------------------------------------------------------------------------------

    /** The inner edge of a bench (0 is the shore), and its feet. */
    private double inner(int k) {
        return LAKE + k * (bench + RISER);
    }

    private static int feet(int k) {
        return SHORE_FEET + (int) STEP * k;
    }

    /** How the bowl's rings swell and narrow round it: never a ruled circle. */
    private double swell(double u, double v, double r) {
        double a = Math.atan2(v, u);
        return 1 + (0.07 * Math.sin(3 * a + ph1) + 0.04 * Math.sin(5 * a + ph2)) * N.smooth(40, 90, r);
    }

    /** A point's distance from the middle as the bowl counts it. */
    private double rho(double u, double v) {
        double r = Math.hypot(u, v);
        return r / swell(u, v, r);
    }

    /** The bowl's height by distance from its middle: island, lake bed, shore, benches and the cliffs between, the wall of peaks. */
    private double at(double p) {
        if (p < ISLE + 4) return N.lerp(ISLE_FEET, WATER - 2.6, N.smooth(ISLE, ISLE + 4, p));
        if (p < LAKE) return N.lerp(WATER - 2.6, SHORE_FEET, N.smooth(LAKE - 5, LAKE, p));
        double out = p - LAKE, span = bench + RISER;
        int k = (int) Math.floor(out / span);
        if (k >= benches) return feet(benches) + 34 * N.smooth(bench, bench + 14, out - benches * span);
        return feet(k) + STEP * N.smooth(bench, span, out - k * span);
    }

    @Override
    double bs(double u, double v) {
        return at(rho(u, v));
    }

    /** The cliffs between the benches and the wall outside the highest: 1 on them, 0 on a bench. */
    private double steep(double p) {
        if (p < LAKE) return 0;
        double out = p - LAKE, span = bench + RISER, in = out - Math.floor(out / span) * span;
        if (out >= benches * span + bench) return 1;
        return N.smooth(bench - 1.5, bench + 0.5, in) * N.smooth(span + 1.5, span - 0.5, in);
    }

    /** How far outside the crevasse's edge a point is (under 0 in it), or 99 well clear. */
    private double riftOut(double u, double v) {
        if (!rift) return 99;
        double dv = (v - riftMid) / riftHalf;
        return Math.abs(dv) >= 1 ? 99 : Math.abs(u) - 6.8 * Math.sqrt(1 - dv * dv);
    }

    @Override
    double natural(double x, double z, double calm) {
        double u = mir * x, v = z, p = rho(u, v), y = at(p);
        if (p < LAKE - 5 && p > ISLE + 4) return y;
        double flat = 1 - steep(p), top = benches * (bench + RISER) + bench + LAKE;
        // snow lies in swells on the benches, and rock stands up through it here and there
        if (p >= LAKE && p < top) y += (1.7 * N.fbm(nSnow, u, v, 1 / 23.0, 2) + 6 * N.smooth(0.5, 0.7, N.fbm(nCrag, u, v, 1 / 24.0, 3))) * calm * flat;
        if (p >= top) y += 12 * N.fbm(nTop, u, v, 1 / 46.0, 3) * N.smooth(top + 10, top + 50, p);
        return y + 0.4 * nFine.get(u / 6.2, v / 6.2) * (p < ISLE + 4 ? 0 : 1);
    }

    @Override
    double cut(double x, double z, double s) {
        double o = riftOut(mir * x, z), floor = deckFeet - 11;
        return o < 0 && s > floor ? N.lerp(s, floor, N.smooth(0, -1.5, o)) : s;
    }

    @Override
    double columns(double x, double z) {
        double u = mir * x, v = z, p = rho(u, v);
        if (p < LAKE || riftOut(u, v) < 3) return 0;
        double top = benches * (bench + RISER) + bench + LAKE;
        return Math.max(steep(p), p < top ? N.smooth(0.5, 0.64, N.fbm(nCrag, u, v, 1 / 24.0, 3)) : 0);
    }

    @Override
    double heat(double x, double z) {
        return N.smooth(5, 0, riftOut(mir * x, z));
    }

    @Override
    double edge(int x, int z) {
        return rho(mir * x, z) - (LAKE + benches * (bench + RISER) + bench + 16);
    }

    @Override
    int half() {
        return ((int) Math.ceil((LAKE + benches * (bench + RISER) + bench + 16) * 1.12 + 44) + 15) / 16 * 16;
    }

    @Override
    float mouthYaw() {
        return 180;
    }

    // ---- the way ----------------------------------------------------------------------------------------------------------------

    /** The point at a ring of the bowl and an angle round it from due south (east is positive). */
    private double[] at(double p, double a) {
        double th = Math.PI / 2 - a, r = p;
        for (int i = 0; i < 4; i++) r = p * swell(Math.cos(th) * r, Math.sin(th) * r, r);
        return new double[]{Math.cos(th) * r, Math.sin(th) * r};
    }

    /** Walks round a ring from one angle to another, a block at a time, drifting from one ring to another as it goes. */
    private double arc(Trail t, double p0, double p1, double a0, double a1) {
        double a = a0, dir = Math.signum(a1 - a0);
        while ((a1 - a) * dir > 0) {
            double f = (a - a0) / (a1 - a0), p = N.lerp(p0, p1, f);
            double[] q = at(p, a);
            add(t, q[0], q[1]);
            a += dir / Math.max(40, Math.hypot(q[0], q[1]));
        }
        return a1;
    }

    @Override
    void lay() {
        List<String> none = List.of();
        int n = benches;
        double span = bench + RISER, mid = bench / 2, east = 0.38 + 0.1 * r.nextDouble(), west = -0.42 - 0.1 * r.nextDouble(), turn = 40;
        // the way in stands at the very lip of the top bench, on a prow of it: from a bench's middle its own edge hides the lake
        double[] m = at(inner(n) + 5, 0);
        int mouthU = 0, mouthV = (int) Math.round(m[1]), mouthFeet = feet(n);

        // ---- the main way, bench by bench
        Trail t = new Trail();
        add(t, 0, mouthV);
        t.level(0, 7);
        // the top bench, eastward; a ramp down the cliff, still eastward; the first hairpin
        double a = arc(t, inner(n) + 5, inner(n) + mid, 0, east * 0.4);
        a = arc(t, inner(n) + mid, inner(n) + 3, a, east);
        a = arc(t, inner(n) + 2, inner(n) - RISER - 2, a, a + turn / inner(n));
        int iH1 = t.last();
        // the second bench, westward, to the crevasse; over it dead level on a line of its own; on westward
        double p2 = inner(n - 1) + mid;
        arc(t, inner(n - 1) + bench - 3, p2, a, 13.5 / p2);
        riftV = li(t.z(t.last()));
        int iLipE = t.last() + 1;
        for (int k = 12; k >= 7; k--) add(t, k, riftV);
        int iBridge = t.last();
        for (int k = 7; k <= 12; k++) add(t, -k, riftV);
        int iLipW = t.last();
        a = arc(t, p2, inner(n - 1) + 3, -13.5 / p2, west);
        a = arc(t, inner(n - 1) + 2, inner(n - 1) - RISER - 2, a, a - turn / inner(n - 1));
        int iH2 = t.last();
        // the third bench, back eastward to a little short of due south; the last ramp, down to the shore
        double back = -(turn * 0.6) / inner(n - 2);
        a = arc(t, inner(n - 2) + bench - 3, inner(n - 2) + 3, a, back);
        arc(t, inner(n - 2) + 2, inner(n - 2) - RISER - 2, a, 0);
        // the landing on the shore, and straight out across the lake to the island
        double[] s = at(inner(0) + mid, 0);
        int shoreV = (int) Math.round(s[1]);
        // (out on the lake the way lies on the ice, not on the bed under it)
        for (double v = t.z(t.last()) - 1; v > ISLE - 6; v--) t.add(0, v, Math.max(bs(0, v), WATER + 1));
        int iEnd = t.last(), iShore = 0, iF1 = 42 + r.nextInt(8), iF2 = (iH1 + iLipE) / 2, iF3 = (iLipW + iH2) / 2;
        for (int i = 0; i <= iEnd; i++) if (Math.abs(t.z(i) - shoreV) < Math.abs(t.z(iShore) - shoreV) && i > iH2) iShore = i;
        if (iShore == 0) iShore = iEnd - 70;
        t.level(iF1 - 3, iF1 + 3);
        t.level(iH1 - 6, iH1 + 6);
        t.level(iF2 - 3, iF2 + 3);
        t.level(iLipE - 2, iLipW + 2);
        t.level(iF3 - 3, iF3 + 3);
        t.level(iH2 - 6, iH2 + 6);
        t.level(iShore - 8, iShore + 8);
        t.level(iEnd - 8, iEnd);
        t.settle(mouthFeet, 0.42);
        deckFeet = (int) Math.round(t.feet(iBridge));
        riftMid = riftV;
        // the crevasse splits its bench from the cliff behind to the edge in front: nobody walks round it
        riftHalf = Math.max(11, bench / 2 + 2);
        rift = true;
        int shoreFeet = (int) Math.round(t.feet(iShore));

        // ---- stations
        st(mouthU, mouthV, mouthFeet, 6.5, 5, FLOOR);
        for (int i = 0; i <= iEnd; i++) {
            st(t.x(i), t.z(i), t.feet(i), 2.6, 6, TRAIL);
            land.wayPoint(mir * t.x(i), t.z(i), t.feet(i));
            if (i > 0 && i % 4 == 0 && !(i - 4 <= iBridge && i > iBridge)) land.way(mir * t.x(i - 4), t.z(i - 4), mir * t.x(i), t.z(i), 3);
        }
        for (int i : new int[]{iF1, iF2, iF3}) st(t.x(i), t.z(i), t.feet(i), 8, 5, PAD);
        st(t.x(iH1), t.z(iH1), t.feet(iH1), 7.5, 5, PAD);
        st(t.x(iH2), t.z(iH2), t.feet(iH2), 7.5, 5, PAD);
        st(0, t.z(iShore), shoreFeet, 11.5, 5, FLOOR);
        st(0, 0, ISLE_FEET, 23.5, 3, FLOOR);
        // rock out of the lake behind the floor, for whatever the country's builders stood there
        for (int k = -1; k <= 1; k++) st(19 * k, -38, ISLE_FEET, 17, 3, FLOOR);
        // the steep track that cuts the first corner: from the top bench straight down the cliff to the second, a ledge at its foot
        double[] c0 = at(inner(n) + 4, east * 0.42), c1 = at(inner(n - 1) + bench - 6, east * 0.5);
        int iFrom = 0, iTo = iH1;
        for (int i = 0; i <= iH1; i++) if (Math.hypot(t.x(i) - c0[0], t.z(i) - c0[1]) < Math.hypot(t.x(iFrom) - c0[0], t.z(iFrom) - c0[1])) iFrom = i;
        for (int i = iH1; i <= iLipE; i++) if (Math.hypot(t.x(i) - c1[0], t.z(i) - c1[1]) < Math.hypot(t.x(iTo) - c1[0], t.z(iTo) - c1[1])) iTo = i;
        int ledgeFeet = feet(n - 1);
        spur(t.x(iFrom), t.z(iFrom), t.feet(iFrom), c1[0], c1[1], ledgeFeet, 1.6);
        st(c1[0], c1[1], ledgeFeet, 4.2, 4, PAD);
        spur(c1[0], c1[1], ledgeFeet, t.x(iTo), t.z(iTo), t.feet(iTo), 1.6);
        // the hidden place: a pocket cut into the cliff behind the third bench
        int iK = (iH2 + iShore) / 2 - 10;
        double kr = Math.hypot(t.x(iK), t.z(iK)), ku = t.x(iK) / kr * (kr + 15), kv = t.z(iK) / kr * (kr + 15);
        int kFeet = (int) Math.round(t.feet(iK)) + 1;
        st(ku, kv, kFeet, 5.2, 4, PAD);
        spur(t.x(iK), t.z(iK), t.feet(iK), t.x(iK) / kr * (kr + 10), t.z(iK) / kr * (kr + 10), kFeet, 1.4);
        // a rock frozen into the lake, a little off the way across
        double rockU = 34, rockV = (ISLE + LAKE) / 2 + 6;
        st(rockU, rockV, WATER + 2, 3, 3, PAD);
        land.freeze();

        // ---- what stands on it
        land.putSite(SiteKind.MOUTH, 0, mouthFeet, mouthV, 12, "");
        land.putSite(SiteKind.PORTAL, 0, mouthFeet, mouthV + 6, 2, "");
        land.must(new LandPieces.Arrival(0, mouthFeet, mouthV, land));
        stone(lx(9), mouthV + 1, "the rim");
        boolean[] deck = {true, true, false, false, false, true, true, true, false, false, true, true, true};
        if (mir < 0) for (int i = 0; i < deck.length / 2; i++) {
            boolean b = deck[i];
            deck[i] = deck[deck.length - 1 - i];
            deck[deck.length - 1 - i] = b;
        }
        land.must(new LandPieces.Bridge(-6, deckFeet, riftV, deck, land));
        List<int[]> ledges = new ArrayList<>();
        for (int k : new int[]{8, 5, 0, -5, -8}) ledges.add(new int[]{lx(k), deckFeet, riftV});
        land.putCourse(new Course("crevasse", -12, deckFeet - 14, riftV - 13, 12, deckFeet + 9, riftV + 13, ledges));
        for (int k = -1; k <= 1; k += 2) land.maybe(new LandPieces.LampPost(lx(9 * k), riftV + 4, 3, land));
        int sz = li(t.z(iShore));
        land.must(new LandPieces.Court(0, shoreFeet, sz, 11.4, land));
        land.must(new LandPieces.Gate(0, sz - 9, 9, true, land));
        stone(lx(7), sz + 5, "the landing");
        land.must(land.hall(0, 0, ISLE_FEET, 23, seed + 97));
        land.heartDoor(-3, 22, 3, 22, ISLE_FEET, 6, 0, 0, 22.4);

        // ---- the run
        int dens = 0;
        land.putLeg("arrive", LegKind.ARRIVE, "The way in", 0, mouthFeet, mouthV, 8, -1, none, "", "fight1");
        den(t, iF1, 8, dens, 0);
        land.putLeg("fight1", LegKind.FIGHT, "A den on the top bench", lx(t.x(iF1)), (int) t.feet(iF1), li(t.z(iF1)), 8, dens++, land.herd(0), "", "ambush", "ledge");
        land.putSite(SiteKind.DEN, lx(t.x(iH1)), (int) t.feet(iH1), li(t.z(iH1)), 9, "");
        land.putLeg("ambush", LegKind.AMBUSH, "A quiet corner", lx(t.x(iH1)), (int) t.feet(iH1), li(t.z(iH1)), 7, dens++, land.ambushPack(0), "", "fight2");
        land.putLeg("ledge", LegKind.BREATHER, "A ledge at the foot of the steep track", lx(c1[0]), ledgeFeet, li(c1[1]), 4, -1, none, "", "fight2");
        den(t, iF2, 8, dens, 1);
        land.putLeg("fight2", LegKind.FIGHT, "A den on the second bench", lx(t.x(iF2)), (int) t.feet(iF2), li(t.z(iF2)), 8, dens++, land.herd(1), "", "crevasse");
        land.putLeg("crevasse", LegKind.MOVEMENT, "A broken bridge", lx(8), deckFeet, riftV, 9, -1, none, "", "fight3");
        den(t, iF3, 8, dens, 2);
        land.putLeg("fight3", LegKind.FIGHT, "A den past the crevasse", lx(t.x(iF3)), (int) t.feet(iF3), li(t.z(iF3)), 8, dens++, land.herd(2), "", "ambush2");
        land.putSite(SiteKind.DEN, lx(t.x(iH2)), (int) t.feet(iH2), li(t.z(iH2)), 9, "");
        land.putLeg("ambush2", LegKind.AMBUSH, "A quiet corner", lx(t.x(iH2)), (int) t.feet(iH2), li(t.z(iH2)), 7, dens++, land.ambushPack(1), "", "warden", "hidden");
        land.putLeg("hidden", LegKind.HIDDEN, "A place kept back", lx(ku), kFeet, li(kv), 5, -1, none, "");
        land.putSite(SiteKind.WARDEN, 0, shoreFeet, sz, 11, "");
        land.putLeg("warden", LegKind.WARDEN, "The landing on the shore", 0, shoreFeet, sz, 11, 0, land.wardenPack(0), "", "lord");
        land.putSite(SiteKind.LORD, 0, ISLE_FEET, 0, 22, land.ctx.bp().heart().lord());
        land.putLeg("lord", LegKind.LORD, "The island", 0, ISLE_FEET, 0, 22, -1, none, "");

        find(c1[0] + 2, c1[1] + 1);
        find(ku + 1, kv + 2);
        find(ku - 2, kv - 1);
        find(-7, t.z(iShore) + 6);
        find(rockU, rockV);
        find(t.x(3) + 9, t.z(3) - 3);
        land.must(new LandPieces.StoneRing(lx(ku), li(kv), 5.6, 6, mir * t.x(iK), t.z(iK), seed + 98, land));
    }
}
