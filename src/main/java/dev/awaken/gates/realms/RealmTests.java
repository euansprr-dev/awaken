package dev.awaken.gates.realms;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.realms.blockout.Blockout.Role;
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

    private static dev.awaken.gates.realms.land.SaltcombFlats salt(long seed) {
        return new dev.awaken.gates.realms.land.SaltcombFlats(new Land.Ctx(new Blueprint(1, "t", "desert_ruin", "hive", List.of("E"), new Blueprint.LandSpec("awaken:saltcomb_flats", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:saltcomb_flats", Rank.E, seed, 1));
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
        dev.awaken.gates.realms.bench.BenchTests.register(); // realm_bench (tools/bench/bench.sh) and the straight-in walk's command
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
        AwakenTests.register("realm_truelight_matches_the_shader", 20, h -> { String why = dev.awaken.gates.realms.preview.TrueLight.problem(); if (!why.isEmpty()) h.fail(why); h.succeed(); });
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
        // Settle (SPEC §50.6): a land drawn from a sheet can't keep a small stray, and the count says what to go and fix
        AwakenTests.register("realm_settle_clears_strays", 200, h -> {
            SettleProbe land = new SettleProbe(false);
            FloatCheck.Report r = FloatCheck.scan(land, 0, 0, 31, 31);
            if (!r.clean()) h.fail("strays survived: " + r.summary());
            V v = new V(16, 16);
            land.paint(v);
            if (v.get(20, 9, 20) == null || v.get(20, 9, 20).getBlock() != Blocks.WATER) h.fail("a stray under water left a hole: " + v.get(20, 9, 20));
            var tally = dev.awaken.gates.realms.kit.Settle.lastTally(land);
            dev.awaken.Awaken.LOG.info("[realms] {}", tally.line());
            // a lantern, a grass, a tall grass (two), a vine, a lily, a lone block with a lantern on it and one under it, a grass in the pool
            if (tally.total() != 10) h.fail("expected ten strays, " + tally.line());
            if (!tally.line().contains("2 tall_grass in the open")) h.fail("the count doesn't name what went: " + tally.line());
            h.succeed();
        });
        AwakenTests.register("realm_settle_keeps_the_sound", 200, h -> {
            SettleProbe land = new SettleProbe(true);
            FloatCheck.Report r = FloatCheck.scan(land, 0, 0, 31, 31);
            if (!r.clean()) h.fail("the probe's held things aren't held: " + r.summary());
            var tally = dev.awaken.gates.realms.kit.Settle.lastTally(land);
            if (tally.total() != 0) h.fail("things properly held were taken out: " + tally.line());
            V v = new V(0, 0);
            land.paint(v);
            int[][] kept = {{8, 20, 8}, {12, 11, 8}, {12, 11, 12}, {12, 12, 12}, {8, 15, 12}, {4, 11, 4}, {4, 11, 12}, {4, 12, 12}};
            for (int[] c : kept) if (v.get(c[0], c[1], c[2]) == null) h.fail("gone from " + c[0] + "," + c[1] + "," + c[2]);
            h.succeed();
        });
        // The blockout (SPEC §50.7): every shape reads back as written, a mistake says where it is, and the sample stands up as a land
        AwakenTests.register("realm_blockout_parses", 2400, h -> {
            var b = dev.awaken.gates.realms.blockout.Blockout.load("sample");
            java.util.function.BiConsumer<Boolean, String> must = (ok, what) -> { if (!ok) h.fail(what); };
            // a box, and the beacon standing on it
            must.accept(b.role(0, 100, 40) == Role.GREAT && b.role(9, 100, 40) != Role.GREAT, "the ship isn't where its box is");
            must.accept(b.mass("ship").edge(-8, 100, 40) == 0.5 && b.mass("ship").edge(-7, 100, 40) == 1.5 && b.mass("ship").edge(-9, 100, 40) < 0, "a box's edge is " + b.mass("ship").edge(-8, 100, 40));
            must.accept(b.role(0, 108, 40) == Role.GLOW, "no beacon");
            // a cut made after the hill, and the hill still over it
            must.accept(b.role(0, 68, 20) == null && !b.solid(0, 68, 20) && b.solid(0, 75, 20) && b.solid(5, 68, 20), "the passage isn't cut through the hill");
            // a dome: tall in the middle, nothing past its rim (the moor is there instead)
            must.accept(b.mass("hill").contains(0, 90, 40) && !b.mass("hill").contains(0, 90, 105) && b.mass("hill").top(80, 40) == -1, "the hill isn't a dome");
            // a wedge rises in a straight line along +z
            var stair = b.mass("stair");
            must.accept(stair.top(-26, -50) == 58 && stair.top(-26, 10) == 94 && stair.top(-26, -20) == 76 && stair.top(-31, -20) == -1, "the stair climbs " + stair.top(-26, -50) + ", " + stair.top(-26, -20) + ", " + stair.top(-26, 10));
            // a ring: wall two thick, floor inside it, open above the floor
            var tower = b.mass("tower");
            must.accept(b.role(99, 80, -20) == Role.WALL && b.role(98, 80, -20) == Role.WALL && b.role(97, 80, -20) == null && b.role(100, 80, -20) == null, "the tower's wall isn't two thick");
            must.accept(tower.edge(99, 80, -20) > 0 && tower.edge(99, 80, -20) < 1 && tower.edge(98, 80, -20) < 2 && tower.edge(97, 80, -20) < 0, "the tower's edge is " + tower.edge(99, 80, -20) + ", " + tower.edge(98, 80, -20));
            must.accept(b.role(90, 64, -20) == Role.FLOOR && b.ground(90, -20) >= 64 && tower.cx == 90 && tower.r == 9 && tower.h == 48, "the tower's floor or numbers are off");
            // a cone: as wide as its base at the bottom, a point at the top
            var spire = b.mass("spire");
            must.accept(spire.contains(100, 104, -20) && !spire.contains(101, 104, -20) && spire.contains(90, 121, -20) && !spire.contains(92, 121, -20) && spire.top(90, -20) == 121, "the spire isn't a cone");
            // a sphere, sitting on its trunk
            var crown = b.mass("crown");
            must.accept(crown.contains(-90, 92, -40) && crown.contains(-90, 104, -40) && crown.contains(-90, 80, -40) && !crown.contains(-90, 105, -40) && !crown.contains(-77, 92, -40), "the crown isn't a sphere");
            must.accept(b.role(-90, 84, -40) == Role.FOLIAGE && b.role(-90, 70, -40) == Role.WALL, "the tree isn't a trunk under a crown");
            // water in a bed cut for it, inside a bank
            must.accept(b.role(120, 62, 120) == Role.WATER && b.role(120, 63, 120) == null && !b.solid(120, 62, 120) && b.role(138, 63, 120) == Role.GROUND, "the pond isn't water in a bank");
            // rough ground: within its three blocks, the same for a seed, different between seeds
            var one = b.seeded(1);
            var again = b.seeded(1);
            var two = b.seeded(2);
            int differ = 0, lo = 999, hi = 0;
            for (int x = -200; x <= -120; x += 4) for (int z = 100; z <= 200; z += 4) {
                int g = one.ground(x, z);
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
                if (g != again.ground(x, z)) h.fail("one seed gave two grounds at " + x + "," + z);
                if (g != two.ground(x, z)) differ++;
            }
            must.accept(lo >= 60 && hi <= 66 && hi > lo, "rough ground runs from " + lo + " to " + hi);
            must.accept(differ > 20, "two seeds made the same moor");
            must.accept(b.ground(0, -190) == 67 && b.ground(500, 0) == -1, "the landing's ground is " + b.ground(0, -190));
            must.accept(b.cameras().size() == 3 && b.tourShots().contains("cmd awaken gate realm_at MOUTH 0 0 8 -15"), "the cameras don't make a walk: " + b.tourShots());
            // a mistake names the mass and the field
            String[][] wrong = {
                    {"{'name':'tower','op':'add','shape':'cylinder','at':[0,64,0],'h':9,'role':'wall'}", "mass 'tower': 'r' is missing"},
                    {"{'name':'tower','op':'add','shape':'cylinder','at':[0,64,0],'r':4,'h':9,'role':'wal'}", "mass 'tower': 'role' is 'wal'"},
                    {"{'name':'ramp','op':'add','shape':'wedge','from':[0,64,0],'to':[4,70,9],'up':'north','role':'walk'}", "mass 'ramp': 'up' is 'north'"},
                    {"{'name':'moor','op':'add','shape':'box','from':[0,0,0],'to':[9,9],'role':'ground'}", "mass 'moor': 'to' must be 3 numbers"},
                    {"{'name':'moor','op':'add','shape':'box','from':[0,0,0],'to':[9,9,9],'role':'ground','ruogh':2}", "mass 'moor': 'ruogh' is not a field"}};
            for (String[] w : wrong) {
                try {
                    dev.awaken.gates.realms.blockout.Blockout.parse("t", ("{'schemaVersion':1,'half':64,'masses':[" + w[0] + "],'sites':[{'kind':'MOUTH','at':[0,64,0]}]}").replace('\'', '"'));
                    h.fail("no complaint about " + w[1]);
                } catch (IllegalArgumentException e) {
                    if (!e.getMessage().contains(w[1])) h.fail("wanted \"" + w[1] + "\", got \"" + e.getMessage() + "\"");
                }
            }
            // the sample as a land: drawn in its role's blocks, sites from the data, nothing floating on two seeds, nothing for Settle to take
            if (!LandPrograms.has("awaken:blockout")) h.fail("no land program awaken:blockout");
            Blueprint bp = new Blueprint(1, "t", "land", "hive", List.of(), new Blueprint.LandSpec("awaken:blockout", 62, Map.of()),
                    AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), "");
            for (long seed : new long[]{1, 2}) {
                Land land = RealmPlan.of("awaken:blockout_sample", bp, Rank.E, seed, 1).land();
                if (land.halfSize() != 220 || land.mouth().y() != 68 || land.sites(Land.SiteKind.LORD).size() != 1) h.fail("the land didn't take its size and sites from the blockout");
                if (at(land, 99, 80, -20) == null || at(land, 99, 80, -20).getBlock() != Blocks.STONE || at(land, 120, 62, 120).getBlock() != Blocks.WATER) h.fail("the tower wall is " + at(land, 99, 80, -20));
                FloatCheck.Report r = FloatCheck.scan(land, -236, -236, 236, 236);
                var tally = dev.awaken.gates.realms.kit.Settle.lastTally(land);
                dev.awaken.Awaken.LOG.info("[realms] blockout sample, seed {}: {} in {} ms; {}", seed, r.summary(), r.millis(), tally.line());
                if (!r.clean()) h.fail("seed " + seed + ": " + r.summary());
                if (tally.total() != 0) h.fail("seed " + seed + ": the sample has strays: " + tally.line());
            }
            // sixty masses over one chunk still draw fast (the best of several, so a busy machine doesn't fail it)
            StringBuilder many = new StringBuilder("{'schemaVersion':1,'half':64,'sites':[{'kind':'MOUTH','at':[0,90,0]}],'masses':[");
            String[] shapes = {"'shape':'box','from':[-9,%d,-9],'to':[24,%d,24]", "'shape':'cylinder','at':[8,%d,8],'r':14,'h':6,'inner':5", "'shape':'dome','at':[8,%d,8],'r':16,'h':7",
                    "'shape':'cone','at':[8,%d,8],'r':15,'h':8", "'shape':'sphere','at':[8,%d,8],'r':13", "'shape':'wedge','from':[-9,%d,-9],'to':[24,%d,24],'up':'+x'"};
            for (int i = 0; i < 60; i++) {
                int y = 20 + i * 4;
                many.append(i == 0 ? "" : ",").append("{'name':'m").append(i).append("','op':'").append(i % 7 == 6 ? "cut" : "add").append("','role':'wall','rough':").append(i % 3).append(',')
                        .append(String.format(shapes[i % 6], y, y + 5)).append('}');
            }
            var crowd = new dev.awaken.gates.realms.blockout.BlockoutLand(new Land.Ctx(bp, "awaken:crowd", Rank.E, 1, 1), dev.awaken.gates.realms.blockout.Blockout.parse("crowd", many.append("]}").toString().replace('\'', '"')));
            long best = Long.MAX_VALUE;
            for (int i = 0; i < 12; i++) {
                long t0 = System.nanoTime();
                crowd.paint(new V(0, 0));
                best = Math.min(best, System.nanoTime() - t0);
            }
            dev.awaken.Awaken.LOG.info("[realms] blockout: a chunk under 60 masses draws and settles in {} ms", String.format("%.2f", best / 1e6));
            if (best > 30_000_000L) h.fail("a chunk under 60 masses took " + best / 1_000_000 + " ms");
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
        AwakenTests.register("realm_saltcomb_walks_to_everything", 2400, h -> {
            List<String> bad = dev.awaken.gates.realms.check.WalkCheck.unreachable(salt(1));
            if (!bad.isEmpty()) h.fail("can't reach: " + bad);
            h.succeed();
        });
        AwakenTests.register("realm_saltcomb_nothing_floats", 2400, h -> {
            FloatCheck.Report r = FloatCheck.scan(salt(1), -436, -436, 436, 436);
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

    /**
     * A flat square with a hanging lantern, a grass, a tall grass, a vine, a lily and a lone block (a lantern standing on it,
     * another hung under it), each properly held or each left in the air, and a grass at the bottom of a pool.
     */
    private static final class SettleProbe extends dev.awaken.gates.realms.kit.SheetLand<dev.awaken.gates.realms.kit.Mats> {
        final boolean held;

        SettleProbe(boolean held) {
            super(new Ctx(AMBER, "settle_probe", Rank.E, 0, 1), 0);
            this.held = held;
        }

        @Override protected dev.awaken.gates.realms.kit.Mats mats() { return null; }
        @Override public int ground(int x, int z) { return 10; }

        @Override
        protected void draw(V v) {
            var stone = Blocks.STONE.defaultBlockState();
            var water = Blocks.WATER.defaultBlockState();
            var grass = Blocks.SHORT_GRASS.defaultBlockState();
            var hung = Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true);
            for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) for (int y = 0; y <= 10; y++) v.set(x, y, z, stone);
            for (int x = 18; x <= 22; x++) for (int z = 18; z <= 22; z++) for (int y = 8; y <= 10; y++) v.set(x, y, z, water);
            if (!held) v.set(20, 9, 20, grass);
            int up = held ? 0 : 4;
            if (held) {
                for (int y = 11; y <= 21; y++) v.set(7, y, 8, stone);
                v.set(8, 21, 8, stone);
                for (int y = 11; y <= 15; y++) v.set(9, y, 12, stone);
                v.set(4, 10, 4, water);
            }
            v.set(8, 20, 8, hung);
            v.set(12, 11 + up, 8, grass);
            v.set(12, 11 + up, 12, Blocks.TALL_GRASS.defaultBlockState());
            v.set(12, 12 + up, 12, Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            v.set(8, 15, 12, Blocks.VINE.defaultBlockState());
            v.set(4, 11, 4, Blocks.LILY_PAD.defaultBlockState());
            v.set(4, 11 + 2 * up, 12, stone);
            v.set(4, 12 + 2 * up, 12, Blocks.LANTERN.defaultBlockState());
            if (!held) v.set(4, 10 + 2 * up, 12, hung);
        }

        @Override public int biome(int x, int y, int z) { return 0; }
        @Override public int halfSize() { return 16; }
        @Override public float mouthYaw() { return 0; }
    }
}
