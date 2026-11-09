package dev.awaken.gates.realms;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.realms.check.FloatCheck;
import dev.awaken.gates.realms.check.LookChecks;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.land.AmberReach;
import dev.awaken.progression.Rank;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Realms' checks (SPEC §50.6 to §50.8). */
public final class RealmTests {
    private RealmTests() {}

    private static final Blueprint AMBER = new Blueprint(1, "Amber Reach", "land", "hive", List.of("E", "D"),
            new Blueprint.LandSpec("awaken:honey_highlands", 62, Map.of()), new Blueprint.Sky(List.of("awaken:realm/amber_meadow"), "amber_reach", List.of()),
            new Blueprint.Life(List.of("g"), List.of("p"), List.of("a")), new Blueprint.Folk("wax_folk", 1), new Blueprint.Law("updrafts", Map.of()),
            new Blueprint.Sites(3, 0, 24), new Blueprint.Heart("hive_regent", "mother_comb"), List.of("late_harvest"), "");

    private static Land hour(long seed) {
        return new dev.awaken.gates.realms.land.HourCrypt(new Land.Ctx(new Blueprint(1, "t", "dungeon", "bell_crypt", List.of("E"), new Blueprint.LandSpec("awaken:hour_crypt", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:hour_crypt", Rank.E, seed, 1));
    }

    private static dev.awaken.gates.realms.land.KingsrootKeep keep(long seed) {
        return new dev.awaken.gates.realms.land.KingsrootKeep(new Land.Ctx(new Blueprint(1, "t", "stronghold", "overgrown_temple", List.of("E"), new Blueprint.LandSpec("awaken:kingsroot_keep", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:kingsroot_keep", Rank.E, seed, 1));
    }

    private static dev.awaken.gates.realms.land.VineSeat vine(long seed) {
        return new dev.awaken.gates.realms.land.VineSeat(new Land.Ctx(new Blueprint(1, "t", "stronghold", "overgrown_temple", List.of("E"), new Blueprint.LandSpec("awaken:vine_seat", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:vine_seat", Rank.E, seed, 1));
    }

    private static Land amber(long seed) {
        return new AmberReach(new Land.Ctx(AMBER, "awaken:amber_reach", Rank.E, seed, 1));
    }

    public static void register() {
        // does nothing unless tools/preview/preview.sh asked for it
        AwakenTests.register("realm_preview", 12000, h -> {
            dev.awaken.gates.realms.preview.Preview.runIfAsked();
            h.succeed();
        });
        AwakenTests.register("realm_blueprints_parse", 40, h -> {
            String why = AMBER.problem();
            if (!why.isEmpty()) h.fail("Amber's blueprint: " + why);
            h.succeed();
        });
        AwakenTests.register("realm_cells", 20, h -> {
            for (int cell : new int[]{0, 1, 63, 64, 500}) {
                int x = RealmCells.centerX(cell), z = RealmCells.centerZ(cell);
                if (RealmCells.cellAt(x, z) != cell || RealmCells.cellAt(x + 3000, z - 3000) != cell) h.fail("cell " + cell + " doesn't find itself");
                if (dev.awaken.gates.world.Cells.cellAt(x, z) >= 0) h.fail("a Realm reads as a floor cell");
            }
            if (RealmCells.cellAt(RealmCells.centerX(0) + RealmCells.HALF + 8, RealmCells.centerZ(0)) != -1) h.fail("the gap between cells belongs to one");
            h.succeed();
        });
        AwakenTests.register("realm_bag_empty", 20, h -> {
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER));
            if (Blueprints.pick(Theme.BELL_CRYPT, Rank.E, Set.of(), 1).isPresent()) h.fail("a theme with no Realm rolled one");
            if (Blueprints.pick(Theme.HIVE, Rank.S, Set.of(), 1).isPresent()) h.fail("a rank with no Realm rolled one");
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of(), 1).isEmpty()) h.fail("the open Realm wasn't rolled");
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of("awaken:amber_reach"), 1).isEmpty()) h.fail("a full bag didn't refill");
            h.succeed();
        });
        // the fence posts Euan caught: a real floating post has to fail, or the check proves nothing
        AwakenTests.register("realm_float_check_catches", 200, h -> {
            Land land = new Probe(8);
            FloatCheck.Report r = FloatCheck.scan(land, 0, 0, 31, 31);
            if (r.floaters().size() != 3) h.fail("expected a post, a plant and a lantern in the air, got: " + r.summary());
            FloatCheck.Report ok = FloatCheck.scan(new Probe(0), 0, 0, 31, 31);
            if (!ok.clean()) h.fail("a post on the ground counted as floating: " + ok.summary());
            h.succeed();
        });
        AwakenTests.register("realm_amber_never_floats", 1200, h -> {
            for (long seed : new long[]{1, 2}) {
                Land land = amber(seed);
                // the whole valley floor, the great rocks and the citadel with its hall (the second seed only where you'll walk)
                FloatCheck.Report r = seed == 1 ? FloatCheck.scan(land, -60, -260, 520, 300) : FloatCheck.scan(land, 230, -60, 520, 240);
                dev.awaken.Awaken.LOG.info("[realms] float check, seed {}: {} in {} ms", seed, r.summary(), r.millis());
                if (!r.clean()) {
                    r.floaters().stream().limit(40).forEach(f -> dev.awaken.Awaken.LOG.info("[realms] floating: {}", f));
                    h.fail("seed " + seed + ": " + r.summary());
                }
            }
            h.succeed();
        });
        AwakenTests.register("realm_amber_plan_checks", 400, h -> {
            for (long seed = 1; seed <= 8; seed++) {
                Land land = amber(seed);
                List<String> bad = LookChecks.problems(land);
                if (!bad.isEmpty()) {
                    bad.forEach(b -> dev.awaken.Awaken.LOG.warn("[realms] {}", b));
                    h.fail("seed " + seed + ": " + bad.size() + ", first: " + bad.getFirst());
                }
                Land.Site m = land.mouth();
                if (m.y() <= 62) h.fail("seed " + seed + ": the Mouth is at the water");
                if (land.sites(Land.SiteKind.WARDEN).size() < 3) h.fail("seed " + seed + ": too few Wardens");
                if (land.sites(Land.SiteKind.FIND).size() < 12) h.fail("seed " + seed + ": too few finds");
            }
            h.succeed();
        });
        AwakenTests.register("realm_hour_walks_to_everything", 2400, h -> {
            for (long seed : new long[]{1, 2}) {
                List<String> bad = dev.awaken.gates.realms.check.WalkCheck.unreachable(hour(seed));
                if (!bad.isEmpty()) h.fail("seed " + seed + ": can't reach " + bad);
            }
            h.succeed();
        });
        AwakenTests.register("realm_hour_nothing_floats", 2400, h -> {
            Land land = hour(1);
            FloatCheck.Report r = FloatCheck.scan(land, -216, -216, 216, 216);
            if (!r.clean()) h.fail(r.summary());
            h.succeed();
        });
        AwakenTests.register("realm_kingsroot_walks_to_everything", 2400, h -> {
            List<String> bad = dev.awaken.gates.realms.check.WalkCheck.unreachable(keep(1));
            if (!bad.isEmpty()) h.fail("can't reach: " + bad);
            h.succeed();
        });
        AwakenTests.register("realm_kingsroot_nothing_floats", 2400, h -> {
            FloatCheck.Report r = FloatCheck.scan(keep(1), -456, -456, 456, 456);
            if (!r.clean()) h.fail(r.summary());
            h.succeed();
        });
        AwakenTests.register("realm_vine_walks_to_everything", 2400, h -> {
            List<String> bad = dev.awaken.gates.realms.check.WalkCheck.unreachable(vine(1));
            if (!bad.isEmpty()) h.fail("can't reach: " + bad);
            h.succeed();
        });
        AwakenTests.register("realm_vine_nothing_floats", 2400, h -> {
            FloatCheck.Report r = FloatCheck.scan(vine(1), -416, -416, 416, 416);
            if (!r.clean()) h.fail(r.summary());
            h.succeed();
        });
        AwakenTests.register("realm_vine_curtains_part", 200, h -> {
            dev.awaken.gates.realms.land.VineSeat land = vine(1);
            if (land.curtainCount() < 5) h.fail("only " + land.curtainCount() + " curtains");
            if (land.change("roots_shut_0").isEmpty() || land.change("roots_open_0").isEmpty()) h.fail("a curtain does nothing");
            h.succeed();
        });
        AwakenTests.register("realm_hour_doors_toll", 200, h -> {
            Land land = hour(1);
            List<Land.Edit> shut = land.change("toll_shut"), open = land.change("toll_open");
            if (shut.isEmpty() || shut.size() != open.size()) h.fail("the doors don't have one way down and one way up: " + shut.size() + ", " + open.size());
            for (Land.Edit e : open) if (!e.state().isAir()) h.fail("a door lifted into " + e.state());
            if (land.change("heart_open").isEmpty() || land.change("heart_shut").isEmpty()) h.fail("the iron round the Lord's floor doesn't move");
            h.succeed();
        });
        AwakenTests.register("realm_amber_all_reachable", 800, h -> {
            for (long seed : new long[]{1, 2}) {
                Land land = amber(seed);
                int ok = reachable(land);
                if (ok < 96) h.fail("seed " + seed + ": only " + ok + "% of the valley can be walked from the Mouth");
            }
            h.succeed();
        });
        // every site is really standing free: nothing meant for open ground got buried in the rock, what you came for is there
        AwakenTests.register("realm_amber_sites_open", 400, h -> {
            for (long seed = 1; seed <= 4; seed++) {
                Land land = amber(seed);
                for (Land.Site s : land.sites()) {
                    net.minecraft.world.level.block.state.BlockState at = at(land, s.x(), s.y(), s.z()), up = at(land, s.x(), s.y() + 1, s.z());
                    switch (s.kind()) {
                        case WAYSTONE -> { if (!idOf(at).equals("rest_shrine")) h.fail("seed " + seed + " waystone " + s.index() + " is " + idOf(at)); }
                        case FIND -> { if (!idOf(at).equals("gate_cache")) h.fail("seed " + seed + " find " + s.index() + " at " + s.x() + "," + s.y() + "," + s.z() + " is " + idOf(at)); }
                        case DEN, WARDEN, MOUTH -> {
                            if (solid(at) || solid(up)) h.fail("seed " + seed + " " + s.kind() + " " + s.index() + " at " + s.x() + "," + s.y() + "," + s.z() + " is buried: " + idOf(at) + ", " + idOf(up));
                        }
                        default -> {}
                    }
                }
                // and one way up for each vent: the air reaches over the rock it stands beside
                if (land.sites(Land.SiteKind.UPDRAFT).isEmpty()) h.fail("seed " + seed + " has no vents");
            }
            h.succeed();
        });
        // the door is wax until it isn't, the hall is clear to fight in, and a fight's changes leave no trace
        AwakenTests.register("realm_amber_heart", 400, h -> {
            Land land = amber(3);
            for (Land.Edit e : land.change("heart_open")) if (at(land, e.x(), e.y(), e.z()) == null || at(land, e.x(), e.y(), e.z()).isAir()) {
                h.fail("the door has a hole at " + e.x() + "," + e.y() + "," + e.z());
            }
            Land.Site lord = land.site(Land.SiteKind.LORD);
            for (int a = -22; a <= 22; a += 2) for (int c = -22; c <= 22; c += 2) {
                if (a * a + c * c > 22 * 22) continue;
                for (int y = lord.y(); y < lord.y() + 18; y++) if (solid(at(land, lord.x() + a, y, lord.z() + c))) h.fail("the Lord's floor is blocked at " + a + "," + y + "," + c + ": " + idOf(at(land, lord.x() + a, y, lord.z() + c)));
            }
            if (land.change("heart_weeps").isEmpty() || land.change("heart_winds").isEmpty() || land.change("heart_still").size() < land.change("heart_weeps").size()) h.fail("the hall has nothing to change");
            h.succeed();
        });
        // Arriving: on something that holds you, with room to stand, out of the honey, and nothing waiting for you on the doorstep.
        AwakenTests.register("realm_mouth_safe", 400, h -> {
            for (long seed = 1; seed <= 6; seed++) {
                Land land = amber(seed);
                Land.Site m = land.mouth();
                if (!solid(at(land, m.x(), m.y() - 1, m.z()))) h.fail("seed " + seed + ": nothing under the Mouth: " + idOf(at(land, m.x(), m.y() - 1, m.z())));
                for (int y = 0; y < 2; y++) if (solid(at(land, m.x(), m.y() + y, m.z()))) h.fail("seed " + seed + ": no room to stand at the Mouth");
                for (Land.Site s : land.sites()) {
                    double d = Math.hypot(s.x() - m.x(), s.z() - m.z());
                    if (s.kind() == Land.SiteKind.DEN && d < 40) h.fail("seed " + seed + ": a den " + (int) d + " from the Mouth");
                    if (s.kind() == Land.SiteKind.WARDEN && d < 56) h.fail("seed " + seed + ": a Warden " + (int) d + " from the Mouth");
                }
                Land.Site home = land.site(Land.SiteKind.PORTAL);
                for (int y = 0; y < 3; y++) if (solid(at(land, home.x(), home.y() + y, home.z()))) h.fail("seed " + seed + ": the arch home is blocked at " + (home.y() + y));
                if (!solid(at(land, home.x(), home.y() - 1, home.z()))) h.fail("seed " + seed + ": no floor under the arch home");
            }
            h.succeed();
        });
        // The land is whole: every column inside the valley's edge has ground under it, and it's above the bottom of the world.
        AwakenTests.register("realm_no_holes", 600, h -> {
            for (long seed = 1; seed <= 3; seed++) {
                Land land = amber(seed);
                for (int x = -320; x <= 560; x += 40) for (int z = -320; z <= 320; z += 40) {
                    if (land.edge(x, z) > 0) continue;
                    int g = land.ground(x, z);
                    if (g < 40) h.fail("seed " + seed + ": ground " + g + " at " + x + "," + z);
                    var v = new dev.awaken.gates.realms.kit.V(Math.floorDiv(x, 16) * 16, Math.floorDiv(z, 16) * 16);
                    land.paint(v);
                    int top = v.groundBelow(x, 340, z);
                    if (top < g - 2) h.fail("seed " + seed + ": a hole at " + x + "," + z + ": top " + top + " for ground " + g);
                }
            }
            h.succeed();
        });
        // The way a place feels from the ground: long views, without a wall in your face, and real height.
        AwakenTests.register("realm_amber_sight", 1200, h -> {
            Land land = amber(1);
            Map<Long, int[]> columns = new java.util.HashMap<>();
            java.util.function.BiFunction<Integer, Integer, Integer> top = (x, z) -> {
                int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
                int[] c = columns.computeIfAbsent(net.minecraft.world.level.ChunkPos.pack(cx, cz), k -> {
                    var v = new dev.awaken.gates.realms.kit.V(cx * 16, cz * 16);
                    land.paint(v);
                    int[] out = new int[256];
                    for (int a = 0; a < 16; a++) for (int b = 0; b < 16; b++) out[a * 16 + b] = v.groundBelow(cx * 16 + a, 340, cz * 16 + b);
                    return out;
                });
                return c[Math.floorMod(x, 16) * 16 + Math.floorMod(z, 16)];
            };
            java.util.List<Integer> sights = new java.util.ArrayList<>(), heights = new java.util.ArrayList<>();
            var r = new java.util.Random(7);
            // from where you actually walk: the road through the valley, and up to thirty blocks either side of it
            List<double[]> way = land.ways();
            for (int n = 0; n < 48; n++) {
                double[] w = way.get(r.nextInt(way.size()));
                double along = r.nextDouble();
                int x = (int) (w[0] + (w[2] - w[0]) * along - 30 + r.nextInt(61)), z = (int) (w[1] + (w[3] - w[1]) * along - 30 + r.nextInt(61));
                if (land.edge(x, z) > -10 || land.inHeart(x, top.apply(x, z), z)) continue;
                int g = top.apply(x, z);
                heights.add(g);
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8, eye = g + 2.6, d = 2;
                    for (; d < 320; d += 3) {
                        int px = (int) Math.floor(x + Math.cos(a) * d), pz = (int) Math.floor(z + Math.sin(a) * d);
                        if (top.apply(px, pz) + 1 > eye) break;
                    }
                    sights.add((int) d);
                }
            }
            java.util.Collections.sort(sights);
            java.util.Collections.sort(heights);
            int median = sights.get(sights.size() / 2), p90 = sights.get(sights.size() * 9 / 10), range = heights.get(heights.size() - 1 - heights.size() / 20) - heights.get(heights.size() / 20);
            long past64 = sights.stream().filter(d -> d > 64).count();
            dev.awaken.Awaken.LOG.info("[realms] sight: median {}, p90 {}, past 64 {}%, ground range {}", median, p90, past64 * 100 / sights.size(), range);
            if (median < 30) h.fail("median sight " + median + " under 30");
            if (p90 < 150) h.fail("90th percentile sight " + p90 + " under 150");
            if (past64 * 100 / sights.size() < 30) h.fail("only " + past64 * 100 / sights.size() + "% of views reach past 64");
            h.succeed();
        });
        // With the switch off, a gate that could have been this Realm is floors as it always was.
        AwakenTests.register("realm_flag_off_rolls_floors", 20, h -> {
            var server = h.getLevel().getServer();
            boolean was = dev.awaken.core.AwakenConfig.GATE_REALMS.get();
            try {
                dev.awaken.core.AwakenConfig.GATE_REALMS.set(false);
                var plan = dev.awaken.gates.GateLiveTests.plan(4242, Theme.HIVE, dev.awaken.gates.content.Layout.LABYRINTH, dev.awaken.gates.content.Twist.NONE, 0);
                if (Realms.choose(server, plan, false, Set.of()).isPresent()) h.fail("the switch is off and a Realm was rolled");
            } finally {
                dev.awaken.core.AwakenConfig.GATE_REALMS.set(was);
            }
            h.succeed();
        });
        // The valley's herd, people and flies: each exists, is never rolled by a biome, and is a peaceful creature with its own voice.
        AwakenTests.register("realm_life_creatures", 40, h -> {
            var level = h.getLevel();
            var at = h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
            for (String id : List.of("pollen_grazer", "wax_folk", "amberfly")) {
                var def = dev.awaken.mobs.def.Bestiary.get(id);
                if (def == null || !def.placed) h.fail(id + " should exist and be spawned only by its Realm");
                if (dev.awaken.mobs.def.Bestiary.fauna().contains(def)) h.fail(id + " is in the world's spawn lists");
                if (dev.awaken.mobs.MobSounds.fauna(id, "ambient") == null) h.fail(id + " has no voice");
                var e = dev.awaken.core.api.MobHooks.spawnCreature(level, id, at, 0, "test");
                if (!(e instanceof dev.awaken.mobs.entity.FaunaMob f) || f.faunaRole() != dev.awaken.mobs.def.FaunaRole.AMBIENT) h.fail(id + " didn't spawn as a peaceful creature");
                else f.discard();
            }
            h.succeed();
        });
        // One of a party leaves: the Realm stands for whoever's still in it.
        AwakenTests.registerIsolated("realm_party_split", 200, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER));
            var g = dev.awaken.gates.GateLiveTests.standUp(h, dev.awaken.gates.GateLiveTests.plan(4545, Theme.HIVE, dev.awaken.gates.content.Layout.LABYRINTH, dev.awaken.gates.content.Twist.NONE, 0));
            var a = dev.awaken.gates.GateLiveTests.player(h);
            var b = dev.awaken.gates.GateLiveTests.player(h);
            try {
                dev.awaken.gates.GateLiveTests.useLevel(level); // the test server has no gate dimensions of its own
                Realms.attach(server, g, "awaken:amber_reach");
                g.interiorReady = true; // a Realm's land is drawn as you walk it; this test doesn't walk
                h.assertTrue(dev.awaken.gates.run.Runs.enter(a, g), "the first went in (" + g.state + ", ready " + g.interiorReady + ")");
                h.assertTrue(dev.awaken.gates.run.Runs.enter(b, g), "the second went in (" + g.state + ", ready " + g.interiorReady + ", already in " + dev.awaken.gates.run.Runs.run(b).active() + ")");
                dev.awaken.gates.run.Runs.extract(a, true);
                h.assertFalse(dev.awaken.gates.run.Runs.run(a).active(), "the one who left is out");
                h.assertTrue(dev.awaken.gates.run.Runs.run(b).active() && g.inside.contains(b.getUUID()), "the other is still in");
                h.assertTrue(g.state != dev.awaken.gates.GateState.RELEASED && Realms.plan(g) != null, "the Realm stands");
            } finally {
                if (dev.awaken.gates.run.Runs.run(b).active()) dev.awaken.gates.Gates.forceExtract(b, false);
                dev.awaken.gates.GateLiveTests.teardown(level, a, g);
                dev.awaken.gates.GateLiveTests.useLevel(null);
                server.getPlayerList().remove(a);
                server.getPlayerList().remove(b);
            }
            h.succeed();
        });
        AwakenTests.register("realm_seeds_differ", 200, h -> {
            Set<Integer> seen = new HashSet<>();
            for (long seed = 1; seed <= 5; seed++) {
                Land l = amber(seed);
                int sum = 0;
                for (int x = 0; x < 400; x += 16) for (int z = -200; z < 200; z += 16) sum = sum * 31 + l.ground(x, z);
                seen.add(sum);
            }
            if (seen.size() != 5) h.fail("seeds made the same land");
            h.succeed();
        });
    }

    private static net.minecraft.world.level.block.state.BlockState at(Land land, int x, int y, int z) {
        dev.awaken.gates.realms.kit.V v = new dev.awaken.gates.realms.kit.V(Math.floorDiv(x, 16) * 16, Math.floorDiv(z, 16) * 16);
        land.paint(v);
        return v.get(x, y, z);
    }

    private static boolean solid(net.minecraft.world.level.block.state.BlockState s) {
        return s != null && s.canOcclude();
    }

    private static String idOf(net.minecraft.world.level.block.state.BlockState s) {
        return s == null ? "air" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    /** A flood from the Mouth over walkable steps (up 1, or 2 if there's room to jump; down any): the share of the valley reached. */
    static int reachable(Land land) {
        int R = 360, step = 2, n = R * 2 / step;
        int[][] h = new int[n][n];
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) h[a][b] = land.ground(190 - R + a * step, -R + b * step);
        boolean[][] seen = new boolean[n][n];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        Land.Site m = land.mouth();
        int sa = Math.max(0, Math.min(n - 1, (m.x() - 190 + R) / step)), sb = Math.max(0, Math.min(n - 1, (m.z() + R) / step));
        q.add(new int[]{sa, sb});
        seen[sa][sb] = true;
        int reach = 0, total = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            reach++;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = p[0] + d[0], b = p[1] + d[1];
                if (a < 0 || b < 0 || a >= n || b >= n || seen[a][b]) continue;
                if (h[a][b] - h[p[0]][p[1]] > 2 * step) continue; // over two blocks of ground, at the sampling distance
                seen[a][b] = true;
                q.add(new int[]{a, b});
            }
        }
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
            // only land inside the valley's wall counts
            if (Math.hypot(190 - R + a * step - 190, (-R + b * step) * 1.15) < AmberBounds.WALK) total += 1;
        }
        return (int) Math.min(100, reach * 100L / Math.max(1, total));
    }

    private static final class AmberBounds {
        static final double WALK = 300;
    }

    /** A flat square with one post, one plant and one lantern that are, or aren't, up in the air. */
    private static final class Probe extends Land {
        final int lift;

        Probe(int lift) {
            super(new Ctx(AMBER, "probe", Rank.E, 0, 1));
            this.lift = lift;
        }

        @Override public int ground(int x, int z) { return 10; }

        @Override
        public void paint(V v) {
            for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) for (int y = 0; y <= 10; y++) v.set(x, y, z, Blocks.STONE.defaultBlockState());
            if (v.in(8, 0, 8)) v.set(8, 11 + lift, 8, Blocks.DARK_OAK_FENCE.defaultBlockState());
            if (v.in(12, 0, 8)) v.set(12, 11 + lift, 8, Blocks.SHORT_GRASS.defaultBlockState());
            if (v.in(8, 0, 12)) v.set(8, 11 + lift, 12, Blocks.LANTERN.defaultBlockState());
        }

        @Override public int biome(int x, int y, int z) { return 0; }
        @Override public int halfSize() { return 16; }
        @Override public List<? extends dev.awaken.gates.realms.kit.Piece<?>> pieces() { return List.of(); }
        @Override public List<double[]> ways() { return List.of(); }
        @Override public float mouthYaw() { return 0; }
    }
}
