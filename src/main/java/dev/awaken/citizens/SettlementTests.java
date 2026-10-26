package dev.awaken.citizens;

import com.mojang.serialization.DynamicOps;
import dev.awaken.Awaken;
import dev.awaken.citizens.api.RosterBuilder;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.city.CityPlan;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Many settlements on one people engine (SPEC §28.4, prefix {@code citizens_}), one test per mechanism: a tiny hamlet
 * built in code beside Carillon's engine, both ticking, sharing one pool of bodies nearest first within the caps, its
 * people coming and going with a player, sleeping and waking, and both saved and read back; and Carillon's save from
 * before W0 (schemaVersion 1) loading into settlement 0 with nothing lost.
 */
final class SettlementTests {
    private SettlementTests() {}

    static void register() {
        AwakenTests.registerIsolated("citizens_many_settlements", 4000, SettlementTests::many);
        AwakenTests.register("citizens_save_v1_loads", 200, SettlementTests::saveV1);
    }

    /**
     * The smallest settlement: three houses on a lane, a well on the green, a smith at his anvil, six people with a day
     * (a smith, a washer and their child; two neighbours; an old man who sits on the green), a thief kept apart, and a
     * road out each way (an escape needs one). Offsets from the origin, all on the origin's level.
     */
    static RosterBuilder hamlet() {
        RosterBuilder b = new RosterBuilder();
        int green = b.district("the green");
        // the lane (west to east) and the road out both ways
        int s0 = b.junction(-12.5, 0, 0.5), s1 = b.junction(0.5, 0, 0.5), s2 = b.junction(12.5, 0, 0.5);
        b.street(s0, s1, 8).street(s1, s2, 8);
        int west = s0, east = s2;
        for (int k = 1; k <= 10; k++) {
            int w = b.junction(-12.5 - 20 * k, 0, 0.5), e = b.junction(12.5 + 20 * k, 0, 0.5);
            b.street(west, w).street(east, e);
            west = w;
            east = e;
        }
        // three houses north of the lane, the smithy south of it
        int lotA = b.lot(-14, -9, -8, -3, 6, -11, -3, "house"), lotB = b.lot(-2, -9, 4, -3, 6, 1, -3, "house"),
                lotC = b.lot(10, -9, 16, -3, 6, 13, -3, "house"), lotS = b.lot(10, 4, 16, 10, 7, 13, 4, "smithy");
        int dA = b.door(-10.5, 0, -2.5, lotA), dB = b.door(1.5, 0, -2.5, lotB), dC = b.door(13.5, 0, -2.5, lotC), dS = b.door(13.5, 0, 4.5, lotS);
        b.street(dA, s0).street(dB, s1).street(dC, s2).street(dS, s2);
        int homeA = b.place("door", dA, -10.5, 0, -2.5).ref(lotA).cap(4).id();
        int homeB = b.place("door", dB, 1.5, 0, -2.5).ref(lotB).cap(4).id();
        int homeC = b.place("door", dC, 13.5, 0, -2.5).ref(lotC).cap(4).id();
        int forge = b.place("door", dS, 13.5, 0, 4.5).ref(lotS).cap(2).id();
        int anvil = b.place("anvil", s2, 11.5, 0, 2.5).yaw(180).spot(11.5, 2.5).id();
        // the well: water in its shaft, the washer's trough beside it
        b.water(0, 5);
        int wash = b.place("wash", s1, 1.5, 0, 4.5).yaw(0).district(green).spot(1.5, 4.5).id();
        int bench = b.place("sit", s1, -1.5, 0, 2.5).yaw(0).cap(2).district(green).spot(-1.5, 2.5).spot(-2.5, 2.5).id();
        int play = b.place("play", s1, 3.5, 0, 1.5).district(green).spot(3.5, 1.5).id();
        int gate = b.place("stand", s0, -14.5, 0, 2.5).yaw(90).spot(-14.5, 2.5).id();
        b.smithy(lotS, forge, anvil, 11, 0, 3).washplace(0, 0, 6, wash);
        int errands = b.route("wander", bench, wash, gate, anvil);
        int smithy = b.group("smithy"), well = b.group("well");
        int ash = b.household("Ashby", homeA, lotA), fenn = b.household("Fenn", homeB, lotB), harrow = b.household("Harrow", homeC, lotC);
        b.person("Bren", "Ashby", "smith").household(ash).home(homeA).work(anvil).group(smithy)
                .segment(0, "home", -1).segment(1000, "stand", anvil).segment(10000, "stand", bench).segment(12500, "home", -1);
        b.person("Mara", "Ashby", "washer").household(ash).home(homeA).work(wash).group(well).build(1)
                .segment(0, "home", -1).segment(1500, "stand", wash).segment(7000, "wander", errands, -1, 240, 0).segment(12000, "home", -1);
        b.person("Tam", "Ashby", "child").household(ash).home(homeA).age(0).scale(0.7f)
                .segment(0, "home", -1).segment(2000, "stand", play).segment(9000, "home", -1);
        b.person("Elda", "Fenn", "resident").household(fenn).home(homeB).build(1)
                .segment(0, "home", -1).segment(2500, "wander", errands, -1, 300, 0).segment(11000, "home", -1);
        b.person("Corin", "Fenn", "resident").household(fenn).home(homeB)
                .segment(0, "home", -1).segment(4000, "stand", bench).segment(10000, "home", -1);
        b.person("Wyn", "Harrow", "resident").household(harrow).home(homeC).age(3).speed(0.8f)
                .segment(0, "home", -1).segment(3000, "stand", bench).segment(8000, "home", -1);
        return b.extras(1);
    }

