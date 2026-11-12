package dev.awaken.gates.realms;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.realms.check.FloatCheck;
import dev.awaken.gates.realms.check.MoveProof;
import dev.awaken.gates.realms.check.RouteCheck;
import dev.awaken.gates.realms.check.WalkCheck;
import dev.awaken.gates.realms.kit.crawl.BeatLine;
import dev.awaken.gates.realms.kit.crawl.Beats;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.gates.realms.kit.crawl.CrawlLand;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import dev.awaken.gates.realms.kit.crawl.Sculpt;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.progression.Rank;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gates in Motion's checks (SPEC §72): every beat alone at its smallest and largest, facing each way, proved with the
 * body's own rules on plain stone; every rolled kind's crawl on today's plan, walked, routed, float-checked and with no
 * move it doesn't need; a gate opened before the beats keeps its layout; every kind's skin keeps the shapes the beats
 * were proved on; the Broodwarren's slide; a fall on a line; what a player has been shown, saved.
 */
public final class BeatTests {
    private BeatTests() {}

    /** Every skin on the crawl generator. */
    static final String[] CRAWLS = {"awaken:brood_burrow", "awaken:catacombs", "awaken:hollow_hills", "awaken:root_cellars", "awaken:tallow_runs",
            "awaken:sunken_stacks", "awaken:sporewells", "awaken:cinder_workings", "awaken:frozen_cells", "awaken:glass_warrens", "awaken:stopped_works",
            "awaken:silvered_halls", "awaken:sealed_wing", "awaken:sleepless_ward", "awaken:unlit_stair", "awaken:rime_hollows", "awaken:green_dark"};

    // the gate ranks (a Sovereign gate is none of these crawls)
    private static final Rank[] RANKS = {Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S};
    private static final int[][] FACINGS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    static Land crawl(String program, Rank rank, long seed, int version) {
        Blueprint bp = new Blueprint(1, "t", "dungeon", program.endsWith("catacombs") ? "bell_crypt" : "hive", List.of("E"), new Blueprint.LandSpec(program, 0, Map.of()),
                new Blueprint.Sky(List.of("awaken:realm/amber_meadow"), "amber_reach", List.of()), new Blueprint.Life(List.of("g"), List.of("p"), List.of("a")),
                new Blueprint.Folk("wax_folk", 1), new Blueprint.Law("updrafts", Map.of()), new Blueprint.Sites(3, 0, 24),
                new Blueprint.Heart("hive_regent", "mother_comb"), List.of("late_harvest"), "");
        return LandPrograms.make(new Land.Ctx(bp, program, rank, seed, version));
    }

    static Land brood(int version) {
        return LandPrograms.make(new Land.Ctx(new Blueprint(1, "t", "dungeon", "hive", List.of("E"), new Blueprint.LandSpec("awaken:broodwarren", 0, Map.of()),
                new Blueprint.Sky(List.of("awaken:realm/amber_meadow"), "amber_reach", List.of()), new Blueprint.Life(List.of("g"), List.of("p"), List.of("a")),
                new Blueprint.Folk("wax_folk", 1), new Blueprint.Law("updrafts", Map.of()), new Blueprint.Sites(3, 0, 24),
                new Blueprint.Heart("hive_regent", "mother_comb"), List.of("late_harvest"), ""), "awaken:broodwarren", Rank.E, 1, version));
    }

