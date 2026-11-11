package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.Land.Course;
import dev.awaken.gates.realms.Land.LegKind;
import dev.awaken.gates.realms.Land.SiteKind;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.N.Hex;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.land.GeneratedLand.FLOOR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.PAD;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.SPUR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.STAIR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.TRAIL;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.WATER;

/**
 * The Ascent (SPEC §50.9, "Rolled lands"): one mountain standing in a sea. The way in is on the shore at its foot and the
 * summit is in sight from the first step. The way goes up the foothills, zigzags the flank between two hairpin terraces
 * (crossing a rift on a broken bridge on the long arm), comes to a court at the knee of the mountain where a Warden
 * stands, and from there a stair cut in flights climbs the crag to a notch in the crater's rim. The Lord's place is the
 * crater floor, under the great thing the skin stands on the rim.
 *
 * <p>Everything here is worked out with the mountain's middle at 0,0, the way in due south of it and the first arm going
 * east; a seed may turn that east to west ({@link #mir}).
 */
final class Ascent extends Landform {
    /** How far the flank's foot is from the middle, the stair's tread count and the gap between its flights. */
    private static final double FOOT = 150, FACE = 1.75;
    private static final int TREADS = 14, ROW = 8;
    private static final double ARENA = 24.5, TIERS = 31;
    /** The spit the way in stands on: where it leaves the foothills, how far it runs out past the shore, how wide it is. */
    private static final double SPIT_FROM = FOOT + 20, SPIT = 90, SPIT_WIDE = 46;

    private final int mir;
    /** The radius of the waterline on a round island; lobes push it out and pull it in by a seventh. */
    private final double R;
    private double[] prof;
    private final SimplexNoise nLobe, nRidge, nCrag, nDune, nBench, nFine;
    private final double[] chan;

    private int mouthV, mouthFeet;
    /** The court, the first flight's row, the crater floor, and where the crest's outer edge is. Set as the way is laid. */
    private int vc, courtFeet, v0, flights, floorFeet;
    private double crestOut, kneeR;
    private boolean laid;
    /** The rift: the row the bridge crosses on, the middle of its length, the deck's height. */
    private int riftV, deckFeet;
    private double riftMid;
    private boolean rift;
    /** The fall down the face: its bearing, and the pool at the knee it ends in. */
    private static final double FALL = Math.PI / 2 - 0.5;
    private double poolU, poolV, poolY;
    /** A vent beside the approach: a knoll of columns with a fall down the side that faces the way in, or none where there was no room. */
    private double ventU, ventV;
    private boolean vent;

    Ascent(GeneratedLand land) {
        super(land, 0xA5CE27L);
        mir = r.nextBoolean() ? 1 : -1;
        R = 246 + 64 * rank;
        nLobe = N.noise(seed + 801);
        nRidge = N.noise(seed + 802);
        nCrag = N.noise(seed + 803);
        nDune = N.noise(seed + 804);
        nBench = N.noise(seed + 805);
        nFine = N.noise(seed + 806);
        // what still burns runs down either shoulder and twice down the back: never across the face the way climbs
        double j = 0.12;
        chan = new double[]{Math.PI / 2 + 0.98 + j * r.nextDouble(), Math.PI / 2 - 0.98 - j * r.nextDouble(), Math.PI / 2 + 2.1 + 0.4 * r.nextDouble(), Math.PI / 2 - 2.1 - 0.4 * r.nextDouble()};
        draft();
    }

    // ---- the big shape --------------------------------------------------------------------------------------------------------

    /** The mountain's height by distance from its middle, before anything is known of the stair: sea, beach, foothills, flank. */
    private void draft() {
        int w = (int) Math.round(R), n = w + 420;
        prof = new double[n + 1];
        prof[w] = WATER + 1;
        // the foothills climb fifteen blocks or so however far they run: a bigger land has a longer way in, not a taller mountain
        double foot = 0.02 + 0.18 * Math.min(1, 73 / (R - 173));
        for (int i = w; i > 0; i--) {
            double p = i - 0.5, s = N.lerp(0.42, foot, N.smooth(R - 5, R - 14, p));
            s = N.lerp(s, 0.62, N.smooth(FOOT + 9, FOOT - 9, p));
            prof[i - 1] = prof[i] + s;
        }
        for (int i = w + 1; i <= n; i++) prof[i] = Math.max(44, prof[i - 1] - 0.4);
    }

