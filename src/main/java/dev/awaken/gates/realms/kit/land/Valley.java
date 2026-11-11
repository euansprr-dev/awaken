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
 * The Valley (SPEC §50.9, "Rolled lands"): a river valley shut in by cliffs, the great thing at its head. You arrive on a
 * terrace cut into the valley's end, twenty-two blocks over its floor, and see all of it: the river, the forest, a stepped
 * temple at the far end and the fall that comes down the head wall behind it. A stair goes down into the valley; the way
 * follows the river up its west bank over a spur of the hills, crosses the river's gorge on a broken bridge, goes on up
 * the east bank over another spur to the temple's forecourt, where a Warden stands, and up the temple's own stair to its
 * top, which is the Lord's. Side hollows are cut into the valley walls: one a den that makes a way round, one hidden.
 *
 * <p>Worked out with the valley running north from the way in (v falls as you go) and the temple's middle on u = 0; a
 * seed may turn east to west.
 */
final class Valley extends Landform {
    private static final int TIERS = 5, TIER_H = 6, TIER_RUN = 5;
    private static final double TOP = 24.5, BASE = TOP + TIERS * TIER_RUN, WATER = 62;

    /** Half the valley's length: the way in is near v = +lh, the head wall at -lh - 24. */
    private final double lh;
    private final SimplexNoise nWide, nFloor, nCrag, nTop, nFine;
    private final double ph1, ph2, zv;
    /** The two spurs the way climbs over: where along the valley, and which bank (-1 west, 1 east). */
    private final double[][] spurs;
    private final int bridgeV;
    private int zBase, zTop, zs, rb, deckFeet;
    private boolean laid, gorge;

    Valley(GeneratedLand land) {
        super(land, 0x7A11E7L);
        // a higher rank's valley is longer, but never so long that the temple is out of sight from the way in
        lh = 200 + 12 * rank;
        nWide = N.noise(seed + 811);
        nFloor = N.noise(seed + 812);
        nCrag = N.noise(seed + 813);
        nTop = N.noise(seed + 814);
        nFine = N.noise(seed + 815);
        ph1 = r.nextDouble() * 6.283;
        ph2 = r.nextDouble() * 6.283;
        zv = -lh + 75;
        bridgeV = (int) Math.round(lh - 145 + r.nextInt(16));
        spurs = new double[][]{{lh - 96 - r.nextInt(14), -1}, {bridgeV - 58 - r.nextInt(12), 1}};
    }

    // ---- the big shape --------------------------------------------------------------------------------------------------------

    /** The middle of the valley's floor at a point along it: it swings, and straightens toward the temple. */
    private double axis(double v) {
        return 24 * Math.sin(v / 95 + ph1) * N.smooth(-lh + 40, -lh + 130, v);
    }

    /** The river's middle: it wanders across the floor, and near the head swings west to pass the temple. */
    private double river(double v) {
        double head = N.smooth(-lh + 205, -lh + 142, v);
        return (axis(v) + 13 * Math.sin(v / 41 + ph2)) * (1 - head) - 60 * head;
    }

    private double floorAt(double v) {
        return 66 + 12 * N.smooth(lh, -lh + 150, v);
    }

    private double wide(double v) {
        return 48 + 9 * nWide.get(v / 70, 3.3) + 24 * N.smooth(-lh + 215, -lh + 150, v);
    }

    /** How far outside the valley's floor a point is: under 0 on the floor, over 0 on its walls. */
    private double out(double u, double v) {
        double du = Math.abs(u - axis(v)) - wide(v), dv = Math.max(v - lh, -lh - 24 - v);
        return du <= 0 && dv <= 0 ? Math.max(du, dv) : Math.hypot(Math.max(du, 0), Math.max(dv, 0));
    }

    /** How far outside the water's edge a point is (under 0 in it): the river, and the pool under the fall behind the temple. */
    private double water(double u, double v) {
        double d = v > -lh - 6 ? Math.abs(u - river(v)) - 5.5 : 99;
        double pu = Math.max(Math.abs(u + 21) - 45, 0), pv = Math.max(Math.abs(v + lh + 2) - 16, 0);
        return Math.min(d, Math.hypot(pu, pv) - 4);
    }

    /** A spur of the hills lying across one bank: full height at the wall, down to nothing at the river. */
    private double spur(double u, double v) {
        double h = 0;
        for (double[] s : spurs) {
            double along = Math.abs(v - s[0]);
            if (along < 15) h += 9 * N.smooth(8, 30, s[1] * (u - river(v))) * N.smooth(15, 4, along);
        }
        return h;
    }

    @Override
    double bs(double u, double v) {
        return floorAt(v) + 0.07 * Math.min(60, Math.abs(u - river(v))) + spur(u, v);
    }

