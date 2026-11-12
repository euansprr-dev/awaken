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

    private static Land brood(long seed) {
        return new dev.awaken.gates.realms.land.Broodwarren(new Land.Ctx(new Blueprint(1, "t", "dungeon", "hive", List.of("E"), new Blueprint.LandSpec("awaken:broodwarren", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:broodwarren", Rank.E, seed, 1));
    }

    /** Every skin on the crawl generator: each has to play on any seed. */
    private static final String[] CRAWLS = {"awaken:brood_burrow", "awaken:catacombs", "awaken:hollow_hills", "awaken:root_cellars", "awaken:tallow_runs",
            "awaken:sunken_stacks", "awaken:sporewells", "awaken:cinder_workings", "awaken:frozen_cells", "awaken:glass_warrens", "awaken:stopped_works",
            "awaken:silvered_halls", "awaken:sealed_wing", "awaken:sleepless_ward", "awaken:unlit_stair", "awaken:rime_hollows", "awaken:green_dark"};

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

    private static dev.awaken.gates.realms.land.QuietBarrow barrow(long seed) {
        return new dev.awaken.gates.realms.land.QuietBarrow(new Land.Ctx(new Blueprint(1, "t", "land", "bell_crypt", List.of("E"), new Blueprint.LandSpec("awaken:quiet_barrow", 62, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:quiet_barrow", Rank.E, seed, 1));
    }

    private static dev.awaken.gates.realms.land.WaxGalleries wax(long seed) {
        return new dev.awaken.gates.realms.land.WaxGalleries(new Land.Ctx(new Blueprint(1, "t", "deep", "hive", List.of("E"), new Blueprint.LandSpec("awaken:wax_galleries", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:wax_galleries", Rank.E, seed, 1));
    }

    private static Land leapers() {
        return LandPrograms.make(new Land.Ctx(new Blueprint(1, "t", "dungeon", "clockwork_foundry", List.of(), new Blueprint.LandSpec("awaken:leapers_gauntlet", 0, Map.of()),
                AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), ""), "awaken:leapers_gauntlet", Rank.D, 1, 1));
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
        dev.awaken.gates.realms.kit.land.LandTests.register(); // the rolled lands (SPEC 50.9): each walks to everything and floats nothing
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
        // Who lives there (SPEC 50.9): a cell's haunt comes from the seed and the ground alone, keeps off the run's own places, and leaves quiet stretches
        AwakenTests.register("realm_country_haunts", 200, h -> {
            Land land = amber(1);
            if (!RealmCountry.has(land)) h.fail("Amber Reach has no country");
            int half = land.halfSize() / RealmCountry.CELL + 1, cells = 0, quiet = 0, herds = 0, packs = 0;
            StringBuilder list = new StringBuilder();
            for (int cz = -half; cz <= half; cz++) {
                for (int cx = -half; cx <= half; cx++) {
                    RealmCountry.Haunt a = RealmCountry.find(land, 1, cx, cz);
                    if (!a.equals(RealmCountry.find(land, 1, cx, cz))) h.fail("cell " + cx + "," + cz + " gave two answers");
                    if (land.edge(cx * RealmCountry.CELL + RealmCountry.CELL / 2, cz * RealmCountry.CELL + RealmCountry.CELL / 2) > -20) {
                        if (a.kind() != RealmCountry.Kind.NONE) h.fail("a haunt past the land's edge at cell " + cx + "," + cz);
                        continue;
                    }
                    cells++;
                    switch (a.kind()) {
                        case NONE -> quiet++;
                        case HERD, SMALL -> herds++;
                        default -> packs++;
                    }
                    if (a.kind() == RealmCountry.Kind.NONE) continue;
                    if (!RealmCountry.free(land, a.x(), a.z(), 24)) h.fail("a " + a.kind() + " at " + a.x() + "," + a.z() + " stands on one of the run's own places");
                    if (Math.abs(a.y() - 1 - land.ground(a.x(), a.z())) != 0) h.fail("a haunt that isn't on the ground at " + a.x() + "," + a.z());
                    list.append(' ').append(a.kind()).append('@').append(a.x()).append(',').append(a.y()).append(',').append(a.z());
                }
            }
            dev.awaken.Awaken.LOG.info("[country] amber seed 1: {} cells, {} quiet, {} herds, {} packs:{}", cells, quiet, herds, packs, list);
            if (herds == 0 || packs == 0) h.fail("Amber Reach's country is empty: " + herds + " herds and " + packs + " packs in " + cells + " cells");
            if (quiet * 3 < cells) h.fail("no quiet stretches: only " + quiet + " of " + cells + " cells hold nothing");
            if (!RealmCountry.find(land, 2, 1, 1).equals(RealmCountry.find(land, 2, 1, 1))) h.fail("another seed gave two answers");
            h.succeed();
        });
        // The Leaper's Gauntlet as a Realm (SPEC 17.10): every leg is reached by a walker who has the leap, no crossing that
        // is said to need a move of it can be made without, and nothing floats
        AwakenTests.register("realm_leapers_gauntlet_needs_its_moves", 2400, h -> {
            Land land = leapers();
            dev.awaken.gates.realms.check.WalkCheck.Walk walk = dev.awaken.gates.realms.check.WalkCheck.walk(land);
            if (!walk.unreachable().isEmpty()) h.fail("can't reach " + walk.unreachable());
            for (Land.Leg leg : land.route()) if (!walk.reaches(leg.x(), leg.y(), leg.z(), 4)) h.fail("the walk never stood in " + leg.id() + " (" + leg.name() + ")");
            List<String> needless = dev.awaken.gates.realms.check.WalkCheck.needless(land);
            if (!needless.isEmpty()) h.fail("crossed without the move it is said to need: " + needless);
            long doubles = land.leaps().stream().filter(l -> l.move().equals("double")).count(), dashes = land.leaps().stream().filter(l -> l.move().equals("dash")).count();
            if (doubles < 4) h.fail("the hall Twice asks for the double jump " + doubles + " times, not four");
            if (dashes < 3) h.fail("the hall Through asks for the dash " + dashes + " times");
            if (land.change("trial_open_dash").isEmpty() || land.change("trial_open_slam").isEmpty()) h.fail("a stand's bars are not there to open");
            for (Land.Course c : land.courses()) if (!c.free()) h.fail("a fall in " + c.leg() + " costs something");
            if (!land.soft()) h.fail("a fall in the trial's land hurts");
            // three stands, each with its press over it; what a slam moves is there to move; a place for every use the plan asks for
            if (land.stands().size() != 3) h.fail(land.stands().size() + " stands, not three");
            for (Land.Stand s : land.stands()) if (s.head() < s.y() + 4 + RealmTrial.STROKE_DROP) h.fail("the press over the " + s.move() + " stand would come down on whoever takes it");
            for (Land.Slam s : land.slams()) {
                if (land.change("slam_" + s.id()).isEmpty() && land.rams().stream().noneMatch(r -> r.freedBy().equals(s.id()))) h.fail("the slam '" + s.id() + "' moves nothing");
            }
            if (land.slams().size() < 3) h.fail("the hall Down asks for the slam " + land.slams().size() + " times");
            for (String mark : new String[]{"reach", "use/double/4", "use/dash/4", "use/slam/3"}) {
                if (land.spots().stream().noneMatch(s -> s.mark().equals(mark))) h.fail("no place for the mark " + mark);
            }
            // the Long Run: a start, five rings stood under in turn, a far line, and a way home at both its ends
            Land.Timed run = land.timed();
            if (run == null || run.rings().size() < 5) h.fail("the Long Run has no clock or too few rings");
            else {
                for (int[] r : run.rings()) if (!walk.reaches(r[0], r[1], r[2], 1)) h.fail("the walk never stood under the ring at " + r[0] + " " + r[1] + " " + r[2]);
                if (!walk.reaches((run.start()[0] + run.start()[3]) / 2, run.start()[1], run.start()[2], 2)) h.fail("the walk never stood where the clock starts");
                if (!walk.reaches((run.finish()[0] + run.finish()[3]) / 2, run.finish()[1], run.finish()[2], 2)) h.fail("the walk never stood on the far line");
            }
            for (Land.Site s : land.sites(Land.SiteKind.PORTAL)) if (!walk.reaches(s.x(), s.y(), s.z(), 3)) h.fail("the walk never stood at the way home at " + s.x() + " " + s.y() + " " + s.z());
            if (land.sites(Land.SiteKind.PORTAL).size() < 3) h.fail("no way home past the mouth");
            for (Land.Slam s : land.slams()) if (s.again() && land.change("rearm_" + s.id()).isEmpty()) h.fail("the slam '" + s.id() + "' is not put back for the next run");
            // a press on a beat is down, comes all the way up, and can be passed under for long enough to run through it
            for (Land.Ram r : land.rams()) {
                if (r.period() == 0) continue;
                int open = 0, top = 0;
                for (int c = 0; c < r.period(); c++) {
                    if (RealmTrial.passable(r, c)) open++;
                    top = Math.max(top, RealmTrial.offset(r, c));
                }
                if (RealmTrial.offset(r, 0) != 0 || top != r.travel() || open < 20) h.fail("a press's beat: up " + top + " of " + r.travel() + ", open for " + open + " ticks");
            }
            h.succeed();
        });
        AwakenTests.register("realm_leapers_gauntlet_nothing_floats", 2400, h -> {
            Land land = leapers();
            int half = land.halfSize();
            dev.awaken.gates.realms.check.FloatCheck.Report r = dev.awaken.gates.realms.check.FloatCheck.scan(land, -half, -half, half, half);
            if (!r.clean()) h.fail(r.summary());
            h.succeed();
        });
        AwakenTests.register("realm_truelight_matches_the_shader", 20, h -> { String why = dev.awaken.gates.realms.preview.TrueLight.problem(); if (!why.isEmpty()) h.fail(why); h.succeed(); });
        AwakenTests.register("realm_bag_empty", 20, h -> {
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER));
            if (Blueprints.pick(Theme.BELL_CRYPT, Rank.E, Set.of(), 1).isPresent()) h.fail("a theme with no Realm rolled one");
            if (Blueprints.pick(Theme.HIVE, Rank.S, Set.of(), 1).isPresent()) h.fail("a rank with no Realm rolled one");
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of(), 1).isEmpty()) h.fail("the open Realm wasn't rolled");
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of("awaken:amber_reach"), 1).isEmpty()) h.fail("a full bag didn't refill");
            // a Realm that doesn't say it is ready is never rolled, alone or beside one that is
            Blueprint unready = new Blueprint(1, "t", "land", "hive", List.of("E"), AMBER.land(), AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(),
                    AMBER.heart(), AMBER.moods(), "", false);
            Blueprints.replace(Map.of("awaken:unready", unready));
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of(), 1).isPresent()) h.fail("a Realm that isn't ready was rolled");
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER, "awaken:unready", unready));
            for (long seed = 0; seed < 8; seed++) {
                if (!Blueprints.pick(Theme.HIVE, Rank.E, Set.of("awaken:amber_reach"), seed).equals(java.util.Optional.of("awaken:amber_reach"))) h.fail("an unready Realm was rolled beside a ready one");
            }
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER));
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
        AwakenTests.register("realm_quiet_barrow_walks_to_everything", 2400, h -> {
            for (long seed : new long[]{1, 2}) {
                List<String> bad = dev.awaken.gates.realms.check.WalkCheck.unreachable(barrow(seed));
                if (!bad.isEmpty()) h.fail("seed " + seed + ": can't reach " + bad);
            }
            h.succeed();
        });
        AwakenTests.register("realm_quiet_barrow_nothing_floats", 2400, h -> {
            dev.awaken.gates.realms.land.QuietBarrow land = barrow(1);
            FloatCheck.Report r = FloatCheck.scan(land, -256, -256, 256, 256);
            dev.awaken.Awaken.LOG.info("[realms] the Quiet Barrow: {} standing stones, {} mounds asleep; {}", land.stoneCount(), land.sleepers().size(), dev.awaken.gates.realms.kit.Settle.lastTally(land).line());
            if (!r.clean()) h.fail(r.summary());
            // each mound that sleeps has a seal to take out and a den behind it, and the way aboard is shut until it's opened
            if (land.sleepers().size() != 9 || land.sites(Land.SiteKind.DEN).size() != 9) h.fail("nine mounds should sleep, each with its den");
            for (int i = 0; i < 9; i++) if (land.change("wake_" + i).size() < 18) h.fail("mound " + i + " has no seal to take out");
            if (land.change("heart_open").isEmpty() || land.change("heart_shut").isEmpty()) h.fail("the way aboard doesn't open and shut");
            h.succeed();
        });
        AwakenTests.register("realm_wax_galleries_walks_to_everything", 2400, h -> {
            List<String> bad = dev.awaken.gates.realms.check.WalkCheck.unreachable(wax(1));
            if (!bad.isEmpty()) h.fail("can't reach: " + bad);
            h.succeed();
        });
        AwakenTests.register("realm_wax_galleries_nothing_floats", 2400, h -> {
            FloatCheck.Report r = FloatCheck.scan(wax(1), -192, -192, 192, 192);
            if (!r.clean()) h.fail(r.summary());
            h.succeed();
        });
        AwakenTests.register("realm_wax_warm_slows_and_cold_holds", 200, h -> {
            if (WarmWaxBlock.KEEPS >= 0.7) h.fail("warm wax barely slows you: " + WarmWaxBlock.KEEPS);
            net.minecraft.core.BlockPos at = h.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
            var empty = net.minecraft.world.phys.shapes.CollisionContext.empty();
            if (!WarmWaxBlock.BLOCK.get().defaultBlockState().getCollisionShape(h.getLevel(), at, empty).isEmpty()) h.fail("warm wax should have no floor to it");
            var cold = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("wax_cold")).defaultBlockState();
            if (cold.getCollisionShape(h.getLevel(), at, empty).isEmpty()) h.fail("cold wax should hold your weight");
            dev.awaken.gates.realms.land.WaxGalleries land = wax(1);
            if (land.coldPlatesOnTheWay() < 600) h.fail("too few cold plates on the way across: " + land.coldPlatesOnTheWay());
            if (land.change("seat_melt_1").isEmpty() || land.change("seat_melt_2").size() <= land.change("seat_melt_1").size()) h.fail("the seat doesn't melt in two stages");
            h.succeed();
        });
        AwakenTests.register("realm_vine_curtains_part", 200, h -> {
            dev.awaken.gates.realms.land.VineSeat land = vine(1);
            if (land.curtainCount() < 5) h.fail("only " + land.curtainCount() + " curtains");
            if (land.change("roots_shut_0").isEmpty() || land.change("roots_open_0").isEmpty()) h.fail("a curtain does nothing");
            h.succeed();
        });
        // The Run (SPEC §50.9): a designed run has to be playable as written, and a land nobody designed still has one read off its sites
        AwakenTests.register("realm_hour_route_plays", 2400, h -> {
            Land land = hour(1);
            var route = dev.awaken.gates.realms.check.RouteCheck.check(land, dev.awaken.gates.realms.check.WalkCheck.walk(land));
            if (!route.problems().isEmpty()) h.fail(route.summary() + ": " + route.problems());
            if (land.route().size() != 16 || !land.designed()) h.fail("the Crypt's run isn't its sixteen legs: " + land.route().size());
            if (land.change("relic_open").isEmpty() || land.change("relic_open").size() != land.change("relic_shut").size()) h.fail("the reliquary's door doesn't go both ways");
            if (land.change("shortcut_balcony").isEmpty() || !land.seals("bones") || land.seals("ropes")) h.fail("the rope, or the Wardens' doors, aren't as the sheet says");
            h.succeed();
        });
        AwakenTests.register("realm_brood_plays", 2400, h -> {
            for (long seed : new long[]{1, 2}) {
                Land land = brood(seed);
                var walk = dev.awaken.gates.realms.check.WalkCheck.walk(land);
                if (!walk.unreachable().isEmpty()) h.fail("seed " + seed + ": can't reach " + walk.unreachable());
                var route = dev.awaken.gates.realms.check.RouteCheck.check(land, walk);
                if (!route.problems().isEmpty()) h.fail("seed " + seed + ": " + route.summary() + ": " + route.problems());
                int half = land.halfSize();
                FloatCheck.Report r = FloatCheck.scan(land, -half, -half, half, half);
                if (!r.clean()) h.fail("seed " + seed + ": " + r.summary());
                for (String leg : new String[]{"ante", "larder", "ring"}) if (!land.seals(leg)) h.fail(leg + " has no doors to shut");
                if (land.change("heart_open").isEmpty() || land.change("shortcut_crawl").isEmpty() || land.change("ambush_larder").isEmpty()) h.fail("the way down, the crawl or the larder's sacs don't change");
            }
            h.succeed();
        });
        // a rolled crawl has to play whatever the seed: every one walks, floats nothing and passes the same route check a designed run does.
        // A layout comes from the seed alone, whatever the kind. In the suite every kind is tried on seed 1 (a great run: a shaft, a chasm,
        // both traps, two Wardens) and on one seed no other kind is tried on: 34 checks, about four seconds each on the Mac and far more on
        // GitHub's runners, where the whole suite has 45 minutes. The wide sweep (eight seeds a kind, 104 layouts) is run by hand whenever
        // the generator or a skin changes: AWAKEN_CRAWL_SEEDS=8 tools/gradle.sh 1700 runGameTestServer -Ptests=realm_generated
        AwakenTests.register("realm_generated_crawls_play", 4800, h -> {
            int perKind = 2;
            String wide = System.getenv("AWAKEN_CRAWL_SEEDS");
            if (wide != null) try {
                perKind = Math.max(2, Math.min(40, Integer.parseInt(wide.trim())));
            } catch (NumberFormatException ignored) {}
            Set<String> sizes = new HashSet<>(), shapes = new HashSet<>(), cuts = new java.util.TreeSet<>();
            List<String> bad = new java.util.ArrayList<>();
            for (int k = 0; k < CRAWLS.length; k++) for (int i = 0; i < perKind; i++) {
                String program = CRAWLS[k];
                long seed = i == 0 ? 1 : i == 2 ? 2 : 100 + k * 40L + i; // the wide sweep holds every seed the suite tries
                Blueprint bp = new Blueprint(1, "t", "dungeon", program.endsWith("catacombs") ? "bell_crypt" : "hive", List.of("E"), new Blueprint.LandSpec(program, 0, Map.of()),
                        AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), "");
                Land land = LandPrograms.make(new Land.Ctx(bp, program, Rank.E, seed, 1));
                String at = program + " seed " + seed + ": ";
                var walk = dev.awaken.gates.realms.check.WalkCheck.walk(land);
                if (!walk.unreachable().isEmpty()) bad.add(at + "can't reach " + walk.unreachable());
                var route = dev.awaken.gates.realms.check.RouteCheck.check(land, walk);
                if (!route.problems().isEmpty()) bad.add(at + route.summary() + ": " + route.problems());
                int half = land.halfSize();
                FloatCheck.Report r = FloatCheck.scan(land, -half, -half, half, half);
                if (!r.clean()) bad.add(at + r.summary());
                if (land.change("heart_open").isEmpty() || !land.seals("fight1") || !land.seals("warden")) bad.add(at + "the Lord's door or a fight's doors are missing");
                var rolled = (dev.awaken.gates.realms.kit.crawl.GeneratedCrawl<?>) land;
                sizes.add(rolled.size().name());
                shapes.add(rolled.shape().name());
                cuts.addAll(rolled.used());
                // what each run is, so whoever writes a tour can pick the seed that shows a kind's own shapes
                if (perKind >= 8) dev.awaken.Awaken.LOG.info("[realms] run {} seed {}: {} {}, {}", program, seed, rolled.size(), rolled.shape(), rolled.used());
            }
            // every fault is listed in the log, so one run says everything that is wrong
            for (String line : bad) dev.awaken.Awaken.LOG.error("[realms] rolled crawl: {}", line);
            if (!bad.isEmpty()) h.fail(bad.size() + " rolled crawls are wrong, the first: " + bad.get(0));
            // so many seeds must not all be the same run: on the wide sweep every size and every shape turns up
            if (sizes.size() < (perKind >= 8 ? 3 : 2) || shapes.size() < (perKind >= 8 ? 4 : 2)) h.fail("the seeds rolled only " + sizes + " and " + shapes);
            dev.awaken.Awaken.LOG.info("[realms] rolled crawls: {} kinds on {} seeds each, sizes {}, shapes {}, cut as {}", CRAWLS.length, perKind, sizes, shapes, cuts);
            // and no two legs need be cut alike: on the wide sweep every room form, hall plan and crossing has turned up somewhere
            if (perKind >= 8) {
                List<String> missing = new java.util.ArrayList<>();
                for (var f : dev.awaken.gates.realms.kit.crawl.CrawlLand.Cave.Form.values()) if (!cuts.contains("room " + f)) missing.add("room " + f);
                for (var f : dev.awaken.gates.realms.kit.crawl.CrawlLand.Arena.Plan.values()) if (!cuts.contains("lord " + f)) missing.add("lord " + f);
                for (var f : dev.awaken.gates.realms.kit.crawl.CrawlLand.Chasm.Kind.values()) if (!cuts.contains("crossing " + f)) missing.add("crossing " + f);
                if (!missing.isEmpty()) h.fail("the wide sweep never rolled " + missing);
            }
            h.succeed();
        });
        // a pack names its monsters: each has to come as itself, in its own tier's role, as a monster of the gate (a theme's own and the cave hunters alike)
        AwakenTests.register("realm_packs_spawn_by_name", 60, h -> {
            var level = h.getLevel();
            var at = h.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2));
            Object[][] want = {{"hive_larva", dev.awaken.core.api.MobRole.Role.NORMAL}, {"toll_ghoul", dev.awaken.core.api.MobRole.Role.NORMAL}, {"hive_guard", dev.awaken.core.api.MobRole.Role.ELITE},
                    {"drip_lurker", dev.awaken.core.api.MobRole.Role.NORMAL}, {"hushling", dev.awaken.core.api.MobRole.Role.NORMAL}, {"thousandfoot", dev.awaken.core.api.MobRole.Role.WARDEN}};
            for (Object[] w : want) {
                var e = dev.awaken.core.api.MobHooks.spawnNamed(level, (String) w[0], at, 3, Set.of(), "test");
                if (e == null) {
                    h.fail(w[0] + " didn't come when a pack named it");
                    continue;
                }
                var role = e.getData(dev.awaken.core.api.CoreApi.MOB_ROLE.get());
                if (role.role() != w[1] || !role.gateId().equals("test")) h.fail(w[0] + " came as " + role.role() + " of gate '" + role.gateId() + "'");
                e.discard();
            }
            if (dev.awaken.core.api.MobHooks.spawnNamed(level, "hive_regent", at, 3, Set.of(), "test") != null) h.fail("a pack could name a Lord");
            if (dev.awaken.core.api.MobHooks.spawnNamed(level, "no_such_thing", at, 3, Set.of(), "test") != null) h.fail("a name nothing has came as something");
            h.succeed();
        });
        AwakenTests.register("realm_route_read_off_sites", 200, h -> {
            Land land = amber(1);
            int dens = land.sites(Land.SiteKind.DEN).size(), wardens = land.sites(Land.SiteKind.WARDEN).size();
            if (land.designed() || land.route().size() != 2 + dens + wardens) h.fail("Amber's run isn't its sites: " + land.route().size() + " legs for " + dens + " dens and " + wardens + " Wardens");
            if (land.legOfDen(0) == null || land.legOfDen(0).kind() != Land.LegKind.FIGHT || land.legOfWarden(0) == null) h.fail("a den or a Warden has no leg");
            if (Math.abs(RealmLive.wardenShare(land) - 1 / 6.0) > 1e-9 || RealmLive.hiddenShare(land) != 0) h.fail("a land with three Wardens and nothing hidden pays as it did");
            Land crypt = hour(1);
            if (Math.abs(3 * RealmLive.wardenShare(crypt) + RealmLive.hiddenShare(crypt) - 0.55) > 1e-9) h.fail("the Crypt's Wardens and its reliquary don't bank 55% between them");
            h.succeed();
        });
        AwakenTests.register("realm_traps_keep_time", 20, h -> {
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            Land.Trap press = new Land.Trap("t", Land.TrapKind.PRESS, 0, 0, 0, 0, 2, 4, 100, 70, air, air), spit = new Land.Trap("t", Land.TrapKind.SPIT, 0, 0, 0, 5, 0, 0, 60, 20, air, air);
            // phase 70 of 100: down from tick 30 to 60, warning for the second and a half before
            if (RealmTraps.pressDown(press, 29) || !RealmTraps.pressDown(press, 30) || !RealmTraps.pressDown(press, 59) || RealmTraps.pressDown(press, 60)) h.fail("a press isn't down for its second and a half");
            if (!RealmTraps.pressWarns(press, 10) || RealmTraps.pressWarns(press, 40) || RealmTraps.pressWarns(press, 95)) h.fail("a press doesn't warn just before it comes");
            if (!RealmTraps.spitLit(spit, 25) || !RealmTraps.spitFires(spit, 45) || RealmTraps.spitFires(spit, 55) || RealmTraps.spitLit(spit, 0)) h.fail("a spitter doesn't light, strike and rest in turn");
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
        // the bell's tongue is hung once, as displays, and a toll's first swing gives every one of them somewhere new to go
        AwakenTests.register("realm_hour_clapper_swings", 200, h -> {
            net.minecraft.server.level.ServerLevel level = h.getLevel();
            net.minecraft.core.BlockPos at = h.absolutePos(new net.minecraft.core.BlockPos(1, 6, 1));
            List<net.minecraft.world.entity.Display.BlockDisplay> parts = RealmClapper.hang(level, at);
            if (parts.size() != RealmClapper.PARTS) h.fail("the clapper has " + parts.size() + " displays, not " + RealmClapper.PARTS);
            if (!RealmClapper.hang(level, at).equals(parts)) h.fail("a second waking hung a second clapper");
            List<String> rest = parts.stream().map(d -> RealmClapper.heading(level, d)).toList();
            if (RealmClapper.target(0) == 0 || RealmClapper.target(1) != -RealmClapper.target(0) || RealmClapper.target(RealmToll.SHUT / RealmClapper.SWING - 1) != 0 || RealmClapper.target(-1) != 0)
                h.fail("a toll's swings don't go out, back, and end straight");
            RealmClapper.swing(level, parts, RealmClapper.target(0), RealmClapper.SWING);
            for (int i = 0; i < parts.size(); i++) if (RealmClapper.heading(level, parts.get(i)).equals(rest.get(i))) h.fail("display " + i + " of the clapper has nowhere new to swing to");
            RealmClapper.swing(level, parts, 0, 1);
            for (int i = 0; i < parts.size(); i++) if (!RealmClapper.heading(level, parts.get(i)).equals(rest.get(i))) h.fail("display " + i + " of the clapper doesn't hang straight again");
            parts.forEach(net.minecraft.world.entity.Entity::discard);
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
        // Open lands off (Euan 12 Nov, SPEC §50.7 and §38.11): no theme at any rank rolls a Realm under the open sky or dresses
        // a floor as an open world, and every program said to be under a roof draws a land that is. The same sweep with the
        // switch on does find open ones, so it is the switch that keeps them out. The real catalogues are read from the data
        // here, since other tests leave a stand-in behind.
        AwakenTests.register("realm_open_lands_off", 1200, h -> {
            var server = h.getLevel().getServer();
            var rm = server.getResourceManager();
            Map<String, Blueprint> realBps = new java.util.TreeMap<>();
            net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener.scanDirectory(rm, net.minecraft.resources.FileToIdConverter.json("realms"),
                    com.mojang.serialization.JsonOps.INSTANCE, Blueprint.CODEC, new java.util.HashMap<>()).forEach((k, v) -> {
                if (v.problem().isEmpty()) realBps.put(k.toString(), v);
            });
            Map<String, dev.awaken.gates.worlds.GateWorld> realWorlds = new java.util.TreeMap<>();
            net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener.scanDirectory(rm, net.minecraft.resources.FileToIdConverter.json("gate_worlds"),
                    com.mojang.serialization.JsonOps.INSTANCE, dev.awaken.gates.worlds.GateWorld.CODEC, new java.util.HashMap<>()).forEach((k, v) -> {
                if (v.problem().isEmpty()) realWorlds.put(k.toString(), v);
            });
            if (realBps.isEmpty() || realWorlds.isEmpty()) h.fail("no Realms or gate worlds in the data");
            Map<String, Blueprint> wasBps = Blueprints.all();
            Map<String, dev.awaken.gates.worlds.GateWorld> wasWorlds = dev.awaken.gates.worlds.GateWorlds.all();
            boolean realms = dev.awaken.core.AwakenConfig.GATE_REALMS.get(), worlds = dev.awaken.core.AwakenConfig.GATE_WORLDS.get(),
                    lands = dev.awaken.core.AwakenConfig.GATE_OPEN_LANDS.get();
            List<String> bad = new java.util.ArrayList<>();
            try {
                Blueprints.replace(realBps);
                dev.awaken.gates.worlds.GateWorlds.replace(realWorlds);
                dev.awaken.core.AwakenConfig.GATE_REALMS.set(true);
                dev.awaken.core.AwakenConfig.GATE_WORLDS.set(true);
                boolean sky = dev.awaken.gates.GateService.hasSky(server);
                for (boolean on : new boolean[]{true, false}) {
                    dev.awaken.core.AwakenConfig.GATE_OPEN_LANDS.set(on);
                    Map<String, Rank> rolled = new java.util.TreeMap<>();
                    Set<String> dressed = new java.util.TreeSet<>();
                    for (Theme t : Theme.values()) {
                        for (Rank r : Rank.values()) {
                            if (!t.fits(r)) continue;
                            for (long s = 0; s < 24; s++) {
                                long seed = s * 7919L + t.ordinal() * 131L + r.ordinal();
                                var plan = new dev.awaken.gates.content.GatePlan(seed, dev.awaken.gates.content.GatePlan.CONTENT_VERSION, r, t,
                                        dev.awaken.gates.content.Layout.LABYRINTH, List.of(), dev.awaken.gates.content.Twist.NONE, 3, t.bossId(),
                                        t == Theme.HIVE ? Theme.BELL_CRYPT : Theme.HIVE, 0);
                                Realms.choose(server, plan, false, Set.of()).ifPresent(id -> rolled.putIfAbsent(id, r));
                                for (var fw : dev.awaken.gates.worlds.GateWorlds.dress(server, plan, java.util.Optional.empty()).worlds()) dressed.add(fw.world());
                            }
                        }
                    }
                    List<String> openRolled = rolled.keySet().stream().filter(id -> Blueprints.openLand(realBps.get(id))).toList();
                    List<String> openDressed = dressed.stream().filter(id -> realWorlds.get(id).open()).toList();
                    if (on) {
                        if (openRolled.isEmpty()) bad.add("with the switch on no open Realm was rolled at all (is the Realms dimension there?)");
                        if (sky && openDressed.isEmpty()) bad.add("with the switch on no floor was dressed as an open world");
                        continue;
                    }
                    if (!openRolled.isEmpty()) bad.add("open lands are off and these Realms were rolled: " + openRolled);
                    if (!openDressed.isEmpty()) bad.add("open lands are off and floors were dressed as: " + openDressed);
                    // what was rolled must really be under a roof: draw each once
                    for (var e : rolled.entrySet()) {
                        Land land = RealmPlan.of(e.getKey(), realBps.get(e.getKey()), e.getValue(), 1, RealmPlan.CURRENT_VERSION).land();
                        if (RealmCountry.has(land)) bad.add(e.getKey() + " (" + realBps.get(e.getKey()).land().program() + ") is said to be under a roof but its land is open: list it in LandPrograms");
                    }
                    dev.awaken.Awaken.LOG.info("[realms] open lands off: {} Realms can roll ({}), {} floor worlds", rolled.size(), String.join(", ", rolled.keySet()), dressed.size());
                }
            } finally {
                Blueprints.replace(wasBps);
                dev.awaken.gates.worlds.GateWorlds.replace(wasWorlds);
                dev.awaken.core.AwakenConfig.GATE_REALMS.set(realms);
                dev.awaken.core.AwakenConfig.GATE_WORLDS.set(worlds);
                dev.awaken.core.AwakenConfig.GATE_OPEN_LANDS.set(lands);
            }
            if (!bad.isEmpty()) h.fail(String.join("; ", bad));
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
        // a Realm is a gate: nobody digs through one of its doors, walls or floors, and nobody builds a way over a gap (SPEC §17's protection, §50.9)
        AwakenTests.registerIsolated("realm_blocks_cannot_be_dug", 200, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER));
            var g = dev.awaken.gates.GateLiveTests.standUp(h, dev.awaken.gates.GateLiveTests.plan(4646, Theme.HIVE, dev.awaken.gates.content.Layout.LABYRINTH, dev.awaken.gates.content.Twist.NONE, 0));
            AwakenTests.Live live = null;
            try {
                dev.awaken.gates.GateLiveTests.useLevel(level);
                Realms.attach(server, g, "awaken:amber_reach");
                // a real survival player: a test's mock player always answers that it is creative, and creative may break anything
                live = AwakenTests.survivalPlayer(h, "Digger", net.minecraft.core.BlockPos.ZERO);
                var p = live.player();
                var at = Realms.entrance(g).above(3);
                h.assertTrue(dev.awaken.gates.GateService.inGate(level, at), "a Realm's ground counts as inside a gate");
                var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                var dug = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.block.BreakBlockEvent(level, at, stone, p));
                h.assertTrue(dug.isCanceled(), "a block of a Realm was dug out by hand");
                var placed = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(
                        net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, at), stone, p));
                h.assertTrue(placed.isCanceled(), "a block was placed in a Realm");
            } finally {
                if (live != null) live.leave();
                dev.awaken.gates.GateLiveTests.teardown(level, null, g);
                dev.awaken.gates.GateLiveTests.useLevel(null);
            }
            h.succeed();
        });
        // the Lord's fight holds through a leap past the heart's line, a pause, a save and a reload, and ends only when nobody
        // is left in it (SPEC §68.6)
        AwakenTests.register("realm_fight_holds", 400, h -> {
            h.assertTrue(RealmLive.outOfFight(true, true, true, 0) == null, "a fighter at the hall is in the fight");
            h.assertTrue(RealmLive.outOfFight(true, true, true, RealmLive.AWAY_TICKS) == null, "a leap or a step out of the door keeps you in the fight");
            h.assertTrue("went far from the hall".equals(RealmLive.outOfFight(true, true, true, RealmLive.AWAY_TICKS + 1)), "away from the hall for long: out");
            h.assertTrue("fell".equals(RealmLive.outOfFight(true, false, true, 0)), "falling is out at once");
            h.assertTrue("left the Realm".equals(RealmLive.outOfFight(true, true, false, 0)), "leaving the Realm is out at once");
            h.assertTrue(RealmLive.outOfFight(false, false, false, 20) == null, "a moment offline (a reload) is still in the fight");
            h.assertTrue("left the game".equals(RealmLive.outOfFight(false, false, false, RealmLive.AWAY_TICKS + 1)), "offline for long: out");
            // who is in the fight is saved with the Realm, and a save from before it still loads
            RealmState st = new RealmState();
            st.lordWoken = st.doorShut = st.heartOpen = true;
            java.util.UUID u = java.util.UUID.randomUUID();
            st.fighters.add(u);
            var tag = RealmState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, st).getOrThrow();
            RealmState back = RealmState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.fighters.contains(u) && back.doorShut && back.lordWoken, "the fight came back from the save as it was");
            ((net.minecraft.nbt.CompoundTag) tag).remove("fighters");
            h.assertTrue(RealmState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow().fighters.isEmpty(), "an older save loads with nobody listed");
            // the old rule's trap: a rolled crawl's hall has floor past the heart's line, where one look ended the fight
            Blueprint bp = new Blueprint(1, "t", "dungeon", "hive", List.of("E"), new Blueprint.LandSpec("awaken:cinder_workings", 0, Map.of()),
                    AMBER.sky(), AMBER.life(), AMBER.folk(), AMBER.law(), AMBER.sites(), AMBER.heart(), AMBER.moods(), "");
            Land land = LandPrograms.make(new Land.Ctx(bp, "awaken:cinder_workings", Rank.E, 383, 1));
            Map<Long, V> painted = new java.util.HashMap<>();
            int[] edge = RealmLive.pastTheLine(land, (x, y, z) -> {
                int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
                V v = painted.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> {
                    V nv = new V(cx * 16, cz * 16);
                    land.paint(nv);
                    return nv;
                });
                return solid(v.get(x, y, z));
            });
            h.assertTrue(edge != null, "the Lord's hall has floor past the heart's line");
            dev.awaken.Awaken.LOG.info("[realms] cinder_workings 383: past the heart's line at {} {} {}, {} blocks from the Lord", edge[0], edge[1], edge[2], edge[3]);
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