    /** With the court found: from its back wall the crag goes up at the stair's own slope to the crest. */
    private void crag() {
        double crest = floorFeet + 8;
        for (int i = 0; i < prof.length && i < v0 + 8; i++) prof[i] = Math.min(crest, Math.max(prof[i], courtFeet + 7 + FACE * (v0 - i)));
        crestOut = v0 - (crest - courtFeet - 7) / FACE;
        kneeR = v0 + 2;
        poolU = Math.cos(FALL) * (kneeR + 13);
        poolV = Math.sin(FALL) * (kneeR + 13);
        poolY = at(kneeR + 13) - 1.5;
        laid = true;
    }

    private double at(double p) {
        if (p <= 0) return prof[0];
        if (p >= prof.length - 1) return prof[prof.length - 1];
        int i = (int) p;
        return N.lerp(prof[i], prof[i + 1], p - i);
    }

    /** A point's distance from the middle as the mountain's shape counts it: the island is not round. */
    private double rho(double u, double v, double r) {
        if (r < 100) return r;
        double lobe = N.fbm(nLobe, u / r * 1.7, v / r * 1.7, 1, 2), p = r / (1 + 0.15 * lobe * N.smooth(100, 175, r));
        // due south the foothills run on into the sea as one long tongue, and the way in is at its tip: far enough off that the
        // whole mountain stands in the first look, crown and all
        if (v > 0 && p > SPIT_FROM) {
            double w = N.smooth(SPIT_WIDE, SPIT_WIDE * 0.3, Math.abs(u));
            if (w > 0) p = SPIT_FROM + (p - SPIT_FROM) * (R - SPIT_FROM) / (R - SPIT_FROM + SPIT * w);
        }
        return p;
    }

    /** The big shape alone: what a way follows. */
    private double bs(double u, double v) {
        double r = Math.hypot(u, v);
        return at(rho(u, v, r));
    }

    /** Uphill on the big shape at a point: a unit vector, and how steep. */
    private double[] up(double u, double v) {
        double gu = (bs(u + 1.5, v) - bs(u - 1.5, v)) / 3, gv = (bs(u, v + 1.5) - bs(u, v - 1.5)) / 3, s = Math.hypot(gu, gv);
        if (s < 1e-4) {
            double l = Math.max(1e-6, Math.hypot(u, v));
            return new double[]{-u / l, -v / l, 0};
        }
        return new double[]{gu / s, gv / s, s};
    }

    /** The top of a sea stack standing on this spot, or 0 where there is only sea. */
    private double stack(double u, double v, double p) {
        if (p < R + 18 || p > R + 120) return 0;
        // in a few clusters off the shore, never a ring of them
        if (nDune.get(u / 140 + 9, v / 140) < 0.15) return 0;
        Hex h = N.hex(u, v, 6.5);
        double k = N.rnd(h.q(), h.r(), seed + 31);
        if (k >= 0.08) return 0;
        // highest at its middle and falling away to the water: a stack, not a box
        double far = Math.hypot(u - h.cx(), v - h.cz()) / 6.5, top = (8 + N.rnd(h.q(), h.r(), seed + 32) * 24) * (1 - 0.7 * far * far);
        return top > 2 ? WATER + top : 0;
    }

    private static double wrap(double a) {
        return Math.atan2(Math.sin(a), Math.cos(a));
    }

    /** How far to the side of the nearest burning channel a point is, in blocks; far off where there is none. */
    private double channel(double u, double v, double r, double p) {
        if (!laid || r < crestOut + 2 || p > R + 4) return 99;
        double a = Math.atan2(v, u), best = 99;
        for (int c = 0; c < chan.length; c++) {
            double th = chan[c] + 0.085 * Math.sin(r / 23 + c * 1.9) + 0.045 * Math.sin(r / 8.7 + c * 4.1);
            best = Math.min(best, Math.abs(wrap(a - th)) * r);
        }
        // and one straight down the face beside the stair, from the crest to a pool at the knee: what you see burning from the shore
        // (a little narrower in the count than it lies, so it comes out a block wider than the rest)
        if (r < kneeR + 13) best = Math.min(best, Math.abs(wrap(a - FALL)) * r - 0.8);
        if (vent) {
            // from the vent's top down the side toward the way in, and the little pool it runs out into
            if (v > ventV - 1.5 && v < ventV + 15) best = Math.min(best, Math.abs(u - ventU) + 1.0);
            best = Math.min(best, Math.hypot(u - ventU, v - ventV - 16) - 2.4);
        }
        return Math.min(best, Math.hypot(u - poolU, v - poolV) - 4.5);
    }

