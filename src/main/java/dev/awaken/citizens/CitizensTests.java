package dev.awaken.citizens;

import com.mojang.serialization.DynamicOps;
import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Carillon's people (prefix {@code citizens_}), only where breakage would be silent (brief M3 §7), one test per
 * mechanism: a simulated play-day in which every citizen reaches every schedule target; every station reachable; every
 * event resolves with no thief stuck; the ledger's round trip through a save.
 */
final class CitizensTests {
    private CitizensTests() {}

    static void register() {
        AwakenTests.register("citizens_day", 200, CitizensTests::day);
        AwakenTests.register("citizens_stations", 200, CitizensTests::stations);
        AwakenTests.register("citizens_ledger_roundtrip", 200, CitizensTests::roundTrip);
        AwakenTests.registerIsolated("citizens_events", 16000, CitizensTests::events);
    }

    private static StreetNet net(Roster r) {
        return StreetNet.of(CityPlan.get(), 0, 0, 70, r.edgeLane);
    }

    /** Two simulated play-days on the ledger alone: every segment of every citizen's day reaches its first target. */
    private static void day(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        h.assertTrue(r.planSha256.equals(CityPlan.get().sha256), "the roster was generated from this plan");
        StreetNet g = net(r);
        CitizensData d = new CitizensData();
        Ledger l = new Ledger(r, g, d, 0, 0, 70, 0, 0);
        boolean[][] reached = new boolean[r.citizens][];
        for (int i = 0; i < r.citizens; i++) reached[i] = new boolean[r.segCount[i]];
        l.work = new Ledger.Work() {
            @Override
            public void arrived(int i, int place, long now) {
                if (i < r.citizens && d.seg[i] >= 0) reached[i][d.seg[i]] = true;
            }
        };
        long t0 = System.nanoTime();
        for (long t = 1; t <= 2L * Ledger.DAY; t++) {
            g.newTick();
            l.tick(t, t);
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        List<String> missed = new ArrayList<>();
        int segs = 0;
        for (int i = 0; i < r.citizens; i++) {
            for (int k = 0; k < r.segCount[i]; k++) {
                segs++;
                if (!reached[i][k]) missed.add("#" + i + " " + Roster.JOBS[r.job[i]] + " seg " + k + " (" + Roster.ACTS[r.segAct[r.segBase[i] + k]]
                        + " at " + r.segStart[r.segBase[i] + k] + ")");
            }
        }
        Awaken.LOG.info("[citizens] day test: {} segments, {} missed, arrivals {}, late {}, early {}, noRoute {}, overflow {}, {} columns, {} ms",
                segs, missed.size(), l.arrivals, l.late, l.early, l.noRoute, l.overflow, g.columns(), String.format("%.0f", ms));
        h.assertTrue(missed.isEmpty(), missed.size() + " schedule targets never reached: " + missed.subList(0, Math.min(8, missed.size())));
        h.assertTrue(l.noRoute == 0, "every walk had a route (" + l.noRoute + " without)");
        h.succeed();
    }

    /** Every place is on the main street graph, reachable from every home; every spot is open ground near its node. */
    private static void stations(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        StreetNet g = net(r);
        CityPlan plan = CityPlan.get();
        // one home's column reaches everything the city uses, and every home
        StreetNet.Column hub = g.columnNow(r.pNode[r.home[0]]);
        List<String> bad = new ArrayList<>();
        for (int p = 0; p < r.places; p++) {
            int node = r.pNode[p];
            if (node < 0 || node >= g.n || hub.dm()[node] < 0) {
                bad.add("place " + p + " (" + Roster.PLACE_KINDS[r.pKind[p]] + ") node " + node + " unreachable");
                continue;
            }
            for (int s = r.spotStart[p]; s < r.spotStart[p + 1]; s++) {
                double dx = r.spotX[s] - (g.x[node]), dz = r.spotZ[s] - (g.z[node]);
                if (Math.hypot(dx, dz) > 14) bad.add("place " + p + " spot " + s + " is " + (int) Math.hypot(dx, dz) + " blocks from its node");
                byte k = plan.kind((int) Math.floor(r.spotX[s]), (int) Math.floor(r.spotZ[s]));
                if (r.pKind[p] != Roster.P_DOOR && !CityPlan.walkable(k)) bad.add("place " + p + " (" + Roster.PLACE_KINDS[r.pKind[p]] + ") spot " + s + " on " + k);
            }
        }
        for (int route = 0; route < r.routePlaces.length; route++) {
            for (int p : r.routePlaces[route]) if (p < 0 || p >= r.places) bad.add("route " + route + " names place " + p);
        }
        for (int i = 0; i < r.citizens; i++) {
            if (hub.dm()[r.pNode[r.home[i]]] < 0) bad.add("citizen " + i + "'s home is cut off");
        }
        Awaken.LOG.info("[citizens] stations test: {} places, {} spots, {} problems", r.places, r.spotX.length, bad.size());
        h.assertTrue(bad.isEmpty(), bad.size() + " problems: " + bad.subList(0, Math.min(8, bad.size())));
        h.succeed();
    }

    /** The ledger saved and read back is the same ledger, and both run on identically. */
    private static void roundTrip(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        StreetNet g = net(r);
        CitizensData d = new CitizensData();
        Ledger l = new Ledger(r, g, d, 0, 0, 70, 0, 5000);
        for (long t = 1; t <= 3000; t++) {
            g.newTick();
            l.tick(t, 5000 + t);
        }
        // some city state and a memory, so every part of the record is exercised
        d.lamps[0] = CitizensData.LAMP_BROKEN;
        d.repairs.add(new CitizensData.Repair(123456789L, net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(), 4000));
        d.events.add(new CitizensData.Event(7, 1, 2, 100, 900, new int[]{1, 2, 3}));
        CitizensData.Memory m = d.memory(java.util.UUID.nameUUIDFromBytes(new byte[]{1}), "Tester", true);
        m.know[3] = 40;
        m.deeds.add(new CitizensData.Deed("awaken:caught_thief", 2, 4, 1));
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        Tag saved = CitizensData.CODEC.encodeStart(ops, d).getOrThrow();
        CitizensData back = CitizensData.CODEC.parse(ops, saved).getOrThrow();
        Tag again = CitizensData.CODEC.encodeStart(ops, back).getOrThrow();
        h.assertTrue(saved.equals(again), "the saved ledger reads back identically");
        // both run on the same
        StreetNet g2 = net(r);
        Ledger l2 = new Ledger(r, g2, back, 0, 0, 70, 3000, 8000);
        h.assertTrue(!l2.fresh, "a saved ledger for this roster is reused, not re-seated");
        for (long t = 3001; t <= 6000; t++) {
            g.newTick();
            g2.newTick();
            l.tick(t, 5000 + t);
            l2.tick(t, 5000 + t);
        }
        h.assertTrue(Arrays.equals(d.at, back.at) && Arrays.equals(d.next, back.next) && Arrays.equals(d.prog, back.prog)
                && Arrays.equals(d.state, back.state) && Arrays.equals(d.seg, back.seg) && Arrays.equals(d.spot, back.spot),
                "the read-back ledger runs on exactly like the original");
        h.succeed();
    }

    /** Every kind of event, started at once in an empty world with the city's graph placed here, ends by itself; no thief is left standing. */
    private static void events(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        ServerLevel level = h.getLevel();
        var origin = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        StreetNet g = StreetNet.of(CityPlan.get(), origin.getX(), origin.getZ(), origin.getY(), r.edgeLane);
        Citizens before = Citizens.active;
        Citizens c = new Citizens(level, r, g, new CitizensData(), origin.getX(), origin.getZ(), origin.getY());
        c.crowd.on = false;
        c.setClock(16000); // night: a burglary's hour
        c.events.noPlan();
        Citizens.active = c;
        long now = level.getGameTime();
        List<String> started = new ArrayList<>();
        for (String kind : List.of("burglary", "pickpocket", "fire", "cart")) {
            String s = c.events.start(kind, null, now);
            if (s != null) started.add(kind);
            Awaken.LOG.info("[citizens] events test: {} -> {}", kind, s);
        }
        h.assertTrue(started.contains("burglary") && started.contains("pickpocket"), "the thieves' events start: " + started);
        level.getServer().tickRateManager().requestGameToSprint(15000);
        h.succeedWhen(() -> {
            long elapsed = level.getGameTime() - now;
            if (!c.data.events.isEmpty()) {
                if (elapsed > 14500 && Citizens.active == c) {
                    Citizens.active = before;
                    level.getServer().tickRateManager().stopSprinting();
                }
                throw new net.minecraft.gametest.framework.GameTestAssertException(net.minecraft.network.chat.Component.literal(
                        c.data.events.size() + " events still running after " + elapsed + " ticks"), (int) elapsed);
            }
            for (int i = r.citizens; i < r.records; i++) {
                h.assertTrue(c.data.role[i] == 0 && c.data.state[i] == CitizensData.ABSENT, "thief " + i + " left the city (role " + c.data.role[i] + ")");
            }
            for (int i = 0; i < r.citizens; i++) h.assertTrue(c.data.role[i] == 0, "citizen " + i + " is back to their day");
            Awaken.LOG.info("[citizens] events test: {} started, {} resolved in {} ticks, noRoute {}", c.events.started, c.events.resolved,
                    elapsed, c.ledger.noRoute);
            Citizens.active = before;
            level.getServer().tickRateManager().stopSprinting();
        });
    }
}
