package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.Land.Course;
import dev.awaken.gates.realms.Land.LegKind;
import dev.awaken.gates.realms.Land.SiteKind;
import dev.awaken.gates.realms.kit.N;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.land.GeneratedLand.FLOOR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.PAD;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.STAIR;
import static dev.awaken.gates.realms.kit.land.GeneratedLand.TRAIL;

/**
 * The Causeway (SPEC §50.9, "Rolled lands"): a flat sea to every horizon, one raised road across it, and on the skyline
 * at the road's end the great thing on its rock. You arrive on a headland and see the whole road. It leaves the headland
 * by a ramp, runs out over the sea on a dyke three blocks over the surface, swings west past an island, crosses back east
 * over a breach on what is left of a bridge, swings past a second island and comes in from the south to a round bastion
 * at the foot of the rock, where a Warden holds the gate; a stair goes up the rock's face to the Lord's floor. The dens are
 * on the islands beside the road, each joined to it by a bar; the first island has a bar at each end (the way round), and a
 * bar at the sea's own level runs on from the second to a small island kept back.
 *
 * <p>Worked out with the Lord's floor due north of the way in; a seed may turn east to west.
 */
final class Causeway extends Landform {
    private static final double WATER = 62, BED = WATER - 2.6;
    private static final int DECK = 66, ISLE = 69, HEAD = 72, HEART = 78, SEA = 63;

    /** Half the distance from the way in to the Lord's floor, and how far the land runs to either side. */
    private final double far;
    private static final double WIDE = 190;
    private final SimplexNoise nDune, nCrag, nFine;
    /** Every island: u, v, radius, the feet of its top, 1 for rock (a table with cliffs) or 0 for a dune, and two phases its outline wobbles by. */
    private final List<double[]> isles = new ArrayList<>();
    private int riftV;
    private boolean rift;

    Causeway(GeneratedLand land) {
        super(land, 0xCA05E7L);
        // (kept short: under Lens's Air a thing 300 blocks off is two thirds lost by day, so the great thing has to stand near)
        far = 110 + 5 * rank;
        nDune = N.noise(seed + 831);
        nCrag = N.noise(seed + 832);
        nFine = N.noise(seed + 833);
        // the headland the way in stands on, and the rock the great thing stands on
        isle(0, far + 22, 34, HEAD, 1);
        isle(0, -far - 14, 52, HEART, 1);
    }

    private double[] isle(double u, double v, double radius, double top, int rock) {
        double[] i = {u, v, radius, top, rock, r.nextDouble() * 6.283, r.nextDouble() * 6.283};
        isles.add(i);
        return i;
    }

    // ---- the big shape --------------------------------------------------------------------------------------------------------

    /** How far a point is from an island's middle as the island counts it: its outline wobbles, and a dune lies long across the wind. */
    private static double reach(double[] i, double u, double v) {
        double du = u - i[0], dv = v - i[1];
        if (i[4] == 0) du *= 0.62;
        double d = Math.hypot(du, dv), a = Math.atan2(dv, du);
        return d / (1 + 0.1 * Math.sin(3 * a + i[5]) + 0.06 * Math.sin(5 * a + i[6]));
    }

    /** How far in from a rock's edge its cliff runs: twelve blocks, less on a small one. */
    private static double cliff(double[] i) {
        return Math.min(12, i[2] * 0.45);
    }

    /** An island's own height at a point: a rock is a table with a cliff round it, a dune a long low dome. */
    private static double rise(double[] i, double d) {
        if (d >= i[2]) return BED;
        return i[4] != 0 ? N.lerp(BED, i[3], N.smooth(i[2], i[2] - cliff(i), d)) : BED + (i[3] - BED) * N.smooth(i[2], i[2] * 0.25, d);
    }

    @Override
    double bs(double u, double v) {
        double y = BED;
        for (double[] i : isles) y = Math.max(y, rise(i, reach(i, u, v)));
        return y;
    }

    @Override
    double natural(double x, double z, double calm) {
        double u = mir * x, v = z, y = bs(u, v);
        // the sea is dead flat; whatever stands out of it rolls, and the wind has ridged the sand
        double dry = N.smooth(WATER, WATER + 3, y);
        if (dry > 0) y += (1.5 * N.fbm(nDune, u * 0.6, v, 1 / 17.0, 2) + 0.4 * nFine.get(u / 6.3, v / 6.3)) * calm * dry;
        return y;
    }