    public static void register() {
        // a beat alone, at its smallest and its largest, at its own rank and at S, both sides, facing each of the four ways:
        // every move it asks for holds with the body's rules on plain stone, the slow way and the fast way
        AwakenTests.register("beats_each_holds_alone", 2400, h -> {
            List<String> bad = new ArrayList<>(), never = new ArrayList<>();
            int built = 0, proved = 0;
            for (Beat b : Beat.values()) {
                boolean any = false;
                for (Rank rank : new Rank[]{b.rank, Rank.S}) for (double t : new double[]{0, 1}) for (int side : new int[]{1, -1}) for (int[] f : FACINGS) {
                    BeatLine L = new BeatLine("t", 0, 64, 0, f[0], f[1]);
                    Beats.pad(L, 4);
                    if (!Beats.build(b, L, t, side, rank, new Random(72L + b.ordinal() * 31L + rank.ordinal()))) continue;
                    any = true;
                    built++;
                    Beats.pad(L, 4);
                    L.s.done();
                    List<String> fails = MoveProof.failures(L.s.view(Sculpt.PLAIN), L.moves, rank);
                    proved += L.moves.size();
                    String at = b.name() + " at " + rank + " size " + t + " side " + side + " facing " + f[0] + "," + f[1] + ": ";
                    if (!fails.isEmpty()) bad.add(at + fails.getFirst());
                    if (b.onTheWay() && b.asks() && L.moves.stream().noneMatch(Land.Move::needed)) bad.add(at + "asks for nothing");
                }
                if (!any) never.add(b.name());
            }
            for (String line : bad) Awaken.LOG.error("[beats] alone: {}", line);
            // what each size of a beat with no shape failed on, so the log says what to change
            for (String n : never) for (Rank rank : new Rank[]{Beat.valueOf(n).rank, Rank.S}) for (String why : Beats.whyNoShape(Beat.valueOf(n), 1, rank))
                Awaken.LOG.error("[beats] no shape: {} at {} {}", n, rank, why);
            if (!never.isEmpty()) h.fail("these beats never found a shape: " + never);
            if (!bad.isEmpty()) h.fail(bad.size() + " beats don't hold alone, the first: " + bad.getFirst());
            Awaken.LOG.info("[beats] alone: {} built, {} moves proved", built, proved);
            h.succeed();
        });

        // every kind on today's plan, at a rank that turns with the kind (so every rank is tried), and the six highest on a
        // second seed at S: each walks to everything, passes the route check, floats nothing, and asks for no move a walk
        // doesn't need; across them every family turns up and few lines fall back
        AwakenTests.register("beats_crawls_play", 9600, h -> {
            List<String> bad = new ArrayList<>();
            EnumMap<Land.Family, Integer> fams = new EnumMap<>(Land.Family.class);
            int lines = 0, fell = 0, holds = 0, lessons = 0, runs = 0;
            Map<String, Integer> shafts = new java.util.TreeMap<>();
            for (int k = 0; k < CRAWLS.length + 6; k++) {
                String program = CRAWLS[k % CRAWLS.length];
                Rank rank = k < CRAWLS.length ? RANKS[k % RANKS.length] : Rank.S;
                long seed = k < CRAWLS.length ? 1 : 2;
                Land land = crawl(program, rank, seed, RealmPlan.CURRENT_VERSION);
                String at = program + " " + rank + " seed " + seed + ": ";
                runs++;
                var walk = WalkCheck.walk(land);
                if (!walk.unreachable().isEmpty()) bad.add(at + "can't reach " + walk.unreachable());
                // where the walk stops on a line, so the log says which move or cell to look at
                for (String st : WalkCheck.stuck(land, walk)) Awaken.LOG.error("[beats] stuck: {}{}", at, st);
                var route = RouteCheck.check(land, walk);
                if (!route.problems().isEmpty()) bad.add(at + route.summary() + ": " + route.problems());
                int half = land.halfSize();
                FloatCheck.Report r = FloatCheck.scan(land, -half, -half, half, half);
                if (!r.clean()) bad.add(at + r.summary());
                for (String n : WalkCheck.needlessMoves(land)) bad.add(at + "needless " + n);
                if (land.lines().isEmpty()) bad.add(at + "no lines at all");
                // the families each line has a move of: asked for (wall, ledge, grip, low) or the way's flow, which anyone can take
                Map<String, java.util.Set<Land.Family>> byLine = new java.util.HashMap<>();
                for (Land.Move mv : land.moves()) byLine.computeIfAbsent(mv.leg(), x -> java.util.EnumSet.noneOf(Land.Family.class)).add(mv.family());
                for (Land.Line l : land.lines()) {
                    lines++;
                    if (l.fallback()) fell++;
                    for (Land.Family f : byLine.getOrDefault(l.leg(), java.util.Set.of())) fams.merge(f, 1, Integer::sum);
                }
                if (land instanceof GeneratedCrawl<?> g) for (String u : g.used()) if (u.startsWith("well ") || u.startsWith("shaft ")) shafts.merge(u.substring(0, u.indexOf(' ')), 1, Integer::sum);
                holds += land.holds().size();
                lessons += land.lessons().size();
                if (land instanceof GeneratedCrawl<?> g) for (String n : g.beatRun().log()) Awaken.LOG.info("[beats] {}fallback {}", at, n);
            }
            for (String line : bad) Awaken.LOG.error("[beats] crawl: {}", line);
            Awaken.LOG.info("[beats] crawls: {} runs, {} lines, {} fell back, {} holds, {} lessons, families {}, shafts that are wells {}", runs, lines, fell, holds, lessons, fams, shafts);
            if (!bad.isEmpty()) h.fail(bad.size() + " crawls are wrong, the first: " + bad.getFirst());
            for (Land.Family f : List.of(Land.Family.LOW, Land.Family.WALL, Land.Family.LEDGE, Land.Family.GRIP, Land.Family.FLOW)) {
                if (!fams.containsKey(f)) h.fail("no line asked for anything of the " + f + " family");
            }
            // the sweep's bar is 3% of legs; a sample this small is let off to a tenth
            if (lines > 0 && fell * 10 > lines) h.fail(fell + " of " + lines + " lines fell back to the old crossing");
            if (holds == 0) h.fail("no line has a fight on it");
            if (lessons == 0) h.fail("no line teaches anything");
            h.succeed();
        });

        // a gate opened before the beats (plan 2, and plan 1 before it) keeps its layout: no lines, no holds, and the same
        // route, moves and courses as plan 1 draws, on three kinds and two seeds
        AwakenTests.register("beats_old_plans_unchanged", 2400, h -> {
            for (String program : new String[]{"awaken:catacombs", "awaken:frozen_cells", "awaken:silvered_halls"}) for (long seed : new long[]{1, 2}) {
                Land one = crawl(program, Rank.C, seed, 1), two = crawl(program, Rank.C, seed, RealmPlan.BEFORE_BEATS);
                String at = program + " seed " + seed + ": ";
                if (!two.lines().isEmpty() || !two.holds().isEmpty() || !two.lessons().isEmpty()) h.fail(at + "plan 2 has lines, holds or lessons");
                if (!legs(one).equals(legs(two))) h.fail(at + "plan 2's route isn't plan 1's");
                if (one.moves().size() != two.moves().size() || one.courses().size() != two.courses().size()) h.fail(at + "plan 2's moves or courses aren't plan 1's");
                Land three = crawl(program, Rank.C, seed, RealmPlan.CURRENT_VERSION);
                if (three.lines().isEmpty()) h.fail(at + "today's plan has no lines");
            }
            h.succeed();
        });

        // every kind's blocks for what a move stands on, hangs from, slides under or runs along have the plain shapes the
        // beats were proved on (a slab for a lip would let a jump through)
        AwakenTests.register("beats_skins_keep_their_shapes", 400, h -> {
            List<String> bad = new ArrayList<>();
            for (String program : CRAWLS) {
                if (!(crawl(program, Rank.C, 1, RealmPlan.CURRENT_VERSION) instanceof CrawlLand<?> c)) {
                    bad.add(program + " isn't a crawl");
                    continue;
                }
                for (String s : c.beatShapes()) bad.add(program + ": " + s);
            }
            if (brood(RealmPlan.CURRENT_VERSION) instanceof CrawlLand<?> c) for (String s : c.beatShapes()) bad.add("awaken:broodwarren: " + s);
            if (!bad.isEmpty()) h.fail(bad.size() + " skins change a shape, the first: " + bad.getFirst());
            h.succeed();
        });

        // the Broodwarren since plan 3 (SPEC §72.7): its throat starts with the root to slide under, which is laid, proved,
        // needed and taught; the nest still walks, routes and floats nothing; before plan 3 it is as it was
        AwakenTests.register("beats_brood_slide", 2400, h -> {
            Land land = brood(RealmPlan.CURRENT_VERSION);
            var walk = WalkCheck.walk(land);
            if (!walk.unreachable().isEmpty()) h.fail("can't reach " + walk.unreachable());
            var route = RouteCheck.check(land, walk);
            if (!route.problems().isEmpty()) h.fail(route.summary() + ": " + route.problems());
            int half = land.halfSize();
            FloatCheck.Report r = FloatCheck.scan(land, -half, -half, half, half);
            if (!r.clean()) h.fail(r.summary());
            if (land.lines().stream().noneMatch(l -> l.leg().equals("throat_slide") && !l.fallback())) h.fail("the throat has no slide");
            if (land.lessons().stream().noneMatch(l -> l.family() == Land.Family.LOW)) h.fail("the slide teaches nothing");
            List<String> needless = WalkCheck.needlessMoves(land);
            if (!needless.isEmpty()) h.fail("a walk gets round the slide: " + needless.getFirst());
            if (!brood(1).lines().isEmpty()) h.fail("the Broodwarren before plan 3 has a line");
            h.succeed();
        });

        // a fall on a line is caught three under the lowest ledge near it; a drop the line asks for, onto a ledge, never is
        AwakenTests.register("beats_fall_is_caught_under_the_ledges", 20, h -> {
            Land.Course c = new Land.Course("t", -10, 40, -10, 10, 80, 10, List.of(new int[]{0, 70, 0}, new int[]{0, 64, 4}), false, 50);
            if (RealmRun.fell(c, 66.5, 0, 0)) h.fail("a drop of four toward the lower ledge was caught");
            if (!RealmRun.fell(c, 60.5, 0, 1)) h.fail("a fall past the lower ledge wasn't caught");
            if (!RealmRun.fell(c, 49.5, 9, 9)) h.fail("a fall into the pit with no ledge near wasn't caught");
            if (RealmRun.fell(c, 55.5, 9, 9)) h.fail("a body over the pit's floor with no ledge near was caught");
            h.succeed();
        });

        // what the gold figure has shown is kept: schema 4 saves it, and a save from before it reads as nothing shown
        AwakenTests.register("beats_shown_is_saved", 20, h -> {
            GateMemory m = new GateMemory();
            m.beatsShown.add("LOW");
            m.beatsShown.add("WALL");
            Tag saved = GateMemory.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, m).getOrThrow();
            GateMemory back = GateMemory.MAP_CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
            if (!back.beatsShown.equals(m.beatsShown)) h.fail("saved " + m.beatsShown + ", read " + back.beatsShown);
            if (saved instanceof net.minecraft.nbt.CompoundTag ct) {
                ct.remove("d");
                GateMemory old = GateMemory.MAP_CODEC.codec().parse(NbtOps.INSTANCE, ct).getOrThrow();
                if (!old.beatsShown.isEmpty()) h.fail("a save from before schema 4 read as " + old.beatsShown);
            } else {
                h.fail("the memory didn't save as a compound");
            }
            h.succeed();
        });
    }

    private static List<String> legs(Land land) {
        List<String> out = new ArrayList<>();
        for (Land.Leg l : land.route()) out.add(l.id() + "@" + l.x() + "," + l.y() + "," + l.z());
        return out;
    }
}