    private static GameTestAssertException running(long el) {
        return new GameTestAssertException(Component.literal("running, tick " + el), (int) el);
    }

    /**
     * Carillon's engine (its plan here, as the events test borrows it) and the hamlet, both ticking from the server
     * tick: their costs measured alone and together; then the pool's caps cut to 12 (no more than 12 bodies, and the
     * nearest 12 whichever settlement they belong to); the player leaves (every body goes, the hamlet sleeps) and
     * another comes (it wakes, its people seated where the hour wants them); both settlements saved and read back.
     */
    private static void many(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        StreetNet g = StreetNet.of(CityPlan.get(), origin.getX(), origin.getZ(), origin.getY(), r.edgeLane).withWall(r, origin.getX(), origin.getZ(), origin.getY());
        Settlement city = Settlement.carillon(level, r, g, new CitizensData(), origin.getX(), origin.getZ(), origin.getY());
        city.work.edits = false; // the shared test world is not Carillon: change no blocks there
        city.events.noPlan();
        city.setClock(6000); // noon
        Settlement before = Citizens.swapCarillon(city);
        Crowd pool = city.crowd;
        int capPlayer = pool.capPlayer, capWorld = pool.capWorld;
        AwakenTests.Live[] player = {AwakenTests.survivalPlayer(h, "w0-a", BlockPos.ZERO.above(2))};
        Settlement[] hamlet = new Settlement[1];
        List<String> problems = new ArrayList<>();
        String[] cost = new String[2];
        long[] hamletLedger = new long[3]; // its ledger's nanoseconds and ticks measured; its jumps before it wakes
        boolean[] done = new boolean[1];
        long start = level.getGameTime();
        h.succeedWhen(() -> {
            long el = level.getGameTime() - start;
            if (!done[0]) {
                try {
                    step(h, level, origin, el, city, pool, hamlet, player, problems, cost, hamletLedger);
                } catch (RuntimeException e) {
                    problems.add("tick " + el + ": " + e);
                    el = 2000;
                }
                if (el < 1900) throw running(el);
                // back as it was, pass or fail
                done[0] = true;
                pool.capPlayer = capPlayer;
                pool.capWorld = capWorld;
                if (player[0] != null) player[0].leave();
                if (hamlet[0] != null) Settlements.unregister(hamlet[0].id);
                Citizens.swapCarillon(before);
                pool.despawnAll(city);
                Awaken.LOG.info("[citizens] W0 costs | Carillon alone: {} | Carillon + hamlet: {} | the hamlet's ledger alone: avg {} ms",
                        cost[0], cost[1], String.format(java.util.Locale.ROOT, "%.4f", hamletLedger[1] == 0 ? 0 : hamletLedger[0] / 1e6 / hamletLedger[1]));
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        });
    }

    private static void step(GameTestHelper h, ServerLevel level, BlockPos origin, long el, Settlement city, Crowd pool, Settlement[] hamlet,
                             AwakenTests.Live[] player, List<String> problems, String[] cost, long[] hamletLedger) {
        if (el == 300) pool.stats.startWindow("carillon");
        if (el == 900) {
            cost[0] = pool.stats.endWindow();
            var spec = new SettlementSpec("test:hamlet", "Little Wick", origin, 48, hamlet());
            hamlet[0] = Settlements.register(level, spec).map(s -> (Settlement) s).orElse(null);
            if (hamlet[0] == null) {
                problems.add("the hamlet didn't register");
                return;
            }
            hamlet[0].work.edits = false;
            hamlet[0].setClock(6000);
            Settlement again = Settlements.register(level, spec).map(s -> (Settlement) s).orElse(null);
            if (again != hamlet[0]) problems.add("registering the same key twice made a second settlement");
            if (hamlet[0].id == Settlements.CARILLON) problems.add("the hamlet took Carillon's id");
            if (Settlements.at(level, origin).map(s -> s.id()).orElse(-1) != hamlet[0].id) problems.add("Settlements.at gives the hamlet at its origin");
            if (Settlements.byId(Settlements.CARILLON).orElse(null) != city) problems.add("Carillon is settlement 0");
            if (hamlet[0].stations("anvil").size() != 1 || hamlet[0].stations("house").size() != 3) problems.add("the hamlet's stations: "
                    + hamlet[0].stations("anvil") + " anvils, " + hamlet[0].stations("house") + " houses");
            if (!hamlet[0].district(origin.offset(1, 0, 3)).equals("the green") || !hamlet[0].district(origin.offset(30, 0, 30)).equals("Little Wick"))
                problems.add("the hamlet's names: " + hamlet[0].district(origin.offset(1, 0, 3)) + " / " + hamlet[0].district(origin.offset(30, 0, 30)));
            // events by tag: a house with a door burns, a thief finds a household (both run in the ledger only)
            long now = level.getGameTime();
            if (hamlet[0].events.start("fire", null, now) == null) problems.add("no fire in the hamlet (a house with a door)");
            hamlet[0].setClock(16000);
            if (hamlet[0].events.start("burglary", player[0].player(), now) == null) problems.add("no burglary in the hamlet");
            hamlet[0].setClock(6000);
            if (hamlet[0].events.start("pickpocket", null, now) != null) problems.add("a pickpocket with no stall to work");
        }
        if (hamlet[0] == null) return;
        Settlement hm = hamlet[0];
        if (el > 1100 && el <= 1700) {
            hamletLedger[0] += hm.ledgerNs;
            hamletLedger[1]++;
        }
        if (el == 1100) pool.stats.startWindow("carillon+hamlet");
        if (el == 1700) {
            cost[1] = pool.stats.endWindow();
            int a = pool.bodiesOf(city), b = pool.bodiesOf(hm);
            if (a == 0 || b == 0) problems.add("one pool for both: Carillon " + a + " bodies, the hamlet " + b);
            if (pool.bodies.size() > pool.capPlayer) problems.add(pool.bodies.size() + " bodies round one player (cap " + pool.capPlayer + ")");
            if (Settlements.bodies(hm.id) != b) problems.add("Settlements.bodies says " + Settlements.bodies(hm.id) + ", the pool " + b);
            for (CitizenBody body : pool.bodies) {
                if (body.home == null || body.home != city && body.home != hm) problems.add("a body of no running settlement");
                else if (body.home == hm && body.settlement() != hm.id) problems.add("a hamlet body synced as settlement " + body.settlement());
            }
            // a smaller pool: no more than it, and the nearest of everyone
            pool.capPlayer = 12;
            pool.capWorld = 12;
        }
        if (el == 1760) {
            if (pool.bodies.size() > 12) problems.add(pool.bodies.size() + " bodies with the caps at 12");
            ServerPlayer p = player[0].player();
            double in = 0, out = Double.MAX_VALUE;
            double[] at = new double[3];
            for (CitizenBody body : pool.bodies) in = Math.max(in, dist(p, body.getX(), body.getY(), body.getZ()));
            for (Settlement s : List.of(city, hm)) {
                for (int i = 0; i < s.ledger.n; i++) {
                    if (s.ledger.body[i] != null || !s.ledger.position(i, at)) continue;
                    double d = dist(p, at[0], at[1], at[2]);
                    if (d < Crowd.EMBODY * Crowd.EMBODY) out = Math.min(out, d);
                }
            }
            // people walk between passes: a little slack
            if (pool.bodies.size() == 12 && Math.sqrt(in) > Math.sqrt(out) + 4)
                problems.add(String.format(java.util.Locale.ROOT, "not the nearest: a body %.1f away while someone %.1f away has none", Math.sqrt(in), Math.sqrt(out)));
            pool.capPlayer = 40;
            pool.capWorld = 96;
            player[0].leave();
            player[0] = null;
        }
        if (el == 1800) {
            if (!pool.bodies.isEmpty()) problems.add(pool.bodies.size() + " bodies with nobody about");
            if (hm.awake) problems.add("the hamlet is still awake with nobody near");
            hamletLedger[2] = hm.ledger.jumps;
            player[0] = AwakenTests.survivalPlayer(h, "w0-b", BlockPos.ZERO.above(2));
        }
        if (el == 1840) {
            if (!hm.awake) problems.add("the hamlet didn't wake for a player");
            if (hm.ledger.jumps <= hamletLedger[2]) problems.add("waking didn't seat the hamlet's people");
            saved(city, hm, problems);
        }
    }

    private static double dist(ServerPlayer p, double x, double y, double z) {
        double dx = p.getX() - x, dy = (p.getY() - y) * 0.5, dz = p.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Both settlements through the world's file and back: the same records, the same ids, and neither re-seated. */
    private static void saved(Settlement city, Settlement hm, List<String> problems) {
        CitizensSave save = new CitizensSave();
        save.entries.add(new CitizensSave.Entry(city.key, city.id, city.data));
        save.entries.add(new CitizensSave.Entry(hm.key, hm.id, hm.data));
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        Tag tag = CitizensSave.CODEC.encodeStart(ops, save).getOrThrow();
        CitizensSave back = CitizensSave.CODEC.parse(ops, tag).getOrThrow();
        Tag again = CitizensSave.CODEC.encodeStart(ops, back).getOrThrow();
        if (!tag.equals(again)) problems.add("the two settlements don't read back identically");
        if (!(tag instanceof CompoundTag c) || c.getIntOr("schemaVersion", 0) != CitizensSave.SCHEMA) problems.add("the file isn't schemaVersion 2");
        CitizensSave.Entry e = back.find(hm.key);
        if (e == null || e.id != hm.id) problems.add("the hamlet's entry is lost or renumbered");
        else {
            CitizensData d = e.data;
            if (!Arrays.equals(d.at, hm.data.at) || !Arrays.equals(d.state, hm.data.state) || !Arrays.equals(d.seg, hm.data.seg) || d.safety.length != hm.site.districts())
                problems.add("the hamlet's records differ after the round trip");
            Ledger l = new Ledger(hm.roster, hm.net, d, hm.site.cx(), hm.site.cz(), hm.site.baseY(), 0, 6000, hm.site.districts());
            if (l.fresh) problems.add("the hamlet's saved records were re-seated on load");
        }
        CitizensSave.Entry ce = back.find(Settlements.CARILLON_KEY);
        if (ce == null || ce.id != Settlements.CARILLON || !Arrays.equals(ce.data.at, city.data.at)) problems.add("Carillon's entry is lost");
    }

    /**
     * Carillon's save from before W0 (its CitizensData alone, schemaVersion 1) reads into settlement 0 with every field
     * as it was, is written back as version 2 that reads the same again, and its ledger runs on (never re-seated).
     */
    private static void saveV1(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        StreetNet g = StreetNet.of(CityPlan.get(), 0, 0, 70, r.edgeLane).withWall(r, 0, 0, 70);
        for (int p = 0; p < r.places; p++) g.columnNow(r.pNode[p]);
        CitizensData d = new CitizensData();
        Ledger l = new Ledger(r, g, d, 0, 0, 70, 0, 5000);
        for (long t = 1; t <= 2000; t++) {
            g.newTick();
            l.tick(t, 5000 + t);
        }
        d.lamps[0] = CitizensData.LAMP_BROKEN;
        d.safety[CityPlan.WORKS] = 51;
        d.events.add(new CitizensData.Event(7, 1, 2, 100, 900, new int[]{1, 2, 3}));
        d.memory(java.util.UUID.nameUUIDFromBytes(new byte[]{2}), "Old Save", true).know[5] = 44;
        l.store();
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        Tag v1 = CitizensData.CODEC.encodeStart(ops, d).getOrThrow(); // the whole file before W0
        CitizensSave loaded = CitizensSave.CODEC.parse(ops, v1).getOrThrow();
        h.assertTrue(loaded.readFrom == 1 && loaded.entries.size() == 1, "a v1 file reads as one settlement (read from " + loaded.readFrom + ")");
        CitizensSave.Entry e = loaded.entries.getFirst();
        h.assertTrue(e.key.equals(Settlements.CARILLON_KEY) && e.id == Settlements.CARILLON, "it is Carillon, settlement 0 (" + e.key + " #" + e.id + ")");
        h.assertTrue(v1.equals(CitizensData.CODEC.encodeStart(ops, e.data).getOrThrow()), "nothing in Carillon's records is lost or changed");
        h.assertTrue(e.data.owner == loaded && loaded.isDirty(), "it is written back as version 2 at the next save");
        Tag v2 = CitizensSave.CODEC.encodeStart(ops, loaded).getOrThrow();
        CitizensSave again = CitizensSave.CODEC.parse(ops, v2).getOrThrow();
        h.assertTrue(again.readFrom == CitizensSave.SCHEMA && v2.equals(CitizensSave.CODEC.encodeStart(ops, again).getOrThrow()),
                "version 2 reads back the same");
        h.assertTrue(v1.equals(CitizensData.CODEC.encodeStart(ops, again.entries.getFirst().data).getOrThrow()), "and still holds the v1 records");
        Ledger back = new Ledger(r, g, e.data, 0, 0, 70, 2000, 7000);
        h.assertTrue(!back.fresh, "the loaded ledger runs on, never re-seated");
        h.succeed();
    }
}