    @Override
    double cut(double x, double z, double s) {
        if (!rift) return s;
        double dv = (z - riftV) / 8.0;
        if (Math.abs(dv) >= 1) return s;
        double o = Math.abs(mir * x) - 6.8 * Math.sqrt(1 - dv * dv);
        return o < 0 && s > BED ? N.lerp(s, BED, N.smooth(0, -1.5, o)) : s;
    }

    @Override
    double columns(double x, double z) {
        double u = mir * x, v = z, c = 0;
        for (double[] i : isles) {
            if (i[4] == 0) continue;
            double d = reach(i, u, v), in = i[2] - cliff(i);
            if (d > i[2] + 1) continue;
            // a rock's cliff stands in columns, and crags stand up on its top here and there
            c = Math.max(c, Math.max(N.smooth(i[2] + 1, i[2] - 2, d) * N.smooth(in - 3, in + 1, d), N.smooth(0.5, 0.64, N.fbm(nCrag, u, v, 1 / 22.0, 3)) * N.smooth(in + 2, in - 4, d)));
        }
        return c;
    }

    @Override
    double edge(int x, int z) {
        return Math.max(Math.abs(x) - WIDE, Math.abs(z) - (far + 76));
    }

    @Override
    int half() {
        return ((int) Math.ceil(Math.max(WIDE, far + 76) * 1.12 + 44) + 15) / 16 * 16;
    }

    @Override
    float mouthYaw() {
        return 180;
    }

    // ---- the way ----------------------------------------------------------------------------------------------------------------

    /** Lays a way through points with its corners rounded, a point every block, every one wanting the same height. */
    static void through(Trail t, double want, double[]... pts) {
        List<double[]> q = new ArrayList<>(List.of(pts));
        for (int pass = 0; pass < 3; pass++) {
            List<double[]> n = new ArrayList<>();
            n.add(q.get(0));
            for (int i = 0; i + 1 < q.size(); i++) {
                double[] a = q.get(i), b = q.get(i + 1);
                if (i > 0) n.add(new double[]{a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25});
                if (i + 2 < q.size()) n.add(new double[]{a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75});
            }
            n.add(q.get(q.size() - 1));
            q = n;
        }
        double at = 0;
        for (int i = 0; i + 1 < q.size(); i++) {
            double[] a = q.get(i), b = q.get(i + 1);
            double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
            for (; at < l; at += 1) t.add(a[0] + (b[0] - a[0]) * at / l, a[1] + (b[1] - a[1]) * at / l, want);
            at -= l;
        }
    }

    static int nearest(Trail t, double u, double v, int from, int to) {
        int best = from;
        for (int i = from; i <= to; i++) if (Math.hypot(t.x(i) - u, t.z(i) - v) < Math.hypot(t.x(best) - u, t.z(best) - v)) best = i;
        return best;
    }

    static double[] p(double u, double v) {
        return new double[]{u, v};
    }