    private static double channelWide(double r) {
        return 1.5 + 1.1 * N.smooth(60, 210, r);
    }

    /** How far outside the rift's edge a point is (under 0 inside it), or 99 well clear of it. */
    private double riftOut(double u, double v) {
        if (!rift) return 99;
        double dv = (v - riftMid) / 9;
        if (Math.abs(dv) >= 1) return 99;
        return Math.abs(u) - 6.8 * Math.sqrt(1 - dv * dv);
    }

    @Override
    double natural(double x, double z, double calm) {
        double u = mir * x, v = z, r = Math.max(1e-6, Math.hypot(u, v));
        if (laid && r <= TIERS) return r <= 25 ? floorFeet : floorFeet + 2 * Math.ceil((r - 25) / 2);
        double p = rho(u, v, r), y = at(p);
        if (p > R + 14) {
            double st = stack(u, v, p);
            return st > 0 ? st : y + 0.6 * nFine.get(u / 6.3, v / 6.3);
        }
        double land = N.smooth(R - 2, R - 26, p), cone = N.smooth(40, 64, r);
        // ridges run down the mountain with gullies between them, as they do on anything that was poured
        double rn = nRidge.get(u / r * 2.3 + 7, v / r * 2.3 + p / 260), ridge = 1 - Math.abs(rn);
        y += 8 * land * cone * calm * (ridge * ridge - 0.42);
        // crags: the ground lifts, and where it lifts most it stands up out of the ash as a stack of columns
        double cr = N.fbm(nCrag, u, v, 1 / 27.0, 3);
        y += (2.6 * cr + 7 * N.smooth(0.4, 0.62, cr)) * land * calm * (p > kneeR ? 1 : 0.35);
        // the foothills roll
        y += 3.2 * N.fbm(nDune, u, v, 1 / 58.0, 2) * N.smooth(FOOT - 6, FOOT + 22, p) * land * calm;
        // the flank stands in benches where one flow stopped on another
        if (p < FOOT + 12 && p > kneeR) {
            double m = N.smooth(0, 0.35, nBench.get(u / 90, v / 90)) * calm * N.smooth(FOOT + 12, FOOT - 6, p);
            if (m > 0) {
                double f = y / 7 - Math.floor(y / 7);
                y += m * (Math.floor(y / 7) * 7 + 7 * N.smooth(0.55, 1, f) - y);
            }
        }
        // channels cut a little into the ground and throw up a bank either side
        // the pool at the foot of the fall lies in a hollow of its own
        double pool = laid ? Math.hypot(u - poolU, v - poolV) : 99;
        if (pool < 10) y = N.lerp(y, poolY, N.smooth(10, 6, pool));
        if (vent) {
            double d = Math.hypot(u - ventU, v - ventV);
            if (d < 9) y += 9 * N.smooth(9, 2.5, d);
        }
        double ch = channel(u, v, r, p);
        if (ch < 8) {
            double w = channelWide(r);
            y += 0.8 * N.smooth(w + 0.4, w + 1.4, ch) * N.smooth(w + 4, w + 1.6, ch) - 1.0 * N.smooth(w + 0.6, w - 0.6, ch);
        }
        return y + 0.5 * nFine.get(u / 6.3, v / 6.3) * (0.3 + 0.7 * land);
    }

    @Override
    double cut(double x, double z, double s) {
        double out = riftOut(mir * x, z);
        if (out >= 0) return s;
        double floor = deckFeet - 11;
        return s > floor ? N.lerp(s, floor, N.smooth(0, -1.5, out)) : s;
    }

