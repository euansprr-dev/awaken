package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.city.SettlementPaint;
import dev.awaken.citizens.api.RosterBuilder;
import dev.awaken.citizens.api.RosterSource;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.TestLand;
import dev.awaken.village.VillageRoster;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where people stand and where they appear (SPEC §28.11, Euan 2026-10-28: "the celebrating is so bunched up it looks
 * glitchy, people are spawning in front of me like ghosts, and the forge man is dying in a wall"): a full place's crowd
 * spreads onto rings a step and a half apart, never two on one spot, never in water or a wall; every spot a village or a
 * town plans has a floor and two clear rows; and nobody gets a body within 24 of a player or in their view.
 */
final class GatheringTests {
    private GatheringTests() {}

    static void register() {
        AwakenTests.register("citizens_gathering_spread", 200, GatheringTests::spread);
        AwakenTests.register("village_work_spots_free", 600, GatheringTests::spots);
        AwakenTests.registerIsolated("citizens_arrivals_unseen", 600, GatheringTests::arrivals);
    }

    /**
     * Forty people for a place with four spots: the four take them (but the one a wall stands on), the rest the rings
     * round it, nearest ring first, at least 1.2 apart, the nearest neighbour never more than two away, none on the strip
     * of water east of it, and all of them within the second ring.
     */
    private static void spread(GameTestHelper h) {
        RosterBuilder b = new RosterBuilder();
        int s0 = b.junction(0.5, 0, 0.5), s1 = b.junction(-20.5, 0, 0.5);
        b.street(s0, s1);
        int lot = b.lot(-34, -4, -28, 4, 6, -31, -4, "house");
        int dn = b.door(-30.5, 0, -4.5, lot);
        b.street(dn, s1);
        int home = b.place("door", dn, -30.5, 0, -4.5).ref(lot).cap(40).id();
        RosterBuilder.Place sq = b.place("stand", s0, 0.5, 0, 0.5).cap(4);
        sq.spot(3.5, 0.5).spot(-2.5, 0.5).spot(0.5, 3.5).spot(0.5, -2.5);
        int square = sq.id();
        int hh = b.household("Gather", home, lot);
        for (int k = 0; k < 40; k++) b.person("P" + k, "Gather", "resident").household(hh).home(home).segment(0, "stand", square);
        RosterSource.Built built = b.build();
        Roster r = built.roster();
        h.assertTrue(r != null && built.graph() != null, "the test square's roster builds");
        StreetNet g = StreetNet.of(built.graph(), 0, 64, 0);
        CitizensData d = new CitizensData();
        Ledger l = new Ledger(r, g, d, 0, 0, 64, 0, 6000, 1);
        // a wall on the east spot, a strip of water past it
        l.ground = new Ledger.Ground() {
            @Override
            public boolean stands(double x, double y, double z) {
                return !(Math.floor(x) == 3 && Math.floor(z) == 0);
            }

            @Override
            public double open(double x, double y, double z) {
                return x >= 5 && x < 7 ? Double.NaN : y;
            }
        };
        l.reseat(10, 6000);
        List<double[]> at = new ArrayList<>();
        for (int i = 0; i < r.citizens; i++) {
            h.assertTrue(d.state[i] == CitizensData.STAND && d.spot[i] != -1, "person " + i + " stands on a spot or a ring slot (spot " + d.spot[i] + ")");
            double[] p = new double[3];
            h.assertTrue(l.position(i, p), "person " + i + " has a place");
            h.assertFalse(Math.floor(p[0]) == 3 && Math.floor(p[2]) == 0, "nobody stands in the wall on the east spot");
            h.assertFalse(p[0] >= 5 && p[0] < 7, "nobody stands in the water: " + fmt(p));
            h.assertTrue(Math.hypot(p[0] - 0.5, p[2] - 0.5) <= 3 + 2 * Ledger.RING_GAP + 0.01, "within the second ring: " + fmt(p));
            at.add(p);
        }
        for (int i = 0; i < at.size(); i++) {
            double near = Double.MAX_VALUE;
            for (int j = 0; j < at.size(); j++) {
                if (i == j) continue;
                double dd = Math.hypot(at.get(i)[0] - at.get(j)[0], at.get(i)[2] - at.get(j)[2]);
                h.assertTrue(dd >= 1.2, String.format(Locale.ROOT, "people %d and %d stand %.2f apart (at least 1.2)", i, j, dd));
                near = Math.min(near, dd);
            }
            h.assertTrue(near <= 2.0, String.format(Locale.ROOT, "person %d has a neighbour within 2 (%.2f): a crowd, not a scatter", i, near));
        }
        // a slot let go is free again, and a saved ledger rebuilds who holds which
        int ringers = 0;
        for (int i = 0; i < r.citizens; i++) if (d.spot[i] <= -2) ringers++;
        h.assertTrue(ringers == 37, "thirty-seven on the rings: " + ringers);
        Ledger again = new Ledger(r, g, d, 0, 0, 64, 10, 6000, 1);
        h.assertTrue(again.ringMask[square] == l.ringMask[square], "the ring slots held come back on load");
        Awaken.LOG.info("[citizens] gathering: 40 people round a place of 4 spots, {} on its rings", ringers);
        h.succeed();
    }

    /**
     * Every spot a village and a town plan for their people (the smith's at the anvil, the washer's, the children's game,
     * the green, the fields, the stalls): its cell clear for the feet and the head over a floor, as the painter paints
     * it (benches aside: they're sat on).
     */
    private static void spots(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        VillagePlan village = TestLand.at(31L, 0, 64, 0).plan("test:spots_village", 4242L, true);
        h.assertTrue(village != null, "a village fits the test valley");
        VillagePlan town = VillagePlanner.plan(new VillagePlanner.Request("test:spots_town", 4711L, 0, 0, VillagePlan.Kind.TOWN, false, -124, -124, 139, 139,
                (x, z) -> false), new TestLand(7));
        h.assertTrue(town != null, "a town fits the test valley: " + VillagePlanner.WHY.get());
        int checked = 0;
        for (VillagePlan p : List.of(village, town)) checked += check(p, bad);
        h.assertTrue(bad.isEmpty(), bad.size() + " of " + checked + " spots have no room to stand: " + String.join("; ", bad.subList(0, Math.min(12, bad.size()))));
        h.assertTrue(village.forge != null || town.forge != null, "a forge was checked");
        Awaken.LOG.info("[citizens] work spots: {} spots of a village and a town, all free", checked);
        h.succeed();
    }

    private static int check(VillagePlan plan, List<String> bad) {
        Roster r = VillageRoster.of(plan, 42L).build().roster();
        if (r == null) {
            bad.add(plan.name + ": no roster");
            return 0;
        }
        BlockGetter ground = SettlementPaint.ground(plan, 42L);
        int n = 0;
        for (int p = 0; p < r.places; p++) {
            if (r.pKind[p] == Roster.P_SIT) continue;
            int s0 = r.spotStart[p], cnt = Math.min(64, r.spots(p));
            for (int s = s0; s < s0 + cnt; s++) {
                int x = (int) Math.floor(r.spotX[s]), z = (int) Math.floor(r.spotZ[s]), y = r.pRy[p] + 1;
                n++;
                if (!Footing.standable(ground, x, y, z))
                    bad.add(String.format(Locale.ROOT, "%s %s %s at %d,%d,%d (feet %s, head %s, floor %s)", plan.name, Roster.PLACE_KINDS[r.pKind[p]],
                            r.pRefId[p], x, y, z, ground.getBlockState(new BlockPos(x, y, z)), ground.getBlockState(new BlockPos(x, y + 1, z)),
                            ground.getBlockState(new BlockPos(x, y - 1, z))));
            }
        }
        return n;
    }

    // ---- arrivals --------------------------------------------------------------------------------------------------

    private static GameTestAssertException running(long el) {
        return new GameTestAssertException(Component.literal("running, tick " + el), (int) el);
    }

    /**
     * The hamlet at noon with a player in its lane: whom the sight rule hides (ten behind, thirty ahead in the open) and
     * whom it lets come (thirty behind, fifty ahead, thirty ahead behind a wall); no body of the hamlet appears within 24
     * of the player or in their view but out of a door; then, the player off down the road looking away, its people do
     * come, where nobody sees them.
     */
    private static void arrivals(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        Crowd pool = Citizens.crowd(level);
        int grace = pool.grace;
        pool.grace = 0;
        AwakenTests.Live[] player = {AwakenTests.survivalPlayer(h, "w8-sight", BlockPos.ZERO.above(1))};
        ServerPlayer p = player[0].player();
        // looking east, down the lane (a living thing looks where its head turns)
        p.setYRot(-90f);
        p.setYHeadRot(-90f);
        p.setXRot(0f);
        Citizens.onlyPlayers = List.of(p);
        List<String> problems = new ArrayList<>();
        // the rule on its own
        Vec3 eye = p.getEyePosition();
        double feet = p.getY();
        List<ServerPlayer> ps = List.of(p);
        if (Footing.unseen(level, ps, eye.x - 10, feet, eye.z)) problems.add("ten behind is too near to appear");
        if (Footing.unseen(level, ps, eye.x + 30, feet, eye.z)) problems.add("thirty ahead in the open is in plain view");
        if (!Footing.unseen(level, ps, eye.x - 30, feet, eye.z)) problems.add("thirty behind is out of sight");
        if (!Footing.unseen(level, ps, eye.x + 50, feet, eye.z)) problems.add("fifty ahead is past where an arrival can be made out");
        BlockPos wall = BlockPos.containing(eye.x + 26, feet, eye.z);
        for (int dy = -1; dy <= 4; dy++) for (int dz = -3; dz <= 3; dz++) level.setBlockAndUpdate(wall.offset(0, dy, dz), Blocks.STONE.defaultBlockState());
        if (!Footing.unseen(level, ps, eye.x + 30, feet, eye.z)) problems.add("thirty ahead behind a wall is out of sight");
        for (int dy = -1; dy <= 4; dy++) for (int dz = -3; dz <= 3; dz++) level.setBlockAndUpdate(wall.offset(0, dy, dz), Blocks.AIR.defaultBlockState());
        var spec = new SettlementSpec("test:arrivals", "Little Wick", origin, 48, SettlementTests.hamlet());
        Settlement hm = Settlements.register(level, spec).map(s -> (Settlement) s).orElse(null);
        h.assertTrue(hm != null, "the hamlet registers");
        hm.work.edits = false;
        hm.setClock(6000);
        Set<CitizenBody> seen = new HashSet<>();
        int[] came = new int[2]; // near the player, then far off
        boolean[] done = new boolean[1];
        long start = level.getGameTime();
        h.succeedWhen(() -> {
            long el = level.getGameTime() - start;
            if (!done[0]) {
                try {
                    ServerPlayer pl = player[0].player();
                    for (CitizenBody b : pool.bodies) {
                        if (b.home != hm || !seen.add(b)) continue;
                        came[el < 200 ? 0 : 1]++;
                        if (door(hm, b)) continue;
                        double dist = pl.getEyePosition().distanceTo(b.position().add(0, 1, 0));
                        if (dist < Footing.NEAR - 1.5)
                            problems.add(String.format(Locale.ROOT, "tick %d: someone appeared %.1f from the player", el, dist));
                        else if (dist >= Footing.NEAR && dist < Footing.FAR - 2 && !Footing.unseen(level, List.of(pl), b.getX(), b.getY(), b.getZ()))
                            problems.add(String.format(Locale.ROOT, "tick %d: someone appeared %.1f away in the player's view", el, dist));
                    }
                    if (el == 200) {
                        // down the road, looking away from the hamlet
                        pl.teleportTo(origin.getX() - 44.5, pl.getY(), origin.getZ() + 0.5);
                        pl.setYRot(90f);
                        pl.setYHeadRot(90f);
                    }
                } catch (RuntimeException e) {
                    problems.add("tick " + el + ": " + e);
                    el = 500;
                }
                if (el < 480) throw running(el);
                done[0] = true;
                if (came[1] == 0) problems.add("nobody came while the player looked away down the road");
                Citizens.onlyPlayers = null;
                pool.grace = grace;
                player[0].leave();
                Settlements.unregister(hm.id);
                Awaken.LOG.info("[citizens] arrivals: {} bodies by the player (out of doors), {} down the road; {} held back", came[0], came[1], pool.held);
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        });
    }

    /** A body that came out of one of the hamlet's doors (a step and a half out of it). */
    private static boolean door(Settlement s, CitizenBody b) {
        StreetNet g = s.net;
        for (int n = 0; n < g.n; n++) if (g.kind[n] == StreetNet.DOOR && Math.hypot(b.getX() - g.x[n], b.getZ() - g.z[n]) < 2.6) return true;
        return false;
    }

    private static String fmt(double[] p) {
        return String.format(Locale.ROOT, "%.1f,%.1f", p[0], p[2]);
    }
}