    @Override
    void lay() {
        List<String> none = List.of();
        double west = -58, east = 58, top = far - 32, span = 2 * far - 98, v2 = top - 0.26 * span, v3 = top - 0.47 * span, v4 = top - 0.71 * span, v5 = top - 0.87 * span;
        int mouthV = (int) Math.round(far), wardenV = (int) Math.round(-far + 50), zs = (int) Math.round(-far + 38), heartV = (int) Math.round(-far);
        riftV = (int) Math.round(top - 0.59 * span);

        // ---- the main way: off the headland, west, back east over the breach, and in to the gate from the south
        Trail t = new Trail();
        t.add(0, mouthV, HEAD);
        through(t, DECK, p(0, mouthV - 1), p(0, far - 14), p(0, top), p(west, v2), p(west, v3), p(-36, riftV), p(-24, riftV), p(-13, riftV));
        int iLipW = t.last() + 1;
        for (int i = 12; i >= 7; i--) t.add(-i, riftV, DECK);
        int iBridge = t.last();
        for (int i = 7; i <= 12; i++) t.add(i, riftV, DECK);
        int iLipE = t.last();
        through(t, DECK, p(13, riftV), p(24, riftV), p(36, riftV), p(east, v4), p(east, v5), p(0, -far + 74), p(0, -far + 66), p(0, wardenV + 1));
        t.add(0, wardenV, DECK);
        int iEnd = t.last();
        double av = (v2 + v3) / 2, bv = (v4 + v5) / 2;
        int iF1 = 44 + r.nextInt(6), iAmb = nearest(t, west, v2, 0, iLipW), iAmb2 = nearest(t, east / 2, (v5 - far + 74) / 2, iLipE, iEnd);
        int iIn = nearest(t, west, av + 12, 0, iLipW), iOut = nearest(t, west, av - 12, 0, iLipW), iB = nearest(t, east, bv, iLipE, iEnd);
        t.level(0, 5);
        t.level(iF1 - 3, iF1 + 3);
        t.level(iAmb - 3, iAmb + 3);
        t.level(iAmb2 - 3, iAmb2 + 3);
        t.level(iEnd - 14, iEnd);
        t.settle(HEAD, 0.3);
        rift = true;

        // ---- the islands the dens are on, the one kept back, and those that are only country
        double au = west - 36, bu = east + 36, ku = bu + 62, kv = bv - 16;
        isle(au, av, 28, ISLE, 1);
        isle(bu, bv, 28, ISLE, 1);
        isle(ku, kv, 15, ISLE - 2, 1);
        for (int tries = 0, made = 0; tries < 400 && made < 11; tries++) {
            double u = (r.nextDouble() * 2 - 1) * (WIDE - 30), v = (r.nextDouble() * 2 - 1) * (far + 40), radius = 18 + r.nextDouble() * 16;
            boolean clear = true;
            for (double[] i : isles) if (Math.hypot(u - i[0], v - i[1]) < radius * 1.7 + i[2] + 10) clear = false;
            for (int i = 0; i <= iEnd && clear; i += 4) if (Math.hypot((u - t.x(i)) * 0.62, v - t.z(i)) < radius + 24) clear = false;
            if (Math.hypot(u - ku, v - kv) < radius * 1.7 + 40) clear = false;
            if (!clear) continue;
            isle(u, v, radius, SEA + 3 + r.nextInt(7), 0);
            made++;
        }

        // ---- stations
        st(0, mouthV, HEAD, 6.5, 5, FLOOR);
        for (int i = 0; i <= iEnd; i++) {
            // the dyke stands steep out of the sea: the ground comes back to its own shape in three blocks, not six
            st(t.x(i), t.z(i), t.feet(i), 2.6, 3, TRAIL);
            land.wayPoint(mir * t.x(i), t.z(i), t.feet(i));
            if (i > 0 && i % 4 == 0 && !(i - 4 <= iBridge && i > iBridge)) land.way(mir * t.x(i - 4), t.z(i - 4), mir * t.x(i), t.z(i), 3);
        }
        for (int i : new int[]{iF1, iAmb, iAmb2}) st(t.x(i), t.z(i), t.feet(i), 8, 3, PAD);
        st(au, av, ISLE, 8.5, 5, PAD);
        spur(t.x(iIn), t.z(iIn), t.feet(iIn), au + 10, av + 5, ISLE, 1.6);
        spur(au + 10, av - 5, ISLE, t.x(iOut), t.z(iOut), t.feet(iOut), 1.6);
        st(bu, bv, ISLE, 8.5, 5, PAD);
        spur(t.x(iB), t.z(iB), t.feet(iB), bu - 10, bv, ISLE, 1.6);
        // on from the second island: down to the sea's own level, along a bar that barely shows, and up onto the island kept back
        st(ku, kv, ISLE - 2, 5.2, 4, PAD);
        spur(bu + 12, bv - 3, ISLE, bu + 27, bv - 6, SEA, 1.4);
        spur(bu + 27, bv - 6, SEA, ku - 16, kv + 3, SEA, 1.4);
        spur(ku - 16, kv + 3, SEA, ku - 6, kv + 1, ISLE - 2, 1.4);
        st(0, wardenV, DECK, 12, 4, FLOOR);
        // the stair up the rock's face, the Lord's floor, and rock behind it for whatever the country's builders stood there
        for (int i = 1; i <= HEART - DECK; i++) land.station(0, zs - i, DECK + i, 2.5, 2, STAIR, Direction.NORTH);
        land.way(0, zs, 0, zs - (HEART - DECK), 3);
        st(0, heartV, HEART, 23.5, 3, FLOOR);
        for (int i = -1; i <= 1; i++) st(19 * i, heartV - 38, HEART, 17, 3, FLOOR);
        land.freeze();

        // ---- what stands on it
        land.putSite(SiteKind.MOUTH, 0, HEAD, mouthV, 12, "");
        land.putSite(SiteKind.PORTAL, 0, HEAD, mouthV + 6, 2, "");
        land.must(new LandPieces.Arrival(0, HEAD, mouthV, land));
        stone(lx(8), mouthV + 2, "the headland");
        boolean[] deck = {true, true, false, false, false, true, true, true, false, false, true, true, true};
        if (mir < 0) for (int i = 0; i < deck.length / 2; i++) {
            boolean b = deck[i];
            deck[i] = deck[deck.length - 1 - i];
            deck[deck.length - 1 - i] = b;
        }
        land.must(new LandPieces.Bridge(-6, DECK, riftV, deck, land));
        List<int[]> ledges = new ArrayList<>();
        for (int i : new int[]{-8, -5, 0, 5, 8}) ledges.add(new int[]{lx(i), DECK, riftV});
        land.putCourse(new Course("breach", -12, (int) WATER - 4, riftV - 10, 12, DECK + 9, riftV + 10, ledges));
        for (int s = -1; s <= 1; s += 2) land.maybe(new LandPieces.LampPost(lx(10 * s), riftV + 3, 3, land));
        land.must(new LandPieces.Court(0, DECK, wardenV, 11.4, land));
        land.must(new LandPieces.Gate(0, wardenV + 10, 9, true, land));
        stone(lx(7), wardenV + 5, "the foot of the rock");
        land.must(land.hall(0, heartV, HEART, 23, seed + 97));
        land.heartDoor(-3, heartV + 22, 3, heartV + 22, HEART, 6, 0, heartV, 22.4);

        // ---- the run
        int dens = 0;
        land.putLeg("arrive", LegKind.ARRIVE, "The way in", 0, HEAD, mouthV, 6, -1, none, "", "fight1");
        den(t, iF1, 8, dens, 0);
        land.putLeg("fight1", LegKind.FIGHT, "A den on the road", lx(t.x(iF1)), (int) t.feet(iF1), li(t.z(iF1)), 8, dens++, land.herd(0), "", "ambush");
        land.putSite(SiteKind.DEN, lx(t.x(iAmb)), (int) t.feet(iAmb), li(t.z(iAmb)), 9, "");
        land.putLeg("ambush", LegKind.AMBUSH, "A quiet bend", lx(t.x(iAmb)), (int) t.feet(iAmb), li(t.z(iAmb)), 7, dens++, land.ambushPack(0), "", "island", "breach");
        land.putSite(SiteKind.DEN, lx(au), ISLE, li(av), 10, "");
        land.denPlace(lx(au), ISLE, li(av), 8, 1, mir * (au + 11), av);
        land.putLeg("island", LegKind.FIGHT, "A den on an island", lx(au), ISLE, li(av), 8, dens++, land.herd(1), "", "breach");
        land.putLeg("breach", LegKind.MOVEMENT, "A broken bridge", lx(-8), DECK, riftV, 9, -1, none, "", "island2");
        land.putSite(SiteKind.DEN, lx(bu), ISLE, li(bv), 10, "");
        land.denPlace(lx(bu), ISLE, li(bv), 8, 2, mir * (bu - 11), bv);
        land.putLeg("island2", LegKind.FIGHT, "A den on the second island", lx(bu), ISLE, li(bv), 8, dens++, land.herd(2), "", "ambush2", "hidden");
        land.putLeg("hidden", LegKind.HIDDEN, "A place kept back", lx(ku), ISLE - 2, li(kv), 5, -1, none, "");
        land.putSite(SiteKind.DEN, lx(t.x(iAmb2)), (int) t.feet(iAmb2), li(t.z(iAmb2)), 9, "");
        land.putLeg("ambush2", LegKind.AMBUSH, "A quiet stretch", lx(t.x(iAmb2)), (int) t.feet(iAmb2), li(t.z(iAmb2)), 7, dens++, land.ambushPack(1), "", "warden");
        land.putSite(SiteKind.WARDEN, 0, DECK, wardenV, 11, "");
        land.putLeg("warden", LegKind.WARDEN, "The gate at the foot of the rock", 0, DECK, wardenV, 11, 0, land.wardenPack(0), "", "lord");
        land.putSite(SiteKind.LORD, 0, HEART, heartV, 22, land.ctx.bp().heart().lord());
        land.putLeg("lord", LegKind.LORD, "The floor at the head of the stair", 0, HEART, heartV, 22, -1, none, "");

        find(au - 3, av + 2);
        find(ku + 1, kv + 2);
        find(ku - 2, kv - 1);
        find(bu + 3, bv + 3);
        find(-7, wardenV - 4);
        find(5, mouthV + 3);
        land.must(new LandPieces.StoneRing(lx(ku), li(kv), 5.6, 6, mir * (ku - 14), kv + 3, seed + 98, land));
    }
}