    @Override
    double columns(double x, double z) {
        double u = mir * x, v = z, r = Math.hypot(u, v);
        if (riftOut(u, v) < 3) return 0;
        double p = rho(u, v, r);
        if (p > R + 14) return stack(u, v, p) > 0 ? 1 : 0;
        double m = laid && r > TIERS + 0.5 ? N.smooth(kneeR + 5, kneeR - 1, p) : 0;
        if (p < R - 22 && p > kneeR) m = Math.max(m, N.smooth(0.42, 0.56, N.fbm(nCrag, u, v, 1 / 27.0, 3)));
        if (vent) m = Math.max(m, N.smooth(9.5, 7, Math.hypot(u - ventU, v - ventV)));
        return m;
    }

    @Override
    boolean crust(double x, double z) {
        double u = mir * x, v = z, r = Math.hypot(u, v);
        if (riftOut(u, v) < -1.4) return true;
        return channel(u, v, r, rho(u, v, r)) < channelWide(r);
    }

    @Override
    double heat(double x, double z) {
        double u = mir * x, v = z, r = Math.hypot(u, v);
        return Math.max(N.smooth(6.5, 2, channel(u, v, r, rho(u, v, r))), N.smooth(5, 0, riftOut(u, v)));
    }

    @Override
    double edge(int x, int z) {
        double r = Math.hypot(x, z);
        return rho(mir * x, z, r) - (R + 34);
    }

    @Override
    int half() {
        // out to where the wind turns you back, on the roundest lobe and off the spit's tip, and a little sea beyond
        double tip = SPIT_FROM + (R + 34 - SPIT_FROM) * (R - SPIT_FROM + SPIT) / (R - SPIT_FROM);
        return ((int) Math.ceil(Math.max((R + 34) * 1.15, tip) + 44) + 15) / 16 * 16;
    }

    @Override
    float mouthYaw() {
        return 180;
    }

    // ---- the way ----------------------------------------------------------------------------------------------------------------

    private void add(Trail t, double u, double v) {
        t.add(u, v, bs(u, v));
    }

    /**
     * Walks the big shape from the trail's end with the hill on one hand, climbing at a grade, until it is as high as
     * asked or has crossed a line of u (NaN for no line).
     */
    private void traverse(Trail t, int dir, double grade, double untilY, double untilU) {
        double u = t.x(t.last()), v = t.z(t.last());
        for (int n = 0; n < 400; n++) {
            if (bs(u, v) >= untilY) return;
            if (!Double.isNaN(untilU) && (dir > 0 ? u >= untilU : u <= untilU)) return;
            double[] g = up(u, v);
            double sin = Math.min(1, grade / Math.max(1e-3, g[2])), cos = Math.sqrt(1 - sin * sin), tu = -g[1], tv = g[0];
            if (tu * dir < 0) {
                tu = -tu;
                tv = -tv;
            }
            u += tu * cos + g[0] * sin;
            v += tv * cos + g[1] * sin;
            add(t, u, v);
        }
    }

    private int lx(double u) {
        return (int) Math.round(mir * u);
    }

    private static int li(double v) {
        return (int) Math.round(v);
    }

    private int st(double u, double v, double feet, double flat, double blend, byte kind) {
        return land.station(mir * u, v, feet, flat, blend, kind, null);
    }