    @Override
    double natural(double x, double z, double calm) {
        double u = mir * x, v = z;
        if (laid) {
            double far = Math.max(Math.abs(u), Math.abs(v - zv));
            if (far <= BASE) return zBase + TIER_H * (TIERS - Math.ceil(Math.max(0, far - TOP) / TIER_RUN));
        }
        double dw = water(u, v);
        if (dw <= 0) return WATER - 2.6;
        double e = out(u, v), y = bs(u, v);
        // the floor rolls, and rock stands up out of the forest here and there
        double floor = N.smooth(4, -12, e);
        if (floor > 0) y += (1.8 * N.fbm(nFloor, u, v, 1 / 21.0, 2) + 6 * N.smooth(0.46, 0.66, N.fbm(nCrag, u, v, 1 / 25.0, 3))) * calm * floor;
        if (e > 0) {
            // the walls: sheer where the fall comes over the head, cut through where the river leaves by the south end
            boolean chute = v < -lh - 24 && Math.abs(u) < 13, slot = v > lh && Math.abs(u - river(v)) < 9;
            if (!slot) y += chute ? 64 : 58 * N.smooth(0, 24, e) + 6 * N.smooth(24, 70, e) + 5 * N.fbm(nTop, u, v, 1 / 40.0, 2) * N.smooth(24, 50, e);
        }
        // the banks come down to the water; but the wall at the valley's head stands sheer over its pool, for the fall to come down
        return (v < -lh - 24 ? y : Math.min(y, WATER + 0.7 + dw * 1.6)) + 0.4 * nFine.get(u / 6.1, v / 6.1);
    }

    @Override
    double cut(double x, double z, double s) {
        if (!gorge) return s;
        double dv = (z - bridgeV) / 9.0;
        if (Math.abs(dv) >= 1) return s;
        double o = Math.abs(mir * x - rb) - 6.8 * Math.sqrt(1 - dv * dv);
        return o < 0 && s > WATER - 2.6 ? N.lerp(s, WATER - 2.6, N.smooth(0, -1.5, o)) : s;
    }

    @Override
    double columns(double x, double z) {
        double u = mir * x, v = z;
        if (built(x, z) || water(u, v) < 2) return 0;
        double e = out(u, v);
        if (e > 0) return N.smooth(1, 6, e);
        return N.smooth(0.46, 0.6, N.fbm(nCrag, u, v, 1 / 25.0, 3)) * N.smooth(4, -12, e);
    }

    @Override
    boolean built(double x, double z) {
        return laid && Math.max(Math.abs(x), Math.abs(z - zv)) <= BASE + 0.5;
    }

    @Override
    double edge(int x, int z) {
        // the wind that turns you back blows toward a point east of the middle (RealmLive), so it is only raised where
        // that is the way back into the valley; the east wall is left to its cliffs
        return x > 40 ? -1 : out(mir * x, z) - 30;
    }

    @Override
    int half() {
        return ((int) Math.ceil(lh + 24 + 96) + 15) / 16 * 16;
    }

    @Override
    float mouthYaw() {
        return 180;
    }

    // ---- the way ----------------------------------------------------------------------------------------------------------------

    /** Adds points along a bank from one v to another, a block apart, each at {@code side} of the river as {@code where} says. */
    private void bank(Trail t, double v0, double v1, java.util.function.DoubleUnaryOperator where) {
        double v = v0;
        while (v > v1) {
            double u = where.applyAsDouble(v), slope = where.applyAsDouble(v - 1) - u;
            add(t, u, v);
            v -= 1 / Math.sqrt(1 + slope * slope);
        }
    }

    private static int nearest(Trail t, double v, int from, int to) {
        int best = from;
        for (int i = from; i <= to; i++) if (Math.abs(t.z(i) - v) < Math.abs(t.z(best) - v)) best = i;
        return best;
    }