    /** A straight track between two points, its height going evenly from one to the other (straight, so it may climb a block in a block). */
    private void spur(double u0, double v0, double f0, double u1, double v1, double f1, double flat) {
        double len = Math.hypot(u1 - u0, v1 - v0);
        int n = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            st(N.lerp(u0, u1, t), N.lerp(v0, v1, t), N.lerp(f0, f1, t), flat, 4, SPUR);
        }
        land.way(mir * u0, v0, mir * u1, v1, 2);
    }

    @Override
    void lay() {
        List<String> none = List.of();
        // the way in: at the tip of the spit due south, where the land is still six or so over the sea
        double vm = FOOT;
        while (bs(0, vm) > WATER + 6.4) vm += 0.5;
        mouthV = (int) Math.round(vm);
        mouthFeet = (int) Math.round(bs(0, mouthV)) + 1;

        // ---- the main way, as points; heights after
        Trail t = new Trail();
        for (int k = 0; k <= 7; k++) add(t, 0, mouthV - k);
        t.level(0, 7);
        // the approach: up the foothills toward the mountain, wandering as a track does. A longer one at a higher rank
        // (it swings out to one side and comes back, a whole turn every ninety blocks or so, and ends near the line it began on)
        double yA = at(FOOT), u = 0, v = mouthV - 7, far = Math.max(40, mouthV - 7 - FOOT) * 1.06, turns = Math.max(1, Math.round(far / 90));
        double wob = (0.4 + 0.25 * r.nextDouble()) * (r.nextBoolean() ? 1 : -1);
        for (int n = 0; n < 900 && bs(u, v) < yA; n++) {
            double[] g = up(u, v);
            double s = wob * Math.sin(n / far * turns * Math.PI * 2), hu = g[0] - g[1] * s, hv = g[1] + g[0] * s, l = Math.hypot(hu, hv);
            u += hu / l;
            v += hv / l;
            add(t, u, v);
        }
        int iA = t.last();
        // the fights along the approach: the first within a minute of the way in, the rest shared out evenly from there to the
        // foot of the flank, forty to seventy blocks apart however long the approach came out
        List<Integer> early = new ArrayList<>();
        int first = Math.min(46 + r.nextInt(8), Math.max(20, iA / 2)), count = Math.max(1, (int) Math.ceil((iA - first) / 70.0));
        for (int k = 0; k < count; k++) early.add(first + (iA - first) * k / count);
        // the flank: out along it, back across it, in again, between two hairpins; each arm climbs as it goes
        double delta = at(100) - yA, grade = 0.19;
        traverse(t, 1, grade, yA + delta * 0.38, Double.NaN);
        int iH1 = t.last();
        traverse(t, -1, grade, 1e9, 12.5);
        // the rift: the long arm crosses it dead level, on a line of its own
        riftV = li(t.z(t.last()));
        int iLipE = t.last() + 1;
        for (int k = 12; k >= 7; k--) add(t, k, riftV);
        int iBridge = t.last();
        for (int k = 7; k <= 12; k++) add(t, -k, riftV);
        int iLipW = t.last();
        traverse(t, -1, grade, yA + delta * 0.9, Double.NaN);
        int iH2 = t.last();
        traverse(t, 1, grade, 1e9, -0.5);
        vc = li(t.z(t.last()));
        add(t, 0, vc);
        int iC = t.last();
        int iF3 = (iH1 + iLipE) / 2, iF4 = (iLipW + iH2) / 2;

        for (int i : early) t.level(i - 3, i + 3);
        t.level(iA - 3, iA + 3);
        t.level(iH1 - 7, iH1 + 7);
        t.level(iF3 - 3, iF3 + 3);
        t.level(iLipE - 2, iLipW + 2);
        t.level(iF4 - 3, iF4 + 3);
        t.level(iH2 - 7, iH2 + 7);
        t.level(iC - 12, iC);
        t.settle(mouthFeet, 0.42);
        // the vent: a little past the first den and well to one side of the way (the side nearer the spit's middle, so it is
        // never in the sea), wherever nothing of the way comes within sixteen blocks of it
        int iV = early.get(0) + 18;
        if (iV < iA - 12) {
            double[] vh = t.heading(iV);
            double au0 = t.x(iV) - vh[1] * 24, av0 = t.z(iV) + vh[0] * 24, bu0 = t.x(iV) + vh[1] * 24, bv0 = t.z(iV) - vh[0] * 24;
            ventU = Math.abs(au0) < Math.abs(bu0) ? au0 : bu0;
            ventV = Math.abs(au0) < Math.abs(bu0) ? av0 : bv0;
            vent = true;
            for (int i = 0; i <= t.last() && vent; i++) if (Math.hypot(t.x(i) - ventU, t.z(i) - ventV) < 16 || Math.hypot(t.x(i) - ventU, t.z(i) - ventV - 16) < 9) vent = false;
        }
        deckFeet = (int) Math.round(t.feet(iBridge));
        riftMid = riftV + 1;
        rift = true;
        courtFeet = (int) Math.round(t.feet(iC));

        // ---- the crag and the stair: five flights, or four where the mountain came out too small for five
        v0 = vc - 12;
        flights = v0 - (TREADS * 5 + 1) / FACE >= TIERS + 2.5 ? 5 : 4;
        floorFeet = courtFeet + TREADS * flights;
        crag();

        // ---- stations: the main way first, then every place on it
        for (int i = 0; i <= t.last(); i++) {
            st(t.x(i), t.z(i), t.feet(i), 2.6, 6, TRAIL);
            land.wayPoint(mir * t.x(i), t.z(i), t.feet(i));
            if (i > 0 && i % 4 == 0 && !(i - 4 < iBridge + 1 && i > iBridge)) land.way(mir * t.x(i - 4), t.z(i - 4), mir * t.x(i), t.z(i), 3);
        }
        st(0, mouthV, mouthFeet, 6.5, 5, FLOOR);
        for (int i : early) st(t.x(i), t.z(i), t.feet(i), 8, 5, PAD);
        st(t.x(iA), t.z(iA), t.feet(iA), 8, 5, PAD);
        st(t.x(iH1), t.z(iH1), t.feet(iH1), 7.5, 5, PAD);
        st(t.x(iF3), t.z(iF3), t.feet(iF3), 8, 5, PAD);
        st(t.x(iF4), t.z(iF4), t.feet(iF4), 8, 5, PAD);
        st(t.x(iH2), t.z(iH2), t.feet(iH2), 7.5, 5, PAD);
        st(0, vc, courtFeet, 11.5, 5, FLOOR);

        // the goat track: from the foot of the flank up to the terrace on the long arm, with a ledge half way
        // (up the hill first and then along it, so it leaves the foot of the flank square to the arm that leaves it eastward)
        double au = t.x(iA), av = t.z(iA), fu = t.x(iF3), fv = t.z(iF3), ledgeU = au + 6, ledgeV = av - 14;
        int ledgeFeet = (int) Math.round(bs(ledgeU, ledgeV));
        spur(au, av, t.feet(iA), ledgeU, ledgeV, ledgeFeet, 1.6);
        st(ledgeU, ledgeV, ledgeFeet, 4.2, 4, PAD);
        spur(ledgeU, ledgeV, ledgeFeet, fu, fv, t.feet(iF3), 1.6);

        // the hidden place: on from the far hairpin along the hill, behind two standing stones
        double[] hd = t.heading(iH2 - 3);
        double hu = t.x(iH2), hv = t.z(iH2), hidU = hu + hd[0] * 22, hidV = hv + hd[1] * 22;
        int hidFeet = (int) Math.round(t.feet(iH2));
        spur(hu + hd[0] * 6, hv + hd[1] * 6, hidFeet, hidU, hidV, hidFeet, 1.4);
        st(hidU, hidV, hidFeet, 5.2, 4, PAD);

        // a cache off the approach, a few steps from the first fight
        int iE = early.get(0);
        double[] ed = t.heading(iE);
        int side = r.nextBoolean() ? 1 : -1;
        double offU = t.x(iE) - ed[1] * 17 * side, offV = t.z(iE) + ed[0] * 17 * side;
        int offFeet = (int) Math.round(bs(offU, offV));
        spur(t.x(iE) - ed[1] * 7 * side, t.z(iE) + ed[0] * 7 * side, t.feet(iE), offU, offV, offFeet, 1.4);
        st(offU, offV, offFeet, 3, 4, PAD);

        // the stair: flights along the face, each a landing higher than the last, turning at each end
        int balcony = flights / 2, balU = 0, balV = 0, balFeet = 0, topU = 0, topV = 0;
        List<int[]> lamps = new ArrayList<>();
        st(-7, v0 + 2, courtFeet, 3, 3, FLOOR);
        for (int k = 0; k < flights; k++) {
            int dir = k % 2 == 0 ? 1 : -1, us = -7 * dir, ue = 7 * dir, vk = v0 - ROW * k, yk = courtFeet + TREADS * k;
            st(us, vk, yk, 1.5, 3, FLOOR);
            Direction climbs = dir * mir > 0 ? Direction.EAST : Direction.WEST;
            for (int i = 1; i <= TREADS; i++) land.station(mir * (us + dir * i), vk, yk + i, 1.5, 3, STAIR, climbs);
            land.way(mir * us, vk, mir * ue, vk, 2);
            for (int j = 0; j <= ROW; j += 4) st(ue + 3 * dir, vk - j, yk + TREADS, 2.8, 3, FLOOR);
            lamps.add(new int[]{ue + 5 * dir, vk - ROW});
            lamps.add(new int[]{0, vk + 3});
            if (k == balcony) {
                balU = ue + 8 * dir;
                balV = vk - 4;
                balFeet = yk + TREADS;
                st(balU, balV, balFeet, 3.4, 3, PAD);
            }
            topU = ue + 3 * dir;
            topV = vk - ROW;
        }
        // through the rim by a notch, to the crater floor
        for (int vv = topV - 1; Math.hypot(topU, vv) > ARENA - 1; vv--) st(topU, vv, floorFeet, 2.6, 2.5, TRAIL);
        land.way(mir * topU, topV, mir * topU, ARENA, 3);
        st(0, 0, floorFeet, ARENA, 1.5, FLOOR);
        land.freeze();

        // ---- what stands on it
        int mx = 0, mz = mouthV;
        land.putSite(SiteKind.MOUTH, mx, mouthFeet, mz, 12, "");
        land.putSite(SiteKind.PORTAL, mx, mouthFeet, mz + 6, 2, "");
        land.must(new LandPieces.Arrival(mx, mouthFeet, mz, land));
        stone(lx(9), mz + 1, "the shore");

        // the bridge: deck from lip to lip with two spans gone, three cells and two
        boolean[] deck = {true, true, false, false, false, true, true, true, false, false, true, true, true};
        if (mir < 0) for (int i = 0; i < deck.length / 2; i++) {
            boolean b = deck[i];
            deck[i] = deck[deck.length - 1 - i];
            deck[deck.length - 1 - i] = b;
        }
        land.must(new LandPieces.Bridge(-6, deckFeet, riftV, deck, land));
        List<int[]> ledges = new ArrayList<>();
        for (int k : new int[]{8, 5, 0, -5, -8}) ledges.add(new int[]{lx(k), deckFeet, riftV});
        land.putCourse(new Course("rift", -12, deckFeet - 14, li(riftMid) - 11, 12, deckFeet + 9, li(riftMid) + 11, ledges));
        for (int s = -1; s <= 1; s += 2) land.maybe(new LandPieces.LampPost(lx(9 * s), riftV + 4, 3, land));
        // a lamp at the far corner of every landing and half way along every flight, on its outer side: from the shore the
        // stair is a line of lights going up the crag
        for (int[] l : lamps) land.maybe(new LandPieces.LampPost(lx(l[0]), l[1], 4, land));

        // the court, its gate, its Waystone; the crown and the Lord's door
        land.must(new LandPieces.Court(0, courtFeet, vc, 11.4, land));
        land.must(new LandPieces.Gate(lx(-9), vc + 1, 10, land));
        stone(lx(6), vc + 7, "the foot of the stair");
        double open = Math.atan2(TIERS, mir * topU);
        land.must(new LandPieces.Crown(0, 0, floorFeet, ARENA, 35, open, 7, seed + 91, land));
        int door = (int) Math.round(crestOut - 3);
        land.heartDoor(lx(topU) - 5, door, lx(topU) + 5, door, floorFeet, 7, 0, 0, ARENA + 2);

        // ---- the run
        int dens = 0, fights = 0;
        land.putLeg("arrive", LegKind.ARRIVE, "The way in", mx, mouthFeet, mz, 8, -1, none, "", "fight1");
        for (int i : early) {
            fights++;
            String id = "fight" + fights, next = fights < early.size() ? "fight" + (fights + 1) : "fight" + (early.size() + 1);
            den(t, i, 8, dens, fights - 1);
            land.putLeg(id, LegKind.FIGHT, "A den in the foothills", lx(t.x(i)), (int) t.feet(i), li(t.z(i)), 8, dens++, land.herd(fights - 1), "", next);
        }
        String fFoot = "fight" + (fights + 1), fArm = "fight" + (fights + 2), fFar = "fight" + (fights + 3);
        den(t, iA, 8, dens, fights);
        land.putLeg(fFoot, LegKind.FIGHT, "A den at the foot of the flank", lx(t.x(iA)), (int) t.feet(iA), li(t.z(iA)), 8, dens++, land.herd(fights), "", "ambush", "ledge");
        land.putSite(SiteKind.DEN, lx(t.x(iH1)), (int) t.feet(iH1), li(t.z(iH1)), 9, "");
        land.putLeg("ambush", LegKind.AMBUSH, "A quiet terrace", lx(t.x(iH1)), (int) t.feet(iH1), li(t.z(iH1)), 7, dens++, land.ambushPack(0), "", fArm);
        land.putLeg("ledge", LegKind.BREATHER, "A ledge on the goat track", lx(ledgeU), ledgeFeet, li(ledgeV), 4, -1, none, "", fArm);
        den(t, iF3, 8, dens, fights + 1);
        land.putLeg(fArm, LegKind.FIGHT, "A den on the long arm", lx(t.x(iF3)), (int) t.feet(iF3), li(t.z(iF3)), 8, dens++, land.herd(fights + 1), "", "rift");
        land.putLeg("rift", LegKind.MOVEMENT, "A broken bridge", lx(8), deckFeet, riftV, 9, -1, none, "", fFar);
        den(t, iF4, 8, dens, fights + 2);
        land.putLeg(fFar, LegKind.FIGHT, "A den past the rift", lx(t.x(iF4)), (int) t.feet(iF4), li(t.z(iF4)), 8, dens++, land.herd(fights + 2), "", "ambush2");
        land.putSite(SiteKind.DEN, lx(t.x(iH2)), (int) t.feet(iH2), li(t.z(iH2)), 9, "");
        land.putLeg("ambush2", LegKind.AMBUSH, "A quiet terrace", lx(t.x(iH2)), (int) t.feet(iH2), li(t.z(iH2)), 7, dens++, land.ambushPack(1), "", "warden", "hidden");
        land.putLeg("hidden", LegKind.HIDDEN, "A place kept back", lx(hidU), hidFeet, li(hidV), 5, -1, none, "");
        land.putSite(SiteKind.WARDEN, 0, courtFeet, vc, 11, "");
        land.putLeg("warden", LegKind.WARDEN, "The court at the foot of the stair", 0, courtFeet, vc, 11, 0, land.wardenPack(0), "", "balcony");
        land.putLeg("balcony", LegKind.BREATHER, "A balcony on the stair", lx(balU), balFeet, balV, 3, -1, none, "", "lord");
        land.putSite(SiteKind.LORD, 0, floorFeet, 0, (int) ARENA, land.ctx.bp().heart().lord());
        land.putLeg("lord", LegKind.LORD, "The crater", 0, floorFeet, 0, (int) ARENA, -1, none, "");

        // what the hidden place and the ledges keep, and the stones in front of the hidden place
        find(ledgeU + 2, ledgeV - 1);
        find(hidU + hd[0] * 2, hidV + hd[1] * 2);
        find(hidU - hd[1] * 3, hidV + hd[0] * 3);
        find(balU, balV);
        find(offU, offV);
        find(5, vc - 6);
        land.must(new LandPieces.StoneRing(lx(hidU), li(hidV), 5.6, 7, mir * (hu + hd[0] * 6), hv + hd[1] * 6, seed + 92, land));
        land.must(new LandPieces.StoneRing(lx(hu + hd[0] * 11), li(hv + hd[1] * 11), 4.6, 2, mir * hu, hv, seed + 93, land));
    }

    /** A den's site in the middle of its place, and whatever the skin stands round it. */
    private void den(Trail t, int i, int radius, int index, int kind) {
        int x = lx(t.x(i)), z = li(t.z(i)), feet = (int) t.feet(i);
        land.putSite(SiteKind.DEN, x, feet, z, 10, "");
        int back = Math.max(0, i - 10);
        land.denPlace(x, feet, z, radius, kind, mir * t.x(back), t.z(back));
    }

    private void find(double u, double v) {
        LandPieces.Cache c = new LandPieces.Cache(lx(u), li(v), seed + 400 + land.sites(SiteKind.FIND).size(), land);
        land.must(c);
        land.putSite(SiteKind.FIND, c.x, c.gy + 1, c.z, 2, "");
    }

    private void stone(int x, int z, String name) {
        LandPieces.Marker m = new LandPieces.Marker(x, z, land);
        land.must(m);
        land.putSite(SiteKind.WAYSTONE, m.x, m.gy + 2, m.z, 4, name);
    }
}