    @Override
    void lay() {
        List<String> none = List.of();
        // the way in: a terrace cut into the valley's south end, twenty-two over the floor, and a stair down from it
        int mouthU = (int) Math.round(river(lh - 24) - 22), mouthV = (int) Math.round(lh + 9), floor0 = (int) Math.round(floorAt(lh - 20)), mouthFeet = floor0 + 22;
        rb = (int) Math.round(river(bridgeV));
        zs = (int) Math.round(zv + TOP + TIERS * TIER_H + 0.5);

        // ---- the main way: down the west bank to the bridge, over it, up the east bank to the temple's forecourt
        Trail t = new Trail();
        bank(t, lh - 21, bridgeV + 0.5, v -> N.lerp(N.lerp(mouthU, river(v) - 22, N.smooth(lh - 24, lh - 60, v)), rb - 12, N.smooth(bridgeV + 16, bridgeV + 2, v)));
        int iLipW = t.last() + 1;
        for (int k = 12; k >= 7; k--) add(t, rb - k, bridgeV);
        int iBridge = t.last();
        for (int k = 7; k <= 12; k++) add(t, rb + k, bridgeV);
        int iLipE = t.last();
        double vF = zs + 13;
        bank(t, bridgeV - 1, vF, v -> N.lerp(N.lerp(rb + 12, river(v) + 22, N.smooth(bridgeV - 2, bridgeV - 16, v)), 0, N.smooth(vF + 60, vF + 6, v)));
        add(t, 0, vF);
        int iF = t.last();
        int iF1 = 40 + r.nextInt(8), iAmb = nearest(t, spurs[0][0], 0, iLipW - 12), iF3 = nearest(t, bridgeV - 30, iLipE, iF), iAmb2 = nearest(t, spurs[1][0], iLipE, iF - 20);
        t.level(iF1 - 3, iF1 + 3);
        t.level(iAmb - 3, iAmb + 3);
        t.level(iLipW - 2, iLipE + 2);
        t.level(iF3 - 3, iF3 + 3);
        t.level(iAmb2 - 3, iAmb2 + 3);
        t.level(iF - 13, iF);
        t.settle(floor0, 0.42);
        deckFeet = (int) Math.round(t.feet(iBridge));
        gorge = true;
        zBase = (int) Math.round(t.feet(iF));
        zTop = zBase + TIERS * TIER_H;
        laid = true;

        // ---- stations
        st(mouthU, mouthV, mouthFeet, 6.5, 5, FLOOR);
        for (int i = 1; i <= 22; i++) land.station(mir * (double) mouthU, lh + 2 - i, mouthFeet - i, 2, 3, STAIR, Direction.SOUTH);
        land.way(mir * (double) mouthU, lh + 2, mir * (double) mouthU, lh - 20, 3);
        for (int i = 0; i <= t.last(); i++) {
            st(t.x(i), t.z(i), t.feet(i), 2.6, 6, TRAIL);
            land.wayPoint(mir * t.x(i), t.z(i), t.feet(i));
            if (i > 0 && i % 4 == 0 && !(i - 4 <= iBridge && i > iBridge)) land.way(mir * t.x(i - 4), t.z(i - 4), mir * t.x(i), t.z(i), 3);
        }
        for (int i : new int[]{iF1, iAmb, iF3, iAmb2}) st(t.x(i), t.z(i), t.feet(i), 8, 5, PAD);
        st(0, vF, zBase, 12, 5, FLOOR);
        // the hollow in the west wall: a den off the way, with a track in from below it and out again above
        double vH = (t.z(iF1) + t.z(iAmb)) / 2, hu = axis(vH) - wide(vH) - 2;
        int iIn = nearest(t, vH + 16, 0, iAmb), iOut = nearest(t, vH - 16, 0, iAmb), hFeet = (int) Math.round((t.feet(iIn) + t.feet(iOut)) / 2) + 1;
        st(hu, vH, hFeet, 8.5, 5, PAD);
        spur(t.x(iIn), t.z(iIn), t.feet(iIn), hu + 7, vH + 3, hFeet, 1.6);
        spur(hu + 7, vH - 3, hFeet, t.x(iOut), t.z(iOut), t.feet(iOut), 1.6);
        // the hidden place: a pocket in the east wall, north of the bridge
        double vK = t.z(iF3) - 16, ku = axis(vK) + wide(vK) + 1;
        int iK = nearest(t, vK + 6, iLipE, iF), kFeet = (int) Math.round(t.feet(iK)) + 1;
        st(ku, vK, kFeet, 5.2, 4, PAD);
        spur(t.x(iK), t.z(iK), t.feet(iK), ku - 5, vK, kFeet, 1.4);
        // the temple's own stair, straight up its south face, and its top
        for (int i = 1; i <= TIERS * TIER_H; i++) land.station(0, zs - i, zBase + i, 2.5, 2, STAIR, Direction.NORTH);
        land.way(0, zs, 0, zs - TIERS * TIER_H, 3);
        st(0, zv, zTop, TOP, 1, FLOOR);
        land.freeze();

        // ---- what stands on it
        int mx = lx(mouthU);
        land.putSite(SiteKind.MOUTH, mx, mouthFeet, mouthV, 12, "");
        land.putSite(SiteKind.PORTAL, mx, mouthFeet, mouthV + 6, 2, "");
        land.must(new LandPieces.Arrival(mx, mouthFeet, mouthV, land));
        stone(lx(t.x(2) + 6), li(t.z(2)), "the foot of the stair");
        boolean[] deck = {true, true, false, false, false, true, true, true, false, false, true, true, true};
        if (mir < 0) for (int i = 0; i < deck.length / 2; i++) {
            boolean b = deck[i];
            deck[i] = deck[deck.length - 1 - i];
            deck[deck.length - 1 - i] = b;
        }
        land.must(new LandPieces.Bridge(lx(rb) - 6, deckFeet, bridgeV, deck, land));
        List<int[]> ledges = new ArrayList<>();
        for (int k : new int[]{-8, -5, 0, 5, 8}) ledges.add(new int[]{lx(rb + k), deckFeet, bridgeV});
        land.putCourse(new Course("gorge", lx(rb) - 12, (int) WATER - 4, bridgeV - 11, lx(rb) + 12, deckFeet + 9, bridgeV + 11, ledges));
        for (int s = -1; s <= 1; s += 2) land.maybe(new LandPieces.LampPost(lx(rb + 9 * s), bridgeV + 4, 3, land));
        int fz = li(vF);
        land.must(new LandPieces.Court(0, zBase, fz, 11.4, land));
        land.must(new LandPieces.Gate(0, fz + 10, 9, true, land));
        stone(lx(7), fz + 5, "the temple's foot");
        land.must(new LandPieces.Temple(0, li(zv), zBase, TIERS, TIER_H, TIER_RUN, TOP, seed + 95, land));
        land.must(new LandPieces.Falls(-6, 6, li(-lh - 24), land));
        land.maybe(new LandPieces.LampPost(lx(ku - 2), li(vK) + 4, 2, land));
        land.heartDoor(-4, zs - TIERS * TIER_H - 1, 4, zs - TIERS * TIER_H - 1, zTop, 6, 0, li(zv), TOP + 0.4);

        // ---- the run
        int dens = 0;
        land.putLeg("arrive", LegKind.ARRIVE, "The way in", mx, mouthFeet, mouthV, 6, -1, none, "", "fight1");
        den(t, iF1, 8, dens, 0);
        land.putLeg("fight1", LegKind.FIGHT, "A den by the river", lx(t.x(iF1)), (int) t.feet(iF1), li(t.z(iF1)), 8, dens++, land.herd(0), "", "hollow", "ambush");
        land.putSite(SiteKind.DEN, lx(hu), hFeet, li(vH), 10, "");
        land.denPlace(lx(hu), hFeet, li(vH), 8, 1, mir * (hu + 9), vH);
        land.putLeg("hollow", LegKind.FIGHT, "A den in a hollow of the hills", lx(hu), hFeet, li(vH), 8, dens++, land.herd(1), "", "gorge");
        land.putSite(SiteKind.DEN, lx(t.x(iAmb)), (int) t.feet(iAmb), li(t.z(iAmb)), 9, "");
        land.putLeg("ambush", LegKind.AMBUSH, "A quiet rise", lx(t.x(iAmb)), (int) t.feet(iAmb), li(t.z(iAmb)), 7, dens++, land.ambushPack(0), "", "gorge");
        land.putLeg("gorge", LegKind.MOVEMENT, "A broken bridge", lx(rb - 8), deckFeet, bridgeV, 9, -1, none, "", "fight2");
        den(t, iF3, 8, dens, 2);
        land.putLeg("fight2", LegKind.FIGHT, "A den on the east bank", lx(t.x(iF3)), (int) t.feet(iF3), li(t.z(iF3)), 8, dens++, land.herd(2), "", "ambush2", "hidden");
        land.putLeg("hidden", LegKind.HIDDEN, "A place kept back", lx(ku), kFeet, li(vK), 5, -1, none, "");
        land.putSite(SiteKind.DEN, lx(t.x(iAmb2)), (int) t.feet(iAmb2), li(t.z(iAmb2)), 9, "");
        land.putLeg("ambush2", LegKind.AMBUSH, "A quiet rise", lx(t.x(iAmb2)), (int) t.feet(iAmb2), li(t.z(iAmb2)), 7, dens++, land.ambushPack(1), "", "warden");
        land.putSite(SiteKind.WARDEN, 0, zBase, fz, 11, "");
        land.putLeg("warden", LegKind.WARDEN, "The temple's forecourt", 0, zBase, fz, 11, 0, land.wardenPack(0), "", "lord");
        land.putSite(SiteKind.LORD, 0, zTop, li(zv), 22, land.ctx.bp().heart().lord());
        land.putLeg("lord", LegKind.LORD, "The temple's top", 0, zTop, li(zv), 22, -1, none, "");

        find(hu - 3, vH + 2);
        find(ku + 2, vK + 1);
        find(ku + 1, vK - 3);
        find(-8, vF - 4);
        find(rb - 10, bridgeV - 3);
        find(t.x(4) - 7, t.z(4));
        land.must(new LandPieces.StoneRing(lx(ku), li(vK), 5.6, 7, mir * (ku - 6), vK, seed + 96, land));
    }
}
